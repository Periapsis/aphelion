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

/**
 *
 * @author Joris
 */
public class PhysicsPointHistory
{
        private static final boolean DEBUG = false;
        public final int HISTORY_LENGTH;
        private int history_index = 0;
        private long history_tick = 0;
        private final int[] history_x;
        private final int[] history_y;

        public PhysicsPointHistory(long initial_tick, int history_length)
        {
                this.HISTORY_LENGTH = history_length;
                this.history_tick = initial_tick;
                history_x = new int[HISTORY_LENGTH];
                history_y = new int[HISTORY_LENGTH];
        }
        
        protected void updated() {}
        
        public int setHistory(long tick, PhysicsPoint point)
        {
                return setHistory(tick, point.x, point.y);
        }
        
        private StackTraceElement[] prevStack;
        /** Add (or update) the current position to the history
         *
         * @param tick The tick the given position (x and y) belongs to
         * @param x The position
         * @param y The position
         * @return The history index that was just set in history_x, history_y, etc -1 if no history was set
         */
        public int setHistory(long tick, int x, int y)
        {
                if (tick > history_tick + 1)
                {
                        if (DEBUG && prevStack != null)
                        {
                                System.out.flush();
                                System.out.println("Previously set by:");
                                for (StackTraceElement s : prevStack)
                                {
                                        System.out.println(s);
                                }
                                System.out.flush();
                        }
                        throw new IllegalStateException("Missing history for the previous tick(s). tick = " + (tick) + "; history_tick = " + history_tick);
                }
                
                if (DEBUG)
                {
                        prevStack = (new Throwable()).getStackTrace();
                }

                if (tick == history_tick + 1)
                {
                        ++history_index;
                        ++history_tick;
                        if (history_index == HISTORY_LENGTH)
                        {
                                history_index = 0;
                        }
                }

                long tick_diff = history_tick - tick;
                if (tick_diff >= HISTORY_LENGTH)
                {
                        // My history does not go back that far, look at a trailing state
                        return -1;
                }
                
                int index = history_index - (int) tick_diff;
                if (index < 0)
                {
                        index += HISTORY_LENGTH;
                }

                history_x[index] = x;
                history_y[index] = y;
                
                updated();
                return index;
        }
        
        public void set(PhysicsPointHistory other)
        {
                assert other.HISTORY_LENGTH <= this.HISTORY_LENGTH;
                history_index = other.history_index;
                history_tick = other.history_tick;
                
                System.arraycopy(other.history_x, 0, history_x, 0, other.HISTORY_LENGTH);
                System.arraycopy(other.history_y, 0, history_y, 0, other.HISTORY_LENGTH);
                
                for (int i = other.HISTORY_LENGTH; i < this.HISTORY_LENGTH; ++i)
                {
                        history_x[i] = 0;
                        history_y[i] = 0;
                }
                
                if (DEBUG)
                {
                        prevStack = (new Throwable()).getStackTrace();
                }
                
                updated();
        }
        
        protected final int getIndex(long ticks_ago)
        {
                if (ticks_ago < 0 || ticks_ago >= HISTORY_LENGTH)
                {
                        return -1;
                }
                
                int index = history_index - (int) ticks_ago;
                if (index < 0) { index += HISTORY_LENGTH; }
                return index;
        }
        
        protected final int getIndex(int ticks_ago)
        {
                if (ticks_ago < 0 || ticks_ago >= HISTORY_LENGTH)
                {
                        return -1;
                }
                
                int index = history_index - ticks_ago;
                if (index < 0) { index += HISTORY_LENGTH; }
                return index;
        }
        
        public int getXRelative(int ticks_ago)
        {
                int i = getIndex(ticks_ago);
                return i < 0 ? 0 : history_x[i];
        }
        
        public int getYRelative(int ticks_ago)
        {
                int i = getIndex(ticks_ago);
                return i < 0 ? 0 : history_y[i];
        }
        
        public void getRelative(PhysicsPoint ret, int ticks_ago)
        {
                ret.unset();
                int index = getIndex(ticks_ago);
                
                if (index < 0)
                {
                        ret.unset();
                        return;
                }
                
                ret.set = true;
                ret.x = history_x[index];
                ret.y = history_y[index];
        }
        
        public boolean isWithinRange(long tick)
        {
                long ticks_ago = getMostRecentTick() - tick;
                if (ticks_ago < 0 || ticks_ago >= HISTORY_LENGTH)
                {
                        return false;
                }
                
                return true;
        }
        
        public int getX(long tick)
        {
                int i = getIndex(getMostRecentTick() - tick);
                return i < 0 ? 0 : history_x[i];
        }
        
        public int getY(long tick)
        {
                int i = getIndex(getMostRecentTick() - tick);
                return i < 0 ? 0 : history_y[i];
        }
        
        public void get(PhysicsPoint ret, long tick)
        {
                ret.unset();
                int index = getIndex(getMostRecentTick() - tick);
                
                if (index < 0)
                {
                        ret.unset();
                        return;
                }
                
                ret.set = true;
                ret.x = history_x[index];
                ret.y = history_y[index];
        }
        
        
        public long getMostRecentTick()
        {
                return history_tick;
        }

        @Override
        public String toString()
        {
                StringBuilder ret = new StringBuilder();
                ret.append(history_tick);
                ret.append(": ");
                
                for (int t = 0; t < HISTORY_LENGTH; ++t)
                {
                        ret.append("(");
                        ret.append(getXRelative(t));
                        ret.append(",");
                        ret.append(getYRelative(t));
                        ret.append(") ");
                }
                
                return ret.toString();
        }
}
