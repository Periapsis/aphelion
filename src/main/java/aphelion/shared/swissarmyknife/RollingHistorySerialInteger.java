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
 *
 * @author Joris
 */
public class RollingHistorySerialInteger
{
        public final int HISTORY_LENGTH;
        public final int SETTERS;
        private int history_index = 0;
        private long history_tick = 0;
        
        private boolean dirty;
        private long dirtySince_tick; // only valid if dirty = true
        
        private int oldestValue; // The value that is right before the oldest value we can look up in values[]
        private final int[] values;
        private final int[][] delta; // delta increase since tick - 1
        private final byte[][] absolute; // if set to 1, the "tick - 1" value is 0 in any "tick >=" calculations. 
                                         // if set to 2, ignore all other setters and min/max
        private final int[] min;
        private final int[] max;
        
        public RollingHistorySerialInteger(long initial_tick, int history_length, int setters)
        {
                this.HISTORY_LENGTH = history_length;
                this.history_tick = initial_tick;
                this.SETTERS = setters;
                
                values = new int[HISTORY_LENGTH];
                delta = new int[SETTERS][];
                absolute = new byte[SETTERS][];
                
                for (int i = 0; i < SETTERS; ++i)
                {
                        delta[i] = new int[HISTORY_LENGTH];
                        absolute[i] = new byte[HISTORY_LENGTH];
                }
                
                
                min = new int[HISTORY_LENGTH];
                max = new int[HISTORY_LENGTH];
                
                for (int i = 0; i < HISTORY_LENGTH; ++i)
                {
                        min[i] = Integer.MIN_VALUE;
                        max[i] = Integer.MAX_VALUE;
                }
        }
        
        private int tickToIndex(long tick, boolean create)
        {
                while (tick > history_tick)
                {
                        if (!create)
                        {
                                return -1;
                        }
                        
                        if (dirty && dirtySince_tick == history_tick)
                        {
                                updateDirty();
                        }
                        
                        ++history_index;
                        ++history_tick;
                        if (history_index == HISTORY_LENGTH)
                        {
                                history_index = 0;
                        }
                        
                        int prev_index = history_index - 1;
                        if (prev_index < 0)
                        {
                                assert prev_index == -1;
                                prev_index = HISTORY_LENGTH-1;
                        }
                        
                        // About to discard the oldest value,
                        // store it.
                        // (if we have less than HISTORY_LENGTH values, oldestValue will become 0)
                        oldestValue = values[history_index];
                        
                        values[history_index] = values[prev_index];
                        for (int s = 0; s < SETTERS; ++s)
                        {
                                delta[s][history_index] = 0;
                                absolute[s][history_index] = 0;
                        }
                        
                        
                        
                        min[history_index] = min[prev_index];
                        max[history_index] = max[prev_index];
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
                
                return index;
        }
        
        /** Set a tick to an relative value by adding up previous ticks.
         */
        public void setRelativeValue(int setterID, long tick, int deltaValue)
        {
                int index = tickToIndex(tick, true);
                if (index >= 0)
                {
                        if (delta[setterID][index] != deltaValue || 
                            absolute[setterID][index] != 0)
                        {    
                                delta[setterID][index] = deltaValue;
                                absolute[setterID][index] = 0;
                                markDirty(tick);
                        }
                }
        }
        
        /** Set a tick to an absolute value by ignoring previous ticks.
         */
        public void setAbsoluteValue(int setterID, long tick, int absoluteValue)
        {
                int index = tickToIndex(tick, true);
                if (index >= 0)
                {
                        if (delta[setterID][index] != absoluteValue ||
                            absolute[setterID][index] != 1)
                        {
                                delta[setterID][index] = absoluteValue;
                                absolute[setterID][index] = 1;
                                markDirty(tick);
                        }
                }
        }
        
        /** Set a tick to an absolute value by ignoring previous ticks and ignore all other setters.
         * Minimum and maximum is also ignored.
         */
        public void setAbsoluteOverrideValue(int setterID, long tick, int absoluteValue)
        {
                int index = tickToIndex(tick, true);
                if (index >= 0)
                {
                        if (delta[setterID][index] != absoluteValue ||
                            absolute[setterID][index] != 2)
                        {
                                delta[setterID][index] = absoluteValue;
                                absolute[setterID][index] = 2;
                                markDirty(tick);
                        }
                }
        }
        
        public int getSetterValue(int setterID, long tick)
        {
                int index = tickToIndex(tick, true);
                if (index >= 0)
                {
                        return delta[setterID][index];
                }
                
                return 0;
        }
        
        public void addRelativeValue(int setterID, long tick, int deltaValue)
        {
                if (deltaValue == 0) { return; }
                int index = tickToIndex(tick, true);
                if (index >= 0 && absolute[setterID][index] != 2) // do not add if setAbsoluteOverrideValue was used
                {
                        delta[setterID][index] += deltaValue;
                        markDirty(tick);
                }
        }
        
        public void addAbsoluteValue(int setterID, long tick, int absoluteValue)
        {
                int index = tickToIndex(tick, true);
                if (index >= 0 && absolute[setterID][index] != 2) // do not add if setAbsoluteOverrideValue was used
                {
                        delta[setterID][index] += absoluteValue;
                        
                        if (absolute[setterID][index] == 0)
                        {
                                absolute[setterID][index] = 1;
                        }
                        markDirty(tick);
                }
        }
        
        public void setMinimum(long tick, int minimum)
        {
                int index = tickToIndex(tick, true);
                if (index >= 0)
                {
                        if (min[index] != minimum)
                        {
                                min[index] = minimum;
                                markDirty(tick);
                        }
                }
        }
        
        public void setMaximum(long tick, int maximum)
        {
                int index = tickToIndex(tick, true);
                if (index >= 0)
                {
                        if (max[index] != maximum)
                        {
                                max[index] = maximum;
                                markDirty(tick);
                        }
                }
        }
        
        
        private void markDirty(long tick)
        {
                if (!dirty || tick < dirtySince_tick)
                {
                        dirty = true;
                        dirtySince_tick = tick;
                }
        }
        
        private int getOldestIndex()
        {
                int oldestIndex = this.history_index - (HISTORY_LENGTH - 1);
                if (oldestIndex < 0)
                {
                        oldestIndex = HISTORY_LENGTH + oldestIndex;
                }
                return oldestIndex;
        }
        
        private void updateDirty()
        {
                long value;
                
                if (!dirty)
                {
                        return;
                }
                
                if (this.dirtySince_tick > this.history_tick)
                {
                        return;
                }
                
                int index = this.tickToIndex(this.dirtySince_tick, false);
                assert index >= 0; // if tick is out of range, we have not updated enough
                
                int oldestIndex = getOldestIndex();
                
                if (index == oldestIndex)
                {
                        value = this.oldestValue;
                }
                else
                {
                        if (index == 0)
                        {
                                value = this.values[HISTORY_LENGTH-1];
                        }
                        else
                        {
                                value = this.values[index-1];
                        }
                }
                
                while (true)
                {
                        boolean hasAbsolute = false;
                        boolean hasAbsoluteOverride = false;
                        long localValue = 0;
                        
                        for (int s = 0; s < SETTERS; ++s)
                        {
                                if (absolute[s][index] > 0)
                                {
                                        hasAbsolute = true;
                                }
                                
                                if (absolute[s][index] == 2)
                                {
                                        localValue = this.delta[s][index];
                                        hasAbsoluteOverride = true;
                                        break;
                                }
                                
                                localValue += this.delta[s][index];
                        }
                        
                        value = (hasAbsolute ? 0 : value) + localValue;
                        
                        if (!hasAbsoluteOverride)
                        {
                                value = SwissArmyKnife.clip(value, this.min[index], this.max[index]);
                        }
                        
                        this.values[index] = (int) value;
                        
                        if (index == this.history_index)
                        {
                                break;
                        }
                        
                        ++index;
                        if (index == HISTORY_LENGTH)
                        {
                                index = 0;
                        }
                }
                
                this.dirty = false;
        }
        
        /** Get the total value for a tick. Relative values of previous ticks are added up.
         */
        public int get(long tick)
        {
                int index = tickToIndex(tick > this.history_tick ? this.history_tick : tick, false);
                if (index >= 0)
                {
                        updateDirty();
                        return values[index];
                }
                
                return 0;
        }
        
        public boolean hasValueFor(long tick)
        {
                return tickToIndex(tick, false) >= 0;
        }
        
        public long getMostRecentTick()
        {
                return history_tick;
        }
        
        public void set(RollingHistorySerialInteger other)
        {
                assert other.HISTORY_LENGTH <= this.HISTORY_LENGTH;
                assert other.SETTERS == this.SETTERS;
                history_index = other.history_index;
                history_tick = other.history_tick;
                dirty = other.dirty;
                dirtySince_tick = other.dirtySince_tick;
                oldestValue = other.oldestValue;
                
                System.arraycopy(other.values, 0, values, 0, other.HISTORY_LENGTH);
                System.arraycopy(other.min, 0, min, 0, other.HISTORY_LENGTH);
                System.arraycopy(other.max, 0, max, 0, other.HISTORY_LENGTH);
                
                for (int i = other.HISTORY_LENGTH; i < this.HISTORY_LENGTH; ++i)
                {
                        values[i] = 0;
                        min[i] = 0;
                        max[i] = 0;
                }
                
                for (int s = 0; s < SETTERS; ++s)
                {
                        System.arraycopy(other.delta[s], 0, delta[s], 0, other.HISTORY_LENGTH);
                        System.arraycopy(other.absolute[s], 0, absolute[s], 0, other.HISTORY_LENGTH);
                        
                        for (int i = other.HISTORY_LENGTH; i < this.HISTORY_LENGTH; ++i)
                        {
                                delta[s][i] = 0;
                                absolute[s][i] = 0;
                        }
                }
        }
}
