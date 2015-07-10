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

public class PhysicsExpiration
{
        //The first tick that is "expired"
        private long expiresAtTick;

        public PhysicsExpiration()
        {
                unset();
        }

        public boolean isSet()
        {
                return expiresAtTick != Long.MIN_VALUE;
        }

        public long getExpiration()
        {
                return expiresAtTick;
        }

        public boolean isExpiredAt(long tick)
        {
                return tick >= expiresAtTick;
        }

        public boolean isActiveAt(long tick)
        {
                return tick < expiresAtTick;
        }

        public void ensureActiveUntil(long tick)
        {
                // (tick is exclusive)
                if (tick > expiresAtTick)
                {
                        expiresAtTick = tick;
                }
        }

        public void set(PhysicsExpiration other)
        {
                expiresAtTick = other.expiresAtTick;
        }

        public void setExpiration(long tick)
        {
                expiresAtTick = tick;
        }

        public void unset()
        {
                expiresAtTick = Long.MIN_VALUE;
        }


}
