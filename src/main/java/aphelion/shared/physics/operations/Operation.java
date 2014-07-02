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
import aphelion.shared.physics.operations.pub.OperationPublic;
import aphelion.shared.physics.State;
import aphelion.shared.swissarmyknife.LinkedListEntry;

/**
 *
 * @author Joris
 */
public abstract class Operation implements OperationPublic
{
        public final OperationKey key;
        public final SimpleEnvironment env;
        public final LinkedListEntry<Operation>[] link; // key is state_id. This is used in the linkedlist for PhysicsState.todo and PhysicsState.history
        public long tick;
        public int pid = 0; // 0 means this operation does not belong to a player
        public final boolean ignorable; // if set, the operation may be dropped if too old
        protected final int priority; // If there are multiple operation within the same tick. 
        
        // Operations with the lowest priority are executed first
        
        public static enum PRIORITY
        {
                UNLOAD_CONFIG(0),
                LOAD_CONFIG(1),
                ACTOR_NEW(10),
                ACTOR_MODIFICATION(11),
                ACTOR_REMOVE(12),
                ACTOR_WARP(20),
                ACTOR_MOVE(21),
                ACTOR_WEAPON_FIRE(30),
                ACTOR_WEAPON_SYNC(30),
                ACTOR_SYNC(1000), // see isLateSyncOperation() and state.tickOperations()
                ;
                
                public int priority;

                private PRIORITY(int priority)
                {
                        this.priority = priority;
                }
        };
        
        public boolean isLateSyncOperation()
        {
                return false;
        }
        
        @SuppressWarnings("unchecked")
        protected Operation(SimpleEnvironment env, boolean ignorable, PRIORITY priority, OperationKey key)
        {
                int i;
  
                this.key = key;
                this.env = env;
                this.ignorable = ignorable;
                this.priority = priority.priority;

                link = new LinkedListEntry[env.econfig.TRAILING_STATES];
                for (i = 0; i < env.econfig.TRAILING_STATES; i++)
                {
                        link[i] = new LinkedListEntry<>(this);
                }
        }

        @Override
        public String toString()
        {
                return this.getClass().getSimpleName() + ": " + tick;
        }
        
        /** This is used for sorting in the state todo list.
         * Subclasses should override this method to make sure operations 
         * of the same type and the same tick are executed in the correct 
         * order on every machine.
         * @param other 
         * @return &lt; 0 if the priority of this operation is less than the argument "other",
         *         in this case this operation will be executed sooner. 
         *         0 for equal, &gt; 0 if this operation has a higher priority.
         */
        public int comparePriority(Operation other)
        {
                int c = Integer.compare(this.priority, other.priority);
                if (c == 0)
                {
                        c = Integer.compare(this.pid, other.pid);
                }
                
                return c;
        }

        /**
         * Execute the operation on a state.
         *
         * Execution should perform the following steps:
         *
         * 1. Verify this operation is allowed at this time (ex: do not allow the player fire while dead, player does no
         *    have enough energy to fire, etc). 
         * 2. Execute operation. 
         * 3. Store the end result of all changes (like the new
         *    position of the player in a move operation). To be used in isConsistent()
         * AND/OR:
         * 3. Immediately check if the execution was consistent with newer states and return false if it is not.
         *
         * Return value vs isConsistent:
         * The main difference is that isConsistent() is called after all other work for this tick is done.
         * Thus using "return false" is not suitable in all situations.
         * 
         * @param state
         * @param late If false, this operation is being executed as part of the State.tickOperations() method, 
         *        because it is on the todo list.
         *        
         *        If true, the state is not currently ticking and the operation has arrived late. Some operations 
         *        may want to perform basic corrections. Trailing state synchronization will correct bigger 
         *        inconsistencies. Note that late=true,ticks_late=0 is a valid combination.
         * @param ticks_late How many ticks this operation is performed late. 
         * @return return false to indicate the execution of this operation was not consistent with newer states.
         *         A time warp to _this_ state will occur.
         */
        abstract public boolean execute(State state, boolean late, long ticks_late);

        /** Has this operation been executed consistently between the given two states?.
         * Compare the stored results from step 3 in execute() with each other. 
         * Depending on the type of operation, this check does not have to be strongly 
         * consistent. For example a MOVE operation may allow a few pixels of deviation. 
         * (other more important events such as weapon hit may resolve such errors later).
         * @param older newer.tick > older.tick
         * @param newer newer.tick > older.tick
         * @return false if no longer consistent and a timewarp should occur
         */
        abstract public boolean isConsistent(State older, State newer);

        /** Called during a timewarp to reset the history state of this operation.
         * 
         * @param state
         * @param resetTo The state to reset to
         * @param resetToOperation The foreign operation to reset to. 
         *        If resetTo is a local state (as opposed to foreign), 
         *        it is the same as "this".
         */
        abstract public void resetExecutionHistory(State state, State resetTo, Operation resetToOperation);
        
        /** Called during a timewarp if this operation is placed back on the todo list for a state. */
        abstract public void placedBackOnTodo(State state);
        
        @Override
        public long getTick()
        {
                return tick;
        }
        
        @Override
        public int getPid()
        {
                return pid;
        }
}
