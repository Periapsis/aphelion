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

/**
 *
 * @author Joris
 */
public class PhysicsShipPosition
{
        public boolean set; 
        public int x;
        public int y;
        public int x_vel;
        public int y_vel;
        // smooth is not included in equals(), two positions are consistent with each other
        // even if the smoothed position is different
        public int smooth_x; 
        public int smooth_y;
        public int rot;
        public int rot_snapped;

        @Override
        public int hashCode()
        {
                int hash = 7;
                hash = 61 * hash + (this.set ? 1 : 0);
                hash = 61 * hash + this.x;
                hash = 61 * hash + this.y;
                hash = 61 * hash + this.x_vel;
                hash = 61 * hash + this.y_vel;
                hash = 61 * hash + this.rot;
                hash = 61 * hash + this.rot_snapped;
                return hash;
        }

        public void unset()
        {
                this.set = false;
                this.x = 0;
                this.y = 0;
                this.x_vel = 0;
                this.y_vel = 0;
                this.smooth_x = 0;
                this.smooth_y = 0;
                this.rot = 0;
                this.rot_snapped = 0;
        }

        public void setPosition(PhysicsPoint p)
        {
                x = p.x;
                y = p.y;
        }

        public void setVelocity(PhysicsPoint p)
        {
                x_vel = p.x;
                y_vel = p.y;
        }

        public void setPositionVectory(PhysicsPositionVector p)
        {
                setPosition(p.pos);
                setVelocity(p.vel);
        }


        public void setRotation(PhysicsRotation r)
        {
                rot = r.points;
                rot_snapped=  r.snapped;
        }

        @Override
        public String toString()
        {
                if (set)
                {
                        return String.format("(%d, %d, %d, %d, [%d], %d, %d)", x, y, x_vel, y_vel, rot, smooth_x, smooth_y);
                }
                else
                {
                        return "(unset)";
                }
        }

        // note: when generating equals, make sure that if set is false in both objects, true is returned
        // also get rid of getClass() which is slow...
        // And do NOT include smooth position
        @Override
        public boolean equals(Object obj)
        {
                if (obj == null)
                {
                        return false;
                }
                
                if (!(obj instanceof PhysicsShipPosition))
                {
                        return false;
                }
                
                final PhysicsShipPosition other = (PhysicsShipPosition) obj;
                if (this.set == false && other.set == false)
                {
                        return true;
                }

                if (this.x != other.x)
                {
                        return false;
                }
                if (this.y != other.y)
                {
                        return false;
                }
                if (this.x_vel != other.x_vel)
                {
                        return false;
                }
                if (this.y_vel != other.y_vel)
                {
                        return false;
                }
                if (this.rot != other.rot)
                {
                        return false;
                }
                if (this.rot_snapped != other.rot_snapped)
                {
                        return false;
                }
                return true;
        }


}
