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

import aphelion.shared.physics.valueobjects.PhysicsPoint;
import org.junit.Test;

import static org.junit.Assert.*;

public class PhysicsMathTest
{
        @Test
        public void testRotationToPoint()
        {
                final PhysicsPoint point = new PhysicsPoint();

                point.set(0, 0);
                PhysicsMath.rotationToPoint(point, 0, 100);
                point.assertEquals(0, -100);

                point.set(0, 0);
                PhysicsMath.rotationToPoint(point, EnvironmentConf.ROTATION_1_4TH, 100);
                point.assertEquals(100, 0);

                point.set(0, 0);
                PhysicsMath.rotationToPoint(point, EnvironmentConf.ROTATION_1_2TH, 100);
                point.assertEquals(0, 100);

                point.set(1, 1);
                PhysicsMath.rotationToPoint(point, EnvironmentConf.ROTATION_3_4TH, 100);
                point.assertEquals(-99, 1);
        }

        @Test
        public void testSnapRotation()
        {
                assertEquals(0,
                        PhysicsMath.snapRotation(100, 40));

                assertEquals(EnvironmentConf.ROTATION_POINTS / 40,
                        PhysicsMath.snapRotation(EnvironmentConf.ROTATION_POINTS / 40 - 1000, 40));

                // nothing to snap
                assertEquals(EnvironmentConf.ROTATION_POINTS / 40,
                        PhysicsMath.snapRotation(EnvironmentConf.ROTATION_POINTS / 40, 40));

                assertEquals(0,
                        PhysicsMath.snapRotation(EnvironmentConf.ROTATION_POINTS - 1000, 40));

                // <= 1 disables snapping
                assertEquals(123456,
                        PhysicsMath.snapRotation(123456, 0));

                assertEquals(123456,
                        PhysicsMath.snapRotation(123456, 1));

                assertEquals(123456,
                        PhysicsMath.snapRotation(123456, -3434));
        }

        @Test
        public void testRectanglesCollide()
        {
                assertFalse(PhysicsMath.rectanglesCollide(
                        0, 0, 10, 10,
                        100, 100, 110, 110
                ));

                assertFalse(PhysicsMath.rectanglesCollide(
                        100, 100, 110, 110,
                        0, 0, 10, 10
                ));

                assertFalse(PhysicsMath.rectanglesCollide(
                        10, 100, 50, 110,
                        20, 0, 100, 30
                ));

                assertFalse(PhysicsMath.rectanglesCollide(
                        10, 10, 50, 20,
                        20, 30, 100, 110
                ));
                // xA1, yA1, xA2, yA2,
                // xB1, yB1, xB2, yB2

                // false: xA1 > xB2 || xA2 < xB1
                // false: yA1 > yB2
                // true:  yA2 < yB1

                // completely contained
                assertTrue(PhysicsMath.rectanglesCollide(
                        10, 10, 100, 100,
                        20, 30, 60, 65
                ));
                assertTrue(PhysicsMath.rectanglesCollide(
                        20, 30, 60, 65,
                        10, 10, 100, 100
                ));

                // horizontal & vertical intersect
                assertTrue(PhysicsMath.rectanglesCollide(
                        0, 0, 50, 50,
                        40, 40, 60, 60
                ));
                assertTrue(PhysicsMath.rectanglesCollide(
                        -10, -10, 10, 10,
                        -50, -50, 0, 0
                ));


                // horizontal intersect only
                assertTrue(PhysicsMath.rectanglesCollide(
                        0, 0, 50, 50,
                        20, 20, 60, 40
                ));
                assertTrue(PhysicsMath.rectanglesCollide(
                        20, 20, 60, 40,
                        0, 0, 50, 50
                ));

                // vertical intersect only
                assertTrue(PhysicsMath.rectanglesCollide(
                        0, 0, 50, 50,
                        20, 20, 40, 60
                ));
                assertTrue(PhysicsMath.rectanglesCollide(
                        20, 20, 40, 60,
                        0, 0, 50, 50
                ));


                // horizontal intersect wherein one line side overlaps exactly
                assertTrue(PhysicsMath.rectanglesCollide(
                        0, 0, 50, 50,
                        50, 20, 60, 40
                ));
                assertTrue(PhysicsMath.rectanglesCollide(
                        50, 20, 60, 40,
                        0, 0, 50, 50
                ));

                // vertical intersect wherein one line side overlaps exactly
                assertTrue(PhysicsMath.rectanglesCollide(
                        0, 0, 50, 50,
                        20, 50, 40, 60
                ));
                assertTrue(PhysicsMath.rectanglesCollide(
                        20, 50, 40, 60,
                        0, 0, 50, 50
                ));

                // todo rectangle as line
        }

        @Test
        public void testForce()
        {
                PhysicsPoint forcePoint = new PhysicsPoint();
                PhysicsPoint applyTo = new PhysicsPoint();
                PhysicsPoint velocity = new PhysicsPoint();


                applyTo.set(400, 400);
                forcePoint.set(400, 400);

                PhysicsMath.force(velocity, applyTo, forcePoint, 0, 50000);
                assertFalse("Return value should not be set if the range is invalid", velocity.set);
                assertEquals(0, velocity.x);
                assertEquals(0, velocity.y);

                forcePoint.set(400, 400);
                PhysicsMath.force(velocity, applyTo, forcePoint, 100, 50000);
                assertTrue("Return value should be set if the positions are equal to each other", velocity.set);
                assertEquals(0, velocity.x);
                assertEquals(50000, velocity.y);

                applyTo.set(500, 400);
                PhysicsMath.force(velocity, applyTo, forcePoint, 100, 50000);
                assertFalse("Return value should not be set if out of range", velocity.set);
                assertEquals(0, velocity.x);
                assertEquals(0, velocity.y);

                applyTo.set(410, 123401);
                PhysicsMath.force(velocity, applyTo, forcePoint, 100, 50000);
                assertFalse("Return value should not be set if out of range", velocity.set);
                assertEquals(0, velocity.x);
                assertEquals(0, velocity.y);

                applyTo.set(350, 400);
                PhysicsMath.force(velocity, applyTo, forcePoint, 100, 50000);
                assertTrue("Return value should be set", velocity.set);
                assertEquals(-50000/2, velocity.x);
                assertEquals(0, velocity.y);

                applyTo.set(450, 400);
                PhysicsMath.force(velocity, applyTo, forcePoint, 100, 50000);
                assertTrue("Return value should be set", velocity.set);
                assertEquals(50000/2, velocity.x);
                assertEquals(0, velocity.y);

                applyTo.set(400, 350);
                PhysicsMath.force(velocity, applyTo, forcePoint, 100, 50000);
                assertTrue("Return value should be set", velocity.set);
                assertEquals(0, velocity.x);
                assertEquals(-50000/2, velocity.y);

                applyTo.set(400, 450);
                PhysicsMath.force(velocity, applyTo, forcePoint, 100, 50000);
                assertTrue("Return value should be set", velocity.set);
                assertEquals(0, velocity.x);
                assertEquals(50000/2, velocity.y);

                applyTo.set(430, 440); // (results in a pythagorean triple, dist=50)
                PhysicsMath.force(velocity, applyTo, forcePoint, 100, 50000);
                assertTrue("Return value should be set", velocity.set);
                assertEquals(50000 * 30 / 100, velocity.x);
                assertEquals(50000 * 40 / 100, velocity.y);
        }
}
