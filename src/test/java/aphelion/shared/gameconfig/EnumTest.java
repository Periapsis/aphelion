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
import static org.junit.Assert.*;

import aphelion.shared.gameconfig.enums.GCFunction2D;
import java.util.List;

public class EnumTest
{
        private ConfigSelection selection;

        @Before
        public void before()
        {
                GameConfig config = new GameConfig();

                String doc = "";
                doc += "- foo: ABSOLUTE\n";
                doc += "  bar: LINEAR\n";
                doc += "  baz: qwerty\n";
                List<Object> yamlDoc = GameConfig.loadYaml(doc);
                config.addFromYaml(yamlDoc, "example.yaml");
                config.applyChanges();

                selection = config.newSelection();
        }

        @Test
        public void testGoodValue() throws Exception
        {
                GCEnum<GCFunction2D> value = selection.getEnum(GCFunction2D.resolver, "foo");

                assertEquals(GCFunction2D.ABSOLUTE, value.get());
                assertTrue(value.isSet());

                value = selection.getEnum(GCFunction2D.resolver, "bar");

                assertEquals(GCFunction2D.LINEAR, value.get());
                assertTrue(value.isSet());
        }

        @Test
        public void testMissingValue() throws Exception
        {
                GCEnum<GCFunction2D> value = selection.getEnum(GCFunction2D.resolver, "thisismissing");
                assertEquals(GCFunction2D.ABSOLUTE, value.get());
                assertFalse(value.isSet());
        }

        @Test
        public void testUnknownValue() throws Exception
        {
                GCEnum<GCFunction2D> value = selection.getEnum(GCFunction2D.resolver, "baz");
                assertEquals(GCFunction2D.ABSOLUTE, value.get());
                assertTrue(value.isSet());
        }

        @Test
        public void testListGoodValue() throws Exception
        {
                GCEnumList<GCFunction2D> value = selection.getEnumList(GCFunction2D.resolver, "foo");
                assertEquals(GCFunction2D.ABSOLUTE, value.get(0));
                assertTrue(value.isSet());

                value = selection.getEnumList(GCFunction2D.resolver, "bar");
                assertEquals(GCFunction2D.LINEAR, value.get(0));
                assertTrue(value.isSet());
        }

        @Test
        public void testListMissingValue() throws Exception
        {
                GCEnumList<GCFunction2D> value = selection.getEnumList(GCFunction2D.resolver, "thisismissing");
                assertEquals(GCFunction2D.ABSOLUTE, value.get(0));
                assertFalse(value.isSet());
        }

        @Test
        public void testListUnknownValue() throws Exception
        {
                GCEnumList<GCFunction2D> value = selection.getEnumList(GCFunction2D.resolver, "baz");
                assertEquals(GCFunction2D.ABSOLUTE, value.get(0));
                assertTrue(value.isSet());
        }
}
