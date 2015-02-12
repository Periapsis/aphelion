/*
 * Aphelion
 * Copyright (c) 2015  Joris van der Wel
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

import static org.junit.Assert.*;
import org.junit.Test;

public class SwissArmyKnifeTest
{
        @Test
        public void testDivideFloorInt()
        {
                try
                {
                        SwissArmyKnife.divideFloor(0, 0);
                        assert false;
                }
                catch(ArithmeticException ex)
                {
                        assert true;
                }

                assertEquals(0, SwissArmyKnife.divideFloor(0, 1));
                assertEquals(1, SwissArmyKnife.divideFloor(1, 1));
                assertEquals(0, SwissArmyKnife.divideFloor(1, 2));
                assertEquals(3, SwissArmyKnife.divideFloor(10, 3));
                assertEquals(5, SwissArmyKnife.divideFloor(10, 2));
                assertEquals(-5, SwissArmyKnife.divideFloor(-10, 2));
                assertEquals(-4, SwissArmyKnife.divideFloor(-10, 3));
                assertEquals(-4, SwissArmyKnife.divideFloor(10, -3));
                assertEquals(3, SwissArmyKnife.divideFloor(-10, -3));
                assertEquals(-1, SwissArmyKnife.divideFloor(-1, 32));
        }

        @Test
        public void testDivideFloorLong()
        {
                try
                {
                        SwissArmyKnife.divideFloor(0L, 0L);
                        assert false;
                }
                catch(ArithmeticException ex)
                {
                        assert true;
                }

                assertEquals(0L, SwissArmyKnife.divideFloor(0L, 1L));
                assertEquals(1L, SwissArmyKnife.divideFloor(1L, 1L));
                assertEquals(0L, SwissArmyKnife.divideFloor(1L, 2L));
                assertEquals(3L, SwissArmyKnife.divideFloor(10L, 3L));
                assertEquals(5L, SwissArmyKnife.divideFloor(10L, 2L));
                assertEquals(-5L, SwissArmyKnife.divideFloor(-10L, 2L));
                assertEquals(-4L, SwissArmyKnife.divideFloor(-10L, 3L));
                assertEquals(-4L, SwissArmyKnife.divideFloor(10L, -3L));
                assertEquals(3L, SwissArmyKnife.divideFloor(-10L, -3L));
                assertEquals(-1L, SwissArmyKnife.divideFloor(-1L, 32L));
        }

        @Test
        public void testDivideCeilInt()
        {
                try
                {
                        SwissArmyKnife.divideCeil(0, 0);
                        assert false;
                }
                catch (ArithmeticException ex)
                {
                        assert true;
                }

                assertEquals(0, SwissArmyKnife.divideCeil(0, 1));
                assertEquals(1, SwissArmyKnife.divideCeil(1, 1));
                assertEquals(1, SwissArmyKnife.divideCeil(1, 2));
                assertEquals(4, SwissArmyKnife.divideCeil(10, 3));
                assertEquals(5, SwissArmyKnife.divideCeil(10, 2));
                assertEquals(-5, SwissArmyKnife.divideCeil(-10, 2));
                assertEquals(-3, SwissArmyKnife.divideCeil(-10, 3));
                assertEquals(-3, SwissArmyKnife.divideCeil(10, -3));
                assertEquals(4, SwissArmyKnife.divideCeil(-10, -3));
                assertEquals(0, SwissArmyKnife.divideCeil(-1, 32));
        }

        @Test
        public void testDivideCeilLong()
        {
                try
                {
                        SwissArmyKnife.divideCeil(0L, 0L);
                        assert false;
                }
                catch (ArithmeticException ex)
                {
                        assert true;
                }

                assertEquals(0L, SwissArmyKnife.divideCeil(0L, 1L));
                assertEquals(1L, SwissArmyKnife.divideCeil(1L, 1L));
                assertEquals(1L, SwissArmyKnife.divideCeil(1L, 2L));
                assertEquals(4L, SwissArmyKnife.divideCeil(10L, 3L));
                assertEquals(5L, SwissArmyKnife.divideCeil(10L, 2L));
                assertEquals(-5L, SwissArmyKnife.divideCeil(-10L, 2L));
                assertEquals(-3L, SwissArmyKnife.divideCeil(-10L, 3L));
                assertEquals(-3L, SwissArmyKnife.divideCeil(10L, -3L));
                assertEquals(4L, SwissArmyKnife.divideCeil(-10L, -3L));
                assertEquals(0L, SwissArmyKnife.divideCeil(-1L, 32L));
        }

        @Test
        public void testDivideUpInt()
        {
                try
                {
                        SwissArmyKnife.divideUp(0, 0);
                        assert false;
                }
                catch (ArithmeticException ex)
                {
                        assert true;
                }

                assertEquals(0, SwissArmyKnife.divideUp(0, 1));
                assertEquals(1, SwissArmyKnife.divideUp(1, 30));
                assertEquals(-1, SwissArmyKnife.divideUp(-1, 30));
                assertEquals(-1, SwissArmyKnife.divideUp(1, -30));
                assertEquals(1, SwissArmyKnife.divideUp(-1, -30));
                assertEquals(5, SwissArmyKnife.divideUp(10, 2));
                assertEquals(-5, SwissArmyKnife.divideUp(10, -2));
                assertEquals(-5, SwissArmyKnife.divideUp(-10, 2));
                assertEquals(5, SwissArmyKnife.divideUp(-10, -2));
        }

        @Test
        public void testDivideUpLong()
        {
                try
                {
                        SwissArmyKnife.divideUp(0L, 0L);
                        assert false;
                }
                catch (ArithmeticException ex)
                {
                        assert true;
                }

                assertEquals(0L, SwissArmyKnife.divideUp(0L, 1L));
                assertEquals(1L, SwissArmyKnife.divideUp(1L, 30L));
                assertEquals(-1L, SwissArmyKnife.divideUp(-1L, 30L));
                assertEquals(-1L, SwissArmyKnife.divideUp(1L, -30L));
                assertEquals(1L, SwissArmyKnife.divideUp(-1L, -30L));
                assertEquals(5L, SwissArmyKnife.divideUp(10L, 2L));
                assertEquals(-5L, SwissArmyKnife.divideUp(10L, -2L));
                assertEquals(-5L, SwissArmyKnife.divideUp(-10L, 2L));
                assertEquals(5L, SwissArmyKnife.divideUp(-10L, -2L));
        }
}
