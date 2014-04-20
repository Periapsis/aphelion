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


import aphelion.shared.gameconfig.GCInteger;
import aphelion.shared.gameconfig.GameConfig;
import static aphelion.shared.physics.PhysicsEnvironmentTest.MOVE_UP;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.events.pub.ActorDiedPublic;
import aphelion.shared.physics.events.pub.EventPublic;
import aphelion.shared.physics.events.pub.ProjectileExplosionPublic;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import java.util.List;
import org.junit.*;
import static org.junit.Assert.*;

/**
 * Test if timewarps are able to execute without error.
 * @author Joris
 */
public class TimewarpTest extends PhysicsTest
{
        @Test
        public void testActorCreation()
        {
                try
                {
                        List<Object> yamlDocuments = GameConfig.loadYaml(""
                                + "- selector: {ship: warbird}\n"
                                + "  test-actor-creation-test: 1944619\n"
                                + "- selector: {ship: javelin}\n"
                                + "  test-actor-creation-test: 391385\n"
                        );
                        env.loadConfig(env.getTick() - env.econfig.HIGHEST_DELAY, "test", yamlDocuments);
                }
                catch (Exception ex)
                {
                        throw new Error(ex);
                }
                
                // todo also test change
                
                env.actorNew(1, ACTOR_FIRST, 1234, "warbird");
                env.actorWarp(1, ACTOR_FIRST, false, 1000, 90, 0, 0, 0);
                
                env.timewarp(1);
                env.tick(); // tick 1, it should now create the actor
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 0, false));
                
                GCInteger testGC = env.getActor(ACTOR_FIRST, 0, false).getActorConfigInteger("test-actor-creation-test");
                assertEquals(1944619, testGC.get());
                
                assertEquals(1, env.getActorCount(0));
                
                env.timewarp(1); // the actor is not yet present in state 1, it should recreate him in state 0
                assertEquals(1, env.getActorCount(0));
                env.timewarp(1);
                assertEquals(1, env.getActorCount(0));
                env.timewarp(1);
                assertEquals(1, env.getActorCount(0));
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 0, false));
                
                assertEquals(1944619, testGC.get());
                
                env.actorModification(3, ACTOR_FIRST, "javelin");
                
                while(env.getTick() < env.econfig.TRAILING_STATE_DELAY)
                {
                        env.tick();
                }
                
                assertEquals(391385, testGC.get());
                
                assertEquals(1, env.getActorCount(0));
                
                env.tick(); // should create the actor at this tick in state 1
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 0, false));
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 1, false));
                env.timewarp(1);
                assertEquals(1, env.getActorCount(0));
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 0, false));
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 1, false));
                env.timewarp(env.econfig.TRAILING_STATES-1);
                assertEquals(1, env.getActorCount(0));
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 0, false));
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 1, false));
                
                assertEquals(391385, testGC.get());
        }
        
        @Test
        public void testActorDestruction()
        {
                env.actorNew(1, ACTOR_FIRST, 1234, "warbird");
                env.actorRemove(env.econfig.TRAILING_STATE_DELAY + 3, ACTOR_FIRST);
                
                env.tick();
                assertActorExists(env.getActor(ACTOR_FIRST, 0, false));
                assertActorNotExists(env.getActor(ACTOR_FIRST, 1, false));
                
                while(env.getTick() < env.econfig.TRAILING_STATE_DELAY)
                {
                        env.tick();
                }
                
                env.tick(); // should create the actor at this tick in state 1
                assertActorExists(env.getActor(ACTOR_FIRST, 0, false));
                assertActorExists(env.getActor(ACTOR_FIRST, 1, false));
                
                env.tick();
                env.tick(); // should remove the actor at this tick in state 0
                
                assertActorNotExists(env.getActor(ACTOR_FIRST, 0, false));
                assertNotNull(env.getActor(ACTOR_FIRST, 1, false));
                
                env.timewarp(1);
                assertActorNotExists(env.getActor(ACTOR_FIRST, 0, false));
                assertNotNull(env.getActor(ACTOR_FIRST, 1, false));
                
                env.timewarp(env.econfig.TRAILING_STATES-1);
                assertActorNotExists(env.getActor(ACTOR_FIRST, 0, false));
                assertNotNull(env.getActor(ACTOR_FIRST, 1, false));
                
                // Soft delete
                assertEquals(1, env.getActorCount(0));
                
                while(env.getTick(env.econfig.TRAILING_STATES-1) < 1 + env.econfig.HIGHEST_DELAY)
                {
                        env.tick();
                }
                
                // hard delete
                assertEquals(0, env.getActorCount(0));
        }
        
        @Test
        public void testConfigChange()
        {
                env.actorNew(1, ACTOR_FIRST, 1234, "warbird");
                env.actorWarp(1, ACTOR_FIRST, false, 1000, 90, 0, 0, 0);
                
                // Config change
                try
                {
                        List<Object> yamlDocuments = GameConfig.loadYaml(
                                "- ship-thrust: 1000\n" // was 28
                        );
                        env.loadConfig(3, "test", yamlDocuments);
                }
                catch (Exception ex)
                {
                        throw new Error(ex);
                }
                
                env.actorMove(2, ACTOR_FIRST, MOVE_UP);
                env.actorMove(3, ACTOR_FIRST, MOVE_UP);
                env.actorMove(4, ACTOR_FIRST, MOVE_UP);
                env.actorMove(5, ACTOR_FIRST, MOVE_UP);
                env.actorMove(6, ACTOR_FIRST, MOVE_UP);
                
                env.tick(); // tick 1
                env.tick(); // tick 2
                env.tick(); // tick 3
                env.tick(); // tick 4
                env.tick(); // tick 5
                env.tick(); // tick 6

                assertEquals(1000, env.getGlobalConfigInteger(0, "ship-thrust").get());
                assertEquals(28, env.getGlobalConfigInteger(1, "ship-thrust").get());
                assertVelocity(0, -3624, env.getActor(ACTOR_FIRST, 0, false));
                
                env.timewarp(1);
                assertEquals(1000, env.getGlobalConfigInteger(0, "ship-thrust").get());
                assertEquals(28, env.getGlobalConfigInteger(1, "ship-thrust").get());
                assertVelocity(0, -3624, env.getActor(ACTOR_FIRST, 0, false));
                
                env.timewarp(env.econfig.TRAILING_STATES-1);
                assertEquals(1000, env.getGlobalConfigInteger(0, "ship-thrust").get());
                assertEquals(28, env.getGlobalConfigInteger(1, "ship-thrust").get());
                assertVelocity(0, -3624, env.getActor(ACTOR_FIRST, 0, false));
        }
        
        private void testProjectileCreation_assertFirstProj(int state, int x, int y)
        {
                for (ProjectilePublic proj : env.projectileIterable(state))
                {
                        assertPosition(x, y, proj);
                        return;
                }
        }
        
        @Test
        public void testProjectileCreation()
        {
                try
                {
                        List<Object> yamlDocuments = GameConfig.loadYaml(""
                                + "- weapon-slot-gun: test-noreload\n"
                                + "  weapon-slot-bomb: test-reload\n"
                                + "  projectile-expiration-ticks: 100\n"
                                
                                + "- selector: {weapon: test-noreload}\n"
                                + "  weapon-switch-delay: 0\n"
                                
                                + "- selector: {weapon: test-reload}\n"
                                + "  weapon-switch-delay: 4\n"
                        );
                        env.loadConfig(env.getTick() - env.econfig.HIGHEST_DELAY, "test", yamlDocuments);
                }
                catch (Exception ex)
                {
                        throw new Error(ex);
                }
                
                
                env.actorNew(1, ACTOR_FIRST, 1234, "warbird");
                env.actorWarp(1, ACTOR_FIRST, false, 1000, 90, 0, 0, 0);
                env.actorWeapon(2, ACTOR_FIRST, WEAPON_SLOT.GUN, false, 0, 0, 0, 0, 0);
                
                env.tick(); // 1
                env.tick(); // 2
                
                assertEquals(1, env.calculateProjectileCount(0));
                assertEquals(0, env.calculateProjectileCount(1));
                testProjectileCreation_assertFirstProj(0, 1000, -14246);
                ProjectilePublic firstProjectile = env.projectileIterator(0).next();
                
                
                env.timewarp(1);
                assertEquals(1, env.calculateProjectileCount(0));
                assertEquals(0, env.calculateProjectileCount(1));
                testProjectileCreation_assertFirstProj(0, 1000, -14246);
                assertContains(env.projectileIterator(0), firstProjectile);
                
                
                env.timewarp(env.econfig.TRAILING_STATES-1);
                assertEquals(1, env.calculateProjectileCount(0));
                assertEquals(0, env.calculateProjectileCount(1));
                testProjectileCreation_assertFirstProj(0, 1000, -14246);
                assertContains(env.projectileIterator(0), firstProjectile);
                
                
                while(env.getTick() < env.econfig.TRAILING_STATE_DELAY)
                {
                        env.tick();
                }
                
                env.tick();
                env.tick();
                
                assertEquals(1, env.calculateProjectileCount(0));
                assertEquals(1, env.calculateProjectileCount(1));
                testProjectileCreation_assertFirstProj(0, 1000, -178086);
                testProjectileCreation_assertFirstProj(1, 1000, -14246);
                assertContains(env.projectileIterator(0), firstProjectile);
                
                env.timewarp(1);
                assertEquals(1, env.calculateProjectileCount(0));
                assertEquals(1, env.calculateProjectileCount(1));
                testProjectileCreation_assertFirstProj(0, 1000, -178086);
                testProjectileCreation_assertFirstProj(1, 1000, -14246);
                assertContains(env.projectileIterator(0), firstProjectile);
                
                env.timewarp(env.econfig.TRAILING_STATES-1);
                assertEquals(1, env.calculateProjectileCount(0));
                assertEquals(1, env.calculateProjectileCount(1));
                testProjectileCreation_assertFirstProj(0, 1000, -178086);
                testProjectileCreation_assertFirstProj(1, 1000, -14246);
                assertContains(env.projectileIterator(0), firstProjectile);
                
                assertEquals(4, env.getTimewarpCount());
                
                // 2 weapons, one of them has a weapon switch delay
                // execute them in the wrong order so that only 1 fires in state 0,
                // but both fire in state 1
                long t = env.getTick();
                env.tick();
                env.tick();
                // make sure they are both late
                env.actorWeapon(t+2, ACTOR_FIRST, WEAPON_SLOT.BOMB, false, 0, 0, 0, 0, 0); // bomb has switch delay,
                env.actorWeapon(t+1, ACTOR_FIRST, WEAPON_SLOT.GUN, false, 0, 0, 0, 0, 0);  // gun does not
                // after resolving inconsistencies, both weapons should have executed properly

                
                assertEquals(2, env.calculateProjectileCount(0));
                
                
                assertEquals(4, env.getTimewarpCount());
                
                // should detect the inconsistency and resolve it
                // (new Projectile() should execute properly)
                while(env.getTick(1) < t+env.econfig.TRAILING_STATE_DELAY)
                {
                        env.tick();
                }
                assertEquals(5, env.getTimewarpCount());
                
                
                env.tick();
                env.tick();
                assertEquals(5, env.getTimewarpCount());
                
                assertEquals(3, env.calculateProjectileCount(0));
                assertContains(env.projectileIterator(0), firstProjectile);
                
                // expire the projectiles
                while(env.getTick(env.econfig.TRAILING_STATES-1) < t + 3 + 100 + env.econfig.HIGHEST_DELAY)
                {
                        env.tick();
                }
                
                assertEquals(0, env.calculateProjectileCount(0));
        }
        
        @Test
        public void testProjectileCreationCoupled()
        {
                try
                {
                        List<Object> yamlDocuments = GameConfig.loadYaml(""
                                + "- weapon-slot-gun: test-noreload\n"
                                + "  weapon-slot-bomb: test-reload\n"
                                + "  projectile-expiration-ticks: 100\n"
                                + "  weapon-projectiles: 50\n"
                                + "  projectile-angle: [LINEAR, 14702688] # 735134400 / 50"
                                
                                + "- selector: {weapon: test-noreload}\n"
                                + "  weapon-switch-delay: 0\n"
                                
                                + "- selector: {weapon: test-reload}\n"
                                + "  weapon-switch-delay: 4\n"
                        );
                        env.loadConfig(env.getTick() - env.econfig.HIGHEST_DELAY, "test", yamlDocuments);
                }
                catch (Exception ex)
                {
                        throw new Error(ex);
                }
                
                
                env.actorNew(1, ACTOR_FIRST, 1234, "warbird");
                env.actorWarp(1, ACTOR_FIRST, false, 1000, 90, 0, 0, 0);
                env.actorWeapon(2, ACTOR_FIRST, WEAPON_SLOT.GUN, false, 0, 0, 0 ,0 ,0);
                
                env.tick(); // 1
                env.tick(); // 2
                
                assertEquals(1 * 50, env.calculateProjectileCount(0));
                
                env.timewarp(1);
                assertEquals(1 * 50, env.calculateProjectileCount(0));
                
                env.timewarp(env.econfig.TRAILING_STATES-1);
                assertEquals(1 * 50, env.calculateProjectileCount(0));
                
                
                
                while(env.getTick() < env.econfig.TRAILING_STATE_DELAY)
                {
                        env.tick();
                }
                
                env.tick();
                env.tick();
                
                assertEquals(1 * 50, env.calculateProjectileCount(0));
                assertEquals(1 * 50, env.calculateProjectileCount(1));
                
                env.timewarp(1);
                assertEquals(1 * 50, env.calculateProjectileCount(0));
                assertEquals(1 * 50, env.calculateProjectileCount(1));
                
                env.timewarp(env.econfig.TRAILING_STATES-1);
                assertEquals(1 * 50, env.calculateProjectileCount(0));
                assertEquals(1 * 50, env.calculateProjectileCount(1));
                
                
                assertEquals(4, env.getTimewarpCount());
                
                // 2 weapons, one of them has a weapon switch delay
                // execute them in the wrong order so that only 1 fires in state 0,
                // but both fire in state 1
                long t = env.getTick();
                env.tick();
                env.tick();
                // make sure they are both late
                env.actorWeapon(t+2, ACTOR_FIRST, WEAPON_SLOT.BOMB, false, 0, 0, 0, 0, 0); // bomb has switch delay,
                env.actorWeapon(t+1, ACTOR_FIRST, WEAPON_SLOT.GUN, false, 0, 0, 0, 0, 0);  // gun does not
                // after resolving inconsistencies, both weapons should have executed properly

                
                assertEquals(2 * 50, env.calculateProjectileCount(0));
                
                
                assertEquals(4, env.getTimewarpCount());
                
                // should detect the inconsistency and resolve it
                // (new Projectile() should execute properly)
                while(env.getTick(1) < t+env.econfig.TRAILING_STATE_DELAY)
                {
                        env.tick();
                }
                assertEquals(5, env.getTimewarpCount());
                
                
                env.tick();
                env.tick();
                assertEquals(5, env.getTimewarpCount());
                
                assertEquals(3 * 50, env.calculateProjectileCount(0));
                
                // expire the projectiles
                while(env.getTick(env.econfig.TRAILING_STATES-1) < t + 3 + 100 + env.econfig.HIGHEST_DELAY)
                {
                        env.tick();
                }
                
                assertEquals(0 * 50, env.calculateProjectileCount(0));
        }
        
        private void testExplosionEventShort_assertEvent(int state)
        {
                int events = 0;
                for (EventPublic e : env.eventIterable())
                {
                        ++events;
                        if (e instanceof ProjectileExplosionPublic)
                        {
                                ProjectileExplosionPublic ev = (ProjectileExplosionPublic) e;
                                assert ev.hasOccurred(state);
                                assertEquals(ACTOR_FIRST, ev.getFireActor(state));
                                assertEquals(ACTOR_SECOND, ev.getHitActor(state));
                                assertEquals(4, ev.getOccurredAt(state));
                                
                                PhysicsPoint pos = new PhysicsPoint();
                                ev.getPosition(state, pos);
                                assertPointEquals(45664, 90, pos);
                                
                                ev.getProjectile(state).getHistoricPosition(pos, ev.getOccurredAt(state), true);
                                assertPointEquals(45664, 90, pos);
                        }
                        else if (e instanceof ActorDiedPublic)
                        {
                                ActorDiedPublic ev = (ActorDiedPublic) e;
                                assert ev.hasOccurred(state);
                                assertEquals(4, ev.getOccurredAt(state));
                                assertEquals(ACTOR_SECOND, ev.getDied(state));
                                assert ev.getCause(state) instanceof ProjectileExplosionPublic;
                        }
                        else
                        {
                                assert false;
                        }
                }
                
                assertEquals(2, events);
        }
        
        @Test
        public void testExplosionEventShort()
        {
                // Short time between fire and explosion (less than TRAILING_STATE_DELAY)
                try
                {
                        List<Object> yamlDocuments = GameConfig.loadYaml(""
                                + "- weapon-projectiles: 1\n"
                                + "  projectile-hit-ship: true\n"
                                + "  projectile-angle-relative: true\n"
                                + "  projectile-speed: 20000\n"
                                + "  projectile-damage: 2000\n"
                                + "  ship-energy: 1500\n"
                        );
                        env.loadConfig(env.getTick() - env.econfig.HIGHEST_DELAY, "test", yamlDocuments);
                }
                catch (Exception ex)
                {
                        throw new Error(ex);
                }
                
                
                env.actorNew(1, ACTOR_FIRST, 1234, "warbird");
                env.actorWarp(1, ACTOR_FIRST, false, 1000, 90, 0, 0, EnvironmentConf.ROTATION_1_4TH);
                env.actorNew(1, ACTOR_SECOND, 4321, "warbird");
                env.actorWarp(1, ACTOR_SECOND, false, 60000, 90, 0, 0, 0);
                
                env.actorWeapon(2, ACTOR_FIRST, WEAPON_SLOT.GUN, false, 0, 0, 0 , 0, 0);
                
                env.tick(); // 1
                env.tick(); // 2
                env.tick(); // 3
                env.tick(); // 4, should hit at this tick
                testExplosionEventShort_assertEvent(0);
                env.timewarp(1);
                testExplosionEventShort_assertEvent(0);
                
                while (env.getTick() < env.econfig.TRAILING_STATE_DELAY + 4)
                {
                        env.tick();
                }
                
                testExplosionEventShort_assertEvent(0);
                testExplosionEventShort_assertEvent(1);
                env.timewarp(1);
                testExplosionEventShort_assertEvent(0);
                testExplosionEventShort_assertEvent(1);
                env.timewarp(env.econfig.TRAILING_STATES-1);
                testExplosionEventShort_assertEvent(0);
                testExplosionEventShort_assertEvent(1);
                
                
                // todo also test time between fire and explosion > TRAILING_STATE_DELAY
        }
        
        private void testExplosionEventLong_assertEvent(int state)
        {
                int events = 0;
                for (EventPublic e : env.eventIterable())
                {
                        ++events;
                        if (e instanceof ProjectileExplosionPublic)
                        {
                                ProjectileExplosionPublic ev = (ProjectileExplosionPublic) e;
                                assert ev.hasOccurred(state);
                                assertEquals(ACTOR_FIRST, ev.getFireActor(state));
                                assertEquals(ACTOR_SECOND, ev.getHitActor(state));
                                assertEquals(21, ev.getOccurredAt(state));
                                
                                PhysicsPoint pos = new PhysicsPoint();
                                ev.getPosition(state, pos);
                                assertPointEquals(385664, 90, pos);
                                
                                ev.getProjectile(state).getHistoricPosition(pos, ev.getOccurredAt(state), true);
                                assertPointEquals(385664, 90, pos);
                        }
                        else if (e instanceof ActorDiedPublic)
                        {
                                ActorDiedPublic ev = (ActorDiedPublic) e;
                                assert ev.hasOccurred(state);
                                assertEquals(21, ev.getOccurredAt(state));
                                assertEquals(ACTOR_SECOND, ev.getDied(state));
                                assert ev.getCause(state) instanceof ProjectileExplosionPublic;
                        }
                        else
                        {
                                assert false;
                        }
                }
                
                assertEquals(2, events);
        }
        
        @Test
        public void testExplosionEventLong()
        {
                // Long time between fire and explosion (more than TRAILING_STATE_DELAY)
                try
                {
                        List<Object> yamlDocuments = GameConfig.loadYaml(""
                                + "- weapon-projectiles: 1\n"
                                + "  projectile-hit-ship: true\n"
                                + "  projectile-angle-relative: true\n"
                                + "  projectile-speed: 20000\n"
                                + "  projectile-damage: 2000\n"
                                + "  ship-energy: 1500\n"
                        );
                        env.loadConfig(env.getTick() - env.econfig.HIGHEST_DELAY, "test", yamlDocuments);
                }
                catch (Exception ex)
                {
                        throw new Error(ex);
                }
                
                
                env.actorNew(1, ACTOR_FIRST, 1234, "warbird");
                env.actorWarp(1, ACTOR_FIRST, false, 1000, 90, 0, 0, EnvironmentConf.ROTATION_1_4TH);
                env.actorNew(1, ACTOR_SECOND, 4321, "warbird");
                env.actorWarp(1, ACTOR_SECOND, false, 400000, 90, 0, 0, 0);
                
                env.actorWeapon(2, ACTOR_FIRST, WEAPON_SLOT.GUN, false, 0, 0, 0 , 0, 0);
                
                // modify this test case if TRAILING_STATE_DELAY changes
                assert env.econfig.TRAILING_STATE_DELAY == 32; 
                
                while (env.getTick() < 38)
                {
                        env.tick();
                }
                
                testExplosionEventLong_assertEvent(0);
                env.timewarp(1);
                testExplosionEventLong_assertEvent(0);
                
                while (env.getTick() < env.econfig.TRAILING_STATE_DELAY + 38)
                {
                        env.tick();
                }
                
                testExplosionEventLong_assertEvent(0);
                testExplosionEventLong_assertEvent(1);
                env.timewarp(1);
                testExplosionEventLong_assertEvent(0);
                testExplosionEventLong_assertEvent(1);
                env.timewarp(env.econfig.TRAILING_STATES-1);
                testExplosionEventLong_assertEvent(0);
                testExplosionEventLong_assertEvent(1);
        }
}
