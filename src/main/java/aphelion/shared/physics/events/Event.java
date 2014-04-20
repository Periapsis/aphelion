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

package aphelion.shared.physics.events;


import aphelion.shared.physics.events.pub.EventPublic;
import aphelion.shared.physics.State;
import aphelion.shared.swissarmyknife.AttachmentData;
import aphelion.shared.swissarmyknife.AttachmentManager;
import aphelion.shared.swissarmyknife.LinkedListEntry;

/**
 *
 * @author Joris
 */
public abstract class Event implements EventPublic
{
        public static final AttachmentManager attachmentManager = new AttachmentManager();
        private final AttachmentData attachments = attachmentManager.getNewDataContainer();
        
        public final LinkedListEntry<Event> link;
        public final EventKey key;
        
        /** if true, this event has been added to PhysicsEnvironment.
         * This is to prevent duplicates. 
         * addEvent() should be called whenever an event is executed.
         */
        public boolean inEnvList = false;
        
        protected long highestExecutionTick = Long.MIN_VALUE;
        
        protected Event(EventKey key)
        {
                link = new LinkedListEntry<>(null, this);
                this.key = key;
        }
        
        @Override
        public final AttachmentData getAttachments()
        {
                return attachments;
        }
        
        public void execute(long tick, State state)
        {
                if (tick > highestExecutionTick)
                {
                        highestExecutionTick = tick;
                }
        }
        
        /** Has this event been executed consistently between the given two states?. 
         * 
         * @param older newer.tick > older.tick
         * @param newer newer.tick > older.tick
         * @return false if no longer consistent and a timewarp should occur.
         */
        abstract public boolean isConsistent(State older, State newer);
        
        /** Is this an old event and is it okay to forget about it?.
         * This normally occurs if the event has been executed in every state.
         * Or if it has not occurred in every state, when any tick value is older 
         * than our history.
         * @param removeOlderThan_tick If we are older (in all states) than this time value, return true.
         *        If equal to or newer than the time value, return false.
         * @return True if the event should be removed (GC or whatever)
         */
        public boolean isOld(long removeOlderThan_tick)
        {
                // If a timewarp causes this event to not be executed anymore,
                // still keep this event in the PhysicsEnvironment list for a while.
                // This works because highestExecutionTick is never reset.
                
                // Keeping an event that is gone because of a timewarp is useful,
                // for example to cancel animations.
                
                return this.highestExecutionTick < removeOlderThan_tick;
        }
        
        /** Called during a timewarp to reset the history state of this event.
         * 
         * @param state
         * @param resetTo The state to reset to
         * @param resetToEvent The foreign event to reset to. 
         *        If resetTo is a local state (as opposed to foreign), 
         *        it is the same as "this".
         */
        abstract public void resetExecutionHistory(State state, State resetTo, Event resetToEvent);
}
