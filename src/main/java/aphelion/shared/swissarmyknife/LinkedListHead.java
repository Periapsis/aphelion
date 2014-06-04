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

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 *
 * @param <T> 
 * @author Joris
 */
public class LinkedListHead<T> implements Iterable<T>
{
        public LinkedListEntry<T> first;
        public LinkedListEntry<T> last;
        
        public LinkedListHead()
        {
        }

        public boolean isEmpty()
        {
                if (first == null || last == null)
                {
                        assert first == null && last == null;
                        
                        return true;
                }
                return false;
        }
        
        public LinkedListEntry<T> prependData(T data)
        {
                return prepend(new LinkedListEntry<>(null, data));
        }

        public LinkedListEntry<T> prepend(LinkedListEntry<T> link)
        {
                if (isEmpty())
                {
                        assert link.head == null;
                        assert link.previous == null;
                        assert link.next == null;

                        link.head = this;
                        
                        first = link;
                        last = link;
                        link.previous = null;
                        link.next = null;
                        
                        return link;
                }
                else
                {
                        return first.prepend(link);
                }
        }

        public LinkedListEntry<T> appendData(T data)
        {
                return append(new LinkedListEntry<>(null, data));
        }

        public LinkedListEntry<T> append(LinkedListEntry<T> link)
        {
                if (isEmpty())
                {                        
                        assert link.head == null;
                        assert link.previous == null;
                        assert link.next == null;

                        link.head = this;
                        
                        first = link;
                        last = link;
                        link.previous = null;
                        link.next = null;

                        return link;
                }
                else
                {
                        return last.append(link);
                }
        }
        
        public void removeRange(LinkedListEntry<T> start, LinkedListEntry<T> end)
        {
                LinkedListEntry<T> link;
                
                assert start.head == this;
                assert end.head == this;
                
                link = start;
                while(true)
                {
                        assert link != null;
                        assert link.head == this;
                        
                        link.head = null;
                        
                        if (link == end)
                        {
                                break;
                        }
                        
                        link = link.next;
                        assert link != start;
                }
                
                
                if (start.previous != null)
                {
                        start.previous.next = end.next;
                }

                if (this.first == start)
                {
                        this.first = end.next;
                }

                if (end.next != null)
                {
                        end.next.previous = start.previous;
                }

                if (this.last == end)
                {
                        this.last = start.previous;
                }

                start.previous = null;
                end.next = null;
        }
        
        public void clear()
        {
                if (this.first == null)
                {
                        assert this.last == null;
                        return;
                }
                
                removeRange(first, last);
        }
        
        public void appendForeignRange(LinkedListEntry<T> start, LinkedListEntry<T> end)
        {
                LinkedListEntry<T> link;
                
                assert start != null;
                assert end != null;
                
                if (first == null)
                {
                        if (start.head != null)
                        {
                                assert start.head != this;
                                start.head.removeRange(start, end);
                        }
                
                        link = start;
                        while(true)
                        {
                                assert link != null;
                                assert link.head == null;

                                link.head = this;

                                if (link == end)
                                {
                                        break;
                                }

                                link = link.next;
                                assert link != start;
                        }

                        this.first = start;
                        this.last = end;
                }
                else
                {
                        last.appendForeignRange(start, end);
                }
        }

        public void prependForeignRange(LinkedListEntry<T> start, LinkedListEntry<T> end)
        {
                LinkedListEntry<T> link;
                
                assert start != null;
                assert end != null;
                
                if (first == null)
                {
                        if (start.head != null)
                        {
                                assert start.head != this;
                                start.head.removeRange(start, end);
                        }
                
                        link = start;
                        while(true)
                        {
                                assert link != null;
                                assert link.head == null;

                                link.head = this;

                                if (link == end)
                                {
                                        break;
                                }

                                link = link.next;
                                assert link != start;
                        }

                        this.first = start;
                        this.last = end;
                }
                else
                {
                        first.prependForeignRange(start, end);
                }
        }

        

        public LinkedListEntry<T> removeByReference(T data)
        {
                LinkedListEntry<T> entry;

                entry = first;
                while (entry != null)
                {
                        if (entry.data == data)
                        {
                                entry.remove();
                                return entry;
                        }
                        entry = entry.next;
                }

                return null;
        }

        public LinkedListEntry<T> removeByEquals(T data)
        {
                LinkedListEntry<T> entry;

                entry = first;
                while (entry != null)
                {
                        if (data == null)
                        {
                                if (entry.data == null)
                                {
                                        entry.remove();
                                        return entry;
                                }
                        }
                        else
                        {
                                if (data.equals(entry.data))
                                {
                                        entry.remove();
                                        return entry;
                                }
                        }
                        entry = entry.next;
                }

                return null;
        }

        @Override
        public String toString()
        {
                int a;
                LinkedListEntry<T> link;
                StringBuilder builder;

                if (first == null)
                {
                        return "LL[]";
                }

                builder = new StringBuilder("LL[");
                link = first;

                for (a = 0; a < 10 && link != null; a++)
                {
                        if (link.data != null)
                        {
                                builder.append(link.data.toString());
                        }
                        else
                        {
                                builder.append("null");
                        }

                        link = link.next;
                        if (link != null)
                        {
                                builder.append(",");
                        }
                }

                if (link != null)
                {
                        builder.append("...");
                }

                builder.append("]");

                return builder.toString();
        }

        public T pop()
        {
                LinkedListEntry<T> val;

                if (last == null)
                {
                        return null;
                }

                val = last;
                val.remove();
                return val.data;
        }

        public T shift()
        {
                LinkedListEntry<T> val;

                if (last == null)
                {
                        return null;
                }

                val = first;
                val.remove();
                return val.data;
        }

        @Override
        public Iterator<T> iterator()
        {
                return new Itr(false, false);
        }
        
        public Iterator<T> iteratorReadOnly()
        {
                return new Itr(true, false);
        }
        
        public Iterator<T> iteratorReverse()
        {
                return new Itr(false, true);
        }
        
        public Iterator<T> iteratorReverseReadOnly()
        {
                return new Itr(true, true);
        }
        
        /** Make all links in this linked list headless.
         */
        public void headless()
        {
                for (LinkedListEntry<T> link = this.first; link != null; link = link.next)
                {
                        assert link.head == this;
                        link.head = null;
                }
        }
        
        public int calculateSize()
        {
                int size = 0;
                for (LinkedListEntry<T> link = this.first; link != null; link = link.next)
                {
                        ++size;
                }
                
                return size;
        }
        
        public void assertConsistency()
        {
                if (!SwissArmyKnife.assertEnabled)
                {
                        return;
                }
                
                LinkedListEntry link = this.first;
                while (link != null)
                {
                        assert link.head == this;
                        
                        if (link.next == null)
                        {
                                assert this.last == link;
                        }
                        else
                        {
                                assert link.next.previous == link;
                                assert link.next != link;
                                assert link.previous != link;
                        }
                        
                        link = link.next;
                }   
        }

        private class Itr implements Iterator<T>
        {
                private LinkedListEntry<T> current;
                private LinkedListEntry<T> next;
                private final boolean readonly;
                private final boolean reverse;

                Itr(boolean readonly, boolean reverse)
                {
                        this.readonly = readonly;
                        this.reverse = reverse;
                        
                        current = null;
                        next = reverse ? last : first;
                }

                @Override
                public boolean hasNext()
                {
                        return next != null;
                }

                @Override
                public T next()
                {
                        if (!hasNext())
                        {
                                throw new NoSuchElementException();
                        }
                        
                        current = next;
                        next = reverse ? current.previous : current.next;
                        return current.data;
                }

                @Override
                public void remove()
                {
                        if (readonly)
                        {
                                throw new UnsupportedOperationException();
                        }
                        
                        if (current == null)
                        {
                                throw new IllegalStateException();
                        }
                        
                        current.remove();
                }
        }
}
