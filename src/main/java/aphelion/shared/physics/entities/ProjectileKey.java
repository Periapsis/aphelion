/*
 * Aphelion
 * Copyright (c) 2014  Joris van der Wel
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
 * different from the original version
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

package aphelion.shared.physics.entities;

import java.util.Objects;



/**
 * Uniquely identifies a single projectile. 
 * The identifier is the same within every state and peer.
 * @author Joris
 */
public final class ProjectileKey
{
        /** A server generated key in case of WeaponSync. */
        private final long syncKey;
        
        /** The tick at which the weapon fire tick occurred. 
         * This must be constant (aka not subject to timewarps).
         * If this projectile is chained, this value must be 0.
         */
        private final long fireTick;
        
        /** The projectile index in the config, used for coupled projectiles. */
        private final int configIndex;
        
        /** The pid of the actor that fired this. */
        private final int owner;
        // Note for the future: if support for ownerless projectiles is to be added,
        // some kind of sequence id must be added if multiple of them might be spawned 
        // within the same tick.
        
        private final ProjectileKey parent;
        
        private final int hashCode;
        
        public ProjectileKey(long syncKey, int configIndex)
        {
                if (syncKey == 0) { throw new IllegalArgumentException(); }
                this.syncKey = syncKey;
                this.configIndex = configIndex;
                this.fireTick = 0;
                this.owner = 0;
                this.parent = null;
                this.hashCode = calculateHashcode();
        }

        public ProjectileKey(long fireTick, int configIndex, int owner)
        {
                if (owner == 0) { throw new IllegalArgumentException("Not implemented yet"); }
                this.fireTick = fireTick;
                this.configIndex = configIndex;
                this.owner = owner;
                this.parent = null;
                this.syncKey = 0;
                this.hashCode = calculateHashcode();
        }
        
        public ProjectileKey(ProjectileKey parent, int configIndex, int owner)
        {
                if (parent == null)
                {
                        throw new IllegalArgumentException();
                }
                if (owner == 0) { throw new IllegalArgumentException("Not implemented yet"); }
                this.configIndex = configIndex;
                this.owner = owner;
                this.parent = parent;
                this.syncKey = 0;
                this.fireTick = 0; // this value might change because this projectile is triggered by the parent
                this.hashCode = calculateHashcode();
        }
        
        private int calculateHashcode()
        {
                int hash = 7;
                hash = 13 * hash + (int) (this.syncKey ^ (this.syncKey >>> 32));
                hash = 13 * hash + (int) (this.fireTick ^ (this.fireTick >>> 32));
                hash = 13 * hash + this.configIndex;
                hash = 13 * hash + this.owner;
                hash = 13 * hash + Objects.hashCode(this.parent);
                return hash;
        }

        @Override
        public int hashCode()
        {
                return hashCode;
        }

        @Override
        public boolean equals(Object obj)
        {
                if (obj == null)
                {
                        return false;
                }
                if (!(obj instanceof ProjectileKey))
                {
                        return false;
                }
                final ProjectileKey other = (ProjectileKey) obj;
                if (this.syncKey != other.syncKey)
                {
                        return false;
                }
                if (this.fireTick != other.fireTick)
                {
                        return false;
                }
                if (this.configIndex != other.configIndex)
                {
                        return false;
                }
                if (this.owner != other.owner)
                {
                        return false;
                }
                if (!Objects.equals(this.parent, other.parent))
                {
                        return false;
                }
                return true;
        }

        @Override
        public String toString()
        {
                return "ProjectileKey{" + "syncKey=" + syncKey + ", fireTick=" + fireTick + ", configIndex=" + configIndex + ", owner=" + owner + ", parent=" + parent + ", hashCode=" + hashCode + '}';
        }
}
