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


import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.physics.entities.Actor;
import aphelion.shared.physics.operations.pub.ActorSyncPublic;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.State;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class ActorSync extends Operation implements ActorSyncPublic
{
        private static final Logger log = Logger.getLogger("Aphelion.Shared.Physics");
        public GameOperation.ActorSync sync;
        public long sync_tick_offset;
        
        private int[] executionCount = new int[PhysicsEnvironment.MAX_TRAILING_STATES];
        
        private boolean logged = false;
        
        public ActorSync()
        {
                super(false, PRIORITY.ACTOR_SYNC);
        }
        
        @Override
        public boolean isLateSyncOperation()
        {
                return true;
        }
        
        @Override
        public boolean execute(State state, long ticks_late)
        {
                Actor actor = state.actors.get(pid);

                if (actor == null) // ignore operation
                {
                        return true;
                }
                
                boolean dirty = false;
                if (this.tick == actor.createdAt_tick)
                {
                        // always execute it, this is the sync for a new actor
                        actor.initFromSync(sync, sync_tick_offset);
                        ++executionCount[state.id];
                }
                else
                {
                        if (ticks_late > 0 && !state.isLast)
                        {
                                // can not execute this operation late 
                                // (unless this is the last state, in which case we have no choice).
                                return true;
                        }
                        
                        if (executionCount[state.id] > 3)
                        {
                                // this is the 4th time we are execution this operation, this means
                                // there have been 3 timewarps caused by this operation. It is unlikely that
                                // things will resolve this time, abandon.
                                
                                if (executionCount[state.id] == 4)
                                {
                                        log.log(Level.INFO, "ActorSync abandoning repeated execution. {0} at state {1} and tick {2} (initial {3}) (execution count {4}).", 
                                                new Object[]
                                                {
                                                        pid, 
                                                        state.id, 
                                                        tick, 
                                                        this.tick == actor.createdAt_tick,
                                                        executionCount[state.id],
                                                });
                                }
                                
                                return true;
                        }
                        
                        // initFromSync returns true if something was changed
                        dirty = actor.initFromSync(sync, sync_tick_offset);
                        
                        // If something changed, force a timwarp!
                        // this works because a consistency check for this tick is done 
                        // right after the operations are executed for this tick
                        ++executionCount[state.id];
                }
                
                if (!logged)
                {
                        logged = true;
                        log.log(Level.INFO, "Synchronized actor {0} at state {1} and tick {2} (initial {3}) (dirty {4}) (execution count {5}) (late {6}).", 
                                new Object[]
                                {
                                        pid, 
                                        state.id, 
                                        tick, 
                                        this.tick == actor.createdAt_tick, 
                                        dirty,
                                        executionCount[state.id],
                                        ticks_late
                                });
                }
                
                
                if (dirty && state.id > 0 && executionCount[state.id-1] == 0)
                {
                        return false; // if this operation already executed on a newer state, there is no need for a timewarp
                }
                
                return true;
                
        }
        
        @Override
        public boolean isConsistent(State older, State newer)
        {
                return true;
        }

        @Override
        public void resetExecutionHistory(State state, State resetTo)
        {
        }
}
