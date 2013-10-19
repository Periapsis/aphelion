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
package aphelion.shared.physics.events.pub;

import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.WEAPON_SLOT;

/**
 * The projectile hit an actor, a tile, or explodes because of prox.
 * @author Joris
 */
public interface ProjectileExplosionPublic extends EventPublic
{
        /** The projectile that caused this event. 
         * @param stateid 
         * @return 
         */
        public ProjectilePublic getProjectile(int stateid);
        public EXPLODE_REASON getReason(int stateid); // null if hasOccured = false
        public void getPosition(int stateid, PhysicsPoint pos);
        public void getVelocity(int stateid, PhysicsPoint vel);
        
        /** The tile that this projectile hit to cause the explosion.
         * @param stateid
         * @param tile The tile coordinates or PhysicsPoint.set == false if a tile was not hit.
         */
        public void getHitTile(int stateid, PhysicsPoint tile);
        
        /** The actors that were killed by this projectile.
         * The actor that was hit by this  projectile (getHitActor) might be 
         * included in this listing.
         * 
         * @param stateid
         * @return actor id's (pid) 
         */
        public Iterable<Integer> getKilled(int stateid);
        
        /** The number of actors that will be iterated over using getKilled().
         * @param stateid
         * @return  
         */
        public int getKilledSize(int stateid);
        
        /** Who did it hit?.
         * @param stateid
         * @return actor id (pid) or 0 if an actor was not hit
         */
        public int getHitActor(int stateid);
        
        /** Who fired this projectile?.
         * @param stateid
         * @return actor id (pid) or 0 if it was fired by no one in particular.
         */
        public int getFireActor(int stateid);
        
        /** Coupled projectiles that were also removed as a result of this event.
         * (excluding the projectile returned from getProjectile())
         * @param stateid 
         * @return 
         */
        public Iterable<ProjectilePublic> getCoupledProjectiles(int stateid);
        
        /** The number of projectiles that will be iterated over using getCoupledProjectiles().
         * @param stateid
         * @return  
         */
        public int getCoupledProjectilesSize(int stateid);
        
        public static enum EXPLODE_REASON
        {
                /** Caused by projectile-expiration-ticks . */
                EXPIRATION,
                
                /** Caused by projectile-prox-explode-ticks . */
                PROX_DELAY,
                
                /** Caused by projectile-prox-distance . */
                PROX_DIST,
                
                /** Caused by projectile-hit-tile . 
                  * Other projectiles removed as part of projectile-hit-tile-coupled do not fire seperate events.
                  */
                HIT_TILE,
                
                /** Caused by projectile-hit-ship . 
                 * Other projectiles removed as part of projectile-hit-ship-coupled do not fire seperate events.
                 */
                HIT_SHIP;
        }
}
