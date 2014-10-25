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
package aphelion.shared.swissarmyknife;

import aphelion.shared.physics.valueobjects.PhysicsPoint;

/**
 *
 * @author JabJabJab
 * @author JoWie
 *
 */
public class Point
{
        public float x = 0;
        public float y = 0;

        public Point()
        {
                this.x = 0;
                this.y = 0;
        }

        public Point(float x2, float y2)
        {
                this.x = x2;
                this.y = y2;
        }

        public Point(Point other)
        {
                this.x = other.x;
                this.y = other.y;
        }

        @Override
        public boolean equals(Object obj)
        {
                if (obj != null && obj instanceof Point)
                {
                        return this.x == ((Point) obj).x
                                && this.y == ((Point) obj).y;
                }

                return false;
        }

        @Override
        public int hashCode()
        {
                int hash = 3;
                hash = 13 * hash + Float.floatToIntBits(this.x);
                hash = 13 * hash + Float.floatToIntBits(this.y);
                return hash;
        }

        public void set(Point other)
        {
                this.x = other.getX();
                this.y = other.getY();
        }

        public void set(PhysicsPoint other)
        {
                this.x = other.x;
                this.y = other.y;
        }

        public void set(float x, float y)
        {
                this.x = x;
                this.y = y;
        }

        public float getX()
        {
                return this.x;
        }

        public float getY()
        {
                return this.y;
        }

        public void add(Point other)
        {
                this.x += other.getX();
                this.y += other.getY();
        }

        public void add(float number)
        {
                this.x += number;
                this.y += number;
        }

        public void sub(Point other)
        {
                this.x -= other.getX();
                this.y -= other.getY();
        }

        public void sub(float number)
        {
                this.x -= number;
                this.y -= number;
        }

        public float lengthSquared()
        {
                return (this.x * this.x) + (this.y * this.y);
        }
        
        public float length()
        {
                return (float) Math.hypot(this.x, this.y);
        }
        
        public float distanceSquared(Point other)
        {
                return distanceSquared(other.x, other.y);
        }

        public float distanceSquared(float otherX, float otherY)
        {
                otherX -= this.x;
                otherY -= this.y;
                
                return (otherX * otherX) + (otherY * otherY);
        }
        
        public float distance(Point other)
        {
                return (float) Math.hypot(other.x - this.x, other.y - this.y);
        }

        public void divide(float number)
        {
                this.x /= number;
                this.y /= number;
        }

        public void multiply(float number)
        {
                this.x *= number;
                this.y *= number;
        }

        public void negate()
        {
                this.x = -this.x;
                this.y = -this.y;
        }

        public void floor()
        {
                this.x = SwissArmyKnife.floor(this.x);
                this.y = SwissArmyKnife.floor(this.y);
        }

        public void ceil()
        {
                this.x = SwissArmyKnife.ceil(this.x);
                this.y = SwissArmyKnife.ceil(this.y);
        }
        
        public void round()
        {
                this.x = Math.round(x);
                this.y = Math.round(y);
        }

        @Override
        public String toString()
        {
                return "[" + x + "; " + y + "]";
        }
}
