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

package aphelion.server.game;

import aphelion.server.game.ClientState.STATE;
import aphelion.shared.event.*;
import aphelion.shared.net.protocols.GameListener;
import aphelion.shared.net.protocols.GameProtocolConnection;
import aphelion.shared.net.WS_CLOSE_STATUS;
import aphelion.shared.net.protobuf.GameC2S;
import aphelion.shared.net.protobuf.GameC2S.Authenticate;
import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.net.protobuf.GameS2C;
import aphelion.shared.net.protobuf.GameS2C.AuthenticateResponse;
import aphelion.shared.physics.operations.pub.ActorModificationPublic;
import aphelion.shared.physics.operations.pub.ActorMovePublic;
import aphelion.shared.physics.operations.pub.ActorNewPublic;
import aphelion.shared.physics.operations.pub.ActorRemovePublic;
import aphelion.shared.physics.operations.pub.ActorWarpPublic;
import aphelion.shared.physics.operations.pub.ActorWeaponFirePublic;
import aphelion.shared.physics.operations.pub.OperationPublic;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.physics.WEAPON_SLOT;
import aphelion.shared.swissarmyknife.AttachmentConsumer;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class ServerGame implements LoopEvent, TickEvent, GameListener
{
        private static final Logger log = Logger.getLogger("aphelion.server.game");
        
        private static final AttachmentConsumer<GameProtocolConnection, ClientState> stateAttachment 
                = new AttachmentConsumer<>(GameProtocolConnection.attachmentManager);

        private final static long SYNC_ACTOR_EVERY_NANOS = 15 * 1_000_000_000L; // 15 sec
        private final static int SYNC_SOME_ACTOR_EVERY_TICKS = 25; // 0.25 sec
        
        public final PhysicsEnvironment physicsEnv;
        public final TickedEventLoop loop;
        
        // Simply raise this value to get the next pid, do not reuse them for now.
        // This way we would go into negative pids after 248 days if we would get a new player every 10ms.
        private int next_pid = 1;
        
        // all players that are in STATE.READY
        private LinkedList<GameProtocolConnection> readyPlayers = new LinkedList<>();
        
        

        
        public ServerGame(PhysicsEnvironment physicsEnv, TickedEventLoop loop)
        {
                this.physicsEnv = physicsEnv;
                this.loop = loop;
                
                loop.addTimerEvent(SYNC_SOME_ACTOR_EVERY_TICKS, actorSyncTimer);
        }
        
        public void addReadyPlayer(GameProtocolConnection gameConn)
        {
                readyPlayers.add(gameConn);
        }
        
        public void removeReadyPlayer(GameProtocolConnection gameConn)
        {
                readyPlayers.remove(gameConn);
        }
        
        public int getPlayerCount()
        {
                return readyPlayers.size();
        }
        
        @Override
        public void loop(long systemNanoTime, long sourceNanoTime)
        {
        }
        
        public int generatePid()
        {
                if (next_pid == 0) { next_pid = 1; }
                return next_pid++;
        }
        
        @Override
        public void tick(long tick)
        {
                physicsEnv.tick();
        }
        
        @Override
        public void gameNewClient(GameProtocolConnection game)
        {
                ClientState state = new ClientState(this, game);
                stateAttachment.set(game, state);
                state.nextState(STATE.ESTABLISHED);
        }
        
        @Override
        public void gameEstablishFailure(WS_CLOSE_STATUS code, String reason)
        {
                assert false;
        }

        @Override
        public void gameRemovedClient(GameProtocolConnection game)
        {
                ClientState state = stateAttachment.get(game);
                state.nextState(STATE.DISCONNECTED);
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
        public void gameC2SMessage(GameProtocolConnection game, GameC2S.C2S c2s, long receivedAt)
        {
                ClientState state = stateAttachment.get(game);
                assert state != null;
                        
                // Do not handle time requests here!
                // They are already handled in the thread that received them
                
                for (GameC2S.Authenticate msg : c2s.getAuthenticateList())
                {
                        if (state.state != STATE.WAIT_FOR_AUTHENTICATE)
                        {
                                log.log(Level.WARNING, "Received a duplicate Authenticate or in an invalid state");
                                break;
                        }
                        
                        AuthenticateResponse.ERROR error = AuthenticateResponse.ERROR.OK;
                        String errorMessage = null;
                        
                        String nickname = msg.getNickname();
                        if (!SwissArmyKnife.isValidNickname(nickname))
                        {
                                error = AuthenticateResponse.ERROR.INVALID_NICKNAME;
                                errorMessage = "Your nickname contains invalid characters.";
                        }
                        else if (msg.getAuthMethod() == Authenticate.AUTH_METHOD.NONE)
                        {
                                // todo: add prefix if someone connects with NONE auth
                                
                                
                                for (GameProtocolConnection otherGame : readyPlayers)
                                {
                                        ClientState otherState = stateAttachment.get(otherGame);
                                        if (SwissArmyKnife.nicknameCompare(nickname, otherState.nickname) == 0)
                                        {
                                                error = AuthenticateResponse.ERROR.NICKNAME_IN_USE;
                                                errorMessage = "Someone else is already using that nickname.";
                                                break;
                                        }
                                }

                                // todo kick the existing player if password authentication is used
                        }
                        
                        GameS2C.S2C.Builder s2c = GameS2C.S2C.newBuilder();
                        GameS2C.AuthenticateResponse.Builder response = s2c.addAuthenticateResponseBuilder();
                        response.setError(error);
                        if (errorMessage != null) response.setErrorDescription(errorMessage);
                        game.send(s2c);
                        
                        if (error == AuthenticateResponse.ERROR.OK)
                        {
                                state.setNickname(nickname);
                                state.nextState(STATE.RECEIVED_AUTHENTICATE);
                        }
                        else
                        {
                                state.nextState(STATE.WAIT_FOR_AUTHENTICATE);
                        }
                }
                
                for (GameC2S.ConnectionReady msg : c2s.getConnectionReadyList())
                {
                        if (state.state != STATE.WAIT_FOR_CONNECTION_READY)
                        {
                                log.log(Level.WARNING, "Received a duplicate ConnectionReady or in an invalid state");
                                break;
                        }
                        state.nextState(STATE.RECEIVED_CONNECTION_READY);
                }
                
                for (GameC2S.ArenaLoaded msg : c2s.getArenaLoadedList())
                {
                        if (state.state != STATE.WAIT_FOR_ARENA_LOADED)
                        {
                                log.log(Level.WARNING, "Received a duplicate ArenaLoaded or in an invalid state");
                                break;
                        }
                        
                        state.nextState(STATE.RECEIVED_ARENA_LOADED);
                }
                
                for (GameC2S.Command msg : c2s.getCommandList())
                {
                        state.parseCommand(msg.getName(), msg.getResponseCode(), msg.getArgumentsList());
                }
                
                for (GameOperation.ActorMove msg : c2s.getActorMoveList())
                {
                        if (state.state != STATE.READY)
                        {
                                log.log(Level.WARNING, "Received an ActorMove in an invalid state");
                                break;
                        }
                        
                        if (msg.getPid() != state.pid)
                        {
                                log.log(Level.WARNING, "Received an invalid pid in ActorMove");
                                break;
                        }
                        
                        if (msg.getMoveCount() < 1)
                        {
                                log.log(Level.WARNING, "Received no actual moves in ActorMove");
                                break;
                        }
                        
                        // Forward the move operation
                        
                        GameS2C.S2C.Builder s2c = GameS2C.S2C.newBuilder();
                        GameOperation.ActorMove.Builder moveBuilder = s2c.addActorMoveBuilder();
                        moveBuilder.setPid(msg.getPid());
                        moveBuilder.setDirect(true);
                        
                        
                        int[] moves = new int[msg.getMoveCount()];
                        
                        int i = 0;
                        for (int move : msg.getMoveList())
                        {
                                boolean valid = physicsEnv.actorMove(
                                        msg.getTick() + i,
                                        msg.getPid(),
                                        PhysicsMovement.get(move));

                                if (!valid)
                                {
                                        move = 0;
                                }
                                
                                moves[i] = move;
                                
                                ++i;
                        }
                        
                        
                        // find end
                        int end;
                        for (end = moves.length - 1; end >= 0; --end)
                        {
                                if (moves[end] != 0)
                                {
                                        break;
                                }
                        }
                        
                        int start;
                        // find start
                        for (start = 0; start < moves.length; ++start)
                        {
                                if (moves[start] != 0)
                                {
                                        break;
                                }
                        }
                        
                        for (i = start; i <= end; ++i)
                        {
                                moveBuilder.addMove(moves[i]);
                        }
                        
                        if (moveBuilder.getMoveCount() > 0)
                        {
                                moveBuilder.setTick(msg.getTick() + start);
                                broadcast(s2c, game); // forward to all other clients
                        }
                }
                
                for (GameOperation.ActorWeapon msg : c2s.getActorWeaponList())
                {
                        if (msg.getPid() != state.pid)
                        {
                                log.log(Level.WARNING, "Received an invalid pid in ActorWeapon");
                                continue;
                        }
                        
                        if (!WEAPON_SLOT.isValidId(msg.getSlot()))
                        {
                                log.log(Level.SEVERE, "Received an ActorWeapon with an invalid slot id {0}", msg.getSlot());
                                continue;
                        }
                        
                        WEAPON_SLOT slot = WEAPON_SLOT.byId(msg.getSlot());
                        
                        GameS2C.S2C.Builder s2c = GameS2C.S2C.newBuilder();
                        GameOperation.ActorWeapon.Builder weaponBuilder = s2c.addActorWeaponBuilder();
                        weaponBuilder.setPid(msg.getPid());
                        weaponBuilder.setTick(msg.getTick());
                        weaponBuilder.setSlot(msg.getSlot());
                        
                        boolean has_hint = msg.hasX() && msg.hasY() && msg.hasXVel() && msg.hasYVel() && msg.hasSnappedRotation();
                                
                        physicsEnv.actorWeapon(
                                msg.getTick(), msg.getPid(), slot,
                                has_hint, 
                                msg.getX(), msg.getY(), 
                                msg.getXVel(), msg.getYVel(), 
                                msg.getSnappedRotation());
                        
                        
                        broadcast(s2c, game); // forward to all other clients
                }
        }

        @Override
        public void gameS2CMessage(GameProtocolConnection game, GameS2C.S2C s2c, long receivedAt)
        {
                // should not occur
        }
        
        public void broadcast(GameS2C.S2COrBuilder s2c)
        {
                broadcast(s2c, null);
        }
        
        public void broadcast(GameS2C.S2COrBuilder s2c, GameProtocolConnection except)
        {
                for (GameProtocolConnection conn : readyPlayers)
                {
                        ClientState state = stateAttachment.get(conn);
                        
                        if (state.state == STATE.READY && conn != except)
                        {
                                conn.send(s2c);
                        }
                }
        }
        
        public static boolean addPhysicsOperationToMessage(GameS2C.S2C.Builder s2c, OperationPublic op)
        {
                if (op instanceof ActorNewPublic)
                {
                        ActorNewPublic opNew = (ActorNewPublic) op;
                        GameOperation.ActorNew.Builder actorNew = s2c.addActorNewBuilder();
                        actorNew.setTick(opNew.getTick());
                        actorNew.setPid(opNew.getPid());
                        actorNew.setName(opNew.getName());
                        actorNew.setSeed(opNew.getSeed());
                        actorNew.setShip(opNew.getShip());
                        return true;
                }
                else if (op instanceof ActorRemovePublic)
                {
                        GameOperation.ActorRemove.Builder actorRemove = s2c.addActorRemoveBuilder();
                        actorRemove.setTick(op.getTick());
                        actorRemove.setPid(op.getPid());
                        return true;
                }
                else if (op instanceof ActorMovePublic)
                {
                        ActorMovePublic opMove = (ActorMovePublic) op;
                        int move = opMove.getMove().bits;

                        if (move > 0)
                        {
                                GameOperation.ActorMove.Builder actorMove = null;
                                
                                if (s2c.getActorMoveCount() > 0)
                                {
                                        // if we had a move for the previous tick, use it
                                        // this optimalization only works properly when this method is called in order of tick
                                        // and the builder is unique for each pid.
                                        actorMove = s2c.getActorMoveBuilder(s2c.getActorMoveCount() - 1);
                                        
                                        // this would be the tick for the move if we would call addMove() right now.
                                        long newTick = actorMove.getTick() + actorMove.getMoveCount();
                                        
                                        if (actorMove.getPid() != op.getPid() && newTick != op.getTick() - 1)
                                        {
                                                actorMove = null;
                                        }
                                }
                                
                                if (actorMove == null)
                                {
                                        actorMove = s2c.addActorMoveBuilder();
                                        actorMove.setTick(op.getTick());
                                        actorMove.setPid(op.getPid());
                                }
                                actorMove.addMove(move);
                                return true;
                        }
                        
                        return false;
                }
                else if (op instanceof ActorWarpPublic)
                {
                        ActorWarpPublic opWarp = (ActorWarpPublic) op;

                        GameOperation.ActorWarp.Builder actorWarp = s2c.addActorWarpBuilder();
                        actorWarp.setTick(op.getTick());
                        actorWarp.setPid(op.getPid());
                        actorWarp.setHint(false);
                        
                        opWarp.getWarp().toProtobuf(actorWarp);
                        
                        return true;
                }
                else if (op instanceof ActorWeaponFirePublic)
                {
                        ActorWeaponFirePublic opWeaponFire = (ActorWeaponFirePublic) op;
                        
                        GameOperation.ActorWeapon.Builder actorWeapon = s2c.addActorWeaponBuilder();
                        actorWeapon.setTick(op.getTick());
                        actorWeapon.setPid(op.getPid());
                        actorWeapon.setSlot(opWeaponFire.getWeaponSlot().id);
                        return true;
                }
                else if (op instanceof ActorModificationPublic)
                {
                        ActorModificationPublic opActorMod = (ActorModificationPublic) op;
                        
                        GameOperation.ActorModification.Builder actorMod = s2c.addActorModificationBuilder();
                        actorMod.setTick(op.getTick());
                        actorMod.setPid(op.getPid());
                        
                        if (opActorMod.getShip() != null)
                        {
                                actorMod.setShip(opActorMod.getShip());
                        }
                        
                        return true;
                }
                else
                {
                        log.log(Level.WARNING, "Unknown operation {0}. IMPLEMENT ME!", op);
                        return false;
                }
        }
        
        private TimerEvent actorSyncTimer = new TimerEvent()
        {
                @Override
                public boolean timerElapsed(long tick)
                {
                        long now = System.nanoTime();
                        long highest = 0;
                        ClientState lowest_client = null;
                        
                        for (GameProtocolConnection game : readyPlayers)
                        {
                                ClientState state = stateAttachment.get(game);
                                
                                long ago = now - state.lastActorSyncBroadcast_nanos;
                                
                                if (ago < SYNC_ACTOR_EVERY_NANOS)
                                {
                                        continue;
                                }
                                
                                if (ago > highest)
                                {
                                        highest = state.lastActorSyncBroadcast_nanos;
                                        lowest_client = state;
                                }
                        }
                        
                        if (lowest_client != null)
                        {
                                lowest_client.broadcastActorSync();
                        }
                        
                        return true; // do not remove timer
                        
                }
                
        };
}
