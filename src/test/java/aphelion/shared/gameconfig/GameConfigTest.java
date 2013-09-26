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
package aphelion.shared.gameconfig;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Joris
 */
public class GameConfigTest
{
        public GameConfigTest()
        {
        }

        @Test
        public void test() throws Exception
        {
                GameConfig config = new GameConfig();
                
                String doc = "";
                doc += "- selector:\n";
                doc += "        ship: [warbird, javelin]\n";
                doc += "  projectile-speed: 5000\n";
                doc += "  max-ship-speed: 2100\n";
                
                doc += "- projectile-speed: 4000\n";
                List<Object> yamlDoc = GameConfig.loadYaml(doc);
                
                ConfigSelection warbirdSelection = config.newSelection();
                warbirdSelection.selection.setShip("warbird");
                
                ConfigSelection globalSelection = config.newSelection();
                
                GCInteger projectileSpeed = warbirdSelection.getInteger("projectile-speed");
                assertFalse(projectileSpeed.isSet());
                
                config.addFromYaml(yamlDoc, "example.yaml");
                config.applyChanges();
                
                GCInteger maxShipSpeed = warbirdSelection.getInteger("max-ship-speed");
                
                assertTrue(maxShipSpeed.isSet());
                assertEquals(2100, maxShipSpeed.get());
                assertTrue(projectileSpeed.isSet());
                assertEquals(5000, projectileSpeed.get());
                
                
                maxShipSpeed = globalSelection.getInteger("max-ship-speed");
                assertFalse(maxShipSpeed.isSet());
                
                projectileSpeed = globalSelection.getInteger("projectile-speed");
                assertTrue(projectileSpeed.isSet());
                assertEquals(4000, projectileSpeed.get());
                
                config.unloadFile("example.yaml");
                config.applyChanges();
                assertFalse(projectileSpeed.isSet());
                assertFalse(warbirdSelection.getInteger("projectile-speed").isSet());
                
                // make sure that if an existing rule is changed, no duplicates are added
                config.addFromYaml(yamlDoc, "example.yaml");
                config.addFromYaml(yamlDoc, "example.yaml");
                config.addFromYaml(yamlDoc, "example.yaml");
                config.applyChanges();
                
                // assert only 2 rules
                assertTrue(config.rules.first.next.next == null);
        }
}