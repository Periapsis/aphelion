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

package aphelion.shared.swissarmyknife;

/**
 * Rolling ticked history which permits null values.
 * @param <T> 
 * @author Joris
 */
public class RollingHistory<T>
{
        public final int HISTORY_LENGTH;
        private int history_index = 0;
        private long history_tick = 0;
        private final T[] history;
        
        public RollingHistory(long initial_tick, int history_length)
        {
                if (history_length < 1)
                {
                        throw new IllegalArgumentException();
                }
                
                this.HISTORY_LENGTH = history_length;
                this.history_tick = initial_tick;
                history = (T[]) new Object[HISTORY_LENGTH];
        }
        
        protected void updated() {}
        
        /** Set the history value for a particular tick.
         * If this tick is greater than getMostRecentTick(), 
         * the oldest value might be removed if the history length has been reached.
         * If this tick is too old, nothing is set and this method returns -1;
         * @param tick
         * @param value
         * @return The index in the internal array that the value was set on. or -1 on failure
         */
        public int setHistory(long tick, T value)
        {
                while (tick > history_tick)
                {
                        ++history_index;
                        ++history_tick;
                        if (history_index == HISTORY_LENGTH)
                        {
                                history_index = 0;
                        }
                        history[history_index] = null;
                }

                long tick_diff = history_tick - tick;
                if (tick_diff >= HISTORY_LENGTH)
                {
                        // My history does not go back that far
                        return -1;
                }
                
                int index = history_index - (int) tick_diff;
                if (index < 0)
                {
                        index += HISTORY_LENGTH;
                }

                history[index] = value;
                
                updated();
                return index;
        }
        
        /** Get a value relative to getMostRecentTick().
         * @param ticks_ago Relative tick value
         * @return historic value previously set by setHistory()
         * @throws IllegalArgumentException if ticks_ago &lt; 0 || ticks_ago &gt;= HISTORY_LENGTH
         */
        public T getRelative(int ticks_ago) throws IllegalArgumentException
        {
                if (ticks_ago < 0 || ticks_ago >= HISTORY_LENGTH)
                {
                        throw new IllegalArgumentException();
                }
                
                int index = history_index - ticks_ago;
                if (index < 0) { index += HISTORY_LENGTH; }

                return history[index];
        }
        
        /** Get a value.
         * @param tick Absolute tick value
         * @return historic value previously set by setHistory() or null if there is no value for this tick.
         */
        public T get(long tick)
        {
                long ticks_ago = history_tick - tick;
                if (ticks_ago < 0 || ticks_ago >= HISTORY_LENGTH)
                {
                        return null;
                }
                
                int index = history_index - (int) ticks_ago;
                if (index < 0) { index += HISTORY_LENGTH; }

                return history[index];
        }
        
        /** The highest tick we have a value for.
         * @return tick
         */
        public long getMostRecentTick()
        {
                return history_tick;
        }
        
        /** The oldest tick we might a value for.
         * This value might be null or have never been actually set.
         * @return tick
         */
        public long getOldestTick()
        {
                return history_tick - HISTORY_LENGTH + 1;
        }
        
        /** Set this instance to be identical to another instance.
         * This method avoids triggering garbage collection (versus .clone()).
         * @param other 
         */
        public void set(RollingHistory<T> other)
        {
                if (other.HISTORY_LENGTH > this.HISTORY_LENGTH)
                {
                        throw new IllegalArgumentException();
                }
                
                this.history_tick = other.history_tick;
                
                this.history_index = this.HISTORY_LENGTH - 1;
                int myIndex = this.history_index;
                int otherIndex = other.history_index;
                boolean moreData = true;
                
                while (myIndex >= 0)
                {
                        if (moreData)
                        {
                                this.history[myIndex] = other.history[otherIndex];
                                
                                --otherIndex;
                        
                                if (otherIndex == -1)
                                {
                                        otherIndex = other.HISTORY_LENGTH-1;
                                }
                                assert otherIndex >= 0;

                                if (otherIndex == other.history_index)
                                {
                                        moreData = false;
                                }
                        }
                        else
                        {
                                this.history[myIndex] = null;
                        }
                        
                        --myIndex;
                }
                
                updated();
        }
        
        @Override
        public String toString()
        {
                StringBuilder ret = new StringBuilder();
                ret.append(history_tick);
                ret.append(": ");
                
                for (int t = 0; t < HISTORY_LENGTH; ++t)
                {
                        ret.append(getRelative(t));
                        ret.append(" ");
                }
                
                return ret.toString();
        }
}