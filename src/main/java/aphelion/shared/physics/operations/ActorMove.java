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
import aphelion.shared.physics.operations.pub.ActorMovePublic;
import aphelion.shared.physics.entities.Actor;
import aphelion.shared.physics.State;
import aphelion.shared.physics.entities.ActorKey;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.physics.valueobjects.PhysicsWarp;

/**
 *
 * @author Joris
 */
public class ActorMove extends Operation implements ActorMovePublic
{
        public PhysicsMovement move;

        public ActorMove(SimpleEnvironment env, OperationKey key)
        {
                super(env, true, PRIORITY.ACTOR_MOVE, key);
        }
        
        @Override
        public int comparePriority(Operation other_)
        {
                int c = super.comparePriority(other_);
                
                if (c == 0 && other_ instanceof ActorMove)
                {
                        ActorMove other = (ActorMove) other_;
                        
                        return Integer.compare(move.bits, other.move.bits);
                }
                
                return c;
        }
        
        @Override
        public boolean execute(State state, boolean late, long ticks_late)
        {
                Actor actor = state.actors.get(new ActorKey(pid));

                if (actor == null) // ignore operation
                {
                        return true;
                }
                
                if (actor.isDead(tick))  // ignore operation
                {
                        return true;
                }
                
                // note: this is only checked within the current state,
                // a timewarp will take care of the rest
                if (actor.moveHistory.get(this.tick) != null)
                {
                        // duplicate move
                        return true;
                }
                
                assert PRIORITY.ACTOR_WARP.priority < PRIORITY.ACTOR_MOVE.priority; // warp must execute before move
                if (actor.moveHistory.get(this.tick) instanceof PhysicsWarp)
                {
                        // do not allow moves when there was already a warp for this tick
                        return true;
                }
                
                // Note: this tick the new velocity is set, but this new velocity is not used yet.
                // It will be used in the next tick
                
                actor.moveHistory.setHistory(this.tick, move);
                if (ticks_late == 0) // not late, apply immediately
                {
                        actor.applyMoveable(move, this.tick);
                        actor.updatedPosition(this.tick);
                }
                else
                {
                        // dead reckon current position so that it is no longer late (defered until state.tickReexecuteDirtyPositionPath() )
                        // note: the position at the tick of this operation is not affected by this operation, therefor +1
                        actor.markDirtyPositionPath(this.tick);
                }
                
                return true;
        }

        @Override
        public boolean isConsistent(State older, State newer)
        {
                return true; // consistency check for actor position is performed by State
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
        public PhysicsMovement getMove()
        {
                return move;
        }

        
}