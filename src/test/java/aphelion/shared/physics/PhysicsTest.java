/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package aphelion.shared.physics;


import aphelion.shared.gameconfig.ConfigSelection;
import aphelion.shared.gameconfig.GameConfig;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import java.util.Iterator;
import java.util.List;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;

/**
 *
 * @author Joris
 */
public abstract class PhysicsTest
{
        protected PhysicsEnvironment env;
        protected ConfigSelection conf;
        
        protected static final PhysicsMovement MOVE_UP = PhysicsMovement.get(true, false, false, false, false);
        protected static final PhysicsMovement MOVE_DOWN = PhysicsMovement.get(false, true, false, false, false);
        protected static final PhysicsMovement MOVE_LEFT = PhysicsMovement.get(false, false, true, false, false);
        protected static final PhysicsMovement MOVE_RIGHT = PhysicsMovement.get(false, false, false, true, false);
        
        protected static final int ACTOR_FIRST = 1;
        protected static final int ACTOR_SECOND = 2;

        @Before
        public void setUp()
        {
                env = new PhysicsEnvironment(true, new MapEmpty());
                conf = applyTestSettings(env);
        }

        @After
        public void tearDown()
        {
                env = null;
        }
        
        
        private ConfigSelection applyTestSettings(PhysicsEnvironment env)
        {
                List<Object> yamlDocuments;
                try
                {
                yamlDocuments = GameConfig.loadYaml(
                "- ship-rotation-speed: 3800000 \n" +
                "  ship-rotation-points: 40\n" +
                "  ship-speed: 2624\n" +
                "  ship-thrust: 28\n" +
                "  ship-bounce-friction: 600\n" +
                "  ship-bounce-friction-other-axis: 900 \n" +
                "  ship-radius: 14336 # 14 * 1024\n" +
                "  ship-spawn-x: 512\n" +
                "  ship-spawn-y: 512\n" +
                "  ship-spawn-radius: 83\n" +
                "  \n" +
                "  projectile-speed: 5120\n" +
                "  projectile-offset-y: 14336\n" +
                "  projectile-bounce-friction: 1024\n" +
                "  projectile-bounce-friction-other-axis: 1024 \n" +
                "  projectile-expiration-ticks: 1000\n" +
                "  projectile-speed-relative: true\n" + 
                "  projectile-angle-relative: true\n");
                }
                catch (Exception ex)
                {
                        throw new Error(ex);
                }
                
                env.loadConfig(env.getTick() - PhysicsEnvironment.TOTAL_HISTORY, "test", yamlDocuments);
                return env.newConfigSelection(0);
        }
        
        public static void assertContains(Iterator it, Object something)
        {
                while (it.hasNext())
                {
                        if (it.next() == something)
                        {
                                return;
                        }
                }
                
                assert false;
        }
        
        public static void assertContains(Iterable it, Object something)
        {
                for (Object o : it)
                {
                        if (o == something)
                        {
                                return;
                        }
                }
                
                assert false;
        }
        
        protected static void assertActorExists(ActorPublic actor)
        {
                assertNotNull(actor);
                assertTrue(!actor.isRemoved());
        }
        
        protected static void assertActorNotExists(ActorPublic actor)
        {
                if (actor == null)
                {
                        return;
                }
                
                assertTrue(actor.isRemoved());
        }
        
        protected static void assertProjectileExists(ProjectilePublic actor)
        {
                assertNotNull(actor);
                assertTrue(!actor.isRemoved());
        }
        
        protected static void assertProjectileNotExists(ProjectilePublic actor)
        {
                if (actor == null)
                {
                        return;
                }
                
                assertTrue(actor.isRemoved());
        }
        
        protected static void assertPointEquals(int x, int y, PhysicsPoint point)
        {
                if (x != point.x || y != point.y)
                {
                        throw new AssertionError("expected point:<" + x + "," + y + "> but was:<" + point.x + "," + point.y + ">");
                }
        }

        protected void assertPosition(int x, int y, ActorPublic actor)
        {
                assertActorExists(actor);
                PhysicsShipPosition pos = new PhysicsShipPosition();
                assertTrue(actor.getPosition(pos));

                if (x != pos.x || y != pos.y)
                {
                        throw new AssertionError("expected position:<" + x + "," + y + "> but was:<" + pos.x + "," + pos.y + ">");
                }
                
                assertTrue(actor.getHistoricPosition(pos, env.getTick(actor.getStateId()), false));
                if (x != pos.x || y != pos.y)
                {
                        throw new AssertionError("getHistoricPosition(0) is not equal to the current position!");
                }
        }
        
        protected void assertPosition(int x, int y, ProjectilePublic projectile)
        {
                assertProjectileExists(projectile);
                ProjectilePublic.Position pos = new ProjectilePublic.Position();
                
                assertTrue(projectile.getPosition(pos));

                if (x != pos.x || y != pos.y)
                {
                        throw new AssertionError("expected position:<" + x + "," + y + "> but was:<" + pos.x + "," + pos.y + ">");
                }
                
                PhysicsPoint pos2 = new PhysicsPoint();
                assertTrue(projectile.getHistoricPosition(pos2, env.getTick(projectile.getStateId()), false));
                if (x != pos.x || y != pos.y)
                {
                        throw new AssertionError("getHistoricPosition(0) is not equal to the current position!");
                }
        }
        
        protected void assertPosition(int x, int y, long tick, ActorPublic actor)
        {
                assertActorExists(actor);
                PhysicsShipPosition pos = new PhysicsShipPosition();
                
                assertTrue(actor.getHistoricPosition(pos, tick, false));
                if (x != pos.x || y != pos.y)
                {
                        throw new AssertionError("expected position:<" + x + "," + y + "> but was:<" + pos.x + "," + pos.y + "> at tick " + tick);
                }
        }

        protected void assertVelocity(int x, int y, ActorPublic actor)
        {
                assertActorExists(actor);
                PhysicsShipPosition pos = new PhysicsShipPosition();
                assertTrue(actor.getPosition(pos));

                if (x != pos.x_vel || y != pos.y_vel)
                {
                        throw new AssertionError("expected velocity:<" + x + "," + y + "> but was:<" + pos.x_vel + "," + pos.y_vel + ">");
                }
                
                assertTrue(actor.getHistoricPosition(pos, env.getTick(actor.getStateId()), false));

                if (x != pos.x_vel || y != pos.y_vel)
                {
                        throw new AssertionError("getHistoricPosition(0) is not equal to the current velocity!");
                }
        }

        protected void assertRotation(int rot, ActorPublic actor)
        {
                assertActorExists(actor);
                PhysicsShipPosition pos = new PhysicsShipPosition();
                
                assertTrue(actor.getPosition(pos));
                assertEquals(rot, pos.rot);
                
                assertTrue(actor.getHistoricPosition(pos, env.getTick(actor.getStateId()), false));
                assertEquals(rot, pos.rot);
        }

        protected void assertSnappedRotation(int rot, ActorPublic actor)
        {
                assertActorExists(actor);
                PhysicsShipPosition pos = new PhysicsShipPosition();
                
                assertTrue(actor.getPosition(pos));
                assertEquals(rot, pos.rot_snapped);
                
                assertTrue(actor.getHistoricPosition(pos, env.getTick(actor.getStateId()), false));
                assertEquals(rot, pos.rot_snapped);
        }
}
