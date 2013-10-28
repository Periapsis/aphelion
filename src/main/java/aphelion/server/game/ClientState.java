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

import aphelion.shared.resource.Asset;
import aphelion.shared.gameconfig.GCBoolean;
import aphelion.shared.gameconfig.GCStringList;
import aphelion.shared.net.protocols.GameProtocolConnection;
import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.net.protobuf.GameS2C;
import aphelion.shared.net.protobuf.GameS2C.ArenaLoad;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.operations.pub.OperationPublic;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.PhysicsMap;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.swissarmyknife.MySecureRandom;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author Joris
 */
public class ClientState
{
        public static enum STATE
        {
                // the order in this enum is also the order the states will move through.

                ESTABLISHED (0), // The client has a session token and atleast 1 websocket
                WAIT_FOR_AUTHENTICATE (1),
                RECEIVED_AUTHENTICATE (2),
                WAIT_FOR_CONNECTION_READY (3), // waiting for ConnectionReady
                RECEIVED_CONNECTION_READY (4),
                WAIT_FOR_ARENA_LOADED (5),
                RECEIVED_ARENA_LOADED (6),
                SEND_ARENA_SYNC (7),
                READY (8), // ready to play the game
                DISCONNECTED (9);
                
                final int sequence;

                private STATE(int sequence)
                {
                        this.sequence = sequence;
                }
                
                public boolean hasOccured(STATE other)
                {
                        return this.sequence >= other.sequence;
                }
        }
        
        private final ServerGame serverGame;
        private final PhysicsEnvironment physicsEnv;
        public int pid;
        public final GameProtocolConnection gameConn;
        public STATE state;
        public String nickname;
        private GCStringList ships;
        public long lastActorSyncBroadcast_nanos;

        ClientState(ServerGame serverGame, GameProtocolConnection gameConn)
        {
                this.serverGame = serverGame;
                this.physicsEnv = serverGame.physicsEnv;
                this.gameConn = gameConn;
                this.ships = physicsEnv.getGlobalConfigStringList(0, "ships");
        }
        
        public void setNickname(String nickname)
        {
                this.nickname = nickname;
        }

        public void nextState(STATE state)
        {
                //System.out.println("Server new state: " + state);

                this.state = state;
                switch(state)
                {
                        case ESTABLISHED:
                                nextState(STATE.WAIT_FOR_AUTHENTICATE);
                                break;
                        case WAIT_FOR_AUTHENTICATE:
                                break;
                        case RECEIVED_AUTHENTICATE:
                                nextState(STATE.WAIT_FOR_CONNECTION_READY);
                                break;
                        case WAIT_FOR_CONNECTION_READY:
                                break;
                        case RECEIVED_CONNECTION_READY:
                                sendInitialResourceRequirements();
                                nextState(STATE.WAIT_FOR_ARENA_LOADED);
                                break;
                        case WAIT_FOR_ARENA_LOADED:
                                break;
                        case RECEIVED_ARENA_LOADED:
                                nextState(STATE.SEND_ARENA_SYNC);
                                break;

                        case SEND_ARENA_SYNC:
                                // never a 0 pid
                                this.pid = serverGame.generatePid();

                                doArenaSync();

                                nextState(STATE.READY);

                                break;
                        case READY:
                                serverGame.addReadyPlayer(gameConn);

                                break;
                        case DISCONNECTED:
                                serverGame.removeReadyPlayer(gameConn);

                                if (pid > 0)
                                {
                                        physicsEnv.actorRemove(physicsEnv.getTick(), pid);

                                        GameS2C.S2C.Builder s2c = GameS2C.S2C.newBuilder();
                                        GameOperation.ActorRemove.Builder actorRemove = s2c.addActorRemoveBuilder();
                                        actorRemove.setTick(physicsEnv.getTick());
                                        actorRemove.setPid(pid);

                                        serverGame.broadcast(s2c);
                                }
                                // else the connection was dropped before the player reached the SEND_ARENA_SYNC state

                                break;
                }
        }

        private void sendInitialResourceRequirements()
        {
                GameS2C.S2C.Builder s2c = GameS2C.S2C.newBuilder();
                ArenaLoad.Builder arenaLoad = s2c.addArenaLoadBuilder();
                
                for (Asset ass : serverGame.assets)
                {
                        ass.toProtoBuf(arenaLoad.addResourceRequirementBuilder());
                }
                
                gameConn.send(s2c);
        }

        private void doArenaSync()
        {
                assert nickname != null;
                
                // random ship
                String ship;
                {
                        int s = ships.getValuesLength();

                        if (s > 0)
                        {
                                s = SwissArmyKnife.random.nextInt(s);
                                ship = ships.get(s);
                        }
                        else
                        {
                                ship = "";
                        }
                }

                long seed = 0;
                while (seed == 0) // make sure seed is not 0 so that 0 is reserved for errors
                {
                        seed = MySecureRandom.nextLong();
                }

                physicsEnv.actorNew(physicsEnv.getTick(), pid, nickname, seed, ship);
                ActorPublic newActor = physicsEnv.getActor(pid);

                PhysicsPoint spawn = new PhysicsPoint();
                int rot = 0;

                if (newActor != null)
                {
                        newActor.findSpawnPoint(spawn, physicsEnv.getTick());
                        rot = newActor.randomRotation(seed);
                }

                int x = spawn.x * PhysicsMap.TILE_PIXELS + PhysicsMap.TILE_PIXELS/2;
                int y = spawn.y * PhysicsMap.TILE_PIXELS + PhysicsMap.TILE_PIXELS/2;
                int x_vel = 0;
                int y_vel = 0;

                physicsEnv.actorWarp(physicsEnv.getTick(), pid, false, x, y, x_vel, y_vel, rot);


                // Send ArenaSync to the new player
                {
                        GameS2C.S2C.Builder s2c = GameS2C.S2C.newBuilder();
                        GameS2C.ArenaSync.Builder inArena = s2c.addArenaSyncBuilder();
                        
                        inArena.setCurrentTicks(physicsEnv.getTick());
                        // currentNano returns the time at which the tick began, not System.nanoTime()
                        inArena.setCurrentNanoTime(serverGame.loop.currentNano());
                        inArena.setName(nickname);
                        inArena.setYourPid(pid);
                        inArena.setYourSeed(seed);
                        inArena.setShip(ship);

                        GameOperation.ActorWarp.Builder actorWarp = s2c.addActorWarpBuilder();
                        actorWarp.setTick(physicsEnv.getTick());
                        actorWarp.setHint(false);
                        actorWarp.setPid(pid);
                        actorWarp.setX(x);
                        actorWarp.setY(y);
                        actorWarp.setXVel(x_vel);
                        actorWarp.setYVel(y_vel);
                        actorWarp.setRotation(rot);

                        gameConn.send(s2c);
                }

                {
                        // Send ActorNew to all other players
                        GameS2C.S2C.Builder s2c = GameS2C.S2C.newBuilder();
                        GameOperation.ActorNew.Builder actorNew = s2c.addActorNewBuilder();
                        actorNew.setTick(physicsEnv.getTick());
                        actorNew.setPid(pid);
                        actorNew.setName(nickname);
                        actorNew.setSeed(seed);
                        actorNew.setShip(ship);

                        // There is no actorSync here. The player will initialize with the same default
                        // values for everyone.

                        GameOperation.ActorWarp.Builder actorWarp = s2c.addActorWarpBuilder();
                        actorWarp.setTick(physicsEnv.getTick());
                        actorWarp.setPid(pid);
                        actorWarp.setHint(false);
                        actorWarp.setX(x);
                        actorWarp.setY(y);
                        actorWarp.setXVel(x_vel);
                        actorWarp.setYVel(y_vel);
                        actorWarp.setRotation(rot);

                        serverGame.broadcast(s2c, this.gameConn);
                }


                // Send the state of all actors to the player
                int oldestState = physicsEnv.TRAILING_STATES-1;
                long oldestStateTick = physicsEnv.getTick(oldestState);
                Iterator<ActorPublic> actorIt = physicsEnv.actorIterator(oldestState);
                int actors = 0;

                actorloop: while (actorIt.hasNext())
                {
                        ActorPublic actor = actorIt.next();
                        int actorPid = actor.getPid();

                        if (actorPid == this.pid)
                        {
                                continue;
                        }

                        // should never happen because we are running in the same thread
                        // as the physics environment (so it has no change to loose its 
                        // reference to the interal actor. If we get an actor from actorIterator
                        // the weak reference return null if used immediatel.
                        assert actor.hasReference();


                        if (actor.isRemoved())
                        {
                                continue;
                        }

                        ++actors;

                        GameS2C.S2C.Builder s2c = GameS2C.S2C.newBuilder();
                        GameOperation.ActorNew.Builder actorNew = s2c.addActorNewBuilder();
                        actorNew.setTick(oldestStateTick);
                        actorNew.setPid(actorPid);
                        actorNew.setName(actor.getName());
                        actorNew.setSeed(actor.getSeed());
                        actorNew.setShip(actor.getShip());

                        GameOperation.ActorSync.Builder actorSync = s2c.addActorSyncBuilder();
                        boolean r = actor.getSync(actorSync);
                        assert r; // should not fail if isDeleted() == false
                        assert actorSync.getTick() == oldestStateTick;
                        assert actorSync.getPid() == actorPid;

                        ProjectilePublic.Position projectilePos = new ProjectilePublic.Position();
                        Iterator<ProjectilePublic> projectileIt = actor.projectileIterator();
                        while (projectileIt.hasNext())
                        {
                                ProjectilePublic projectile = projectileIt.next();
                                if (projectile.isRemoved())
                                {
                                        continue;
                                }

                                assert projectile.getOwner() == actorPid;

                                if (projectile.getProjectileIndex() != 0)
                                {
                                        continue;
                                }

                                GameOperation.WeaponSync.Builder weaponSync = s2c.addWeaponSyncBuilder();
                                weaponSync.setTick(oldestStateTick);
                                weaponSync.setPid(projectile.getOwner());
                                weaponSync.setWeaponKey(projectile.getWeaponKey());

                                Iterator<ProjectilePublic> projIt = projectile.getCoupledProjectiles();
                                while (projIt.hasNext())
                                {
                                        ProjectilePublic projCoupled = projIt.next();
                                        if (projCoupled.isRemoved())
                                        {
                                                continue;
                                        }

                                        assert projectile.getOwner() == projCoupled.getOwner();
                                        assert projectile.getWeaponKey().equals(projCoupled.getWeaponKey());

                                        projCoupled.getPosition(projectilePos);

                                        GameOperation.WeaponSync.Projectile.Builder weaponSyncProjectile = weaponSync.addProjectilesBuilder();
                                        projCoupled.getSync(weaponSyncProjectile);
                                }

                        }

                        gameConn.send(s2c);
                }



                TIntObjectHashMap<GameS2C.S2C.Builder> s2cMessages = new TIntObjectHashMap<>(actors);

                // play back all the operations in the todo list of the oldest state
                Iterator<OperationPublic> opIt = physicsEnv.todoListIterator(oldestState); // the todo list is ordered by tick

                while (opIt.hasNext())
                {
                        OperationPublic op = opIt.next();
                        if (op.getPid() == this.pid)
                        {
                                continue;
                        }

                        GameS2C.S2C.Builder s2c = s2cMessages.get(op.getPid());
                        if (s2c == null)
                        {
                                s2c = GameS2C.S2C.newBuilder();
                                s2cMessages.put(op.getPid(), s2c);
                        }

                        ServerGame.addPhysicsOperationToMessage(s2c, op);
                }

                TIntObjectIterator<GameS2C.S2C.Builder> s2cIt = s2cMessages.iterator();
                while (s2cIt.hasNext())
                {
                        s2cIt.advance();
                        gameConn.send(s2cIt.value());
                }
                
                lastActorSyncBroadcast_nanos = System.nanoTime();
        }
        
        public void broadcastActorSync()
        {
                // send the sync of my actor to all players (including myself)
                
                int oldestState = physicsEnv.TRAILING_STATES-1;
                ActorPublic actor = physicsEnv.getActor(pid, oldestState, false);
                if (actor == null)
                {
                        return;
                }
                
                long oldestStateTick = physicsEnv.getTick(oldestState);
                
                GameS2C.S2C.Builder s2c = GameS2C.S2C.newBuilder();
                
                GameOperation.ActorSync.Builder actorSync = s2c.addActorSyncBuilder();
                if (actor.getSync(actorSync))
                {
                        assert actorSync.getPid() == pid;
                        assert actorSync.getTick() == oldestStateTick;
                        
                        serverGame.broadcast(s2c);
                }
                
                lastActorSyncBroadcast_nanos = System.nanoTime();
        }

        public void parseCommand(String name, int responseCode, List<String> argumentsList)
        {
                // todo move these somewhere else?
                switch (name)
                {
                        case "ship":
                                ActorPublic actor = physicsEnv.getActor(this.pid);

                                if (argumentsList.size() < 1)
                                {
                                        break;
                                }

                                if (actor == null)
                                {
                                        break;
                                }

                                if (!actor.canChangeShip())
                                {
                                        break;
                                }



                                String ship = argumentsList.get(0);

                                if (!ships.hasValue(ship))
                                {
                                        // todo generate list of valid ships from config
                                        break;
                                }

                                long tick = physicsEnv.getTick() + 50; // todo 50 to config

                                GCBoolean respawn = actor.getActorConfigBoolean("ship-change-respawn");

                                physicsEnv.actorModification(tick, this.pid, ship);

                                GameS2C.S2C.Builder s2c = GameS2C.S2C.newBuilder();
                                GameOperation.ActorModification.Builder actorMod = s2c.addActorModificationBuilder();
                                actorMod.setTick(tick);
                                actorMod.setPid(this.pid);
                                actorMod.setShip(ship);

                                if (respawn.get())
                                {
                                        GameOperation.ActorWarp.Builder actorWarp = s2c.addActorWarpBuilder();
                                        actorWarp.setTick(tick);
                                        actorWarp.setPid(this.pid);
                                        actorWarp.setHint(false);

                                        PhysicsPoint spawn = new PhysicsPoint();
                                        actor.findSpawnPoint(spawn, tick);

                                        int rot = actor.randomRotation(actor.getSeed());
                                        int x = spawn.x * PhysicsMap.TILE_PIXELS + PhysicsMap.TILE_PIXELS/2;
                                        int y = spawn.y * PhysicsMap.TILE_PIXELS + PhysicsMap.TILE_PIXELS/2;
                                        int x_vel = 0;
                                        int y_vel = 0;

                                        physicsEnv.actorWarp(tick, pid, false, x, y, x_vel, y_vel, rot);
                                        actorWarp.setX(x);
                                        actorWarp.setY(y);
                                        actorWarp.setXVel(x_vel);
                                        actorWarp.setYVel(y_vel);
                                        actorWarp.setRotation(rot);
                                }

                                serverGame.broadcast(s2c);

                                break;
                        default:
                                // todo: unknown command
                }
        }
}
