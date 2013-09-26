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

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 *
 * @param <T> 
 * @author Joris
 */
public class WeakList<T> implements Iterable<T>
{
        private final LinkedList<WeakReference<T>> list = new LinkedList<>();

        public void add(T o)
        {
                list.add(new WeakReference<>(o));
        }
        
        public void remove(T o)
        {
                Iterator<T> it = this.iterator();
                while (it.hasNext())
                {
                        T entry = it.next();
                        
                        if (entry == o)
                        {
                                it.remove();
                                return;
                        }
                }
        }
        
        @Override
        public Iterator<T> iterator()
        {
                return new WeakIterator<>(list.iterator());
        }
        
        public void cleanup()
        {
                for (T o : this)
                {
                }
        }
        
        private static class WeakIterator<T> implements Iterator<T>
        {
                private final Iterator<WeakReference<T>> wrapped;
                private T next;

                WeakIterator(Iterator<WeakReference<T>> wrapped)
                {
                        this.wrapped = wrapped;
                        findNext();
                }
                
                @Override
                public boolean hasNext()
                {
                        return next != null;
                }

                @Override
                public T next()
                {
                        if (next == null)
                        {
                                throw new NoSuchElementException();
                        }
                        else
                        {
                                T ret = next;
                                findNext();
                                return ret;
                        }
                }

                @Override
                public void remove()
                {
                        wrapped.remove();
                }
                
                private void findNext()
                {
                        while (wrapped.hasNext())
                        {
                                WeakReference<T> ref = wrapped.next();
                                next = ref.get();
                                if (next == null)
                                {
                                        wrapped.remove();
                                }
                                else
                                {
                                        return;
                                }
                        }
                        
                        next = null;
                }
        }
}
