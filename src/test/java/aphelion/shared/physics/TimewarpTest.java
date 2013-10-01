/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package aphelion.shared.physics;


import aphelion.shared.gameconfig.GameConfig;
import static aphelion.shared.physics.PhysicsEnvironmentTest.MOVE_UP;
import aphelion.shared.physics.entities.ProjectilePublic;
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
                env.actorNew(1, ACTOR_FIRST, "Bla", 1234, "warbird");
                env.actorWarp(1, ACTOR_FIRST, false, 1000, 90, 0, 0, 0);
                
                env.timewarp(1); // todo list should remain intact
                env.tick(); // tick 1, it should now create the actor
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 0, false));
                
                env.timewarp(1); // the actor is not yet present in state 1, it should recreate him in state 0
                env.timewarp(1);
                env.timewarp(1);
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 0, false));
                
                while(env.getTick() < PhysicsEnvironment.TRAILING_STATE_DELAY)
                {
                        env.tick();
                }
                
                env.tick(); // should create the actor at this tick in state 1
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 0, false));
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 1, false));
                env.timewarp(1);
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 0, false));
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 1, false));
                env.timewarp(env.TRAILING_STATES-1);
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 0, false));
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 1, false));
        }
        
        @Test
        public void testActorDestruction()
        {
                env.actorNew(1, ACTOR_FIRST, "Bla", 1234, "warbird");
                env.actorRemove(PhysicsEnvironment.TRAILING_STATE_DELAY + 3, ACTOR_FIRST);
                
                env.tick();
                assertActorExists(env.getActor(ACTOR_FIRST, 0, false));
                assertActorNotExists(env.getActor(ACTOR_FIRST, 1, false));
                
                while(env.getTick() < PhysicsEnvironment.TRAILING_STATE_DELAY)
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
                
                env.timewarp(env.TRAILING_STATES-1);
                assertActorNotExists(env.getActor(ACTOR_FIRST, 0, false));
                assertNotNull(env.getActor(ACTOR_FIRST, 1, false));
        }
        
        @Test
        public void testConfigChange()
        {
                env.actorNew(1, ACTOR_FIRST, "Bla", 1234, "warbird");
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
                
                env.timewarp(env.TRAILING_STATES-1);
                assertEquals(1000, env.getGlobalConfigInteger(0, "ship-thrust").get());
                assertEquals(28, env.getGlobalConfigInteger(1, "ship-thrust").get());
                assertVelocity(0, -3624, env.getActor(ACTOR_FIRST, 0, false));
        }
        
        private void testProjectileCreation_assertSingleProj(int state, int x, int y)
        {
                int count = 0;
                for (ProjectilePublic proj : env.projectileIterable(state))
                {
                        ++count;
                        assertPosition(x, y, proj);
                }

                assertEquals(1, count);
        }
        
        @Test
        public void testProjectileCreation()
        {
                env.actorNew(1, ACTOR_FIRST, "Bla", 1234, "warbird");
                env.actorWarp(1, ACTOR_FIRST, false, 1000, 90, 0, 0, 0);
                env.actorWeapon(2, ACTOR_FIRST, WEAPON_SLOT.GUN, false, 0, 0, 0 ,0 ,0);
                
                env.tick(); // 1
                env.tick(); // 2
                
                testProjectileCreation_assertSingleProj(0, 1000, -14246);
                
                env.timewarp(1);
                testProjectileCreation_assertSingleProj(0, 1000, -14246);
                
                env.timewarp(env.TRAILING_STATES-1);
                testProjectileCreation_assertSingleProj(0, 1000, -14246);
                
                
                
                while(env.getTick() < PhysicsEnvironment.TRAILING_STATE_DELAY)
                {
                        env.tick();
                }
                
                env.tick();
                env.tick();
                
                testProjectileCreation_assertSingleProj(0, 1000, -96166);
                testProjectileCreation_assertSingleProj(1, 1000, -14246);
                
                env.timewarp(1);
                testProjectileCreation_assertSingleProj(0, 1000, -96166);
                testProjectileCreation_assertSingleProj(1, 1000, -14246);
                
                env.timewarp(env.TRAILING_STATES-1);
                testProjectileCreation_assertSingleProj(0, 1000, -96166);
                testProjectileCreation_assertSingleProj(1, 1000, -14246);
        }
}
