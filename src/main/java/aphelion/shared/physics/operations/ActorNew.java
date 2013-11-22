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

package aphelion.shared.physics.operations;

import aphelion.shared.physics.EnvironmentConfiguration;
import aphelion.shared.physics.PhysicsMap;
import aphelion.shared.physics.operations.pub.ActorNewPublic;
import aphelion.shared.physics.entities.Actor;
import aphelion.shared.physics.entities.MapEntity;
import aphelion.shared.physics.State;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsWarp;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class ActorNew extends Operation implements ActorNewPublic
{
        private static final Logger log = Logger.getLogger("aphelion.shared.physics");
        
        // this list is only kept here temporary.
        // it will be set as an attribute in every actor.
        private final MapEntity[] crossStateList;
        public long seed;
        public String ship;
        
        public ActorNew(EnvironmentConfiguration econfig)
        {
                super(econfig, false, PRIORITY.ACTOR_NEW);
                crossStateList = new MapEntity[econfig.TRAILING_STATES];
        }
        
        @Override
        public boolean execute(State state, long ticks_late)
        {
                Actor actor;
                
                if (pid == 0)
                {
                        log.log(Level.SEVERE, "A pid of 0 is not allowed");
                        return true;
                }

                if (state.actors.containsKey(pid))
                {
                        // pids should not be reused (or, only after a very long timeout)
                        log.log(Level.SEVERE, "Duplicate ActorNew for pid {0}, received at tick {1} (late {2}) in {3}", new Object[] {pid, tick, ticks_late, state});
                        return true;
                }

                if (ticks_late > 0)
                {
                        // this works because PIDs are unique.
                        // An ActorNew or ActorRemove never has the same pid twice.
                        if (state.unknownActorRemove.contains(pid))
                        {
                                state.unknownActorRemove.remove(pid);
                                // ignore this ActorNew, it has already been removed (however it has been received in the wrong order)
                                return true;
                        }
                }
                
                actor = state.getActorRemovedDuringReset(pid, this.tick);
                
                if (actor == null)
                {
                        actor = new Actor(state, crossStateList, pid, this.tick);
                        crossStateList[state.id] = (MapEntity) actor;
                }
                
                assert actor.crossStateList == crossStateList;
                
                actor.seed = seed;
                actor.seed_high = (int) (actor.seed >> 32);
                actor.seed_low = (int) (actor.seed);
                actor.setShip(ship);
                actor.spawnAt_tick = this.tick;
                actor.energy.addAbsoluteValue(Actor.ENERGY_SETTER.OTHER.id, this.tick, actor.getMaxEnergy());
                
                
                actor.energy.setMaximum(this.tick, actor.getMaxEnergy());
                
                state.actors.put(actor.pid, actor);
                state.actorsList.add(actor);
                
                PhysicsPoint spawn = new PhysicsPoint();
                actor.findSpawnPoint(spawn, this.tick);
                
                if (spawn.set)
                {
                        PhysicsWarp spawnWarp = new PhysicsWarp(
                                spawn.x * PhysicsMap.TILE_PIXELS + PhysicsMap.TILE_PIXELS/2, 
                                spawn.y * PhysicsMap.TILE_PIXELS + PhysicsMap.TILE_PIXELS/2, 
                                0, 
                                0, 
                                actor.randomRotation(seed));
                        actor.moveHistory.setHistory(this.tick, spawnWarp);
                        actor.applyMoveable(spawnWarp, this.tick); // sets the current position
                }
                
                actor.updatePositionHistory(this.tick);
                
                // dead reckon current position so that it is no longer late
                // the position at the tick of this operation should not be dead reckoned, therefor +1
                actor.performDeadReckoning(state.env.getMap(), this.tick + 1, ticks_late);

                if (state.id == 0)
                {
                        log.log(Level.INFO, "Added actor {0} at tick {1} (late {2})", new Object[] {pid, tick, ticks_late});
                }
                
                return true;
        }

        @Override
        public boolean isConsistent(State older, State newer)
        {
                // nothing to do unless there is a condition in which ActorNew might fail and not create the player

                return true;
        }

        @Override
        public void resetExecutionHistory(State state, State resetTo)
        {
        }

        @Override
        public long getSeed()
        {
                return seed;
        }

        @Override
        public String getShip()
        {
                return ship;
        }
}