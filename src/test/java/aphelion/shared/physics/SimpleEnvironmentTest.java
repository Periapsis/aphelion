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

import aphelion.shared.gameconfig.GameConfig;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.entities.ActorPublic;
import java.util.Iterator;
import java.util.List;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Joris
 */
public class SimpleEnvironmentTest extends PhysicsTest
{
        @Test
        public void testPhysicsPointConstant()
        {
                assertTrue(EnvironmentConf.ROTATION_POINTS % 2 == 0);
                assertTrue(EnvironmentConf.ROTATION_POINTS % 4 == 0);
                assertTrue(Integer.MAX_VALUE / EnvironmentConf.ROTATION_POINTS >= 2);
                
                // Continuum compatibility (not absolutely necessary, however nice to have):
                assertTrue(EnvironmentConf.ROTATION_POINTS % 40 == 0); // continuum rotation points

        }

        @Test
        public void testSnapRotation()
        {
                assertEquals(0,
                        PhysicsMath.snapRotation(100, 40));

                assertEquals(EnvironmentConf.ROTATION_POINTS / 40,
                        PhysicsMath.snapRotation(EnvironmentConf.ROTATION_POINTS / 40 - 1000, 40));

                assertEquals(0,
                        PhysicsMath.snapRotation(EnvironmentConf.ROTATION_POINTS - 1000, 40));
        }

        @Test
        public void testActorCreation()
        {
                SimpleEnvironment env = (SimpleEnvironment) this.env;
                env.tick();
                assertTrue(env.tick_now == 1);

                // Should trigger the operation immediately
                env.actorNew(1, 1, 1234, "Warbird");                 assertNotNull(env.getActor(1));

                // Should not trigger the operation yet
                env.actorNew(2, 2, 1234, "Warbird");
                env.actorRemove(2, 1);
                assertNotNull(env.getActor(1));
                assertNull(env.getActor(2));

                env.tick(); // 2
                assertTrue(env.getActor(1).isRemoved());
                assertNotNull(env.getActor(2));

                // late operation, should execute immediately
                env.actorNew(1, 3, 1234, "Warbird");
                assertNotNull(env.getActor(3));

                // operations with the same tick should execute in the same order they are added
                env.actorRemove(1, 3);
                assertTrue(env.getActor(3).isRemoved());

                env.actorNew(3, 4, 1234, "Warbird");
                env.actorRemove(3, 4);
                env.tick(); // 3
                assertTrue(env.getActor(4).isRemoved());

                ActorPublic actor2 = env.getActor(2);
                assertFalse(actor2.isRemoved());
                env.actorRemove(3, 2);
                assertTrue(actor2.isRemoved());
        }

        @Test
        public void testActorMovement()
        {
                // todo: break apart test case
                
                int rotSpeed = conf.getInteger("ship-rotation-speed").get();

                env.tick(); // 0 -> 1
                env.tick(); // 2
                env.tick(); // 3

                env.actorNew(3, 1, 1234, "Warbird");                 ActorPublic actor = env.getActor(1); // actor created at tick 3

                env.tick(); // 4

                env.actorWarp(4, 1, false, 1000, 90, 100, 15, 0);
                assertPosition(1000, 90, actor);
                assertVelocity(100, 15, actor);
                assertRotation(0, actor);

                env.tick(); // 5
                
                assertPosition(1100, 105, actor); // ERROR
                

                env.actorMove(5, 1, MOVE_UP); // up
                assertPosition(1100, 105, actor); // no change in position yet
                assertVelocity(100, -13, actor); // but the velocity should have been changed

                env.actorMove(5, 1, MOVE_UP); // duplicate move, should be ignored
                assertPosition(1100, 105, actor);
                assertVelocity(100, -13, actor);


                env.tick(); // 6

                assertPosition(1200, 92, actor); // dead reckon
                assertVelocity(100, -13, actor);

                // velocity change in the future
                env.actorMove(7, 1, MOVE_DOWN); // down
                assertPosition(1200, 92, actor); // no change
                assertVelocity(100, -13, actor);

                env.tick(); // 7
                assertPosition(1300, 79, actor); // dead reckon using previous velocity
                assertVelocity(100, 15, actor); // velocity change

                env.tick(); // 8
                assertPosition(1400, 94, actor); // dead reckon using new velocity
                assertVelocity(100, 15, actor);

                // test max speed (2624)
                assertRotation(0, actor);
                env.actorMove(9, 1, MOVE_UP);
                env.actorWarp(9, 1, false, -500, -1400, 0, -2500, 0);

                env.tick(); // 9
                // test operation priority (the move should have been ignored since there is a warp for the same tick)
                assertVelocity(0, -2500, actor); 

                env.actorMove(10, 1, MOVE_UP);
                env.actorMove(11, 1, MOVE_UP);
                env.actorMove(12, 1, MOVE_UP);
                env.actorMove(13, 1, MOVE_UP);
                env.actorMove(14, 1, MOVE_UP);

                env.tick();
                env.tick();
                env.tick();
                env.tick();
                env.tick(); // 14
                
                // maximum speed is 2624. however this is not corrected until the next tick.
                // (this correction is done as the very first step when dead reckoning, so
                // the velocity that is larger than the limit is never really used)
                assertVelocity(0, -2640, actor);
                env.tick(); // 15
                assertVelocity(0, -2624, actor);



                env.actorWarp(16, 1, false, 0, 0, 0, 0, EnvironmentConf.ROTATION_1_2TH); // facing down
                env.actorMove(17, 1, MOVE_UP);

                env.actorWarp(18, 1, false, 0, 0, 0, 0, EnvironmentConf.ROTATION_1_4TH); // facing right
                env.actorMove(19, 1, MOVE_UP);

                env.actorWarp(20, 1, false, 0, 0, 0, 0, EnvironmentConf.ROTATION_3_4TH); // facing left
                env.actorMove(21, 1, MOVE_UP);

                env.tick(); // 16
                env.tick(); // 17
                assertPosition(0, 0, actor);
                assertVelocity(0, 28, actor);

                env.tick(); // 18
                env.tick(); // 19
                assertPosition(0, 0, actor);
                assertVelocity(28, 0, actor);

                env.tick(); // 20
                env.tick(); // 21
                assertPosition(0, 0, actor);
                assertVelocity(-28, 0, actor);


                env.actorWarp(21, 1, false, 0, 0, 0, 0, 0);
                env.actorMove(22, 1, MOVE_RIGHT);
                env.tick(); // 22
                assertRotation(rotSpeed, actor);
                assertSnappedRotation(0, actor);

                env.actorMove(23, 1, MOVE_LEFT);
                env.tick(); // 23
                assertRotation(0, actor);
                assertSnappedRotation(0, actor);

                env.actorMove(24, 1, MOVE_LEFT);
                env.tick(); // 24
                assertRotation(EnvironmentConf.ROTATION_POINTS - rotSpeed, actor);
                assertSnappedRotation(0, actor);

                env.actorMove(25, 1, MOVE_LEFT);
                env.tick(); // 25
                assertSnappedRotation(0, actor);

                env.actorMove(26, 1, MOVE_LEFT);
                env.tick(); // 26
                assertSnappedRotation(EnvironmentConf.ROTATION_POINTS - EnvironmentConf.ROTATION_POINTS / 40, actor);

                env.actorMove(27, 1, MOVE_LEFT);
                env.tick(); // 27
                assertSnappedRotation(EnvironmentConf.ROTATION_POINTS - EnvironmentConf.ROTATION_POINTS / 40, actor);

                env.actorMove(28, 1, MOVE_LEFT);
                env.tick(); // 28
                assertSnappedRotation(EnvironmentConf.ROTATION_POINTS - EnvironmentConf.ROTATION_POINTS / 40, actor);


                env.actorWarp(30, 1, false, 0, 0, 10, 11, 0);
                env.tick(); // 29
                env.tick(); // 30

                for (int a = 0; a < 10; ++a)
                {
                        env.tick();
                }
                // tick 40

                assertVelocity(10, 11, actor);
                assertPosition(100, 11 * 10, actor);

                // late move at tick 35. position should use the old velocity up to and including tick 35.
                // tick 36 and later use the new velocity
                env.actorMove(35, 1, MOVE_DOWN);
                assertVelocity(10, 39, actor);
                assertPosition(100, 11 * 5 + 39 * 5, actor);

                // another late move at tick 36
                env.actorMove(36, 1, MOVE_DOWN);
                assertVelocity(10, 67, actor);
                assertPosition(100, 11 * 5 + 39 * 1 + 67 * 4, actor);

                // a late warp
                env.actorWarp(37, 1, false, 0, 0, 1000, 1001, 0);
                assertVelocity(1000, 1001, actor);
                assertPosition(1000 * 3, 1001 * 3, actor);

                // a late move on the same tick as the late warp (the move should be ignored)
                env.actorMove(37, 1, MOVE_DOWN);
                assertVelocity(1000, 1001, actor);
                assertPosition(1000 * 3, 1001 * 3, actor);


        }

        @Test
        public void testMoveConsistency1()
        {
                SimpleEnvironment env = (SimpleEnvironment) this.env;
                // Test multiple move's received in the past (but still in order)

                env.actorNew(0, 1, 1234, "Warbird");
                env.actorWarp(0, 1, false, 0, 0, 1000, 1001, EnvironmentConf.ROTATION_1_2TH);
                ActorPublic actor = env.getActor(1);

                while (env.tick_now < 20)
                {
                        env.tick();
                }
                // now at tick 20

                env.actorMove(10, 1, MOVE_UP);
                env.actorMove(15, 1, MOVE_UP);

                // Ensure a timewarp can happen
                while (env.tick_now < 15 + env.econfig.TRAILING_STATE_DELAY)
                {
                        env.tick();
                }
                // now at tick 25

                assertVelocity(1000, 1001 + 28 * 2, actor);
                assertPosition(
                        (15 + env.econfig.TRAILING_STATE_DELAY) * 1000,
                        10 * 1001 + 5 * (1001+28) + env.econfig.TRAILING_STATE_DELAY * (1001+28*2),
                        actor);

        }

        @Test
        public void testMoveConsistency2()
        {
                SimpleEnvironment env = (SimpleEnvironment) this.env;
                // Test multiple move's received in the past, out of order
                // This is solved without a timewarp

                env.actorNew(0, 1, 1234, "Warbird");
                // facing down
                env.actorWarp(0, 1, false, 0, 0, 1000, 1001, EnvironmentConf.ROTATION_1_2TH);
                ActorPublic actor = env.getActor(1);

                while (env.tick_now < 20)
                {
                        env.tick();
                }
                // now at tick 20

                env.actorMove(15, 1, MOVE_UP);
                assertVelocity(1000, 1001 + 28, actor);
                assertPosition(20 * 1000, 20 * 1001 + 5 * 28, actor);

                assertPosition(10 * 1000, 10 * 1001, 10, actor);
                
                // Re ordered operations. The following operation is ignored.
                // This can be fixed using a timewarp.
                env.actorMove(10, 1, MOVE_UP);
                
                assertVelocity(1000, 1001 + 28 * 2, actor);
                assertPosition(
                        20 * 1000,
                        20 * 1001 + 10 * 28 + 5 * 28,
                        actor);


        }
        
        @Test
        public void testInvalidOperationOrder()
        {
                SimpleEnvironment env = (SimpleEnvironment) this.env;
                List<Object> yamlDocuments;
                try
                {
                        yamlDocuments = GameConfig.loadYaml(
                        "- ship-spawn-x: 100\n" +
                        "  ship-spawn-y: 100\n" +
                        "  ship-spawn-radius: 0\n");
                }
                catch (Exception ex)
                {
                        throw new Error(ex);
                }
                
                env.loadConfig(env.getTick() - EnvironmentConf.HIGHEST_DELAY + 1, "testInvalidOperationOrder()", yamlDocuments);
                
                env.actorWarp(1, 1, false, 1000, 1000, 100, 100, 0);
                env.actorNew(5, 1, 1234, "Warbird");
                
                while (env.tick_now < 200)
                {
                        env.tick();
                }
                
                assertEquals(0, env.getTimewarpCount());
                
                ActorPublic actor = env.getActor(1);
                assertPosition(100 * PhysicsMap.TILE_PIXELS + PhysicsMap.TILE_PIXELS / 2, 
                               100 * PhysicsMap.TILE_PIXELS + PhysicsMap.TILE_PIXELS / 2, 
                               actor); // warp should be ignored
                
        }
        
        @Test
        public void testLateActorRemove()
        {
                // these tick values are so long ago, they are not part of any trailing state
                env.actorRemove(-10000, 1);
                env.actorNew(-11000, 1, 1234, "Warbird");
                
                env.tick();
        }
        
        @Test
        public void testWeaponFire()
        {
                SimpleEnvironment env = (SimpleEnvironment) this.env;
                env.actorNew(0, 1, 1234, "Warbird");
                env.actorWarp(0, 1, false, 1000, 2000, 0, 10, EnvironmentConf.ROTATION_1_2TH);
                env.actorWeapon(0, 1, WEAPON_SLOT.GUN, false, 0, 0, 0, 0, 0);
                
                int offsetY = conf.getInteger("projectile-offset-y").get();
                int fireSpeed = conf.getInteger("projectile-speed").get();
                
                Iterator<ProjectilePublic> it = env.projectileIterator(0);
                assertTrue(it.hasNext());
                
                ProjectilePublic.Position pos = new ProjectilePublic.Position();
                ProjectilePublic proj = it.next();
                assertFalse(it.hasNext());
                proj.getPosition(pos);
                
                assertEquals(1000, pos.x);
                assertEquals(2000 + offsetY, pos.y);
                assertEquals(0, pos.x_vel);
                assertEquals(fireSpeed + 10, pos.y_vel); 
                
                it = null;
                proj = null;
                
                env.tick();
                
                it = env.projectileIterator(0);
                assertTrue(it.hasNext());
                proj = it.next();
                assertFalse(it.hasNext());
                proj.getPosition(pos);
                
                assertEquals(1000, pos.x);
                assertEquals(2000 + offsetY + fireSpeed + 10, pos.y);
                assertEquals(0, pos.x_vel);
                assertEquals(fireSpeed + 10, pos.y_vel);
        }
        
        @Test
        public void testWeaponFireConsistency()
        {
                SimpleEnvironment env = (SimpleEnvironment) this.env;
                env.actorNew(0, 1, 1234, "Warbird");
                env.actorWarp(0, 1, false, 1000, 2000, 0, 0, EnvironmentConf.ROTATION_1_2TH);
                env.actorWeapon(2, 1, WEAPON_SLOT.GUN, false, 0, 0, 0, 0, 0);
                
                env.tick(); // 1 
                env.tick(); // 2
                
                int offsetY = conf.getInteger("projectile-offset-y").get();
                int fireSpeed = conf.getInteger("projectile-speed").get();
                
                Iterator<ProjectilePublic> it = env.projectileIterator(0);
                assertTrue(it.hasNext());
                
                ProjectilePublic.Position pos = new ProjectilePublic.Position();
                ProjectilePublic proj = it.next();
                assertFalse(it.hasNext());
                proj.getPosition(pos);
                
                assertEquals(1000, pos.x);
                assertEquals(2000 + offsetY, pos.y);
                assertEquals(0, pos.x_vel);
                assertEquals(fireSpeed, pos.y_vel);
                
                it = null;
                proj = null;
                
                // weapon fire was at tick 2
                env.actorMove(1, 1, MOVE_UP);
                
                
                
                // Ensure a timewarp can happen
                while (env.tick_now < 2 + env.econfig.TRAILING_STATE_DELAY)
                {
                        env.tick();
                }
                // now at tick 13
                
                assertEquals(1, env.getTimewarpCount());
                
                assertPosition(1000, 2000 + ((int) env.tick_now - 1) * 28, env.getActor(1));
                
                it = env.projectileIterator(0);
                assertTrue(it.hasNext());
                proj = it.next();
                assertFalse(it.hasNext());
                proj.getPosition(pos);
                
                // 57548 but was: 57296
                
                assertEquals(1000, pos.x);
                assertEquals(2000 + offsetY + 28 // ship position
                        + (fireSpeed + 28) * env.econfig.TRAILING_STATE_DELAY, // the distance the projectile has traveled
                        pos.y
                );
                
                assertEquals(0, pos.x_vel);
                assertEquals(fireSpeed + 28, pos.y_vel);
        }
        
        
}
