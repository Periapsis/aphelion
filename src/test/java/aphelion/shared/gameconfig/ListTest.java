/*
 * Aphelion
 * Copyright (c) 2015  Joris van der Wel
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

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ListTest
{
        private ConfigSelection selection;

        @Before
        public void before()
        {
                GameConfig config = new GameConfig();

                String doc = "";
                doc += "- abc: 5\n";
                doc += "  def: [5,10,-3]\n";
                doc += "  ghj: [~REPEAT, 4, 5]\n";
                doc += "  klm: [~REPEAT_ALL, 4, 5, 6]\n";
                doc += "  nop: [~LINEAR, 100, 200, 210]\n";
                doc += "  qrs: [~LINEAR, 5, 3]\n";
                List<Object> yamlDoc = GameConfig.loadYaml(doc);
                config.addFromYaml(yamlDoc, "example.yaml");
                config.applyChanges();

                selection = config.newSelection();
        }

        @Test
        public void testSingle()
        {
                GCIntegerList list = selection.getIntegerList("abc");
                assertEquals(5, list.get(0));
        }

        @Test
        public void testMultiple()
        {
                GCIntegerList list = selection.getIntegerList("def");
                assertEquals( 5, list.get(0));
                assertEquals(10, list.get(1));
                assertEquals(-3, list.get(2));
        }

        @Test
        public void testDefaultFunction()
        {
                // REPEAT
                GCIntegerList list = selection.getIntegerList("abc");
                assertEquals(5, list.get(0));
                assertEquals(5, list.get(1));
                assertEquals(5, list.get(2));
        }

        @Test
        public void testRepeat()
        {
                GCIntegerList list = selection.getIntegerList("ghj");
                assertEquals(4, list.get(0));
                assertEquals(5, list.get(1));
                assertEquals(5, list.get(2));
                assertEquals(5, list.get(3));
        }

        @Test
        public void testRepeatAll()
        {
                GCIntegerList list = selection.getIntegerList("klm");
                assertEquals(4, list.get(0));
                assertEquals(5, list.get(1));
                assertEquals(6, list.get(2));

                assertEquals(4, list.get(3));
                assertEquals(5, list.get(4));
                assertEquals(6, list.get(5));

                assertEquals(4, list.get(6));
                assertEquals(5, list.get(7));
                assertEquals(6, list.get(8));
        }

        @Test
        public void testLinear()
        {
                GCIntegerList list = selection.getIntegerList("nop");
                // [100, 200, 210]
                assertEquals(100, list.get(0));
                assertEquals(200, list.get(1));
                assertEquals(210, list.get(2));
                assertEquals(220, list.get(3));
                assertEquals(230, list.get(4));
                assertEquals(240, list.get(5));

                list = selection.getIntegerList("qrs");
                assertEquals( 5, list.get(0));
                assertEquals( 3, list.get(1));
                assertEquals( 1, list.get(2));
                assertEquals(-1, list.get(3));
                assertEquals(-3, list.get(4));
                assertEquals(-5, list.get(5));
        }

        // todo test random and shuffle
}
