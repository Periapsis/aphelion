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
public class PhysicsPointHistoryDetailed extends PhysicsPointHistory
{
        private static class Entry
        {
                long tick;
                int x;
                int y;
                Entry next;
        }
        
        // Entries will be destroyed and created by a large volume in the main loop
        // Never GC them (an intentional "memory leak" until the thread is destroyed)
        private static ThreadLocal<Entry> unused = new ThreadLocal<>();
        
        private static Entry newEntry(long tick, int x, int y)
        {
                Entry ret = unused.get();
                if (ret == null)
                {
                        ret = new Entry();
                }
                else
                {
                        unused.set(ret.next);
                }
                
                ret.tick = tick;
                ret.x = x;
                ret.y = y;
                ret.next = null;
                
                return ret;
        }
        
        private static void releaseEntry(Entry e)
        {
                if (e == null) return;
                Entry current = unused.get();
                unused.set(e);
                while (e.next != null) // remove multiple entries at once
                {
                        e = e.next;
                }
                e.next = current;
        }
        
        
        private Entry first; // sorted by highest tick first. if ticks are equal, the last one to be added is first
        private Entry iteration_current;
        
        public PhysicsPointHistoryDetailed(long initial_tick, int history_length)
        {
                super(initial_tick, history_length);
        }
        
        public void appendDetail(long tick, int x, int y)
        {
                Entry myEntry = newEntry(tick, x, y);
                
                iteration_current = null;
                
                if (this.first == null)
                {
                        this.first = myEntry;
                        return;
                }
                
                if (this.first != null && this.first.tick <= tick)
                {
                        myEntry.next = this.first;
                        this.first = myEntry;
                        return;
                }
                
                for (Entry e = this.first; e != null; e = e.next)
                {
                        if (e.next == null)
                        {
                                e.next = myEntry;
                                return;
                        }
                        
                        if (e.next.tick <= tick)
                        {
                                myEntry.next = e.next;
                                e.next = myEntry;
                                return;
                        }
                }
        }
        
        public void appendDetail(long tick, PhysicsPoint pos)
        {
                appendDetail(tick, pos.x, pos.y);
        }
        
        public void clearDetails(long tick)
        {
                iteration_current = null;
                
                Entry one_before_start = null;
                Entry start = null;
                Entry end = null;
                
                Entry prev = null;
                for (Entry e = this.first; e != null; e = e.next)
                {
                        if (e.tick == tick)
                        {
                                if (one_before_start == null)
                                {
                                        one_before_start = prev;
                                        start = e;
                                }
                                
                                if (e.next == null || e.next.tick != tick)
                                {
                                        end = e;
                                }
                        }
                        
                        // no need to keep looking
                        else if (e.tick < tick)
                        {
                                break;
                        }
                        
                        prev = e;
                }
                
                if (one_before_start == null)
                {
                        if (end == null)
                        {
                                this.first = null;
                        }
                        else
                        {
                                this.first = end.next;
                                end.next = null;
                        }
                        releaseEntry(start);
                }
                else
                {
                        if (end == null)
                        {
                                one_before_start.next = null;
                        }
                        else
                        {
                                one_before_start.next = end.next;
                                end.next = null;
                        }
                        releaseEntry(start);
                }
        }
        
        
        public void seekDetail(long tick)
        {
                iteration_current = null;
                
                for (Entry e = this.first; e != null; e = e.next)
                {
                        if (e.tick == tick)
                        {
                                iteration_current = e;
                                break;
                        }
                        
                        // no need to keep looking
                        if (e.tick < tick)
                        {
                                break;
                        }
                }
        }
        
        public void seekDetailRelative(int tick)
        {
                seekDetail(getMostRecentTick() - tick);
        }
        
        public boolean hasNextDetail()
        {
                return iteration_current != null;
        }
        
        // the last one to be added is return first
        public void nextDetail(PhysicsPoint result)
        {
                if (result == null)
                {
                        throw new IllegalStateException();
                }
                
                result.set(iteration_current.x, iteration_current.y);
                
                if (iteration_current.next != null && iteration_current.next.tick == iteration_current.tick)
                {
                        iteration_current = iteration_current.next;
                        return;
                }
                
                iteration_current = null;
        }

        @Override
        protected void updated()
        {
                // remove old data
                long removeBefore = getMostRecentTick() - HISTORY_LENGTH + 1;
                
                Entry prev = null;
                // highest tick come first
                for (Entry e = this.first; e != null; e = e.next)
                {
                        if (e.tick < removeBefore) 
                        {
                                if (prev == null)
                                {
                                        this.first = null;
                                }
                                else
                                {
                                        prev.next = null;
                                }
                                releaseEntry(e);
                                return;
                        }
                        
                        prev = e;
                }
        }

        @Override
        public void set(PhysicsPointHistory other)
        {
                super.set(other);
                
                releaseEntry(first);
                first = null;
                iteration_current = null;
                
                if (other instanceof PhysicsPointHistoryDetailed)
                {
                        PhysicsPointHistoryDetailed otherDetailed = (PhysicsPointHistoryDetailed) other;
                        
                        Entry last = null;
                        
                        for (Entry e = otherDetailed.first; e != null; e = e.next)
                        {
                                if (last == null)
                                {
                                        last = newEntry(e.tick, e.x, e.y);
                                        this.first = last;
                                }
                                else
                                {
                                        last.next = newEntry(e.tick, e.x, e.y);
                                        last = last.next;
                                }
                        }
                }
        }
}
