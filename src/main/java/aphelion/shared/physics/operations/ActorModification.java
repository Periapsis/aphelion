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
import aphelion.shared.physics.entities.Actor;
import aphelion.shared.physics.operations.pub.ActorModificationPublic;
import aphelion.shared.physics.State;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class ActorModification extends Operation implements ActorModificationPublic
{
        private static final Logger log = Logger.getLogger("aphelion.shared.physics");
        
        private final boolean[] executed;
        
        // all attributes are optional
        public String ship;
        
        public ActorModification(EnvironmentConfiguration econfig)
        {
                super(econfig, false, PRIORITY.ACTOR_MODIFICATION);
                executed = new boolean[econfig.TRAILING_STATES];
        }
        
        @Override
        public int comparePriority(Operation other_)
        {
                int c = super.comparePriority(other_);
                
                if (c == 0 && other_ instanceof ActorModification)
                {
                        ActorModification other = (ActorModification) other_;
                        
                        // this should rarely happen, the server should not send 
                        // conflicting modifications for the same tick
                        
                        return SwissArmyKnife.stringCompare(ship, other.ship);
                        
                }
                
                return c;
        }
        
        @Override
        public boolean execute(State state, long ticks_late)
        {
                Actor actor = state.actors.get(pid);
                
                if (actor == null)
                {
                        return true; // unknown pid
                }
                
                assert !executed[state.id];
                
                if (ship != null)
                {
                        if (!state.isLast && ticks_late > 0)
                        {
                                // A lot of config is not checked for consistency explicitely
                                // So make sure config is never executed late. (the server should
                                // just send config into the future)
                                // Force a timewarp
                                return true;
                        }
                }
                
                // might not execute if the player does not exist yet
                // so track that we have executed
                executed[state.id] = true;
                
                if (ship != null)
                {
                        actor.setShip(ship);
                }
                
                return true;
        }
        
        @Override
        public boolean isConsistent(State older, State newer)
        {
                /*if (this.tick > older.tick_now)
                {
                        return true; // This operation has not had the chance to execute on both states (yet)
                }*/
                
                if (executed[older.id] && !executed[newer.id])
                {
                        //was executed in older but not in newer
                        return false;
                }
                
                return true;
        }
        
        @Override
        public void resetExecutionHistory(State state, State resetTo)
        {
                if (state.tick_now <= this.tick)
                {
                        executed[state.id] = false;
                }
        }

        @Override
        public String getShip()
        {
                return ship;
        }
}
