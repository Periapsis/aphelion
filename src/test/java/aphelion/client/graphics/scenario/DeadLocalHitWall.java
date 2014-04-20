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

package aphelion.client.graphics.scenario;

import aphelion.client.graphics.Scenario;
import static aphelion.client.graphics.Scenario.ACTOR_2;
import static aphelion.client.graphics.Scenario.ACTOR_LOCAL;
import aphelion.shared.physics.EnvironmentConf;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.PhysicsMap;
import aphelion.shared.physics.WEAPON_SLOT;
import aphelion.shared.physics.valueobjects.PhysicsMovement;



/**
 *
 * @author Joris
 */
public class DeadLocalHitWall extends Scenario
{
        @Override
        protected String getConfig()
        {
                return null;
        }

        @Override
        protected void setup()
        {
                actorNew(0, ACTOR_2, 1234, "warbird");
                actorWarp(0, 0, ACTOR_2, false, 508 * PhysicsMap.TILE_PIXELS, 500 * PhysicsMap.TILE_PIXELS, -3000, 0, EnvironmentConf.ROTATION_1_4TH);
                
                actorNew(0, ACTOR_LOCAL, 5678, "warbird");
                actorWarp(0, 0, ACTOR_LOCAL, false, 517 * PhysicsMap.TILE_PIXELS, 500 * PhysicsMap.TILE_PIXELS, -3000, 0, EnvironmentConf.ROTATION_1_2TH);
                
                actorWeapon(0, 100, ACTOR_2, WEAPON_SLOT.GUN);
        }
}
