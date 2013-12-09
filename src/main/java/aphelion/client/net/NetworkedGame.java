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

import aphelion.shared.net.game.NetworkedActor;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.net.game.GameProtoListener;
import aphelion.shared.net.game.GameProtocolConnection;
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
import aphelion.shared.net.COMMAND_SOURCE;
import aphelion.shared.net.game.ActorListener;
import aphelion.shared.resource.Asset;
import aphelion.shared.resource.AssetCache;
import aphelion.shared.resource.LocalUserStorage;
import aphelion.shared.swissarmyknife.RollingHistory;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class NetworkedGame implements GameProtoListener, TickEvent
{
        private static final Logger log = Logger.getLogger("aphelion.net");
        
        /** How often to send our queued move operations to the server. (in ticks) */
        private static final int SEND_MOVE_DELAY = 5;
        
        /** How many move operations to send that were already sent previously. 
         * If, for example, SEND_MOVE_DELAY is 5 and SEND_MOVE_OVERLAP is 10, a 
         * maximum of 15 move operations will be sent every 5 ticks.
         * And each move operation will be sent a total of 3 times.
         */
        private static final int SEND_MOVE_OVERLAP = 10;
        
        /** How often to sync the clock (in nanoseconds).
         * The clock sync is also used to measure round trip latency.
         */
        private static final long CLOCKSYNC_DELAY = 30 * 1000_000_000L;
        
        private final ResourceDB resourceDB;
        private final TickedEventLoop loop;
        private final URL httpServer;
        
        private PhysicsEnvironment physicsEnv;
        private GameProtocolConnection gameConn;
        private ClockSync clockSync;
        private int initial_timeSync_count = 0;
        
        private final AssetCache assetCache;
        private final List<Asset> assets = new ArrayList<>();
        public String mapResource;
        public final List<String> gameConfigResources = new ArrayList<>();
        public final List<String> niftyGuiResources = new ArrayList<>();
        
        private final ArrayList<ActorListener> actorListeners = new ArrayList<>(4);
        private final Map<Integer, NetworkedActor> actors = new HashMap<>();
        
        private String nickname;
        private STATE state;
        private int myPid = -1;
        /** The tick difference between the local PhysicsEnvironment ticks and the servers PhysicsEnvironment ticks. */
        
        private long sendMove_lastSent_tick; // physicsEnv.getTick()
        private RollingHistory<PhysicsMovement> sendMove_history; // physicsEnv.getTick()
        
        private long clockSync_lastSync_nano;
        private long clockSync_lastRTT_nano;
        
        /** This list is used to queue any physics operations we receive before ArenaSync has been received. */
        private LinkedList<GameS2C.S2C> arenaSyncOperationQueue;
        private final HashSet<Integer> unknownActorRemove = new HashSet<>(); // Assumes PIDs are unique. ActorNew is never called twice with the same PID.
        
        private WS_CLOSE_STATUS disconnect_code; // null if unknown
        private String disconnect_reason; // null if unknown
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
                WAIT_FOR_ARENALOAD (8),
                RECEIVED_ARENALOAD (9),
                ARENA_LOADING (10), // we are loading the map, etc
                SEND_ARENA_LOADED (11),
                WAIT_FOR_ARENASYNC (12), // server is busy sending us arena sync data
                RECEIVED_ARENASYNC (13),
                READY (14), // ready to play the game
                AUTH_FAILED (20),
                DISCONNECTED (21);
                
                int sequence;

                private STATE(int sequence)
                {
                        this.sequence = sequence;
                }
                
                public boolean hasOccurred(STATE other)
                {
                        return this.sequence >= other.sequence;
                }
        }

        public NetworkedGame(ResourceDB resourceDB, TickedEventLoop loop, URL httpServer, String nickname)
        {
                this.resourceDB = resourceDB;
                this.loop = loop;
                this.httpServer = httpServer;
                this.nickname = nickname;
                
                loop.addTickEvent(this);
                nextState(STATE.ESTABLISHING);
                
                
                try
                {
                        assetCache = new AssetCache(new LocalUserStorage("assets"));
                }
                catch (IOException ex)
                {
                        // Should not fail
                        throw new Error(ex);
                }
        }
        
        public void addActorListener(ActorListener listener)
        {
                this.actorListeners.add(listener);
                
                for (NetworkedActor actor : this.actors.values())
                {
                        listener.newActor(actor);
                }
        }

        public void arenaLoaded(PhysicsEnvironment physicsEnv)
        {
                if (this.state != STATE.ARENA_LOADING)
                {
                        throw new IllegalStateException();
                }

                if (physicsEnv.getTick() != 0)
                {
                        throw new IllegalStateException();
                }

                this.physicsEnv = physicsEnv;
                
                nextState(STATE.SEND_ARENA_LOADED);
        }

        public boolean isConnecting()
        {
                // all states before WAIT_FOR_ARENALOAD count as "connecting"
                // DISCONNECTED should return false!
                return !state.hasOccurred(STATE.ARENA_LOADING);
        }
        
        public boolean hasArenaSynced()
        {
                return state.hasOccurred(STATE.RECEIVED_ARENASYNC);
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

        public GameProtocolConnection getGameConn()
        {
                return gameConn;
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
                                gameConn.send(c2s);
                                
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
                                gameConn.send(c2s);

                                nextState(STATE.WAIT_FOR_ARENALOAD);

                                break;
                                
                        case WAIT_FOR_ARENALOAD:
                                break;
                                
                        case RECEIVED_ARENALOAD:
                                // handled by GameLoop
                                nextState(STATE.ARENA_LOADING);
                                break;
                                
                        case ARENA_LOADING:
                                break;
                        
                        case SEND_ARENA_LOADED:
                                c2s = GameC2S.C2S.newBuilder();
                                GameC2S.ArenaLoaded.Builder loadedRequest = c2s.addArenaLoadedBuilder();
                                gameConn.send(c2s);
                                
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
                                gameConn.requestClose(WS_CLOSE_STATUS.NORMAL);
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

                this.gameConn = game;
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

                if (game != this.gameConn)
                {
                        log.log(Level.SEVERE, "Received a message for an invalid GameProtocol (session token). Ignoring message");
                        assert false;
                        return;
                }
                
                for (GameS2C.AuthenticateResponse msg : s2c.getAuthenticateResponseList())
                {
                        if (state != STATE.WAIT_AUTHENTICATE)
                        {
                                log.log(Level.SEVERE, "Received AuthenticateResponse in an invalid state");
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
                
                for (GameS2C.ArenaLoad msg : s2c.getArenaLoadList())
                {
                        log.log(Level.INFO, "Received ArenaLoad");
                        
                        if (state != STATE.WAIT_FOR_ARENALOAD)
                        {
                                log.log(Level.SEVERE, "Received ArenaLoad in an invalid state");
                                return;
                        }
                        
                        this.mapResource = msg.getMap();
                        this.gameConfigResources.clear();
                        this.gameConfigResources.addAll(msg.getGameGonfigList());
                        this.niftyGuiResources.addAll(msg.getNiftyGuiList());
                        
                        for (GameS2C.ResourceRequirement req : msg.getResourceRequirementList())
                        {
                                try
                                {
                                        assets.add(new Asset(assetCache, httpServer, req));
                                }
                                catch (MalformedURLException ex)
                                {
                                        log.log(Level.SEVERE, "Received a malformed url from the server", ex);
                                        
                                        this.disconnect_code = WS_CLOSE_STATUS.MALFORMED_PACKET;
                                        this.disconnect_reason = "Received a malformed url from the server";
                                        this.gameConn.requestClose(disconnect_code, disconnect_reason);
                                        nextState(STATE.DISCONNECTED);
                                        return;
                                }
                                
                        }
                        
                        nextState(STATE.RECEIVED_ARENALOAD);
                }

                for (GameS2C.ArenaSync msg : s2c.getArenaSyncList())
                {
                        log.log(Level.INFO, "Received ArenaSync");
                        // should only receive this message once (for now)
                        this.myPid = msg.getYourPid();
                        
                        // The current server time
                        long serverNow = clockSync.nanoTime();
                        
                        // how many ticks ago was the ArenSync message sent by the server? (rounded up)
                        long tickLatency = SwissArmyKnife.divideCeil(serverNow - msg.getCurrentNanoTime(), loop.TICK);

                        // At what tick was our event loop when the server sent that message?
                        long loopTick = loop.currentTick() - tickLatency;
                        
                        // make sure our loop is synchronized to the servers time value
                        loop.synchronize(msg.getCurrentNanoTime(), loopTick);
                        
                        // At what tick is the server now? (loop.synchronize will wait or fast forward ahead to compensate for latency)                     
                        physicsEnv.skipForward(msg.getCurrentTicks() + tickLatency);
                        
                        physicsEnv.actorNew(msg.getCurrentTicks(), myPid, msg.getYourSeed(), msg.getShip());

                        NetworkedActor actor = new NetworkedActor(myPid, true, msg.getName());
                        this.actors.put(actor.pid, actor);
                        
                        nextState(STATE.RECEIVED_ARENASYNC);
                        
                        for (ActorListener listener : actorListeners)
                        {
                                listener.newActor(actor);
                        }
                }
                
                for (GameS2C.ActorDied msg : s2c.getActorDiedList())
                {
                        // ...
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
                                msg.getTick(), 
                                msg.getPid(), msg.getSeed(), msg.getShip()
                                );
                        
                        if (unknownActorRemove.contains(msg.getPid()))
                        {
                                // Received ActorNew and ActorRemove out of order
                                unknownActorRemove.remove(msg.getPid());
                        }
                        else
                        {
                                NetworkedActor actor = new NetworkedActor(msg.getPid(), false, msg.getName());
                                this.actors.put(actor.pid, actor);

                                for (ActorListener listener : actorListeners)
                                {
                                        listener.newActor(actor);
                                }
                        }
                }
                
                for (GameOperation.ActorSync msg : s2c.getActorSyncList())
                {
                        physicsEnv.actorSync(msg);
                }
                
                for (GameOperation.ActorModification msg : s2c.getActorModificationList())
                {
                        long tick = msg.getTick();
                        
                        physicsEnv.actorModification(tick, msg.getPid(), msg.getShip());
                }

                for (GameOperation.ActorRemove msg : s2c.getActorRemoveList())
                {
                        log.log(Level.INFO, "Received ActorRemove {0} {1}", new Object[] { msg.getTick(), msg.getPid()});
                        physicsEnv.actorRemove(msg.getTick(), msg.getPid());
                        
                        NetworkedActor actor = actors.get(msg.getPid());
                        if (actor == null)
                        {
                                // Received ActorNew and ActorRemove out of order
                                // Assumes PIDs are unique. ActorNew is never called twice with the same PID.
                                unknownActorRemove.add(msg.getPid());
                        }
                        else
                        {
                                actors.remove(actor.pid);
                                
                                for (ActorListener listener : actorListeners)
                                {
                                        listener.removedActor(actor);
                                }
                        }
                }

                for (GameOperation.ActorWarp msg : s2c.getActorWarpList())
                {
                        //log.log(Level.INFO, "Received ActorWarp {0} {1}", new Object[] { msg.getTick(), msg.getPid()});
                        physicsEnv.actorWarp(
                                msg.getTick(), msg.getPid(), msg.getHint(),
                                msg.getX(), msg.getY(), msg.getXVel(), msg.getYVel(), msg.getRotation(),
                                msg.hasX(), msg.hasY(), msg.hasXVel(), msg.hasYVel(), msg.hasRotation());
                }

                for (GameOperation.ActorMove msg : s2c.getActorMoveList())
                {
                        //log.log(Level.INFO, "Received ActorMove {0} {1}", new Object[] { msg.getTick(), msg.getPid()});
                        long tick = msg.getTick();
                        
                        for (int move : msg.getMoveList())
                        {
                                physicsEnv.actorMove(
                                        tick,
                                        msg.getPid(),
                                        PhysicsMovement.get(move)
                                        );

                                ++tick;
                        }
                }
                
                for (GameOperation.ActorWeapon msg : s2c.getActorWeaponList())
                {
                        long tick = msg.getTick();
                        
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
                        long tick = msg.getTick();
                        
                        physicsEnv.weaponSync(
                                tick, 
                                msg.getPid(), 
                                msg.getWeaponKey(),
                                msg.getProjectilesList().toArray(new GameOperation.WeaponSync.Projectile[0]));
                }
                
                
        }
        
        @Override
        public void tick(long tick)
        {
                long now = System.nanoTime();
                
                if (sendMove_history != null)
                {
                        // we might have move operations queued but the caller is not
                        // calling sendMove() anymore
                        sendMove(physicsEnv.getTick(), null);
                }
                
                if (isReady())
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
                if (!state.hasOccurred(STATE.RECEIVED_ARENASYNC))
                {
                        throw new IllegalStateException();
                }
                
                return myPid;
        }
        
        public List<Asset> getRequiredAssets()
        {
                return Collections.unmodifiableList(assets);
        }

        public void sendTimeSync()
        {
                GameC2S.C2S.Builder c2s = GameC2S.C2S.newBuilder();
                TimeRequest.Builder timeRequest = c2s.addTimeRequestBuilder();
                timeRequest.setClientTime(System.nanoTime());
                gameConn.send(c2s);
                clockSync_lastSync_nano = System.nanoTime();
        }
        
        /** Queue a move to be sent to the server.
         * Moves into the past, or changing previously sent moves is not allowed.
         * 
         * @param tick The tick at which the move occurred (based on physicsEnv.getTick() )
         * @param move A move. 
         * Null is a special value which is only used to make 
         * sure the queue gets emptied in time (this is called for 
         * every possible tick by NetworkedGame.tick() ).
         */
        public void sendMove(long tick, PhysicsMovement move)
        {        
                if (sendMove_history == null)
                {
                        if (move == null || !move.hasEffect())
                        {
                                return;
                        }
                        
                        sendMove_history = new RollingHistory<>(tick, SEND_MOVE_DELAY + SEND_MOVE_OVERLAP);
                        sendMove_lastSent_tick = tick - 1;
                }
                else if (tick < sendMove_history.getOldestTick())
                {
                        throw new IllegalStateException();
                        // Too far into the past
                }
                
                sendMove_history.setHistory(tick, move);
                
                
                if (tick - sendMove_lastSent_tick < SEND_MOVE_DELAY)
                {
                        return; //queue
                }
                
                // Skip trailing and leading NONE / null moves.
                // note that this is not just an optimalization!
                // Trailing NONE's need to be skipped because a 
                // NONE may be overriden at a (slightly) later moment.
                
                long first_tick = sendMove_history.getOldestTick();
                while (true)
                {
                        PhysicsMovement m = sendMove_history.get(first_tick);
                        if (m != null && m != PhysicsMovement.NONE)
                        {
                                break;
                        }
                        
                        ++first_tick;
                        
                        if (first_tick > sendMove_history.getMostRecentTick())
                        {
                                return; // history is completely empty
                        }
                }
                
                long last_tick = sendMove_history.getMostRecentTick();
                while (true)
                {
                        PhysicsMovement m = sendMove_history.get(last_tick);
                        if (m != null && m != PhysicsMovement.NONE)
                        {
                                break;
                        }
                        
                        --last_tick;
                        
                        
                        // should have returned already in the previous loop
                        assert last_tick >= sendMove_history.getOldestTick(); 
                }
                
                sendMove_lastSent_tick = tick;
                
                GameC2S.C2S.Builder c2s = GameC2S.C2S.newBuilder();
                GameOperation.ActorMove.Builder actorMove = c2s.addActorMoveBuilder();
                actorMove.setPid(myPid);
                actorMove.setTick(first_tick);
                actorMove.setDirect(true);
                
                for (long t = first_tick; t <= last_tick; ++t)
                {
                        PhysicsMovement m = sendMove_history.get(t);
                        if (m == null)
                        {
                                m = PhysicsMovement.NONE;
                        }
                        actorMove.addMove(m.bits);
                }

                gameConn.send(c2s);

        }
        
        public void sendActorWeapon(long tick, WEAPON_SLOT weaponSlot, PhysicsShipPosition positionHint)
        {
                GameC2S.C2S.Builder c2s = GameC2S.C2S.newBuilder();
                GameOperation.ActorWeapon.Builder actorWeapon = c2s.addActorWeaponBuilder();
                
                actorWeapon.setTick(tick);
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
                
                gameConn.send(c2s);
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
                        log.log(Level.INFO, "Received a time response. Request sent {0}ms ago; Offset {1}", new Object[]
                        {
                                (receivedAt - message.getClientTime()) / 1000000d,
                                clockSync.getOffset()
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
        
        public void sendCommand(COMMAND_SOURCE source, String name, String ... args)
        {
                sendCommand(source, name, 0, false, args);
        }
        
        public void sendCommand(COMMAND_SOURCE source, String name, int responseCode, boolean fromGUI, String ... args)
        {
                GameC2S.C2S.Builder c2s = GameC2S.C2S.newBuilder();
                GameC2S.Command.Builder command = c2s.addCommandBuilder();
                command.setSource(source.id);
                command.setName(name);
                if (responseCode != 0)
                {
                        command.setResponseCode(responseCode);
                }
                command.addAllArguments(Arrays.asList(args));
                gameConn.send(c2s);
        }
}
