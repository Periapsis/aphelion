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

/** Iterate over all the permutations of n elements.
 * 
 * For example n = 3 will return arrays in following order:
 * {0,1,2}
 * {0,2,1}
 * {2,0,1}
 * {2,1,0}
 * {1,2,0}
 * {1,0,2}
 * 
 * The total number of arrays will be n!
 * 
 * @author http://stackoverflow.com/a/11916946
 * https://en.wikipedia.org/wiki/Steinhaus–Johnson–Trotter_algorithm
 */
public class EvenSteinhausJohnsonTrotterIterator implements Iterator<int[]>
{
        private int[] next = null;
        private final int n;
        private int[] perm;
        private int[] dirs;

        public EvenSteinhausJohnsonTrotterIterator(int size)
        {
                n = size;
                if (n <= 0)
                {
                        perm = null;
                        dirs = null;
                }
                else
                {
                        perm = new int[n];
                        dirs = new int[n];
                        for (int i = 0; i < n; i++)
                        {
                                perm[i] = i;
                                dirs[i] = -1;
                        }
                        dirs[0] = 0;
                }

                next = perm;
        }

        @Override
        public int[] next()
        {
                int[] r = makeNext();
                next = null;
                return r;
        }

        @Override
        public boolean hasNext()
        {
                return (makeNext() != null);
        }

        @Override
        public void remove()
        {
                throw new UnsupportedOperationException();
        }

        private int[] makeNext()
        {
                if (next != null)
                {
                        return next;
                }
                if (perm == null)
                {
                        return null;
                }

                // find the largest element with != 0 direction
                int i = -1, e = -1;
                for (int j = 0; j < n; j++)
                {
                        if ((dirs[j] != 0) && (perm[j] > e))
                        {
                                e = perm[j];
                                i = j;
                        }
                }

                if (i == -1) // no such element -> no more premutations
                {
                        next = null;
                        perm = null;
                        dirs = null;
                        return null; // no more permutations
                }
                // swap with the element in its direction
                int k = i + dirs[i];
                swap(i, k, dirs);
                swap(i, k, perm);
                // if it's at the start/end or the next element in the direction
                // is greater, reset its direction.
                if ((k == 0) || (k == n - 1) || (perm[k + dirs[k]] > e))
                {
                        dirs[k] = 0;
                }

                // set directions to all greater elements
                for (int j = 0; j < n; j++)
                {
                        if (perm[j] > e)
                        {
                                dirs[j] = (j < k) ? +1 : -1;
                        }
                }

                next = perm;
                return next;
        }

        protected static void swap(int i, int j, int[] arr)
        {
                int v = arr[i];
                arr[i] = arr[j];
                arr[j] = v;
        }
}
