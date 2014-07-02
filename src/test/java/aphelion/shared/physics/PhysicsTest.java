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


import aphelion.shared.gameconfig.ConfigSelection;
import aphelion.shared.gameconfig.GameConfig;
import aphelion.shared.physics.entities.Actor;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.entities.ActorPublicImpl;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import java.lang.reflect.Field;
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
        
        protected void createEnvironment()
        {
                env = new SimpleEnvironment(new EnvironmentConf(false, true, true), new MapEmpty(), false);
                ((SimpleEnvironment) env).testcaseImmediateMove = true;
        }

        @Before
        public void setUp()
        {
                EnvironmentConf.testCaseAssertions = true;
                createEnvironment();
                conf = applyTestSettings(env);
        }

        @After
        public void tearDown()
        {
                env = null;
                EnvironmentConf.testCaseAssertions = false;
        }
        
        
        private static final Field privateActorField;
        static
        {
                try
                {
                        privateActorField = ActorPublicImpl.class.getDeclaredField("privateActor");
                        privateActorField.setAccessible(true);
                }
                catch (NoSuchFieldException | SecurityException ex)
                {
                        throw new Error(ex);
                }
        }
        
        public static Actor getPrivateActor(ActorPublic actor)
        {
                try
                {
                        return (Actor) privateActorField.get(actor);
                }
                catch (IllegalArgumentException | IllegalAccessException ex)
                {
                        throw new Error(ex);
                }
        }
        
        
        public static ConfigSelection applyTestSettings(PhysicsEnvironment env)
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
                
                env.loadConfig(env.getTick() - env.getConfig().HIGHEST_DELAY, "PhysicsTest", yamlDocuments);
                return env.newConfigSelection();
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
        
        public static void assertActorExists(ActorPublic actor)
        {
                assertNotNull(actor);
                assertTrue(!actor.isRemoved());
        }
        
        public static void assertActorNotExists(ActorPublic actor)
        {
                if (actor == null)
                {
                        return;
                }
                
                assertTrue(actor.isRemoved());
        }
        
        public static void assertProjectileExists(ProjectilePublic actor)
        {
                assertNotNull(actor);
                assertTrue(!actor.isRemoved());
        }
        
        public static void assertProjectileNotExists(ProjectilePublic actor)
        {
                if (actor == null)
                {
                        return;
                }
                
                assertTrue(actor.isRemoved());
        }
        
        public static void assertPointEquals(int x, int y, PhysicsPoint point)
        {
                if (x != point.x || y != point.y)
                {
                        throw new AssertionError("expected point:<" + x + "," + y + "> but was:<" + point.x + "," + point.y + ">");
                }
        }

        public void assertPosition(int x, int y, ActorPublic actor)
        {
                assertActorExists(actor);
                PhysicsShipPosition pos = new PhysicsShipPosition();
                assertTrue(actor.getPosition(pos));

                if (x != pos.x || y != pos.y)
                {
                        throw new AssertionError("expected position:<" + x + "," + y + "> but was:<" + pos.x + "," + pos.y + ">");
                }
                
                if (env instanceof SimpleEnvironment)
                {
                        assertTrue(actor.getHistoricPosition(pos, ((SimpleEnvironment) env).getTick(actor.getStateId()), false));
                }
                else
                {
                        assertTrue(actor.getHistoricPosition(pos, env.getTick(), false));
                }
                
                if (x != pos.x || y != pos.y)
                {
                        throw new AssertionError("getHistoricPosition(0) is not equal to the current position!");
                }
        }
        
        public void assertPosition(int x, int y, ProjectilePublic projectile)
        {
                assertProjectileExists(projectile);
                ProjectilePublic.Position pos = new ProjectilePublic.Position();
                
                assertTrue(projectile.getPosition(pos));

                if (x != pos.x || y != pos.y)
                {
                        throw new AssertionError("expected position:<" + x + "," + y + "> but was:<" + pos.x + "," + pos.y + ">");
                }
                
                PhysicsPoint pos2 = new PhysicsPoint();
                
                if (env instanceof SimpleEnvironment)
                {
                        assertTrue(projectile.getHistoricPosition(pos2, ((SimpleEnvironment) env).getTick(projectile.getStateId()), false));
                }
                else
                {
                        assertTrue(projectile.getHistoricPosition(pos2, env.getTick(), false));
                }
                
                
                if (x != pos.x || y != pos.y)
                {
                        throw new AssertionError("getHistoricPosition(0) is not equal to the current position!");
                }
        }
        
        public void assertPosition(int x, int y, long tick, ActorPublic actor)
        {
                assertActorExists(actor);
                PhysicsShipPosition pos = new PhysicsShipPosition();
                
                assertTrue(actor.getHistoricPosition(pos, tick, false));
                if (x != pos.x || y != pos.y)
                {
                        throw new AssertionError("expected position:<" + x + "," + y + "> but was:<" + pos.x + "," + pos.y + "> at tick " + tick);
                }
        }

        public void assertVelocity(int x, int y, ActorPublic actor)
        {
                assertActorExists(actor);
                PhysicsShipPosition pos = new PhysicsShipPosition();
                assertTrue(actor.getPosition(pos));

                if (x != pos.x_vel || y != pos.y_vel)
                {
                        throw new AssertionError("expected velocity:<" + x + "," + y + "> but was:<" + pos.x_vel + "," + pos.y_vel + ">");
                }
                
                if (env instanceof SimpleEnvironment)
                {
                        assertTrue(actor.getHistoricPosition(pos, ((SimpleEnvironment) env).getTick(actor.getStateId()), false));
                }
                else
                {
                        assertTrue(actor.getHistoricPosition(pos, env.getTick(), false));
                }

                if (x != pos.x_vel || y != pos.y_vel)
                {
                        throw new AssertionError("getHistoricPosition(0) is not equal to the current velocity!");
                }
        }

        public void assertRotation(int rot, ActorPublic actor)
        {
                assertActorExists(actor);
                PhysicsShipPosition pos = new PhysicsShipPosition();
                
                assertTrue(actor.getPosition(pos));
                assertEquals(rot, pos.rot);
                
                if (env instanceof SimpleEnvironment)
                {
                        assertTrue(actor.getHistoricPosition(pos, ((SimpleEnvironment) env).getTick(actor.getStateId()), false));
                }
                else
                {
                        assertTrue(actor.getHistoricPosition(pos, env.getTick(), false));
                }
                
                assertEquals(rot, pos.rot);
        }

        public void assertSnappedRotation(int rot, ActorPublic actor)
        {
                assertActorExists(actor);
                PhysicsShipPosition pos = new PhysicsShipPosition();
                
                assertTrue(actor.getPosition(pos));
                assertEquals(rot, pos.rot_snapped);
                
                if (env instanceof SimpleEnvironment)
                {
                        assertTrue(actor.getHistoricPosition(pos, ((SimpleEnvironment) env).getTick(actor.getStateId()), false));
                }
                else
                {
                        assertTrue(actor.getHistoricPosition(pos, env.getTick(), false));
                }
                
                assertEquals(rot, pos.rot_snapped);
        }
}
