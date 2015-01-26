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
 */

package aphelion.shared.physics;

import aphelion.shared.event.ManualClockSource;
import aphelion.shared.event.TickedEventLoop;
import aphelion.shared.gameconfig.GameConfig;
import static aphelion.shared.physics.PhysicsTest.MOVE_UP;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.events.pub.ActorDiedPublic;
import aphelion.shared.physics.events.pub.EventPublic;
import aphelion.shared.physics.events.pub.ProjectileExplosionPublic;
import aphelion.shared.physics.operations.ActorWeaponFire;
import aphelion.shared.physics.operations.Operation;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsPositionVector;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author Joris
 */
public class DualRunnerEnvironmentTest extends PhysicsTest
{
        protected TickedEventLoop loop;
        protected ManualClockSource clock;
                
        @Override
        protected void createEnvironment()
        {
                clock = new ManualClockSource(EnvironmentConf.TICK_LENGTH);
                loop = new TickedEventLoop(EnvironmentConf.TICK_LENGTH, 0, clock);
                env = new DualRunnerEnvironment(loop, new MapEmpty());
                loop.addTickEvent((DualRunnerEnvironment) env);
        }

        @Override
        public void tearDown()
        {
                DualRunnerEnvironment env = (DualRunnerEnvironment) this.env;
                env.done(); // joins
                
                try
                {
                        Field threadField = DualRunnerEnvironment.class.getDeclaredField("thread");
                        threadField.setAccessible(true);
                        assert ! ((Thread) threadField.get(env)).isAlive();
                }
                catch (Exception ex)
                {
                        throw new Error(ex);
                }
                
                loop.breakdown();
                loop = null;
                clock = null;
                super.tearDown();
        }
        
        private void loopSetup()
        {
                loop.setup();
                loop.loop();
                clock.advanceHalfTick();
                //thread will start on the first tick!
        }
        
        @Test(timeout=10000)
        public void testMoveConsistency1() throws InterruptedException
        {
                DualRunnerEnvironment env = (DualRunnerEnvironment) this.env;
                // Test multiple move's received in the past (but still in order)

                loopSetup();
                
                env.actorNew(0, 1, 1234, "Warbird");
                env.actorWarp(0, 1, false, 0, 0, 1000, 1001, EnvironmentConf.ROTATION_1_2TH);
                ActorPublic actor = env.getActor(1);

                clock.advanceTick(20);
                loop.loop(); // now at tick 20
                assertEquals(20, env.getTick());
                

                env.actorMove(10, 1, MOVE_UP);
                env.actorMove(15, 1, MOVE_UP);

                // make sure the thread catches up
                assertEquals(5, env.waitForThreadParsedOperations(5));
                assertEquals(20, env.waitForThreadToCatchup());
                
                // Ensure a timewarp can happen (might need to update this if the state length changes)
                clock.advanceTick(30);
                loop.loop(); // now at tick 50
                assertEquals(50, env.getTick());
                
                // make sure the thread catches up
                assertEquals(50, env.waitForThreadToCatchup());
                
                assertEquals(0, env.getTimewarpCount()); // no need for a timewarp, there is a faster solution
                
                assertVelocity(1000, 1001 + 28 * 2, actor);
                assertPosition(
                        50 * 1000,
                        50 * 1001 + (50-10) * 28 + (50-15) * 28,
                        actor);
        }
        
        @Test(timeout=10000)
        public void testResetConsistency() throws InterruptedException, NoSuchFieldException, IllegalAccessException
        {
                DualRunnerEnvironment env = (DualRunnerEnvironment) this.env;
                
                loopSetup();
                
                env.actorNew(0, 1, 1234, "Warbird");
                env.actorWarp(0, 1, false, 1000, 2000, 0, 0, EnvironmentConf.ROTATION_1_2TH);
                env.actorWeapon(2, 1, WEAPON_SLOT.GUN, false, 0, 0, 0, 0, 0);
                
                // Execute the 3 operations in both threads
                clock.advanceTick(2);
                loop.loop();
                assertEquals(2, env.waitForThreadToCatchup());
                
                int offsetY = conf.getInteger("projectile-offset-y").get();
                int fireSpeed = conf.getInteger("projectile-speed").get();
                
                // Verify operation history
                Field fireHistoryField = ActorWeaponFire.class.getDeclaredField("fireHistories");
                PhysicsPositionVector opFireHistory = new PhysicsPositionVector();
                fireHistoryField.setAccessible(true);
                LinkedListEntry<Operation> opLink = env.environment.trailingStates[0].history.first; // actor new
                opLink = opLink.next; // actor warp
                opLink = opLink.next; // actor weapon
                ActorWeaponFire opWeaponA = (ActorWeaponFire) opLink.data;
                assertNotNull(opWeaponA);
                opFireHistory.set(((ArrayList<PhysicsPositionVector[]>) fireHistoryField.get(opWeaponA)).get(0)[0]);
                
                // Position history before reset
                assertPointEquals(1000, 2000 + offsetY, opFireHistory.pos);
                assertPointEquals(0, fireSpeed, opFireHistory.vel);
                
                // Verify the actor reference does not changed (comparison follows after reset)
                ActorPublic actorA = env.getActor(1, false);
                assertNotNull(actorA);
                
                
                // Verify the projectile reference does not changed (comparison follows after reset)
                Iterator<ProjectilePublic> it = env.projectileIterator();
                assertTrue(it.hasNext());

                PhysicsPositionVector pos = new PhysicsPositionVector();
                assertTrue(it.hasNext());
                ProjectilePublic projA = it.next();
                assertFalse(it.hasNext());
                projA.getPosition(pos);
                
                // Projectile location at tick 2, before reset
                assertEquals(1000, pos.pos.x);
                assertEquals(2000 + offsetY, pos.pos.y);
                assertEquals(0, pos.vel.x);
                assertEquals(fireSpeed, pos.vel.y);
                
                it = null;
                
                
                // weapon fire was at tick 2, this should cause can inconsistency
                env.actorMove(1, 1, MOVE_UP);
                
                // Ensure a timewarp can happen
                clock.advanceTick(env.econfig_thread.TRAILING_STATE_DELAY);
                loop.loop();
                assertEquals(2 + env.econfig_thread.TRAILING_STATE_DELAY, env.waitForThreadToCatchup());
                assertEquals(1, env.getTimewarpCount());
                assertEquals(1, env.tryResetStateNow());
                
                // Verify the actor reference does not changed (this is what ActorKey is for)
                ActorPublic actorB = env.getActor(1, false);
                assertNotNull(actorB);
                assert actorA == actorB;
                
                // Verify actor position after reset
                assertPosition(1000, 2000 + ((int) env.getTick() - 1) * 28, actorB);
                
                // Verify the projectile reference did not change (this is what ProjectileKey is for)
                it = env.projectileIterator();
                assertTrue(it.hasNext());
                ProjectilePublic projB = it.next();
                assertFalse(it.hasNext());
                assert projA == projB; 
                projB.getPosition(pos);
                
                // Verify projectile position after the reset
                assertEquals(1000, pos.pos.x);
                assertEquals(2000 + offsetY + 28 // ship position
                        + (fireSpeed + 28) * ((int) env.getTick() - 2),
                        pos.pos.y
                );
                assertEquals(0, pos.vel.x);
                assertEquals(fireSpeed + 28, pos.vel.y);
                
                
                // Verify the operation reference does not change, and
                // Was the operation history changed properly?
                opLink = env.environment.trailingStates[0].history.first; // actor new
                opLink = opLink.next; // actor warp
                opLink = opLink.next; // actor move
                opLink = opLink.next; // actor weapon
                ActorWeaponFire opWeaponB = (ActorWeaponFire) opLink.data;
                assertNotNull(opWeaponB);
                assert opWeaponA == opWeaponB;
                
                opFireHistory.set(((ArrayList<PhysicsPositionVector[]>) fireHistoryField.get(opWeaponB)).get(0)[0]);
                assertPointEquals(1000, 2000 + offsetY + 28, opFireHistory.pos);
                assertPointEquals(0, fireSpeed + 28, opFireHistory.vel);
        }
        
        private ProjectileExplosionPublic testExplosionEventLong_explosion;
        private ActorDiedPublic testExplosionEventLong_died;
        private void testExplosionEventLong_assertEvent(boolean afterReset)
        {
                DualRunnerEnvironment env = (DualRunnerEnvironment) this.env;
                int events = 0;
                for (EventPublic e : env.eventIterable())
                {
                        ++events;
                        if (e instanceof ProjectileExplosionPublic)
                        {
                                ProjectileExplosionPublic ev = (ProjectileExplosionPublic) e;
                                
                                // The reference should not change
                                if (testExplosionEventLong_explosion != null)
                                {
                                        assert testExplosionEventLong_explosion == ev;
                                }
                                testExplosionEventLong_explosion = ev;
                                
                                assert ev.hasOccurred(0);
                                assertEquals(ACTOR_FIRST, ev.getFireActor(0));
                                assertEquals(ACTOR_SECOND, ev.getHitActor(0));
                                assertEquals(afterReset ? 29 : 24, ev.getOccurredAt(0));
                                
                                PhysicsPoint pos = new PhysicsPoint();
                                ev.getPosition(0, pos);
                                assertPointEquals(afterReset ? 485664 : 385664, 90, pos);

                                PhysicsPositionVector posv = new PhysicsPositionVector();

                                // The projectile is removed at the moment of impact, it no longer has a position
                                assertFalse(ev.getProjectile(0).getHistoricPosition(posv, ev.getOccurredAt(0), true));
                                assertFalse(ev.getProjectile(0).getHistoricPosition(posv, ev.getOccurredAt(0), false));
                                assertTrue(ev.getProjectile(0).getHistoricPosition(posv, ev.getOccurredAt(0) - 1, true));
                                assertPointEquals(afterReset ? 475336 : 375336, 90, posv.pos);
                                assertTrue(ev.getProjectile(0).getHistoricPosition(posv, ev.getOccurredAt(0) - 1, false));
                                assertPointEquals(afterReset ? 475336 : 375336, 90, posv.pos);
                        }
                        else if (e instanceof ActorDiedPublic)
                        {
                                ActorDiedPublic ev = (ActorDiedPublic) e;
                                
                                // The reference should not change
                                if (testExplosionEventLong_died != null)
                                {
                                        assert testExplosionEventLong_died == ev;
                                }
                                testExplosionEventLong_died = ev;
                                
                                assertTrue(ev.hasOccurred(0));
                                assertEquals(afterReset ? 29 : 24, ev.getOccurredAt(0));
                                assertEquals(ACTOR_SECOND, ev.getDied(0));
                                assertEquals(testExplosionEventLong_explosion, ev.getCause(0));
                        }
                        else
                        {
                                assert false;
                        }
                }
                
                assertEquals(2, events);
        }
        
        @Test
        public void testExplosionEventLong() throws InterruptedException
        {
                DualRunnerEnvironment env = (DualRunnerEnvironment) this.env;
                
                testExplosionEventLong_explosion = null;
                testExplosionEventLong_died = null;
                
                loopSetup();
                
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
                        env.loadConfig(env.getTick() - env.getConfig().HIGHEST_DELAY, "test", yamlDocuments);
                }
                catch (Exception ex)
                {
                        throw new Error(ex);
                }
                
                
                env.actorNew(1, ACTOR_FIRST, 1234, "warbird");
                env.actorWarp(1, ACTOR_FIRST, false, 1000, 90, 0, 0, EnvironmentConf.ROTATION_1_4TH);
                env.actorNew(1, ACTOR_SECOND, 4321, "warbird");
                env.actorWarp(1, ACTOR_SECOND, false, 400000, 90, 0, 0, 0);
                
                env.actorWeapon(5, ACTOR_FIRST, WEAPON_SLOT.GUN, false, 0, 0, 0 , 0, 0);
                
                clock.advanceTick(2);
                loop.loop();
                assertEquals(2, env.waitForThreadToCatchup());
                assertEquals(7, env.waitForThreadParsedOperations(7));
                
                // modify this test case if TRAILING_STATE_DELAY changes
                assert env.econfig_thread.TRAILING_STATE_DELAY == 32; 
                
                
                clock.advanceTick(36);
                loop.loop();
                assertEquals(38, env.waitForThreadToCatchup());
        
                
                testExplosionEventLong_assertEvent(false);
                
                // Should cause an inconsistency (event hits somewhere else now)
                env.actorWarp(2, ACTOR_SECOND, false, 500000, 90, 0, 0, 0);
                assertEquals(8, env.waitForThreadParsedOperations(8));
                
                clock.advanceTick(env.econfig_thread.TRAILING_STATE_DELAY);
                loop.loop();
                assertEquals(38 + env.econfig_thread.TRAILING_STATE_DELAY, env.waitForThreadToCatchup());
                assertEquals(1, env.getTimewarpCount());
                assertEquals(1, env.tryResetStateNow());
                
                testExplosionEventLong_assertEvent(true);
        }
}
