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
package aphelion.client.graphics.scenario;

import aphelion.client.graphics.Scenario;
import static aphelion.client.graphics.Scenario.ACTOR_LOCAL;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.PhysicsMap;
import aphelion.shared.physics.valueobjects.PhysicsMovement;

/**
 *
 * @author Joris
 */
public class HighSpeedTurning500msLag extends Scenario
{

        @Override
        protected String getConfig()
        {
                return "- selector: {ship: warbird}\n" +
                        "  ship-speed: 5000\n" +
                        "  ship-thrust: 50";
                
                
        }

        @Override
        protected void setup()
        {
                actorNew(0, ACTOR_LOCAL, 1234, "warbird");
                actorWarp(0, 0, ACTOR_LOCAL, false, 512 * PhysicsMap.TILE_PIXELS, 512 * PhysicsMap.TILE_PIXELS, 0, 0, PhysicsEnvironment.ROTATION_1_4TH);
                
                actorNew(0, ACTOR_2, 1234, "warbird");
                actorWarp(0, 0, ACTOR_2, false, 512 * PhysicsMap.TILE_PIXELS, 512 * PhysicsMap.TILE_PIXELS, 0, 0, PhysicsEnvironment.ROTATION_1_4TH);
                
                schedule(0);
                schedule(100);
        }
        
        @Override
        public void tick(long unused)
        {
                if (env.getTick() < 1000)
                {
                        // schedule the next 100 ticks
                        if (env.getTick() % 100 == 0)
                        {
                                schedule(env.getTick() + 100);
                        }
                }
                
                super.tick(unused);
        }
        
        private void schedule(long tick)
        {
                for (long t = tick; t < tick + 100; ++t)
                {
                        actorMove(0, t, ACTOR_LOCAL, PhysicsMovement.get(true, false, false, true, false));
                        
                        // execute all moves 50 ticks late (as if there is 500ms of latency between 2 players), 
                        // and execute them in batches of 5 ticks
                        long t_late = t / 5 * 5 + 50;
                        actorMove(t_late, t, ACTOR_2, PhysicsMovement.get(true, false, false, true, false));
                }
        }
}
