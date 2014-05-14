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
import aphelion.shared.physics.operations.pub.ActorRemovePublic;
import aphelion.shared.physics.entities.Actor;
import aphelion.shared.physics.entities.Projectile;
import aphelion.shared.physics.State;
import aphelion.shared.physics.entities.ActorKey;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class ActorRemove extends Operation implements ActorRemovePublic
{
        private static final Logger log = Logger.getLogger("aphelion.shared.physics");
        
        public ActorRemove(SimpleEnvironment env, OperationKey key)
        {
                super(env, false, PRIORITY.ACTOR_REMOVE, key);
        }

        @Override
        public boolean execute(State state, long ticks_late)
        {
                Actor actor;

                actor = state.actors.get(new ActorKey(pid));
                if (actor == null)
                {
                        // this works because PIDs are unique.
                        // An ActorNew or ActorRemove never has the same pid twice.
                        state.unknownActorRemove.add(pid);
                        return true;
                }
                actor.softRemove(tick);
                
                Iterator<Projectile> itProjectile = state.projectilesList.iterator();
                while(itProjectile.hasNext())
                {
                        Projectile projectile = itProjectile.next();
                        if (projectile.owner != null && projectile.owner.pid == this.pid)
                        {
                                projectile.softRemove(tick);
                        }
                }
                
                if (state.id == 0)
                {
                        log.log(Level.INFO, "Removed actor {0} at tick {1} (late {2})", new Object[] {pid, tick, ticks_late});
                }
                
                return true;
        }

        @Override
        public boolean isConsistent(State older, State newer)
        {
                // nothing to do unless there is a condition in which ActorRemove might fail and not remove the player
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
}