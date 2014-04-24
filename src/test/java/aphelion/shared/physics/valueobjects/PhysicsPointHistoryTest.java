
package aphelion.shared.physics.valueobjects;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Joris
 */
public class PhysicsPointHistoryTest
{
        
        @Test
        public void testSet()
        {
                PhysicsPointHistory histA = new PhysicsPointHistory(100, 4);
                histA.setHistory(100, 100, 1234);
                histA.setHistory(101, 101, 5678);
                histA.setHistory(102, 102, 9108);
                histA.setHistory(103, 103, 2950);
                
                PhysicsPointHistory histB = new PhysicsPointHistory(100, 4);
                histB.setHistory(103, -103, 5736);
                histB.setHistory(104, -104, 4563);
                histB.setHistory(105, -105, 6748);
                histB.setHistory(106, -106, 2752);
                
                histB.set(histA);
                
                assertEquals(103, histB.getHighestTick());
                assertEquals(100, histB.getLowestTick());
                assertEquals( 100, histB.getX(100));
                assertEquals( 101, histB.getX(101));
                assertEquals( 102, histB.getX(102));
                assertEquals( 103, histB.getX(103));
        }

        @Test
        public void testOverwriteOutOfRange()
        {
                PhysicsPointHistory histA = new PhysicsPointHistory(100, 4);
                histA.setHistory(100, 100, 1234);
                histA.setHistory(101, 101, 5678);
                histA.setHistory(102, 102, 9108);
                histA.setHistory(103, 103, 2950);
                
                PhysicsPointHistory histB = new PhysicsPointHistory(23, 4);
                histB.setHistory( 20,  -20, 5736);
                histB.setHistory( 21,  -21, 4563);
                histB.setHistory( 22,  -22, 6748);
                histB.setHistory( 23,  -23, 2752);
                
                assertEquals(23, histB.getHighestTick());
                assertEquals(20, histB.getLowestTick());
                assertEquals(-20, histB.getX( 20));
                assertEquals(-21, histB.getX( 21));
                assertEquals(-22, histB.getX( 22));
                assertEquals(-23, histB.getX( 23));
                
                histB.overwrite(histA);
                
                assertEquals(23, histB.getHighestTick());
                assertEquals(20, histB.getLowestTick());
                assertEquals(-20, histB.getX( 20));
                assertEquals(-21, histB.getX( 21));
                assertEquals(-22, histB.getX( 22));
                assertEquals(-23, histB.getX( 23));
        }
        
        @Test
        public void testOverwriteLeftIntersect()
        {
                PhysicsPointHistory histA = new PhysicsPointHistory(100, 4);
                histA.setHistory(100, 100, 1234);
                histA.setHistory(101, 101, 5678);
                histA.setHistory(102, 102, 9108);
                histA.setHistory(103, 103, 2950);
                
                PhysicsPointHistory histB = new PhysicsPointHistory(100, 4);
                histB.setHistory(103, -103, 5736);
                histB.setHistory(104, -104, 4563);
                histB.setHistory(105, -105, 6748);
                histB.setHistory(106, -106, 2752);
                
                assertEquals(106, histB.getHighestTick());
                assertEquals(103, histB.getLowestTick());
                assertEquals(-103, histB.getX(103));
                assertEquals(-104, histB.getX(104));
                assertEquals(-105, histB.getX(105));
                assertEquals(-106, histB.getX(106));
                
                histB.overwrite(histA);
                
                assertEquals(106, histB.getHighestTick());
                assertEquals(103, histB.getLowestTick());
                assertEquals( 103, histB.getX(103));
                assertEquals(-104, histB.getX(104));
                assertEquals(-105, histB.getX(105));
                assertEquals(-106, histB.getX(106));
        }
        
        @Test
        public void testOverwriteRightIntersect()
        {
                PhysicsPointHistory histA = new PhysicsPointHistory(100, 4);
                histA.setHistory(100, 100, 1234);
                histA.setHistory(101, 101, 5678);
                histA.setHistory(102, 102, 9108);
                histA.setHistory(103, 103, 2950);
                
                PhysicsPointHistory histB = new PhysicsPointHistory(100, 4);
                histB.setHistory( 98,  -98, 5736);
                histB.setHistory( 99,  -99, 4563);
                histB.setHistory(100, -100, 6748);
                histB.setHistory(101, -101, 2752);
                
                assertEquals(101, histB.getHighestTick());
                assertEquals( 98, histB.getLowestTick());
                assertEquals( -98, histB.getX( 98));
                assertEquals( -99, histB.getX( 99));
                assertEquals(-100, histB.getX(100));
                assertEquals(-101, histB.getX(101));
                
                histB.overwrite(histA);
                
                assertEquals(101, histB.getHighestTick());
                assertEquals( 98, histB.getLowestTick());
                assertEquals( -98, histB.getX( 98));
                assertEquals( -99, histB.getX( 99));
                assertEquals( 100, histB.getX(100));
                assertEquals( 101, histB.getX(101));
        }
        
        @Test
        public void testOverwriteContains()
        {
                PhysicsPointHistory histA = new PhysicsPointHistory(100, 4);
                histA.setHistory(100, 100, 1234);
                histA.setHistory(101, 101, 5678);
                histA.setHistory(102, 102, 9108);
                histA.setHistory(103, 103, 2950);
                
                PhysicsPointHistory histB = new PhysicsPointHistory(100, 8);
                histB.setHistory( 98,  -98, 5736);
                histB.setHistory( 99,  -99, 4563);
                histB.setHistory(100, -100, 6748);
                histB.setHistory(101, -101, 2752);
                histB.setHistory(102, -102, 7562);
                histB.setHistory(103, -103, 1532);
                histB.setHistory(104, -104, 4673);
                histB.setHistory(105, -105, 4683);
                
                assertEquals(105, histB.getHighestTick());
                assertEquals(98, histB.getLowestTick());
                
                histB.overwrite(histA);
                
                assertEquals(105, histB.getHighestTick());
                assertEquals(98, histB.getLowestTick());
                
                assertEquals(-98, histB.getX( 98));
                assertEquals(-99, histB.getX( 99));
                assertEquals(100, histB.getX(100));
                assertEquals(101, histB.getX(101));
                assertEquals(102, histB.getX(102));
                assertEquals(103, histB.getX(103));
                assertEquals(-104, histB.getX(104));
                assertEquals(-105, histB.getX(105));
        }

        @Test
        public void testToString()
        {
        }
        
}
