/*
 * Aphelion
 * Copyright (c) 2014  Joris van der Wel
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

package aphelion.shared.physics;

import org.junit.Test;
import static org.junit.Assert.*;
import static aphelion.shared.physics.EnvironmentConf.ROTATION_POINTS;
import static aphelion.shared.physics.EnvironmentConf.ROTATION_1_2TH;
import static aphelion.shared.physics.EnvironmentConf.ROTATION_1_4TH;
import static aphelion.shared.physics.EnvironmentConf.ROTATION_3_4TH;

/**
 * @author Joris
 */
public class PhysicsTrigTest
{
        @Test
        public void testSin()
        {
                assertEquals(0                     , PhysicsTrig.sin(0));
                assertEquals( PhysicsTrig.MAX_VALUE, PhysicsTrig.sin(ROTATION_1_4TH));
                assertEquals(0                     , PhysicsTrig.sin(ROTATION_1_2TH));
                assertEquals(-PhysicsTrig.MAX_VALUE, PhysicsTrig.sin(ROTATION_3_4TH));
                assertEquals(0                     , PhysicsTrig.sin(ROTATION_POINTS));
        }

        @Test
        public void testCos()
        {
                assertEquals( PhysicsTrig.MAX_VALUE, PhysicsTrig.cos(0));
                assertEquals(0                     , PhysicsTrig.cos(ROTATION_1_4TH));
                assertEquals(-PhysicsTrig.MAX_VALUE, PhysicsTrig.cos(ROTATION_1_2TH));
                assertEquals(0                     , PhysicsTrig.cos(ROTATION_3_4TH));
                assertEquals( PhysicsTrig.MAX_VALUE, PhysicsTrig.cos(ROTATION_POINTS));
        }

        @Test
        public void testTan()
        {
                assertEquals( 0, PhysicsTrig.tan(0));
                assertEquals( 1, PhysicsTrig.tan(ROTATION_1_4TH / 2));

                try
                {
                        PhysicsTrig.tan(ROTATION_1_4TH);
                        assertTrue(false);
                }
                catch (ArithmeticException ex)
                {
                        assertTrue(true);
                }

                assertEquals(-1, PhysicsTrig.tan(ROTATION_1_4TH / 2 * 3));
                assertEquals( 0, PhysicsTrig.tan(ROTATION_1_2TH));
                assertEquals( 1, PhysicsTrig.tan(ROTATION_1_4TH / 2 * 5));

                try
                {
                        PhysicsTrig.tan(ROTATION_3_4TH);
                        assertTrue(false);
                }
                catch (ArithmeticException ex)
                {
                        assertTrue(true);
                }

                assertEquals(-1, PhysicsTrig.tan(ROTATION_1_4TH / 2 * 7));
                assertEquals( 0, PhysicsTrig.tan(ROTATION_POINTS));
        }

        @Test
        public void testCotan()
        {
                try
                {
                        PhysicsTrig.cotan(0);
                        assertTrue(false);
                }
                catch (ArithmeticException ex)
                {
                        assertTrue(true);
                }

                assertEquals( 1, PhysicsTrig.cotan(ROTATION_1_4TH / 2));
                assertEquals( 0, PhysicsTrig.cotan(ROTATION_1_4TH));
                assertEquals(-1, PhysicsTrig.cotan(ROTATION_1_4TH / 2 * 3));

                try
                {
                        assertEquals( 0, PhysicsTrig.cotan(ROTATION_1_2TH)); // pi
                        assertTrue(false);
                }
                catch (ArithmeticException ex)
                {
                        assertTrue(true);
                }

                assertEquals( 1, PhysicsTrig.cotan(ROTATION_1_4TH / 2 * 5));
                assertEquals( 0, PhysicsTrig.cotan(ROTATION_3_4TH));
                assertEquals(-1, PhysicsTrig.cotan(ROTATION_1_4TH / 2 * 7));


                try
                {
                        assertEquals( 0, PhysicsTrig.cotan(ROTATION_POINTS)); // pi
                        assertTrue(false);
                }
                catch (ArithmeticException ex)
                {
                        assertTrue(true);
                }
        }
}
