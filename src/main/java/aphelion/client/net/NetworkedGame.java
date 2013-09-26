/*
 * Aphelion
 * Copyright (c) 2013  Joris van der Wel
 * 
 * This file is part of Aphelion
 * 
 * Aphelion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * 
 * Aphelion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with Aphelion.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * In addition, the following supplemental terms apply, based on section 7 of
 * the GNU Affero General Public License (version 3):
 * a) Preservation of all legal notices and author attributions
 * b) Prohibition of misrepresentation of the origin of this material, and
 * modified versions are required to be marked in reasonable ways as
 * different from the original version (for example by appending a copyright notice).
 * 
 * Linking this library statically or dynamically with other modules is making a
 * combined work based on this library. Thus, the terms and conditions of the
 * GNU Affero General Public License cover the whole combination.
 * 
 * As a special exception, the copyright holders of this library give you 
 * permission to link this library with independent modules to produce an 
 * executable, regardless of the license terms of these independent modules,
 * and to copy and distribute the resulting executable under terms of your 
 * choice, provided that you also meet, for each linked independent module,
 * the terms and conditions of the license of that module. An independent
 * module is a module which is not derived from or based on this library.
 */
package aphelion.client.net;

import aphelion.client.graphics.world.ActorShip;
import aphelion.shared.resource.ResourceDB;
import aphelion.client.graphics.world.MapEntities;
import aphelion.shared.net.protocols.GameListener;
import aphelion.shared.net.protocols.GameProtocolConnection;
import aphelion.shared.net.WS_CLOSE_STATUS;
import aphelion.shared.net.protobuf.GameC2S;
import aphelion.shared.net.protobuf.GameC2S.Authenticate;
import aphelion.shared.net.protobuf.GameC2S.ConnectionReady;
import aphelion.shared.net.protobuf.GameC2S.TimeRequest;
import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.net.protobuf.GameS2C;
import aphelion.shared.net.protobuf.GameS2C.AuthenticateResponse;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.physics.WEAPON_SLOT;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import aphelion.shared.event.ClockSource;
import aphelion.shared.event.TickEvent;
import aphelion.shared.event.TickedEventLoop;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class NetworkedGame implements GameListener, TickEvent
{
        private static final Logger log = Logger.getLogger(NetworkedGame.class.getName());
        private static final int SEND_MOVE_DELAY = 5; // in ticks
        private static final long CLOCKSYNC_DELAY = 1 * 30 * 1000000000L; // in nano seconds. clock sync also measures lag
        
        private ResourceDB resourceDB;
        private TickedEventLoop loop;
        private String nickname;
        private PhysicsEnvironment physicsEnv;
        private MapEntities mapEntities;
        private GameProtocolConnection game;
        private ClockSync clockSync;
        private int initial_timeSync_count = 0;
        private STATE state;
        private int myPid = -1;
        private long physics_tick_offset; // the tick difference between the local PhysicsEnvironment ticks and the servers PhysicsEnvironment ticks
        private GameC2S.C2S.Builder pendingOutgoingMove;
        private long clockSync_lastSync_nano;
        private long clockSync_lastRTT_nano;
        private LinkedList<GameS2C.S2C> arenaSyncOperationQueue;
        private HashSet<Integer> unknownActorRemove = new HashSet<>(); // Assumes PIDs are unique. ActorNew is never called twice with the same PID.
        private WS_CLOSE_STATUS disconnect_code; // null if unknown
        private String disconnect_reason; // null unknown
        private AuthenticateResponse.ERROR auth_error;
        private String auth_error_desc;
        
        private static enum STATE
        {
                // the order in this enum is also the order the states will move through.
                ESTABLISHING (1), // busy establishing sockets and getting a session token
                ESTABLISHED (2), // we now have a session token and atleast 1 websocket
                SEND_AUTHENTICATE (3),
                WAIT_AUTHENTICATE (4),
                INITIAL_TIME_SYNC (5), // Busy with the initial time sync
                INITIAL_TIME_SYNC_DONE (6),
                SEND_CONNECTION_READY (7),
                WAIT_FOR_ARENALOAD (8), // we are loading the map, etc
                SEND_ARENA_LOADED (9),
                WAIT_FOR_ARENASYNC (10), // server is busy sending us arena sync data
                RECEIVED_ARENASYNC (11),
                READY (12), // ready to play the game
                AUTH_FAILED (20),
                DISCONNECTED (21);
                
                int sequence;

                private STATE(int sequence)
                {
                        this.sequence = sequence;
                }
                
                public boolean hasOccured(STATE other)
                {
                        return this.sequence >= other.sequence;
                }
        }

        public NetworkedGame(ResourceDB resourceDB, TickedEventLoop loop, String nickname)
        {
                this.resourceDB = resourceDB;
                this.loop = loop;
                this.nickname = nickname;
                loop.addTickEvent(this);
                nextState(STATE.ESTABLISHING);
        }

        public void registerArenaResources(PhysicsEnvironment physicsEnv, MapEntities mapEntities)
        {
                if (this.state != STATE.WAIT_FOR_ARENALOAD)
                {
                        throw new IllegalStateException();
                }

                if (physicsEnv.getTick() != 0)
                {
                        throw new IllegalStateException();
                }

                this.physicsEnv = physicsEnv;
                this.mapEntities = mapEntities;
                
                nextState(STATE.SEND_ARENA_LOADED);
        }

        public boolean isConnecting()
        {
                // all states before WAIT_FOR_ARENALOAD count as "connecting"
                // DISCONNECTED should return false!
                return !state.hasOccured(STATE.WAIT_FOR_ARENALOAD);
        }
        
        public boolean isDownloading()
        {
                return false;
        }

        public boolean isReady()
        {
                return state == STATE.READY;
        }

        public boolean isDisconnected()
        {
                // no way out of this state, you should create a new object
                return state == STATE.DISCONNECTED;
        }

        public WS_CLOSE_STATUS getDisconnectCode()
        {
                return disconnect_code;
        }

        public String getDisconnectReason()
        {
                return disconnect_reason;
        }

        public AuthenticateResponse.ERROR getAuthError()
        {
                return auth_error;
        }

        public String getAuthErrorDesc()
        {
                return auth_error_desc;
        }
        
        
        

        public ClockSource getSyncedClockSource()
        {
                return clockSync;
        }

        private void nextState(STATE newState)
        {
                if (this.state == newState)
                {
                        throw new IllegalStateException();
                }

                this.state = newState;
                
                //System.out.println("Client new state: " + newState);

                GameC2S.C2S.Builder c2s;
                
                switch (state)
                {
                        case ESTABLISHING:
                                disconnect_code = null;
                                disconnect_reason = null;
                                auth_error = null;
                                auth_error_desc = null;
                                break;

                        case ESTABLISHED:
                                nextState(STATE.SEND_AUTHENTICATE);
                                break;
                                
                        case SEND_AUTHENTICATE:
                                c2s = GameC2S.C2S.newBuilder();
                                Authenticate.Builder auth = c2s.addAuthenticateBuilder();
                                auth.setAuthMethod(Authenticate.AUTH_METHOD.NONE);
                                auth.setNickname(nickname);
                                game.send(c2s);
                                
                                nextState(STATE.WAIT_AUTHENTICATE);
                                break;
                                
                        case WAIT_AUTHENTICATE:
                                break;

                        case INITIAL_TIME_SYNC:
                                sendTimeSync();
                                break;

                        case INITIAL_TIME_SYNC_DONE:
                                nextState(STATE.SEND_CONNECTION_READY);
                                break;

                        case SEND_CONNECTION_READY:
                                c2s = GameC2S.C2S.newBuilder();
                                ConnectionReady.Builder readyRequest = c2s.addConnectionReadyBuilder();
                                game.send(c2s);

                                nextState(STATE.WAIT_FOR_ARENALOAD);

                                break;
                        
                        case WAIT_FOR_ARENALOAD:
                                break;
                        
                        case SEND_ARENA_LOADED:
                                c2s = GameC2S.C2S.newBuilder();
                                GameC2S.ArenaLoaded.Builder loadedRequest = c2s.addArenaLoadedBuilder();
                                game.send(c2s);
                                
                                nextState(STATE.WAIT_FOR_ARENASYNC);
                                break;

                        case WAIT_FOR_ARENASYNC:
                                arenaSyncOperationQueue = new LinkedList<>();
                                break;

                        case RECEIVED_ARENASYNC:
                                
                                for (GameS2C.S2C s2c : arenaSyncOperationQueue)
                                {
                                        parseOperation(s2c);
                                }
                                arenaSyncOperationQueue = null;
                                
                                nextState(STATE.READY);
                                break;

                        case READY:
                                
                                break;

                        case AUTH_FAILED:
                                game.requestClose(WS_CLOSE_STATUS.NORMAL);
                                break;
                                
                        case DISCONNECTED:
                                break;
                }
        }
        
        @Override
        public void gameEstablishFailure(WS_CLOSE_STATUS code, String reason)
        {
                nextState(STATE.DISCONNECTED);
                
                disconnect_code = code;
                disconnect_reason = reason;
        }

        @Override
        public void gameNewClient(GameProtocolConnection game)
        {
                if (isDisconnected())
                {
                        log.log(Level.WARNING, "Received gameNewClient while in a disconnected state");
                        return;
                }

                this.game = game;
                clockSync = new ClockSync(30); // limit of 30 means a half hour of history at most.

                nextState(STATE.ESTABLISHED);
        }

        @Override
        public void gameRemovedClient(GameProtocolConnection game)
        {
                game = null;
                nextState(STATE.DISCONNECTED);
        }

        @Override
        public void gameNewConnection(GameProtocolConnection game)
        {
        }

        @Override
        public void gameDropConnection(GameProtocolConnection game, WS_CLOSE_STATUS code, String reason)
        {
        }

        @Override
        public void gameS2CMessage(GameProtocolConnection game, GameS2C.S2C s2c, long receivedAt)
        {
                if (isDisconnected())
                {
                        log.log(Level.WARNING, "Received a message while in a disconnected state");
                        return;
                }

                if (game != this.game)
                {
                        log.log(Level.SEVERE, "Received a message for an invalid GameProtocol (session token). Ignoring message");
                        assert false;
                        return;
                }
                
                for (GameS2C.AuthenticateResponse msg : s2c.getAuthenticateResponseList())
                {
                        if (state != STATE.WAIT_AUTHENTICATE)
                        {
                                log.log(Level.SEVERE, "Received an operation in an invalid state");
                                return;
                        }
                        
                        if (msg.getError() == AuthenticateResponse.ERROR.OK)
                        {
                                log.log(Level.INFO, "Authenticate OK");
                                nextState(STATE.INITIAL_TIME_SYNC);
                        }
                        else
                        {
                                auth_error = msg.getError();
                                auth_error_desc = msg.getErrorDescription();
                                log.log(Level.WARNING, "Authentication failed {0}: {1}", new Object[]{auth_error, auth_error_desc});
                                
                                nextState(STATE.AUTH_FAILED);
                        }
                }

                for (GameS2C.TimeResponse msg : s2c.getTimeResponseList())
                {
                        parseTimeResponse(msg, receivedAt);
                }

                for (GameS2C.ArenaSync msg : s2c.getArenaSyncList())
                {
                        log.log(Level.INFO, "Received ArenaSync");
                        // should only receive this message once (for now)
                        this.myPid = msg.getYourPid();
                        
                        long serverNow = clockSync.nanoTime();
                        
                        // how many ticks ago was the ArenSync message sent by the server? (rounded up)
                        long tickLatency = SwissArmyKnife.divideCeil(serverNow - msg.getCurrentNanoTime(), loop.TICK);

                        // At what tick was our event loop we when the server sent that message?
                        long loopTick = loop.currentTick() - tickLatency;
                        
                        // make sure our loop is synchronized to the servers time value
                        loop.synchronize(msg.getCurrentNanoTime(), loopTick);
                        
                        // At what tick was our physics environment when the server sent that message? (negative is OK)
                        long physicsTick = physicsEnv.getTick() - tickLatency;
                        
                        // By how many ticks must we shift all incomming and outgoing networked physics operations?
                        this.physics_tick_offset = msg.getCurrentTicks() - physicsTick;
                        
                        physicsEnv.setTickOffsetToServer(physics_tick_offset);
                        
                        physicsEnv.actorNew(physicsTick, myPid, msg.getName(), msg.getYourSeed(), msg.getShip());
                        
                        ActorShip ship = new ActorShip(this.resourceDB, physicsEnv.getActor(myPid, 0, true), true, mapEntities); 
                        mapEntities.addShip(ship);

                        
                        nextState(STATE.RECEIVED_ARENASYNC);
                }

                if (s2c.getActorNewCount() > 0 || 
                        s2c.getActorSyncCount() > 0 ||
                        s2c.getActorModificationCount() > 0 ||
                        s2c.getActorRemoveCount() > 0 || 
                        s2c.getActorWarpCount() > 0 ||
                        s2c.getActorMoveCount() > 0 ||
                        s2c.getActorWeaponCount() > 0 ||
                        s2c.getWeaponSyncCount() > 0
                        )
                {
                        
                        if (state == STATE.WAIT_FOR_ARENASYNC)
                        {
                                // special case
                                // An operation might arrive before arenasync
                                // so queue them.
                                // It would be possible to notify the server with another completion message
                                // (something like ARENASYNC_DONE), however that would only move the queue 
                                // onto the server.
                                
                                arenaSyncOperationQueue.add(s2c);
                        }
                        else if (state == STATE.READY)
                        {
                                parseOperation(s2c);
                        }
                        else
                        {
                                log.log(Level.SEVERE, "Received an operation in an invalid state");
                        }
                }
        }
        
        @Override
        public void gameC2SMessage(GameProtocolConnection game, GameC2S.C2S c2s, long receivedAt)
        {
                assert false;
        }
        
        private void parseOperation(GameS2C.S2C s2c)
        {
                // If implementing a new message type here, also add it to a conditional in gameS2CMessage()
                for (GameOperation.ActorNew msg : s2c.getActorNewList())
                {
                        log.log(Level.INFO, "Received ActorNew {0} {1}", new Object[] { msg.getTick(), msg.getPid()});
                        physicsEnv.actorNew(
                                physicsServerTickToLocal(msg.getTick()), 
                                msg.getPid(), msg.getName(), msg.getSeed(), msg.getShip()
                                );
                        
                        if (unknownActorRemove.contains(msg.getPid()))
                        {
                                // Received ActorNew and ActorRemove out of order
                                unknownActorRemove.remove(msg.getPid());
                        }
                        else
                        {
                                ActorShip ship = new ActorShip(this.resourceDB, physicsEnv.getActor(msg.getPid(), 0, true), false, mapEntities);
                                mapEntities.addShip(ship);
                        }
                }
                
                for (GameOperation.ActorSync msg : s2c.getActorSyncList())
                {
                        physicsEnv.actorSync(msg, physics_tick_offset);
                }
                
                for (GameOperation.ActorModification msg : s2c.getActorModificationList())
                {
                        long tick = physicsServerTickToLocal(msg.getTick());
                        
                        physicsEnv.actorModification(tick, msg.getPid(), msg.getShip());
                }

                for (GameOperation.ActorRemove msg : s2c.getActorRemoveList())
                {
                        log.log(Level.INFO, "Received ActorRemove {0} {1}", new Object[] { msg.getTick(), msg.getPid()});
                        physicsEnv.actorRemove(physicsServerTickToLocal(msg.getTick()), msg.getPid());
                        
                        ActorShip ship = mapEntities.getActorShip(msg.getPid());
                        if (ship == null)
                        {
                                // Received ActorNew and ActorRemove out of order
                                // Assumes PIDs are unique. ActorNew is never called twice with the same PID.
                                unknownActorRemove.add(msg.getPid());
                        }
                        else
                        {
                                mapEntities.removeShip(ship);
                        }
                }

                for (GameOperation.ActorWarp msg : s2c.getActorWarpList())
                {
                        //log.log(Level.INFO, "Received ActorWarp {0} {1}", new Object[] { msg.getTick(), msg.getPid()});
                        physicsEnv.actorWarp(
                                physicsServerTickToLocal(msg.getTick()), msg.getPid(), msg.getHint(),
                                msg.getX(), msg.getY(), msg.getXVel(), msg.getYVel(), msg.getRotation(),
                                msg.hasX(), msg.hasY(), msg.hasXVel(), msg.hasYVel(), msg.hasRotation());
                }

                for (GameOperation.ActorMove msg : s2c.getActorMoveList())
                {
                        //log.log(Level.INFO, "Received ActorMove {0} {1}", new Object[] { msg.getTick(), msg.getPid()});
                        long tick = physicsServerTickToLocal(msg.getTick());
                        
                        for (int move : msg.getMoveList())
                        {
                                physicsEnv.actorMove(
                                        tick,
                                        msg.getPid(),
                                        PhysicsMovement.get(move)
                                        );

                                ++tick;
                        }
                        
                        if (msg.getDirect())
                        {
                                ActorShip ship = mapEntities.getActorShip(msg.getPid());
                                if (ship != null)
                                {
                                        // Use the tick of the first move
                                        // This way the render delay does not continuesly drift because 
                                        // of the delayed move update mechanism (SEND_MOVE_DELAY, 
                                        // this is very similar to how Nagle works.)

                                        if (ship.isLocalPlayer())
                                        {
                                                ship.renderDelay.set(0);
                                        }
                                        else
                                        {
                                                ship.renderDelay.setByPositionUpdate(physicsEnv.getTick(), physicsServerTickToLocal(msg.getTick()));
                                        }
                                }
                        }
                }
                
                for (GameOperation.ActorWeapon msg : s2c.getActorWeaponList())
                {
                        long tick = physicsServerTickToLocal(msg.getTick());
                        
                        boolean has_hint = msg.hasX() && msg.hasY() && msg.hasXVel() && msg.hasYVel() && msg.hasSnappedRotation();
                        
                        if (!WEAPON_SLOT.isValidId(msg.getSlot()))
                        {
                                log.log(Level.SEVERE, "Received an ActorWeapon with an invalid slot id {0}", msg.getSlot());
                                continue;
                        }
                        
                        WEAPON_SLOT slot = WEAPON_SLOT.byId(msg.getSlot());
                        
                        physicsEnv.actorWeapon(
                                tick, msg.getPid(), slot,
                                has_hint, 
                                msg.getX(), msg.getY(), 
                                msg.getXVel(), msg.getYVel(), 
                                msg.getSnappedRotation());
                }
                
                for (GameOperation.WeaponSync msg : s2c.getWeaponSyncList())
                {
                        long tick = physicsServerTickToLocal(msg.getTick());
                        
                        physicsEnv.weaponSync(
                                tick, 
                                msg.getPid(), 
                                msg.getWeaponKey(),
                                msg.getProjectilesList().toArray(new GameOperation.WeaponSync.Projectile[0]),
                                this.physics_tick_offset);
                }
                
                
        }
        
        @Override
        public void tick(long tick)
        {
                long now = System.nanoTime();
                
                if (this.pendingOutgoingMove != null)
                {
                        if (physicsCurrentServerTicks() - pendingOutgoingMove.getActorMoveBuilder(0).getTick() 
                                >= SEND_MOVE_DELAY)
                        {
                                game.send(pendingOutgoingMove);
                                pendingOutgoingMove = null;       
                        }
                }
                
                if (isReady() && !isDownloading())
                {
                        if (now - clockSync_lastSync_nano > CLOCKSYNC_DELAY)
                        {
                                sendTimeSync(); // this method should immediately update lastClockSync
                        }
                }
        }

        /** Available after RECEIVED_ARENASYNC
         *
         * @return The pid for the local player
         */
        public int getMyPid()
        {
                if (!state.hasOccured(STATE.RECEIVED_ARENASYNC))
                {
                        throw new IllegalStateException();
                }
                
                return myPid;
        }
        
        public long physicsServerTickToLocal(long serverTick)
        {
                if (!state.hasOccured(STATE.RECEIVED_ARENASYNC))
                {
                        throw new IllegalStateException();
                }
                
                return serverTick - this.physics_tick_offset;
        }
        
        public long physicsLocalTickToServer(long localTick)
        {
                if (!state.hasOccured(STATE.RECEIVED_ARENASYNC))
                {
                        throw new IllegalStateException();
                }
                
                return localTick + this.physics_tick_offset;
        }
        
        public long physicsCurrentServerTicks()
        {
                return physicsLocalTickToServer(physicsEnv.getTick());
        }

        public void sendTimeSync()
        {
                GameC2S.C2S.Builder c2s = GameC2S.C2S.newBuilder();
                TimeRequest.Builder timeRequest = c2s.addTimeRequestBuilder();
                timeRequest.setClientTime(System.nanoTime());
                game.send(c2s);
                clockSync_lastSync_nano = System.nanoTime();
        }
        
        public void sendMove(long tick, PhysicsMovement move)
        {
                if (!move.hasEffect())
                {
                        return;
                }
                
                long serverTick = physicsLocalTickToServer(tick);
                
                if (this.pendingOutgoingMove == null)
                {
                        pendingOutgoingMove = GameC2S.C2S.newBuilder();
                        GameOperation.ActorMove.Builder actorMove = pendingOutgoingMove.addActorMoveBuilder();
                        actorMove.setTick(serverTick);
                        actorMove.setPid(myPid);
                        actorMove.setDirect(true);
                }
                
                GameOperation.ActorMove.Builder actorMove = pendingOutgoingMove.getActorMoveBuilder(0);
                
                for (long t = actorMove.getTick() + actorMove.getMoveCount(); t < serverTick; ++t)
                {
                        // fill the gap
                        actorMove.addMove(0);
                }
                
                actorMove.addMove(move.bits);
                
                if (physicsCurrentServerTicks() - actorMove.getTick() >= SEND_MOVE_DELAY)
                {
                        game.send(pendingOutgoingMove);
                        pendingOutgoingMove = null;
                }
        }
        
        public void sendActorWeapon(long tick, WEAPON_SLOT weaponSlot, PhysicsShipPosition positionHint)
        {
                // todo weapon id                
                
                long serverTick = physicsLocalTickToServer(tick);
                
                GameC2S.C2S.Builder c2s = GameC2S.C2S.newBuilder();
                GameOperation.ActorWeapon.Builder actorWeapon = c2s.addActorWeaponBuilder();
                
                actorWeapon.setTick(serverTick);
                actorWeapon.setPid(myPid);
                actorWeapon.setSlot(weaponSlot.id);
                
                if (positionHint != null && positionHint.set)
                {
                        actorWeapon.setX(positionHint.x);
                        actorWeapon.setY(positionHint.y);
                        actorWeapon.setXVel(positionHint.x_vel);
                        actorWeapon.setYVel(positionHint.y_vel);
                        actorWeapon.setSnappedRotation(positionHint.rot_snapped);
                }
                
                game.send(c2s);
        }

        public long getlastRTTNano()
        {
                return clockSync_lastRTT_nano;
        }        

        private boolean parseTimeResponse_first = true;
        private void parseTimeResponse(GameS2C.TimeResponse message, long receivedAt)
        {
                if (parseTimeResponse_first)
                {
                        parseTimeResponse_first = false;
                        // ignore the first one due to lazy class loading messing up the results
                        
                        sendTimeSync();
                        
                        return;
                }
                clockSync_lastRTT_nano = receivedAt - message.getClientTime();
                clockSync.addResponse(receivedAt, message.getClientTime(), message.getServerTime());

                if (log.isLoggable(Level.INFO))
                {
                        log.log(Level.INFO, "Received a time response. Request sent {0}ms ago;", new Object[]
                        {
                                (receivedAt - message.getClientTime()) / 1000000d
                        });
                }
                
                if (state == STATE.INITIAL_TIME_SYNC)
                {
                        if (initial_timeSync_count < 10)
                        {
                                ++initial_timeSync_count;
                                sendTimeSync();
                        }
                        else
                        {
                                nextState(STATE.INITIAL_TIME_SYNC_DONE);
                        }
                }
        }
        
        public void sendCommand(String name, String ... args)
        {
                sendCommand(name, 0, args);
        }
        
        public void sendCommand(String name, int responseCode, String ... args)
        {
                GameC2S.C2S.Builder c2s = GameC2S.C2S.newBuilder();
                GameC2S.Command.Builder command = c2s.addCommandBuilder();
                command.setName(name);
                if (responseCode != 0)
                {
                        command.setResponseCode(responseCode);
                }
                command.addAllArguments(Arrays.asList(args));
                game.send(c2s);
        }
}
