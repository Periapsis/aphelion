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

import aphelion.shared.gameconfig.GameConfig;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;



/**
 *
 * @author joris
 */
public class ForceEmitterTest extends PhysicsTest
{
        
        public static void applyMoreTestSettings(PhysicsEnvironment env)
        {
                List<Object> yamlDocuments;
                try
                {
                yamlDocuments = GameConfig.loadYaml(
                        "- weapon-slot-repel: repelA\n" +
                        "  weapon-slot-burst: repelB\n" +
                        "  weapon-slot-bomb: bomb\n" +
                        "  ship-speed: "+EnvironmentConf.MAX_POSITION+"\n" +

                        "- selector: {weapon: [repelA, repelB]}\n" +
                        "  projectile-force-distance-ship: 204800\n" +
                        "  projectile-force-velocity-ship: 3840000\n" +
                        "  projectile-force-distance-projectile: 409600\n" +
                        "  projectile-force-velocity-projectile: 5120000\n" +
                        
                        "  projectile-speed: 0\n" +
                        "  projectile-hit-ship: false\n" +
                        "  projectile-hit-tile: false\n" +
                        "  projectile-offset-y: 0\n" +
                        "  projectile-expiration-ticks: 2\n" +
                        "  projectile-speed-relative: false\n" +
                        
                        "- selector: {weapon: [repelB]}\n" +
                        "  projectile-force-velocity-ship: 10\n" +
                        "  projectile-force-velocity-projectile: 20\n" +
                        "  projectile-expiration-ticks: 4\n" +

                        "- selector: {weapon: [bomb]}\n" +
                        "  projectile-speed: 3\n" +
                        "  projectile-expiration-ticks: 1000\n" +
                        "  projectile-angle-relative: true\n" +
                        "  projectile-offset-x: 0\n" +
                        "  projectile-offset-y: 0\n"
                );
                
                }
                catch (Exception ex)
                {
                        throw new Error(ex);
                }
                
                env.loadConfig(env.getTick() - env.getConfig().HIGHEST_DELAY, "ForceEmitterTest", yamlDocuments);
        }

        @Override
        public void setUp()
        {
                super.setUp();
                applyMoreTestSettings(env);
        }
        
        @Test
        public void testStrongForceOnShip()
        {
                SimpleEnvironment env = (SimpleEnvironment) this.env;
                
                env.actorNew(0, ACTOR_FIRST, 1234, "NX-01");
                env.actorNew(0, ACTOR_SECOND, 1234, "O'Neill");
                
                final PhysicsPoint FIRST_POSITION_START = new PhysicsPoint(20000, 50000);
                final PhysicsPoint SECOND_POSITION_START = new PhysicsPoint(20000 + 204800 / 2, 50000);
                
                final PhysicsPoint SECOND_VEL_START = new PhysicsPoint(100, 150);
                SECOND_POSITION_START.sub(SECOND_VEL_START);
                
                env.actorWarp(0, ACTOR_FIRST , false, FIRST_POSITION_START.x, FIRST_POSITION_START.y, 0, 0, 0);
                env.actorWarp(0, ACTOR_SECOND, false, SECOND_POSITION_START.x, SECOND_POSITION_START.y, SECOND_VEL_START.x, SECOND_VEL_START.y, 0);
                
                env.actorWeapon(1, ACTOR_FIRST, WEAPON_SLOT.REPEL);
                
                ActorPublic firstActor = env.getActor(ACTOR_FIRST);
                ActorPublic secondActor = env.getActor(ACTOR_SECOND);
                
                assertVelocity(SECOND_VEL_START.x, SECOND_VEL_START.y, secondActor);
                
                env.tick(); // now at 1
                // force should not have been applied to the position or velocity yet.
                // it should affect the velocity and position in the next tick
                assertPosition(FIRST_POSITION_START.x, FIRST_POSITION_START.y, firstActor);
                assertPosition(SECOND_POSITION_START.x + SECOND_VEL_START.x,
                        SECOND_POSITION_START.y + SECOND_VEL_START.y, 
                        secondActor);
                assertVelocity(SECOND_VEL_START.x,
                               SECOND_VEL_START.y, 
                               secondActor);
                
                
                env.tick(); // now at 2
                assertPosition(FIRST_POSITION_START.x, FIRST_POSITION_START.y, firstActor);
                assertPosition(SECOND_POSITION_START.x + SECOND_VEL_START.x * 2 + (3840000 / 2),
                        SECOND_POSITION_START.y + SECOND_VEL_START.y * 2, 
                        secondActor);
                assertVelocity(SECOND_VEL_START.x + (3840000 / 2),
                               SECOND_VEL_START.y, 
                               secondActor);
                
                env.tick(); // now at 3
                // force is applied for 2 ticks, but we should be out of range now
                assertPosition(FIRST_POSITION_START.x, FIRST_POSITION_START.y, firstActor);
                assertPosition(SECOND_POSITION_START.x + SECOND_VEL_START.x * 3 + (3840000 / 2) * 2,
                        SECOND_POSITION_START.y + SECOND_VEL_START.y * 3, 
                        secondActor);
                assertVelocity(SECOND_VEL_START.x + (3840000 / 2),
                               SECOND_VEL_START.y, 
                               secondActor);
        }
        
        @Test
        public void testWeakForceOnShip()
        {
                SimpleEnvironment env = (SimpleEnvironment) this.env;
                
                env.actorNew(0, ACTOR_FIRST, 1234, "NX-01");
                env.actorNew(0, ACTOR_SECOND, 1234, "O'Neill");
                
                final PhysicsPoint FIRST_POSITION_START = new PhysicsPoint(20000, 50000);
                final PhysicsPoint SECOND_POSITION_START = new PhysicsPoint(20000 + 204800 / 2, 50000);
                
                final PhysicsPoint SECOND_VEL_START = new PhysicsPoint(100, 150);
                SECOND_POSITION_START.sub(SECOND_VEL_START);
                
                env.actorWarp(0, ACTOR_FIRST , false, FIRST_POSITION_START.x, FIRST_POSITION_START.y, 0, 0, 0);
                env.actorWarp(0, ACTOR_SECOND, false, SECOND_POSITION_START.x, SECOND_POSITION_START.y, SECOND_VEL_START.x, SECOND_VEL_START.y, 0);
                
                env.actorWeapon(1, ACTOR_FIRST, WEAPON_SLOT.BURST);
                
                ActorPublic firstActor = env.getActor(ACTOR_FIRST);
                ActorPublic secondActor = env.getActor(ACTOR_SECOND);
                
                assertVelocity(SECOND_VEL_START.x, SECOND_VEL_START.y, secondActor);
                
                env.tick(); // now at 1
                // force should not have been applied to the position or velocity yet.
                // it should affect the velocity and position in the next tick
                assertPosition(FIRST_POSITION_START.x, FIRST_POSITION_START.y, firstActor);
                assertPosition(SECOND_POSITION_START.x + SECOND_VEL_START.x,
                        SECOND_POSITION_START.y + SECOND_VEL_START.y, 
                        secondActor);
                assertVelocity(SECOND_VEL_START.x,
                               SECOND_VEL_START.y, 
                               secondActor);
                
                
                env.tick(); // now at 2
                assertPosition(FIRST_POSITION_START.x, FIRST_POSITION_START.y, firstActor);
                assertPosition(SECOND_POSITION_START.x + SECOND_VEL_START.x * 2 + 5,
                        SECOND_POSITION_START.y + SECOND_VEL_START.y * 2, 
                        secondActor);
                assertVelocity(SECOND_VEL_START.x + 5,
                               SECOND_VEL_START.y, 
                               secondActor);
                
                env.tick(); // now at 3
                assertVelocity(SECOND_VEL_START.x + 5 + 4,
                               SECOND_VEL_START.y, 
                               secondActor);
                
                env.tick(); // now at 4 (weapon expires at this tick)
                assertVelocity(SECOND_VEL_START.x + 5 + 4 + 4,
                               SECOND_VEL_START.y, 
                               secondActor);
                
                env.tick(); // now at 5 
                assertVelocity(SECOND_VEL_START.x + 5 + 4 + 4 + 4,
                               SECOND_VEL_START.y, 
                               secondActor);
                
                
                env.tick(); // now at 6
                assertVelocity(SECOND_VEL_START.x + 5 + 4 + 4 + 4,
                               SECOND_VEL_START.y, 
                               secondActor);
        }

        @Test
        public void testLateAsPossibleWithoutTimeWarp_forceOnProjectile_control()
        {
                SimpleEnvironment env = (SimpleEnvironment) this.env;

                env.actorNew(0, ACTOR_FIRST , 1234, "NX-01");
                env.actorNew(0, ACTOR_SECOND, 1234, "O'Neill");

                final PhysicsPoint FIRST_POSITION_START  = new PhysicsPoint(20000             , 50000);
                final PhysicsPoint SECOND_POSITION_START = new PhysicsPoint(20000 + 204800 / 2, 50000);

                env.actorWarp(0, ACTOR_FIRST , false, FIRST_POSITION_START.x , FIRST_POSITION_START.y , 0, 0, EnvironmentConf.ROTATION_1_4TH);
                env.actorWarp(0, ACTOR_SECOND, false, SECOND_POSITION_START.x, SECOND_POSITION_START.y, 0, 0, EnvironmentConf.ROTATION_1_4TH * 3);

                env.actorWeapon(1, ACTOR_FIRST, WEAPON_SLOT.BOMB);

                // This is the operation that will arrive late in the next test:
                env.actorWeapon(7, ACTOR_SECOND, WEAPON_SLOT.BURST); // repelB

                ActorPublic firstActor = env.getActor(ACTOR_FIRST);
                ActorPublic secondActor = env.getActor(ACTOR_SECOND);

                env.tick(); // 1
                env.tick(); // 2

                ProjectilePublic projectile = env.projectileIterator().next();
                assertEquals(ACTOR_FIRST, projectile.getOwner());
                assertVelocity(3, 0, projectile);


                while (env.getTick() < 7) { env.tick(); }
                assertVelocity(3, 0, projectile);

                // force is applied during 4 ticks
                env.tick();
                assertVelocity(3 -15, 0, projectile);
                env.tick();
                assertVelocity(3 -15-15, 0, projectile);
                env.tick();
                assertVelocity(3 -15-15-14, 0, projectile);
                env.tick();
                assertVelocity(3 -15-15-14-14, 0, projectile);
                env.tick();
                assertVelocity(3 -15-15-14-14, 0, projectile);

                while (env.getTick() < 40) { env.tick(); }

                assertVelocity(3 -15-15-14-14, 0, projectile);
                assertPosition(
                        18288,
                        FIRST_POSITION_START.y,
                        projectile);
        }

        @Test
        @org.junit.Ignore // disabled until it is fixed
        public void testLateAsPossibleWithoutTimeWarp_forceOnProjectile()
        {
                SimpleEnvironment env = (SimpleEnvironment) this.env;

                env.actorNew(0, ACTOR_FIRST , 1234, "NX-01");
                env.actorNew(0, ACTOR_SECOND, 1234, "O'Neill");

                final PhysicsPoint FIRST_POSITION_START  = new PhysicsPoint(20000             , 50000);
                final PhysicsPoint SECOND_POSITION_START = new PhysicsPoint(20000 + 204800 / 2, 50000);

                env.actorWarp(0, ACTOR_FIRST , false, FIRST_POSITION_START.x , FIRST_POSITION_START.y , 0, 0, EnvironmentConf.ROTATION_1_4TH);
                env.actorWarp(0, ACTOR_SECOND, false, SECOND_POSITION_START.x, SECOND_POSITION_START.y, 0, 0, EnvironmentConf.ROTATION_1_4TH * 3);

                env.actorWeapon(1, ACTOR_FIRST, WEAPON_SLOT.BOMB);

                ActorPublic firstActor = env.getActor(ACTOR_FIRST);
                ActorPublic secondActor = env.getActor(ACTOR_SECOND);

                final int PROJECTILE_HISTORY_LENGTH = env.getConfig().TRAILING_STATE_DELAY + env.getConfig().MINIMUM_HISTORY_TICKS;
                assert PROJECTILE_HISTORY_LENGTH == 34 : "test case needs adjusting";

                env.tick(); // 1
                env.tick(); // 2

                ProjectilePublic projectile = env.projectileIterator().next();
                assertEquals(ACTOR_FIRST, projectile.getOwner());
                assertVelocity(3, 0, projectile);

                while (env.getTick() < 40) { env.tick(); }

                /* */
                assertVelocity(3, 0, projectile);
                assertPosition(
                        FIRST_POSITION_START.x + 3 * ((int) env.getTick() - 1),
                        FIRST_POSITION_START.y,
                        projectile);

                // the only difference between this test case and the previous one is that this this operation is late:
                assert env.getTick() - PROJECTILE_HISTORY_LENGTH + 1 == 7;
                // this is the oldest tick we can fix without a timewarp
                env.actorWeapon(env.getTick() - PROJECTILE_HISTORY_LENGTH + 1, ACTOR_SECOND, WEAPON_SLOT.BURST); // repelB
                /* */
                env.doReexecuteDirtyPositionPath();

                // see previous test case
                assertVelocity(3 -15-15-14-14, 0, projectile);
                assertPosition(
                        18288,
                        FIRST_POSITION_START.y,
                        projectile);
        }
}
