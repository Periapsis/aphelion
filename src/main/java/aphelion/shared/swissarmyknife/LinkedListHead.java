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
 * A Linked List implementation that lets you reference the links it uses (LinkedListEntry).
 * By using circular references between the link and the object it stores
 * removal / reinsertion, is O(1) instead of O(n) in java.util.LinkedList.
 * Also, in this case, rapid reinsertion will not create new objects.
 * This implementation is doubly linked (you can traverse values in either direction).
 * Optionally, you can also construct circular linked lists (in this case you would use
 * LinkedListEntry's without a LinkedListHead)
 *
 * @param <T> The type of values contained by the links
 * @author Joris
 * @see LinkedListEntry
 */
public class LinkedListHead<T> implements Iterable<T>
{
        /**
         * The first Link of this list.
         * If this property is null, last must also be null
         */
        public LinkedListEntry<T> first;

        /**
         * The last Link of this list.
         * If this property is null, last must also be null
         */
        public LinkedListEntry<T> last;

        /**
         * Create a new empty linked list
         */
        public LinkedListHead()
        {
        }

        /**
         * Is this list empty?
         * @return
         */
        public boolean isEmpty()
        {
                if (first == null || last == null)
                {
                        assert first == null && last == null : "Inconsistency: first may only be null if last is also null";
                        
                        return true;
                }
                return false;
        }

        /**
         * Create a new LinkedListEntry with the given data and prepend to the start of the list
         * @param data
         * @return The newly created LinkedListEntry
         */
        public LinkedListEntry<T> prependData(T data)
        {
                return prepend(new LinkedListEntry<>(data));
        }

        /**
         * Prepend the given LinkedListEntry to the start of the list
         * @param link
         * @return `link`
         */
        public LinkedListEntry<T> prepend(LinkedListEntry<T> link)
        {
                if (isEmpty())
                {
                        assert link.head == null : "The given `link` is already part of a list";
                        assert link.previous == null : "The given `link` is already part of a list";
                        assert link.next == null : "The given `link` is already part of a list";

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

        /**
         * Create a new LinkedListEntry with the given data and append to the end of the list
         * @param data
         * @return The newly created LinkedListEntry
         */
        public LinkedListEntry<T> appendData(T data)
        {
                return append(new LinkedListEntry<>(data));
        }

        /**
         * Append the given LinkedListEntry to the end of the list
         * @param link
         * @return `link`
         */
        public LinkedListEntry<T> append(LinkedListEntry<T> link)
        {
                if (isEmpty())
                {                        
                        assert link.head == null : "The given `link` is already part of a list";
                        assert link.previous == null : "The given `link` is already part of a list";
                        assert link.next == null : "The given `link` is already part of a list";

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

        /**
         * Remove the given range of links between `start` and `end` but do not clear the links.
         * The links between start and end will form their own headless list.
         * @param start
         * @param end
         */
        public void extractRange(LinkedListEntry<T> start, LinkedListEntry<T> end)
        {
                removeRange(start, end, false);
        }

        /**
         * Remove the given range of links between `start` and `end` and clear all the links.
         * @param start
         * @param end
         */
        public void removeRange(LinkedListEntry<T> start, LinkedListEntry<T> end)
        {
                removeRange(start, end, true);
        }

        private void removeRange(LinkedListEntry<T> start, LinkedListEntry<T> end, boolean clearLinks)
        {
                LinkedListEntry<T> link;
                
                assert start.head == this : "The given `start` link is not part of this list";
                assert end.head == this : "The given `end` link is not part of this list";

                LinkedListEntry<T> left = start.previous;
                LinkedListEntry<T> right = end.next;
                
                link = start;
                while(true)
                {
                        assert link != null;
                        assert link.head == this : "One of the links in the given range is not part of this list";
                        LinkedListEntry<T> next = link.next;
                        
                        link.head = null;

                        if (clearLinks)
                        {
                                link.previous = null;
                                link.next = null;
                        }
                        
                        if (link == end)
                        {
                                break;
                        }
                        
                        link = next;
                        assert link != start;
                }
                
                if (left != null)
                {
                        left.next = right;
                }

                if (this.first == start)
                {
                        this.first = right;
                }

                if (right != null)
                {
                        right.previous = left;
                }

                if (this.last == end)
                {
                        this.last = left;
                }

                start.previous = null;
                end.next = null;
        }

        /**
         * Remove all links
         */
        public void clear()
        {
                if (first == null || last == null)
                {
                        assert first == null && last == null : "Inconsistency: first may only be null if last is also null";
                        return;
                }
                
                removeRange(first, last);
        }

        /**
         * Append the given headless list of entries to the end of this list
         * @param start
         * @param end
         */
        public void appendForeignRange(LinkedListEntry<T> start, LinkedListEntry<T> end)
        {
                LinkedListEntry<T> link;
                
                assert start != null : "`start` should not be null";
                assert end != null : "`end` should not be null";
                
                if (first == null)
                {
                        if (start.head != null)
                        {
                                assert start.head != this : "One of the links in the given range is not a foreign link";
                                start.head.extractRange(start, end);
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

        /**
         * Prepend the given headless list of entries to the start of this list
         * @param start
         * @param end
         */
        public void prependForeignRange(LinkedListEntry<T> start, LinkedListEntry<T> end)
        {
                LinkedListEntry<T> link;

                assert start != null : "`start` should not be null";
                assert end != null : "`end` should not be null";
                
                if (first == null)
                {
                        if (start.head != null)
                        {
                                assert start.head != this : "One of the links in the given range is not a foreign link";
                                start.head.extractRange(start, end);
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

        /**
         * Find a link by matching the given `data` using == and remove it.
         * @param data The reference to compare
         * @return The link that was removed, or null if `data` was not found
         */
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

        /**
         * Find a link by matching the given `data` using data.equals() and remove it.
         * @param data The object to test for equality, or null to test for null.
         * @return The link that was removed, or null if `data` was not found
         */
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

        /**
         * Return and remove the last item
         * @return The last item or null if the list is empty
         */
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

        /**
         * Return and remove the first item
         * @return The first item or null if the list is empty
         */
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

        /**
         * Iterate over the values from `start` to `end`. Calling it.remove() is allowed.
         * @return
         */
        @Override
        public Iterator<T> iterator()
        {
                return new Itr(false, false);
        }

        /**
         * Iterate over the values from `start` to `end`. Calling it.remove() is NOT allowed.
         * @return
         */
        public Iterator<T> iteratorReadOnly()
        {
                return new Itr(true, false);
        }

        /**
         * Iterate over the values from `end` to `start`. Calling it.remove() is allowed.
         * @return
         */
        public Iterator<T> iteratorReverse()
        {
                return new Itr(false, true);
        }

        /**
         * Iterate over the values from `end` to `start`. Calling it.remove() is NOT allowed.
         * @return
         */
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

        /**
         *
         * @return The number of links in this list
         */
        public int calculateSize()
        {
                int size = 0;
                for (LinkedListEntry<T> link = this.first; link != null; link = link.next)
                {
                        ++size;
                }
                
                return size;
        }

        /**
         * Verify the internal consistency of this list by using assert.
         * This is slow when performed too often, it is most useful in test cases
         */
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
