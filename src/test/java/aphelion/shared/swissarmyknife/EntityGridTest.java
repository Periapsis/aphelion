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
 * different from the original version
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
 * If you modify this library, you may extend this exception to your version
 * of the library, but you are not obliged to do so. If you do not wish to do
 * so, delete this exception statement from your version.
 */
package aphelion.shared.swissarmyknife;

import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.swissarmyknife.EntityGridEntity;
import aphelion.shared.swissarmyknife.EntityGrid;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import java.util.Iterator;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author joris
 */
public class EntityGridTest
{
        private EntityGrid<MyEntity> grid;
        @Before
        public void setUp()
        {
                grid = new EntityGrid<>(32768, 1024 * 16 * 1024 / 32768);
        }
        
        @Test
        public void testSingle()
        {
                MyEntity a = new MyEntity("a");
                
                grid.updateLocation(a, 0, 0);
                assertContains("position 0,0 should be stored in cell 0, 0", a, cellLow(0, 0), cellHigh(0, 0));
                grid.updateLocation(a, 32768, 0);
                assertNotContains("position 32768,0 should not be stored in cell 0, 0", a, cellLow(0, 0), cellHigh(0, 0));
                assertContains("position 32768,0 should be stored in cell 1, 0", a, cellLow(1, 0), cellHigh(1, 0));
                
                grid.updateLocation(a, 32768 * 3 - 1, 0);
                assertNotContains("position 32768 * 3 - 1, 0 should not be stored in cell 0, 0",  a, cellLow(0, 0), cellHigh(0, 0));
                assertNotContains("position 32768 * 3 - 1, 0 should not be stored in cell 0, 1",  a, cellLow(0, 1), cellHigh(0, 1));
                assertNotContains("position 32768 * 3 - 1, 0 should not be stored in cell 1, 0",  a, cellLow(1, 0), cellHigh(1, 0));
                assertContains("position 32768 * 3 - 1, 0 should be stored in cell 2, 0", a, cellLow(2, 0), cellHigh(2, 0));
                assertContains("position 32768 * 3 - 1, 0 should be between cell 1,0 and 3,3", a, cellLow(1, 0), cellHigh(3, 3));
        }
        
        @Test
        public void testNegative()
        {
                MyEntity a = new MyEntity("a");
                
                grid.updateLocation(a, -32768, 0);
                assertEmpty("negative positions should be ignored", new PhysicsPoint(0, 0), new PhysicsPoint(40000, 40000));
                
                grid.updateLocation(a, -32767, 0);
                assertContains("negative position >= CELL_SIZE should be interpreted as 0 (documented caveat)", a, new PhysicsPoint(0, 0), new PhysicsPoint(40000, 40000));
        }
        
        @Test
        public void testMultiple()
        {
                MyEntity a = new MyEntity("a");
                MyEntity b = new MyEntity("b");
                MyEntity c = new MyEntity("c");
                MyEntity d = new MyEntity("d");
                
                grid.updateLocation(a, 4000, 10000);
                grid.updateLocation(b, 2320, 11230);
                grid.updateLocation(c, 40323, 11230);
                grid.updateLocation(d, 32768*15, 32768*15);
                
                assertContains("Should contain a", a, cellLow(0, 0), cellHigh(3, 3));
                assertContains("Should contain a", b, cellLow(0, 0), cellHigh(3, 3));
                assertContains("Should contain a", c, cellLow(0, 0), cellHigh(3, 3));
                assertNotContains("Should not contain d", d, cellLow(0, 0), cellHigh(3, 3));
        }
        
        private PhysicsPoint cellLow(int x, int y)
        {
                return new PhysicsPoint(x * 32768, y * 32768);
        }
        
        private PhysicsPoint cellHigh(int x, int y)
        {
                return new PhysicsPoint(x * 32768 + 32767, y * 32768 + 32767);
        }
        
        private void assertEmpty(String message, PhysicsPoint low, PhysicsPoint high)
        {
                Iterator<MyEntity> it = grid.iterator(low, high);
                assertFalse(message, it.hasNext());
        }
        
        private void assertContains(String message, MyEntity first, PhysicsPoint low, PhysicsPoint high)
        {
                Iterator<MyEntity> it = grid.iterator(low, high);
                while (it.hasNext())
                {
                        MyEntity en = it.next();
                        if (en == first)
                        {
                                return;
                        }
                }
                assertTrue(message, false);
        }
        
        private void assertNotContains(String message, MyEntity first, PhysicsPoint low, PhysicsPoint high)
        {
                Iterator<MyEntity> it = grid.iterator(low, high);
                while (it.hasNext())
                {
                        MyEntity en = it.next();
                        if (en == first)
                        {
                                assertTrue(message, false);
                        }
                }
        }
        
        private class MyEntity implements EntityGridEntity
        {
                private final LinkedListEntry entry = new LinkedListEntry(this);
                public final String data;

                MyEntity(String data)
                {
                        this.data = data;
                }
                
                @Override
                public LinkedListEntry getEntityGridEntry(EntityGrid grid)
                {
                        return entry;
                }

                @Override
                public String toString()
                {
                        return "MyEntity " + data;
                }
        }
}
