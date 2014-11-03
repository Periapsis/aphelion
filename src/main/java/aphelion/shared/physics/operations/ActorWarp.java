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

import aphelion.shared.physics.SimpleEnvironment;
import aphelion.shared.physics.operations.pub.ActorWarpPublic;
import aphelion.shared.physics.entities.Actor;
import aphelion.shared.physics.State;
import aphelion.shared.physics.entities.ActorKey;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.physics.valueobjects.PhysicsWarp;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class ActorWarp extends Operation implements ActorWarpPublic
{
        private static final Logger log = Logger.getLogger("aphelion.shared.physics");
        public PhysicsWarp warp;
        public boolean hint; // optional execution, used to correct inconsistencies caused by super high latency

        public ActorWarp(SimpleEnvironment env, OperationKey key)
        {
                super(env, true, PRIORITY.ACTOR_WARP, key);
        }
        
        @Override
        public int comparePriority(Operation other_)
        {
                int c = super.comparePriority(other_);
                
                if (c == 0 && other_ instanceof ActorWarp)
                {
                        ActorWarp other = (ActorWarp) other_;
                        
                        return warp.compareTo(other.warp);
                }
                
                return c;
        }

        @Override
        public boolean execute(State state, boolean late, long ticks_late)
        {
                Actor actor;

                actor = state.actors.get(new ActorKey(pid));
                
                if (actor == null) // ignore operation
                {
                        return true;
                }
                
                if (this.hint)
                {
                        PhysicsShipPosition historicPos = new PhysicsShipPosition();
                        if (actor.getHistoricPosition(historicPos, this.tick, false))
                        {
                                if (warp.equalsShipPosition(historicPos))
                                {
                                        // no need to execute, our position is still consistent
                                        
                                        if (state.id == 0)
                                        {
                                                log.log(Level.INFO, "ActorWarp (consistent) for pid {0} at tick {1} (late {2}) (hint {3})", new Object[] {pid, tick, ticks_late, hint});
                                        }
                                        
                                        return true;
                                }
                        }
                }
                
                // allow duplicate warps (unlike moves), warps are only generated by the server
                // the old warp is replaced (see this.comparePriority())
                
                actor.moveHistory.setHistory(this.tick, warp);
                actor.applyMoveable(warp, this.tick); // sets the current position
                actor.updatedPosition(this.tick);
                
                // Note: this tick the new velocity is set, but this new velocity is not used yet.
                // It will be used in the next tick

                // dead reckon current position so that it is no longer late
                // the position at the tick of this operation should not be dead reckoned, therefor +1
                actor.performDeadReckoning(state.env.getMap(), this.tick + 1, ticks_late, true);
                
                if (state.id == 0)
                {
                        log.log(Level.INFO, "ActorWarp for pid {0} at tick {1} (late {2}) (hint {3})", new Object[] {pid, tick, ticks_late, hint});
                }
                
                return true;
        }

        @Override
        public boolean isConsistent(State older, State newer)
        {
                // position is already checked by State (a history is kept for every tick)
                return true;
        }

        @Override
        public void resetExecutionHistory(State state, State resetTo, Operation resetToOperation)
        {
        }

        @Override
        public void placedBackOnTodo(State state)
        {
        }

        @Override
        public PhysicsWarp getWarp()
        {
                return warp;
        }
}