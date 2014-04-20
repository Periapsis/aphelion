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
package aphelion.shared.physics;

import aphelion.shared.physics.valueobjects.PhysicsPoint;
import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;
import static aphelion.shared.physics.EnvironmentConf.MAX_POSITION;

/**
 *
 * @author Joris
 */
public class CollisionTest
{
        public CollisionTest()
        {
        }

        @Test
        public void testLineSegmentIntersectsConvexPolygon()
        {
                testLineSegmentIntersectsConvexPolygon_offset(0, 0);
                int offset = MAX_POSITION - 20;
                testLineSegmentIntersectsConvexPolygon_offset(offset,  offset);
                testLineSegmentIntersectsConvexPolygon_offset(-offset, -offset);
                
                testLineSegmentIntersectsConvexPolygon_offset( offset, -offset);
                testLineSegmentIntersectsConvexPolygon_offset(-offset, offset);
        }
        
        private void testLineSegmentIntersectsConvexPolygon_offset(int offsetX, int offsetY)
        {
                ArrayList<PhysicsPoint> vertices = new ArrayList<>();
                
                vertices.add(new PhysicsPoint(10 + offsetX, 10 + offsetY));
                vertices.add(new PhysicsPoint(20 + offsetX, 10 + offsetY));
                vertices.add(new PhysicsPoint(20 + offsetX, 20 + offsetY));
                vertices.add(new PhysicsPoint(10 + offsetX, 20 + offsetY));
                
                PhysicsPoint near = new PhysicsPoint();
                PhysicsPoint far = new PhysicsPoint();
                
                // horizontal
                assertTrue(Collision.lineSegmentIntersectsConvexPolygon(
                        -32768 + offsetX, 15 + offsetY, 
                         32768 + offsetX, 15 + offsetY,
                        vertices,
                        near, far
                        ));
                
                near.sub(offsetX, offsetY);
                far.sub(offsetX, offsetY);
                
                assertTrue(near.set);
                assertEquals(10, near.x);
                assertEquals(15, near.y);
                assertTrue(far.set);
                assertEquals(20, far.x);
                assertEquals(15, far.y);
                
                
                
                // vertical
                assertTrue(Collision.lineSegmentIntersectsConvexPolygon(
                        15 + offsetX, -32768 + offsetY, 
                        15 + offsetX,  32768 + offsetY,
                        vertices,
                        near, far
                        ));
                
                near.sub(offsetX, offsetY);
                far.sub(offsetX, offsetY);
                
                assertTrue(near.set);
                assertEquals(15, near.x);
                assertEquals(10, near.y);
                assertTrue(far.set);
                assertEquals(15, far.x);
                assertEquals(20, far.y);
                
                
                // diagonal going south east
                assertTrue(Collision.lineSegmentIntersectsConvexPolygon(
                        -32768 + offsetX, -32768 + offsetY, 
                         32768 + offsetX,  32768 + offsetY,
                        vertices,
                        near, far
                        ));
                
                near.sub(offsetX, offsetY);
                far.sub(offsetX, offsetY);
                
                assertTrue(near.set);
                assertEquals(10, near.x);
                assertEquals(10, near.y);
                assertTrue(far.set);
                assertEquals(20, far.x);
                assertEquals(20, far.y);
                
                
                // diagonal going south east
                assertTrue(Collision.lineSegmentIntersectsConvexPolygon(
                        -32768 + offsetX, -32768 + offsetY, 
                         32768 + offsetX,  32768 + offsetY,
                        vertices,
                        near, far
                        ));
                
                near.sub(offsetX, offsetY);
                far.sub(offsetX, offsetY);
                
                assertTrue(near.set);
                assertEquals(10, near.x);
                assertEquals(10, near.y);
                assertTrue(far.set);
                assertEquals(20, far.x);
                assertEquals(20, far.y);
                
                
                // vertical, starting inside the polygon
                assertTrue(Collision.lineSegmentIntersectsConvexPolygon(
                        15 + offsetX, 12 + offsetY, 
                        15 + offsetX, 32768 + offsetY,
                        vertices,
                        near, far
                        ));
                
                near.sub(offsetX, offsetY);
                far.sub(offsetX, offsetY);
                
                assertTrue(near.set);
                assertEquals(15, near.x);
                assertEquals(12, near.y);
                assertTrue(far.set);
                assertEquals(15, far.x);
                assertEquals(20, far.y);
                
                // vertical, ending inside the polygon
                assertTrue(Collision.lineSegmentIntersectsConvexPolygon(
                        15 + offsetX, -32768 + offsetY, 
                        15 + offsetX, 19 + offsetY,
                        vertices,
                        near, far
                        ));
                
                near.sub(offsetX, offsetY);
                far.sub(offsetX, offsetY);
                
                assertTrue(near.set);
                assertEquals(15, near.x);
                assertEquals(10, near.y);
                assertTrue(far.set);
                assertEquals(15, far.x);
                assertEquals(19, far.y);
                
                // vertical, start and end in the polygon
                assertTrue(Collision.lineSegmentIntersectsConvexPolygon(
                        15 + offsetX, 12 + offsetY, 
                        15 + offsetX, 19 + offsetY,
                        vertices,
                        near, far
                        ));
                
                near.sub(offsetX, offsetY);
                far.sub(offsetX, offsetY);
                
                assertTrue(near.set);
                assertEquals(15, near.x);
                assertEquals(12, near.y);
                assertTrue(far.set);
                assertEquals(15, far.x);
                assertEquals(19, far.y);
                
                // single point inside
                assertTrue(Collision.lineSegmentIntersectsConvexPolygon(
                        15 + offsetX, 17 + offsetY, 
                        15 + offsetX, 17 + offsetY,
                        vertices,
                        near, far
                        ));
                
                near.sub(offsetX, offsetY);
                far.sub(offsetX, offsetY);
                
                assertTrue(near.set);
                assertEquals(15, near.x);
                assertEquals(17, near.y);
                assertTrue(far.set);
                assertEquals(15, far.x);
                assertEquals(17, far.y);
                
                // single point outside
                assertFalse(Collision.lineSegmentIntersectsConvexPolygon(
                        2 + offsetX, 3 + offsetY, 
                        2 + offsetX, 3 + offsetY,
                        vertices,
                        near, far
                        ));
        }
        
}