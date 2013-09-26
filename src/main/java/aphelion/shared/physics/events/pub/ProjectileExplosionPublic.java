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
        public boolean hasOccured(int stateid);
        public EXPLODE_REASON getReason(int stateid); // null if hasOccured = false
        public ProjectilePublic getProjectile(int stateid);
        public long getTick(int stateid);
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
         * @return 
         */
        public Iterable<ProjectilePublic> getCoupledProjectiles(int stateid);
        
        public static enum EXPLODE_REASON
        {
                EXPIRATION,
                PROX_DELAY,
                PROX_DIST,
                HIT_TILE,
                HIT_SHIP;
        }
}
