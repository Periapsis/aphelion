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
 * Used to manually create double linked lists. (useful in certain performance scenerios)
 * @param <T> 
 * @author Joris
 */
public class LinkedListEntry<T> implements Iterable<T> // A list is only as strong as its weakest link.
{
        public LinkedListEntry<T> previous;
        public LinkedListEntry<T> next;
        public LinkedListHead<T> head;
        public final T data;

        public LinkedListEntry(LinkedListHead<T> head, T data)
        {
                this.head = head;
                this.data = data;
        }
        
        public void beginCircular()
        {
                if (head != null || previous != null || next != null)
                {
                        throw new IllegalStateException();
                }
                
                previous = this;
                next = this;
        }
        
        public LinkedListEntry<T> prependData(T data)
        {
                return prepend(new LinkedListEntry<>(null, data));
        }
        
        public LinkedListEntry<T> prepend(LinkedListEntry<T> link)
        {
                assert link.head == null;
                assert link.previous == null;
                assert link.next == null;
                
                link.head = this.head;
                
                link.previous = this.previous;
                link.next = this;
                
                if (this.previous != null)
                {
                        this.previous.next = link;
                }
                
                if (head != null && this == head.first)
                {
                        head.first = link;
                }
                
                this.previous = link;
                
                return link;
        }
        
        public LinkedListEntry<T> appendData(T data)
        {
                return append(new LinkedListEntry<>(null, data));
        }
        
        public LinkedListEntry<T> append(LinkedListEntry<T> link)
        {
                assert link.head == null;
                assert link.previous == null;
                assert link.next == null;
                
                link.head = this.head;
                
                link.previous = this;
                link.next = this.next;
                
                if (this.next != null)
                {
                        this.next.previous = link;
                }
                
                if (head != null && this == head.last)
                {
                        head.last = link;
                }
                
                this.next = link;
                
                return link;
        }
        
        public void appendForeignRange(LinkedListEntry<T> start, LinkedListEntry<T> end)
        {
                LinkedListEntry<T> link;
                
                assert start != null;
                assert end != null;
                
                if (start.head != null)
                {
                        assert start.head != this.head : "'start' is not a foreign link";
                        start.head.removeRange(start, end);
                }
                
                if (start.previous != null)
                {
                        start.previous.next = end.next;
                }
                
                if (end.next != null)
                {
                        end.next.previous = start.previous;
                }
                
                
                start.previous = null;
                end.next = null;
                
                link = start;
                while(true)
                {
                        assert link != null;
                        assert link.head == null;
                        
                        link.head = this.head;
                        
                        if (link == end)
                        {
                                break;
                        }
                        
                        link = link.next;
                        assert link != start;
                }
                
                
                start.previous = this;
                end.next = this.next;
                
                if (this.next != null)
                {
                        this.next.previous = end;
                }
                
                if (head != null && this == head.last)
                {
                        head.last = end;
                }
                
                this.next = start;
        }
        
        public void prependForeignRange(LinkedListEntry<T> start, LinkedListEntry<T> end)
        {
                LinkedListEntry<T> link;
                assert start != null;
                assert end != null;
                
                if (start.head != null)
                {
                        assert start.head != this.head;
                        start.head.removeRange(start, end);
                }
                
                link = start;
                while(true)
                {
                        assert link != null;
                        assert link.head == null;
                        
                        link.head = this.head;
                        
                        if (link == end)
                        {
                                break;
                        }
                        
                        link = link.next;
                        assert link != start;
                }
                
                start.previous = this.previous;
                end.next = this;
                
                if (this.previous != null)
                {
                        this.previous.next = start;
                }
                
                if (head != null && this == head.first)
                {
                        head.first = start;
                }
                
                this.previous = end;
        }
        
        public void remove()
        {
                if (head != null)
                {
                        if (this == head.first)
                        {
                                assert this.previous == null;
                                head.first = this.next;
                        }

                        if (this == head.last)
                        {
                                assert this.next == null;
                                head.last = this.previous;
                        }

                        // both null or none null
                        assert head.first != null || head.last == null;
                        assert head.last != null || head.first == null;
                }

                
                if (this.previous != null)
                {
                        this.previous.next = this.next;
                }
                
                if (this.next != null)
                {
                        this.next.previous = this.previous;
                }
                
                this.next = null;
                this.previous = null;
                this.head = null;
        }
        
        @Override public String toString()
        {
                if (data == null)
                {
                        return "link: null";
                }
                else
                {
                        return "link: " + data.toString();
                }
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
        
        private class Itr implements Iterator<T>
        {
                private boolean first = true;
                private LinkedListEntry<T> current;
                private LinkedListEntry<T> next;
                private boolean readonly;
                private boolean reverse;

                Itr(boolean readonly, boolean reverse)
                {
                        this.readonly = readonly;
                        this.reverse = reverse;
                        
                        current = null;
                        next = LinkedListEntry.this;
                }

                @Override
                public boolean hasNext()
                {
                        if (!first)
                        {
                                // dealing with a circular linked list and we are back at the start
                                if (next == LinkedListEntry.this)
                                {
                                        return false;
                                }
                        }
                        
                        return next != null;
                }

                @Override
                public T next()
                {
                        if (!hasNext())
                        {
                                throw new NoSuchElementException();
                        }
                        
                        first = false;
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
                        current = null;
                }
        }
        
        public void assertCircularConsistency()
        {
                if (!SwissArmyKnife.assertEnabled)
                {
                        return;
                }
                
                if (this.previous == null || this.next == null)
                {
                       assert this.previous == null;
                       assert this.next == null;
                       return;
                }
                
                LinkedListEntry prev = null;
                LinkedListEntry link = this;
                do
                {
                        assert link.head == null;
                        assert link.next != null;
                        assert link.previous != null;
                        
                        if (prev != null)
                        {
                                assert link.previous == prev;
                        }
                        
                        link = link.next;
                }
                while (link != this);
        }
}
