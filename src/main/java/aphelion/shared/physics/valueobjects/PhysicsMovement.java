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

package aphelion.shared.physics.valueobjects;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 *
 * @author Joris
 */
public class PhysicsMovement implements PhysicsMoveable
{
        public final boolean up;
        public final boolean down;
        public final boolean left;
        public final boolean right;
        public final boolean boost;
        public final int bits;
        
        private static final PhysicsMovement[] movements = new PhysicsMovement[32];
        static
        {
                for (int i = 0; i < 32; ++i)
                {
                        movements[i] = new PhysicsMovement(
                                (i & 0b00001) != 0, 
                                (i & 0b00010) != 0, 
                                (i & 0b00100) != 0, 
                                (i & 0b01000) != 0,
                                (i & 0b10000) != 0
                                );
                }
        }
        
        public static final PhysicsMovement NONE = get(0);

        private PhysicsMovement(boolean up, boolean down, boolean left, boolean right, boolean boost)
        {
                this.up = up;
                this.down = down;
                this.left = left;
                this.right = right;
                this.boost = boost;
                
                int i = 0;
                if (up)    i |= 0b00001;
                if (down)  i |= 0b00010;
                if (left)  i |= 0b00100;
                if (right) i |= 0b01000;
                if (boost) i |= 0b10000;
                bits = i;
        }
        
        public static PhysicsMovement get(boolean up, boolean down, boolean left, boolean right, boolean boost)
        {
                int i = 0;
                if (up)    i |= 0b00001;
                if (down)  i |= 0b00010;
                if (left)  i |= 0b00100;
                if (right) i |= 0b01000;
                if (boost) i |= 0b10000;
                
                return movements[i];
        }
        
        public static PhysicsMovement get(int bits)
        {
                return movements[bits];
        }
        
        public boolean isValidBoost()
        {
                return (up || down) && boost;
        }
        
        /** Does this movement do anything useful?.
         * If this method returns false, it is okay to ignore this movement.
         * @return 
         */
        public boolean hasEffect()
        {
                return up || down || left || right;
        }

        @Override
        public int hashCode()
        {
                return bits;
        }

        @Override
        public boolean equals(Object obj)
        {
                if (obj == null)
                {
                        return false;
                }
                if (!(obj instanceof PhysicsMovement))
                {
                        return false;
                }
                final PhysicsMovement other = (PhysicsMovement) obj;
                return this.bits == other.bits;
        }
        
        /** Serialize a list of movements as a compact bit set (little endian).
         * Trailing 0's will not be included, unless needed for padding.
         * This means that trailing NONE moves, will not be included!
         * @param list
         * @return 
         */
        public static byte[] serializeListLE(List<PhysicsMovement> list)
        {
                BitSet bits = new BitSet(list.size() * 5);
                
                int i = 0;
                for (PhysicsMovement move : list)
                {
                        bits.set(i++, move.boost);
                        bits.set(i++, move.right);
                        bits.set(i++, move.left);
                        bits.set(i++, move.down);
                        bits.set(i++, move.up);
                }
                
                return bits.toByteArray();
        }
        
        public static ArrayList<PhysicsMovement> unserializeListLE(ByteBuffer bytes)
        {
                ArrayList<PhysicsMovement> list = new ArrayList<>(bytes.remaining() * 8 / 5);
                
                BitSet bits = BitSet.valueOf(bytes);
                
                for (int i = 0; i < bits.length(); i += 5)
                {
                        list.add(get(bits.get(i + 4), bits.get(i + 3), bits.get(i + 2), bits.get(i + 1), bits.get(i + 0)));
                }
                
                return list;
        }
}
