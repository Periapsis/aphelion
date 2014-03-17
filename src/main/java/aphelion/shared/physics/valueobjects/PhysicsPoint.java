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

import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import javax.annotation.Nonnull;

/**
 *
 * @author JoWie
 *
 */
public final class PhysicsPoint
{
        public static final PhysicsPoint ZERO = new PhysicsPoint(0, 0); // do not modify me
        public int x = 0;
        public int y = 0;
        public boolean set = false;

        public PhysicsPoint()
        {
                this.x = 0;
                this.y = 0;
                set = false;
        }

        public PhysicsPoint(int x2, int y2)
        {
                this.x = x2;
                this.y = y2;
                set = true;
        }

        public PhysicsPoint(@Nonnull PhysicsPoint other)
        {
                set(other);
        }
        
        public void enforceOverflowLimit()
        {
                x = SwissArmyKnife.clip(x, -PhysicsEnvironment.MAX_POSITION, PhysicsEnvironment.MAX_POSITION);
                y = SwissArmyKnife.clip(y, -PhysicsEnvironment.MAX_POSITION, PhysicsEnvironment.MAX_POSITION);
        }

        public void unset()
        {
                set = false;
                x = 0;
                y = 0;
        }

        @Override
        public boolean equals(Object obj)
        {
                if (obj != null && obj instanceof PhysicsPoint)
                {
                        return this.equals((PhysicsPoint) obj);
                }

                return false;
        }

        public boolean equals(PhysicsPoint other)
        {
                if (!this.set && !other.set)
                {
                        return true;
                }

                return this.x == other.x
                        && this.y == other.y;
        }

        public boolean equals(int x, int y)
        {
                return this.x == x
                        && this.y == y;
        }

        @Override
        public int hashCode()
        {
                int hash = 5;
                if (set)
                {
                        hash = 17 * hash + this.x;
                        hash = 17 * hash + this.y;
                }
                return hash;
        }

        @Override
        public String toString()
        {
                if (set)
                {
                        return "(" + x + "," + y + ")";
                }
                else
                {
                        return "(unset)";
                }
        }

        public void set(@Nonnull PhysicsPoint other)
        {
                this.x = other.x;
                this.y = other.y;
                this.set = other.set;
        }

        public void set(int x, int y)
        {
                this.x = x;
                this.y = y;
                this.set = true;
        }

        public int getX()
        {
                return this.x;
        }

        public int getY()
        {
                return this.y;
        }

        public void add(@Nonnull PhysicsPoint other)
        {
                addX(other.x);
                addY(other.y);
        }

        public void add(int n)
        {
                addX(n);
                addY(n);
        }

        public void addX(int x)
        {
                this.x = this.x + x;
                set = true;
        }

        public void addY(int y)
        {
                this.y = this.y + y;
                set = true;
        }

        public void sub(@Nonnull PhysicsPoint other)
        {
                subX(other.x);
                subY(other.y);
        }
        
        public void sub(int x, int y)
        {
                subX(x);
                subY(y);
        }

        public void sub(int n)
        {
                subX(n);
                subY(n);
        }

        public void subX(int x)
        {
                this.x = this.x - x;
                set = true;
        }

        public void subY(int y)
        {
                this.y = this.y - y;
                set = true;
        }

        public long lengthSquared()
        {
                // int32 * int32 always fits in int64
                return (long) this.x * (long) this.x + 
                       (long) this.y * (long) this.y;
        }

        public long distanceSquared(@Nonnull PhysicsPoint other)
        {
                return distanceSquared(other.x, other.y);
        }

        public long distanceSquared(int otherX, int otherY)
        {
                // int32 * int32 always fits in int64
                otherX -= this.x;
                otherY -= this.y;
                return (long) otherX * (long) otherX + 
                       (long) otherY * (long) otherY;
        }
        
        public long length()
        {
                return SwissArmyKnife.hypot(this.x, this.y);
        }
        
        public long length(long lengthSq)
        {
                return SwissArmyKnife.hypot(this.x, this.y, lengthSq);
        }
        
        public long distance(@Nonnull PhysicsPoint other)
        {
                return SwissArmyKnife.hypot(other.x - this.x, other.y - this.y);
        }
        
        public long distance(@Nonnull PhysicsPoint other, long distSq)
        {
                return SwissArmyKnife.hypot(other.x - this.x, other.y - this.y, distSq);
        }
        
        

        /**
         * Integer division. 5 / 2 = 2 -5 / 2 = -2
         */
        public void divide(int number)
        {
                this.x /= number;
                this.y /= number;
                set = true;
        }

        public void divideFloor(int number)
        {
                this.x = SwissArmyKnife.divideFloor(this.x, number);
                this.y = SwissArmyKnife.divideFloor(this.y, number);
                set = true;
        }

        public void divideCeil(int number)
        {
                this.x = SwissArmyKnife.divideCeil(this.x, number);
                this.y = SwissArmyKnife.divideCeil(this.y, number);
                set = true;
        }
        
        /** Divides by rounding up. In such a way that:
         * x = x / number
         * x = (x < 0) ? floor(x) : ceil(x)
         * 
         */
        public void divideUp(int number)
        {
                this.x = SwissArmyKnife.divideUp(this.x, number);
                this.y = SwissArmyKnife.divideUp(this.y, number);
                set = true;
        }

        public void multiply(int number)
        {
                this.x = this.x * number;
                this.y = this.y * number;
                set = true;
        }

        public void negate()
        {
                negateX();
                negateY();
                set = true;
        }

        public void negateX()
        {
                this.x = -this.x;
        }

        public void negateY()
        {
                this.y = -this.y;
        }

        public void abs()
        {
                this.x = SwissArmyKnife.abs(this.x);
                this.y = SwissArmyKnife.abs(this.y);
                set = true;
        }
        
        public boolean isZero()
        {
                return !this.set || (this.x == 0 && this.y == 0);
        }
        
        public long dotProduct(@Nonnull PhysicsPoint other)
        {
                return (long)x * other.x + (long)y * other.y;
        }
        
        public long crossProduct(@Nonnull PhysicsPoint other)
        {
                return (long)x * other.y - (long)y * other.x;
        }
        
        public void limitLength(int limit)
        {
                set = true;
                if (limit == 0)
                {
                        this.x = 0;
                        this.y = 0;
                        return;
                }
                
                long lim = limit;
                
                long lengthSquared = this.lengthSquared();
                long limitSquared = lim * lim;
                
                
                if (lengthSquared <= limitSquared)
                {
                        // bail without doing a slow square root
                        return;
                }
                
                // Note. StrictMath.sqrt is usually faster, however determinism 
                // is more important (not all languages support strictfp).
                long length = SwissArmyKnife.hypot(x, y, lengthSquared);
                
                if (length == 0)
                {
                        this.x = 0;
                        this.y = 0;
                        return;
                }
                else
                {
                        this.x = (int) ((this.x * lim) / length);
                        this.y = (int) ((this.y * lim) / length);
                }
                
                // verify
                lengthSquared = this.lengthSquared();
                
                if (lengthSquared > limitSquared)
                {
                        // move both x and y towards 0
                        // so that the new length is always under the limit
                        this.x += this.x > 0 ? -1 : 1;
                        this.y += this.y > 0 ? -1 : 1;
                }
        }
        
        public void setLength(int desiredLength)
        {
                set = true;
                if (desiredLength == 0)
                {
                        this.x = 0;
                        this.y = 0;
                        return;
                }
                
                long len = desiredLength;
                long lengthSquared = this.lengthSquared();
                
                // Note. StrictMath.sqrt is usually faster, however determinism 
                // is more important (not all languages support strictfp).
                long length = SwissArmyKnife.hypot(x, y, lengthSquared);
                
                if (length == 0)
                {
                        this.x = 0;
                        this.y = 0;
                }
                else
                {
                        this.x = (int) ((this.x * len) / length);
                        this.y = (int) ((this.y * len) / length);
                }
        }
        
        public void applyRatio(int fraction, int consequent)
        {
                this.x = (int) (this.x * (long) fraction / consequent);
                this.y = (int) (this.y * (long) fraction / consequent);
        }
        
        public void clip(PhysicsPoint min, PhysicsPoint max)
        {
                if (min != null)
                {
                        if (this.x < min.x) { this.x = min.x; }
                        if (this.y < min.y) { this.y = min.y; }
                }
                
                if (max != null)
                {
                        if (this.x > max.x) { this.x = max.x; }
                        if (this.y > max.y) { this.y = max.y; }
                }
        }
}
