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

package aphelion.shared.physics;

import aphelion.shared.event.ManualClockSource;
import aphelion.shared.event.TickedEventLoop;
import static aphelion.shared.physics.PhysicsTest.MOVE_UP;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.entities.ProjectilePublic;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
                clock = new ManualClockSource(TimeUnit.MILLISECONDS.toNanos(EnvironmentConf.TICK_LENGTH));
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
        public void testWeaponFireConsistency() throws InterruptedException
        {
                DualRunnerEnvironment env = (DualRunnerEnvironment) this.env;
                
                loopSetup();
                
                env.actorNew(0, 1, 1234, "Warbird");
                env.actorWarp(0, 1, false, 1000, 2000, 0, 0, EnvironmentConf.ROTATION_1_2TH);
                env.actorWeapon(2, 1, WEAPON_SLOT.GUN, false, 0, 0, 0, 0, 0);
                
                clock.advanceTick(2);
                loop.loop();
                assertEquals(2, env.waitForThreadToCatchup());
                
                int offsetY = conf.getInteger("projectile-offset-y").get();
                int fireSpeed = conf.getInteger("projectile-speed").get();
                
                Iterator<ProjectilePublic> it = env.projectileIterator();
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
                clock.advanceTick(env.econfig_thread.TRAILING_STATE_DELAY);
                loop.loop();
                assertEquals(2 + env.econfig_thread.TRAILING_STATE_DELAY, env.waitForThreadToCatchup());
                assertEquals(1, env.getTimewarpCount());
                
                assertPosition(1000, 2000 + ((int) env.getTick() - 1) * 28, env.getActor(1));
                
                it = env.projectileIterator();
                assertTrue(it.hasNext());
                proj = it.next();
                assertFalse(it.hasNext());
                proj.getPosition(pos);
                
                assertEquals(1000, pos.x);
                assertEquals(2000 + offsetY + 28 // ship position
                        + (fireSpeed + 28) * env.econfig_thread.TRAILING_STATE_DELAY, // the distance the projectile has traveled
                        pos.y
                );
                
                assertEquals(0, pos.x_vel);
                assertEquals(fireSpeed + 28, pos.y_vel);
        }
}
