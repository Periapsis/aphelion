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
package aphelion.shared.physics.entities;

import aphelion.shared.gameconfig.GCBoolean;
import aphelion.shared.gameconfig.GCBooleanList;
import aphelion.shared.gameconfig.GCColour;
import aphelion.shared.gameconfig.GCImage;
import aphelion.shared.gameconfig.GCInteger;
import aphelion.shared.gameconfig.GCIntegerList;
import aphelion.shared.gameconfig.GCString;
import aphelion.shared.gameconfig.GCStringList;
import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.Attachable;
import java.util.Iterator;

/**
 *
 * @author Joris
 */
public interface ProjectilePublic extends Attachable
{
        boolean isRemoved();
        
        int getProjectileIndex();
        /** Iterate over the projectiles that were triggered by the same event as this one. 
         * (including this instance)
         * @return 
         */
        Iterator<ProjectilePublic> getCoupledProjectiles();
        
        
        /** Gets the most current position, velocity for the projectile.
         * @param pos The object to fill with position, velocity, and rotation
         * @return true if the projectile exists and pos has been filled 
         */
        boolean getPosition(Position pos);
        
        /** Gets the historic position for the projectile.
         * @param pos The object to fill with position and rotation
         * @param tick
         * @param lookAtOtherStates 
         * @return true if the actor exists and pos has been filled
         */
        boolean getHistoricPosition(PhysicsPoint pos, long tick, boolean lookAtOtherStates);
        
        /** Returns the actor that fired this weapon.
         * @return the pid of the actor
         */
        int getOwner();
        
        /** Returns when this projectile will expire.
         * @return The tick at which this projectile will expire;
         */
        long getExpiry();
        
        /** Returns how many times this projectile may bounce
         * @return The number of bounces left; -1 for infinite
         */
        int getBouncesLeft();
        
        int getActivateBouncesLeft();
        boolean isActive();
        
        String getWeaponKey();
        
        void getSync(GameOperation.WeaponSync.Projectile.Builder builder);
        
        
        GCInteger getWeaponConfigInteger(String name);
        GCString getWeaponConfigString(String name);
        GCBoolean getWeaponConfigBoolean(String name);
        GCColour getWeaponConfigColour(String name);
        GCIntegerList getWeaponConfigIntegerList(String name);
        GCStringList getWeaponConfigStringList(String name);
        GCBooleanList getWeaponConfigBooleanList(String name);
        GCImage getWeaponConfigImage(String name, ResourceDB db);
        
        public static class Position
        {
                public int x;
                public int y;
                public int x_vel;
                public int y_vel;

                @Override
                public int hashCode()
                {
                        int hash = 5;
                        hash = 47 * hash + this.x;
                        hash = 47 * hash + this.y;
                        hash = 47 * hash + this.x_vel;
                        hash = 47 * hash + this.y_vel;
                        return hash;
                }

                @Override
                public boolean equals(Object obj)
                {
                        if (obj == null)
                        {
                                return false;
                        }
                        if (!(obj instanceof Position))
                        {
                                return false;
                        }
                        final Position other = (Position) obj;
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
                        return true;
                }
                
                
        }
}
