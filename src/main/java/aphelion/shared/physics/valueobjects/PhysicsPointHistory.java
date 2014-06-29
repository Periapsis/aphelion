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

package aphelion.shared.physics.valueobjects;

import aphelion.shared.swissarmyknife.SwissArmyKnife;

/**
 * History for x and y.
 * Similar to RollingHistory, however instead a generic, you can use two integer values.
 * @author Joris
 */
public class PhysicsPointHistory
{
        public final int HISTORY_LENGTH;
        private int history_index = 0;
        private long history_tick = 0;
        private final int[] history_x;
        private final int[] history_y;
        private UpdateListener listener;
        
        public PhysicsPointHistory(long initial_tick, int history_length)
        {
                this.HISTORY_LENGTH = history_length;
                this.history_tick = initial_tick;
                history_x = new int[HISTORY_LENGTH];
                history_y = new int[HISTORY_LENGTH];
        }
        
        protected void updated(long tick, int index)
        {
                if (listener != null)
                {
                        listener.updated(tick, index);
                }
        }
        
        protected void updatedAll() 
        {
                if (listener != null)
                {
                        listener.updatedAll();
                }
        }
        
        void setListener(UpdateListener listener)
        {
                if (this.listener != null)
                {
                        throw new IllegalStateException();
                }
                this.listener = listener;
        }
        
        public void ensureHighestTick(long tick)
        {
                while (tick > history_tick)
                {
                        ++history_index;
                        ++history_tick;
                        if (history_index == HISTORY_LENGTH)
                        {
                                history_index = 0;
                        }
                        history_x[history_index] = 0;
                        history_y[history_index] = 0;
                }
        }
        
        public int setHistory(long tick, PhysicsPoint point)
        {
                return setHistory(tick, point.x, point.y);
        }
        
        public int setHistory(long tick, int x, int y)
        {
                ensureHighestTick(tick);

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

                history_x[index] = x;
                history_y[index] = y;
                
                updated(tick, index);
                return index;
        }
        
        public void getRelative(PhysicsPoint ret, int ticks_ago) throws IllegalArgumentException
        {
                if (ticks_ago < 0 || ticks_ago >= HISTORY_LENGTH)
                {
                        throw new IllegalArgumentException();
                }
                
                int index = history_index - ticks_ago;
                if (index < 0) { index += HISTORY_LENGTH; }

                ret.set = true;
                ret.x = history_x[index];
                ret.y = history_y[index];
        }
        
        public void get(PhysicsPoint ret, long tick)
        {
                int index = getIndex(tick);
                if (index < 0) { ret.unset(); return; }

                getByIndex(ret, index);
        }
        
        protected void getByIndex(PhysicsPoint ret, int index)
        {
                ret.set = true;
                ret.x = history_x[index];
                ret.y = history_y[index];
        }
        
        protected int getIndex(long tick)
        {
                long ticks_ago = history_tick - tick;
                if (ticks_ago < 0 || ticks_ago >= HISTORY_LENGTH)
                {
                        return -1;
                }
                
                int index = history_index - (int) ticks_ago;
                if (index < 0) { index += HISTORY_LENGTH; }
                
                return index;
        }
        
        public int getX(long tick)
        {
                int index = getIndex(tick);
                if (index < 0) { return 0; }
                return history_x[index];
        }
        
        public int getY(long tick)
        {
                int index = getIndex(tick);
                if (index < 0) { return 0; }
                return history_y[index];
        }

        protected final int getHistoryIndex()
        {
                return history_index;
        }
        
        /** The highest tick we can currently get data for. 
         * Calling setHistory with a value greater than this value will discard the oldest history.
         * @return tick
         */
        public final long getHighestTick()
        {
                return history_tick;
        }
        
        /** The highest tick we can currently get data for. 
         * Calling setHistory with a value lower than this value will have no effect
         * @return tick
         */
        public final long getLowestTick()
        {
                return history_tick - HISTORY_LENGTH + 1;
        }
        
        /** Efficiently set our content to match the content of an other PhysicsPointHistory.
         * @param other A PhysicsPointHistory with an equal or lesser length to set this content to.
         *        If the given PhysicsPointHistory has a lesser length, the oldest ticks are given 
         *        a value of 0.
         */
        public void set(PhysicsPointHistory other)
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
                                this.history_x[myIndex] = other.history_x[otherIndex];
                                this.history_y[myIndex] = other.history_y[otherIndex];
                                
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
                                this.history_x[myIndex] = 0;
                                this.history_y[myIndex] = 0;
                        }
                        
                        --myIndex;
                }
                
                updatedAll();
        }
        
        /** Efficiently set our content to match the content of an other PhysicsPointHistory 
         *  for any tick that fits.
         *  The highest or lowest tick is not modified. Any tick in "other" that falls outside 
         *  of this range is ignored.
         * @param other PhysicsPointHistory of any length
         */
        public void overwrite(PhysicsPointHistory other)
        {
                final long myHighestTick = getHighestTick();
                final long myLowestTick = getLowestTick();
                
                final long otherHighestTick = other.getHighestTick();
                final long otherLowestTick = other.getLowestTick();
                
                long tick = SwissArmyKnife.min(myHighestTick, otherHighestTick);
                int otherIndex = other.getIndex(tick);
                int myIndex = this.getIndex(tick);
                
                if (otherIndex == -1 || myIndex == -1)
                {
                        // History ranges do not overlap
                        return;
                }
                
                while (tick >= myLowestTick && tick >= otherLowestTick)
                {
                        this.history_x[myIndex] = other.history_x[otherIndex];
                        this.history_y[myIndex] = other.history_y[otherIndex];
                        
                        --tick;
                        
                        --otherIndex;
                        if (otherIndex == -1)
                        {
                                otherIndex = other.HISTORY_LENGTH - 1;
                        }
                        
                        --myIndex;
                        if (myIndex == -1)
                        {
                                myIndex = this.HISTORY_LENGTH - 1;
                        }
                }
                
                updatedAll();
        }

        @Override
        public String toString()
        {
                StringBuilder ret = new StringBuilder();
                PhysicsPoint p = new PhysicsPoint();
                ret.append(history_tick);
                ret.append(": ");
                
                for (int t = 0; t < HISTORY_LENGTH; ++t)
                {
                        getRelative(p, t);
                        ret.append(p);
                        ret.append(' ');
                }
                
                return ret.toString();
        }
        
        static interface UpdateListener
        {
                void updated(long tick, int index);
                void updatedAll();
        }
}
