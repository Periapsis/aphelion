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
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.swissarmyknife.EvenSteinhausJohnsonTrotterIterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 * @author Joris
 */
public class ConsistencyFuzz
{
        private static interface Command
        {
                void execute();
        }
        List<Object> yamlDocuments;
        private SimpleEnvironment env;
        private ConfigSelection conf;
        private final Command tick = new Command()
        {
                public void execute()
                {
                        env.tick();
                }
        };

        public ConsistencyFuzz()
        {
                try
                {
                        yamlDocuments = GameConfig.loadYaml(
                                "- ship-radius: 14336\n"
                                + "  ship-bounce-friction: 600\n"
                                + "  ship-bounce-friction-other-axis: 900 \n"
                                + "  ship-rotation-speed: 3800000 \n"
                                + "  ship-rotation-points: 40\n"
                                + "  ship-speed: 2624\n"
                                + "  ship-thrust: 28\n"
                                + "  ship-boost-speed: 1000\n"
                                + "  ship-boost-thrust: 120\n"
                                + "  ship-boost-energy: 2500000\n"
                                + "  ship-energy: 10000\n"
                                + "  ship-recharge: 1\n"
                                + "  ship-spawn-x: 13\n"
                                + "  ship-spawn-y: 8\n"
                                + "  ship-respawn-delay: 3\n"
                                + "  weapon-fire-energy: 1000\n"
                                + "  weapon-fire-delay: 5\n"
                                + "  weapon-projectiles: 1\n"
                                + "  projectile-offset-y: 14336\n"
                                + "  projectile-bounce-friction: 1024\n"
                                + "  projectile-bounce-friction-other-axis: 1024 \n"
                                + "  projectile-speed: 20000\n"
                                + "  projectile-speed-relative: true\n"
                                + "  projectile-angle-relative: true\n"
                                + "  projectile-expiration-ticks: 1000\n"
                                + "  projectile-damage: 5000\n"
                                + "  projectile-emp-ticks: 5\n"
                                + "");
                }
                catch (Exception ex)
                {
                        throw new Error(ex);
                }
        }

        private ConfigSelection applyTestSettings(PhysicsEnvironment env)
        {
                env.loadConfig(env.getTick() - env.getConfig().HIGHEST_DELAY, "test", yamlDocuments);
                return env.newConfigSelection();
        }

        @Test
        @Ignore
        public void testWeapon()
        {
                // O(n!)
                Command[] commands = new Command[]
                {
                        // tick 0
                        new Command(){public void execute(){ env.actorNew(1, 1, 357436389, "warbird"); }},
                        new Command(){public void execute(){ env.actorWarp(2, 1, false, 200000, 150000, -1000, 0, EnvironmentConf.ROTATION_1_4TH); }},
                        new Command(){public void execute(){ env.actorMove(3, 1, PhysicsMovement.get(false, true, false, true, true)); }},
                        new Command(){public void execute(){ env.actorMove(4, 1, PhysicsMovement.get(true, false, false, false, false)); }},
                        new Command(){public void execute(){ env.actorMove(10, 1, PhysicsMovement.get(true, true, false, false, true)); }},
                        new Command(){public void execute(){ env.actorMove(16, 1, PhysicsMovement.get(false, true, false, true, true)); }},
                        new Command(){public void execute(){ env.actorMove(20, 1, PhysicsMovement.get(true, true, true, true, true)); }},
                        new Command(){public void execute(){ env.actorMove(3, 1, PhysicsMovement.get(false, true, true, true, true)); }},
                };

                
                
                EvenSteinhausJohnsonTrotterIterator it = new EvenSteinhausJohnsonTrotterIterator(commands.length);
                int iterations = 1;
                for (int i = 1; i <= commands.length; ++i)
                {
                        iterations *= i;
                }
                
                Logger.getLogger("aphelion.shared.physics").setLevel(Level.OFF); // speed up execution
                
                int iteration = 0;
                long startNano = System.nanoTime();
                while (it.hasNext())
                {
                        ++iteration;
                        
                        env = new SimpleEnvironment(false, new MapEmpty());
                        conf = applyTestSettings(env);
                        
                        testWeapon_doTest(commands, it.next());
                        
                        if (iteration % 10000 == 0)
                        {
                                long now = System.nanoTime();
                                // (now - startNano) / iteration * (iterations - iteration)
                                
                                System.out.printf("Progress: %d of %d. Seconds left: %f\n", 
                                        iteration, 
                                        iterations,
                                        (now - startNano) / iteration * (iterations - iteration) / 1_000_000_000.0);
                        }
                }
                
                Logger.getGlobal().setLevel(Level.ALL);
        }
        
        private void testWeapon_doTest(Command[] commands, int[] order)
        {
                while (env.tick_now < 16)
                {
                        env.tick();
                }
                
                for (int i = 0; i < order.length; ++i)
                {
                        commands[order[i]].execute();
                }
                
                // this stuff needs to be adjusted if any of the constants in PhysicsEnvironment change
                while (env.tick_now < 16 * 2)
                {
                        env.tick();
                }
                
                assertPosition(0, 1, 170353,149891, -999, -6, 191383600);
                assertEquals(240030, env.getActor(1, 0, false).getEnergy());
                
                assertPosition(1, 1, 186337, 149987, -1091, -8, 191383600);
                assertEquals(2740014, env.getActor(1, 1, false).getEnergy());
                
                assert env.consistencyCheck();
        }

        private void assertPosition(int stateid, int pid, int x, int y, int x_vel, int y_vel, int rot)
        {
                ActorPublic actor = env.getActor(pid, stateid, false);
                PhysicsShipPosition pos = new PhysicsShipPosition();
                assertTrue(actor.getPosition(pos));

                if (x != pos.x || y != pos.y)
                {
                        throw new AssertionError("expected position:<" + x + "," + y + "> but was:<" + pos.x + "," + pos.y + ">");
                }

                if (x_vel != pos.x_vel || y_vel != pos.y_vel)
                {
                        throw new AssertionError("expected velocity:<" + x_vel + "," + y_vel + "> but was:<" + pos.x_vel + "," + pos.y_vel + ">");
                }
                assertEquals(rot, pos.rot);
                
                assertTrue(actor.getHistoricPosition(pos, env.getTick(stateid), false));

                if (x != pos.x || y != pos.y)
                {
                        throw new AssertionError("getHistoricPosition(0) is not equal to the current position!");
                }

                if (x_vel != pos.x_vel || y_vel != pos.y_vel)
                {
                        throw new AssertionError("getHistoricPosition(0) is not equal to the current velocity!");
                }

                assertEquals(rot, pos.rot);
        }

        
}
