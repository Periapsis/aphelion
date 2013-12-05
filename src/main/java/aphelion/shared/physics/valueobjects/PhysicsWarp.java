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

import aphelion.shared.net.protobuf.GameOperation;

/**
 *
 * @author Joris
 */
public class PhysicsWarp implements Comparable<PhysicsWarp>, PhysicsMoveable
{
        public final int x;
        public final int y;
        public final int x_vel;
        public final int y_vel;
        public final int rotation;

        public final boolean has_x;
        public final boolean has_y;
        public final boolean has_x_vel;
        public final boolean has_y_vel;
        public final boolean has_rotation;

        public PhysicsWarp(int x, int y, int x_vel, int y_vel, int rotation)
        {
                this.x = x;
                this.y = y;
                this.x_vel = x_vel;
                this.y_vel = y_vel;
                this.rotation = rotation;
                
                has_x = true;
                has_y = true;
                has_x_vel = true;
                has_y_vel = true;
                has_rotation = true;
        }
        
        public PhysicsWarp(Integer x, Integer y, Integer x_vel, Integer y_vel, Integer rotation)
        {
                this.x = nullToZero(x);
                this.y = nullToZero(y);
                this.x_vel = nullToZero(x_vel);
                this.y_vel = nullToZero(y_vel);
                this.rotation = nullToZero(rotation);
                
                has_x = x != null;
                has_y = y != null;
                has_x_vel = x_vel != null;
                has_y_vel = y_vel != null;
                has_rotation = rotation != null;
        }
        
        private static int nullToZero(Integer i)
        {
                if (i == null) return 0;
                return i;
        }
        
        public void toProtobuf(GameOperation.ActorWarp.Builder b)
        {
                if (has_x)
                {
                        b.setX(x);
                }

                if (has_y)
                {
                        b.setY(y);
                }
                
                if (has_x_vel)
                {
                        b.setXVel(x_vel);
                }
                
                if (has_y_vel)
                {
                        b.setYVel(y_vel);
                }
                
                if (has_rotation)
                {
                        b.setRotation(rotation);
                }
        }

        @Override
        public int compareTo(PhysicsWarp o)
        {
                int c = Boolean.compare(has_x, o.has_x);
                if (c == 0) c = Boolean.compare(has_y, o.has_y);
                if (c == 0) c = Boolean.compare(has_x_vel, o.has_x_vel);
                if (c == 0) c = Boolean.compare(has_y_vel, o.has_y_vel);
                if (c == 0) c = Boolean.compare(has_rotation, o.has_rotation);

                if (c == 0) c = Integer.compare(x, o.x);
                if (c == 0) c = Integer.compare(y, o.y);
                if (c == 0) c = Integer.compare(x_vel, o.x_vel);
                if (c == 0) c = Integer.compare(y_vel, o.y_vel);
                if (c == 0) c = Integer.compare(rotation, o.rotation);
                
                return c;
        }
        
        public boolean equalsShipPosition(PhysicsShipPosition pos)
        {
                if (has_x && pos.x != x)
                {
                        return false;
                }

                if (has_y && pos.y != y)
                {
                        return false;
                }
                
                if (has_x_vel && pos.x_vel != x_vel)
                {
                        return false;
                }
                
                if (has_y_vel && pos.y_vel != y_vel)
                {
                        return false;
                }
                
                if (has_rotation && pos.rot != rotation)
                {
                        return false;
                }
                
                return true;
        }
}
