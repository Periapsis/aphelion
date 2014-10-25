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
public class PhysicsPositionVector
{
        final public PhysicsPoint pos = new PhysicsPoint(0, 0); // pixels * 1024
        final public PhysicsPoint vel = new PhysicsPoint(0, 0); // pixels * 1024 per tick
        
        public void set(PhysicsPositionVector other)
        {
                this.pos.set(other.pos);
                this.vel.set(other.vel);
        }
        
        public void unset()
        {
                this.pos.unset();
                this.vel.unset();
        }

        public void markSet()
        {
                this.pos.set = true;
                this.vel.set = true;
        }
        
        public void enforceOverflowLimit()
        {
                this.pos.enforceOverflowLimit();
                this.vel.enforceOverflowLimit();
        }
        
        @Override
        public int hashCode()
        {
                int hash = 7;
                hash = 59 * hash + this.pos.hashCode();
                hash = 59 * hash + this.vel.hashCode();
                return hash;
        }

        @Override
        public boolean equals(Object obj)
        {
                if (obj == null)
                {
                        return false;
                }
                if (!(obj instanceof PhysicsPositionVector))
                {
                        return false;
                }
                final PhysicsPositionVector other = (PhysicsPositionVector) obj;
                if (this.pos != other.pos && !this.pos.equals(other.pos))
                {
                        return false;
                }
                if (this.vel != other.vel && !this.vel.equals(other.vel))
                {
                        return false;
                }
                return true;
        }

        @Override
        public String toString()
        {
                return pos.toString() + " " + vel.toString();
        }
        
}
