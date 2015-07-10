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
package aphelion.shared.physics.valueobjects;

import org.junit.Test;
import static org.junit.Assert.*;

public class PhysicsExpirationTest
{
        @Test
        public void testIsSet() throws Exception
        {
                PhysicsExpiration expiration = new PhysicsExpiration();
                assertFalse(expiration.isSet());
                expiration.setExpiration(0);
                assertTrue(expiration.isSet());
                expiration.unset();
                assertFalse(expiration.isSet());
        }

        @Test
        public void testGetSet() throws Exception
        {
                PhysicsExpiration expiration = new PhysicsExpiration();
                assertEquals(Long.MIN_VALUE, expiration.getExpiration());
                expiration.setExpiration(0);
                assertEquals(0, expiration.getExpiration());
                expiration.setExpiration(-100);
                assertEquals(-100, expiration.getExpiration());
                expiration.setExpiration(400);
                assertEquals(400, expiration.getExpiration());
                expiration.unset();
                assertEquals(Long.MIN_VALUE, expiration.getExpiration());
        }

        @Test
        public void testIsActiveAt() throws Exception
        {
                PhysicsExpiration expiration = new PhysicsExpiration();
                assertFalse(expiration.isActiveAt(0));
                assertFalse(expiration.isActiveAt(Long.MIN_VALUE));
                assertFalse(expiration.isActiveAt(1000));
                assertFalse(expiration.isActiveAt(Long.MAX_VALUE));

                expiration.setExpiration(0);
                assertTrue(expiration.isActiveAt(-1));
                assertFalse(expiration.isActiveAt(0));

                expiration.setExpiration(100);
                assertTrue(expiration.isActiveAt(99));
                assertFalse(expiration.isActiveAt(100));
        }

        @Test
        public void testIsExpireAt() throws Exception
        {
                PhysicsExpiration expiration = new PhysicsExpiration();
                assertTrue(expiration.isExpiredAt(0));
                assertTrue(expiration.isExpiredAt(Long.MIN_VALUE));
                assertTrue(expiration.isExpiredAt(1000));
                assertTrue(expiration.isExpiredAt(Long.MAX_VALUE));

                expiration.setExpiration(0);
                assertFalse(expiration.isExpiredAt(-1));
                assertTrue(expiration.isExpiredAt(0));

                expiration.setExpiration(100);
                assertFalse(expiration.isExpiredAt(99));
                assertTrue(expiration.isExpiredAt(100));
        }

        @Test
        public void testEnsureActiveUntil() throws Exception
        {
                PhysicsExpiration expiration = new PhysicsExpiration();
                assertEquals(Long.MIN_VALUE, expiration.getExpiration());

                expiration.ensureActiveUntil(-100);
                assertEquals(-100, expiration.getExpiration());

                expiration.ensureActiveUntil(-500);
                assertEquals(-100, expiration.getExpiration());

                expiration.ensureActiveUntil(123);
                assertEquals(123, expiration.getExpiration());

                expiration.ensureActiveUntil(-50);
                assertEquals(123, expiration.getExpiration());
        }
}