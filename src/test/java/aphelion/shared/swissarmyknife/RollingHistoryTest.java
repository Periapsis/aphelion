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

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Joris
 */
public class RollingHistoryTest
{
        @Test
        public void test()
        {
                RollingHistory<Integer> a = new RollingHistory<>(10, 4);
                
                assertTrue(a.setHistory(10, 1) >= 0);
                assertTrue(a.setHistory(9, 0) >= 0);
                assertTrue(a.setHistory(8, -1) >= 0);
                assertTrue(a.setHistory(7, -2) >= 0);
                assertTrue(a.setHistory(6, -3) < 0); // too far in the past, can not be set
                assertNull(a.get(-3));
                assertEquals((Integer) (-2), a.get(7));
                assertEquals((Integer) (-1), a.get(8));
                assertEquals((Integer) 0, a.get(9));
                assertEquals((Integer) 1, a.get(10));
                
                assertTrue(a.setHistory(11, 1) >= 0);
                assertTrue(a.setHistory(12, 2) >= 0);
                assertTrue(a.setHistory(13, 3) >= 0);
                assertTrue(a.setHistory(15, 5) >= 0);
                assertTrue(a.setHistory(16, 6) >= 0);
                
                
                assertNull(a.get(11));
                assertNull(a.get(12));
                assertEquals((Integer) 3, a.get(13));
                assertNull(a.get(14)); // not set
                assertEquals((Integer) 5, a.get(15));
                assertEquals((Integer) 6, a.get(16));
        }
        
        @Test
        public void testSet()
        {
                // different history lengths
                RollingHistory<Integer> a = new RollingHistory<>(100, 4);
                RollingHistory<Integer> b = new RollingHistory<>(100, 8);
                
                // enough stuff to cause oldestValue to be set
                // and enough that get() has to wrap
                
                a.setHistory(100, 10001); // index 0
                a.setHistory(101, 10010); // 1
                a.setHistory(102, 10100); // 2
                b.set(a);
                testSet_assertUpto(a, 102);
                testSet_assertUpto(b, 102);
                
                // fill b with some crap
                b.setHistory(104, 2934203);
                b.setHistory(106, -9999);
                b.setHistory(105, 5);
                b.setHistory(105, 4);
                
                
                a.setHistory(103, 11000); // 3
                b.set(a);
                testSet_assertUpto(a, 103);
                testSet_assertUpto(b, 103);
                
                //more crap
                for (int n = 0; n < 17; ++n)
                {
                        b.setHistory(100 + n, 50000 * n);
                }
                
                a.setHistory(104, 20000); // 0
                b.set(a);
                testSet_assertUpto(a, 104);
                testSet_assertUpto(b, 104);
                
                a.setHistory(105, 30000); // 1
                b.set(a);
                testSet_assertUpto(a, 105);
                testSet_assertUpto(b, 105);
                
                
                
                a = new RollingHistory<>(100, 4);
                b = new RollingHistory<>(100, 8);
                
                
                
                a.setHistory(100, 10001); // index 0
                a.setHistory(101, 10010); // 1
                a.setHistory(102, 10100); // 2                
                a.setHistory(103, 11000); // 3
                a.setHistory(104, 20000); // 0
                a.setHistory(105, 30000); // 1
                b.set(a);
                testSet_assertUpto(a, 105);
                testSet_assertUpto(b, 105);
        }
        
        private void testSet_assertUpto(RollingHistory a, long upto_tick)
        {
                if (upto_tick >= 102) assertEquals(10100, a.get(102));
                if (upto_tick >= 103) assertEquals(11000, a.get(103));
                if (upto_tick >= 104) assertEquals(20000, a.get(104));
                if (upto_tick >= 105) assertEquals(30000, a.get(105));
        }
}