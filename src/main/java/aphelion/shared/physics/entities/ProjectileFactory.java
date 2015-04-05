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


import aphelion.shared.physics.State;
import aphelion.shared.physics.config.WeaponConfig;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import java.util.ArrayList;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This object is used by operations and events to construct projectiles properly.
 * It should have a 1:1 relation for projectiles and operations and should this 
 * object should not be recreated during a timewarp.
 * This factory properly sets up the cross state list and coupled list. But also
 * makes sure the same object is reused after a timewarp
 * @author Joris
 */
public class ProjectileFactory
{
        private static final Logger log = Logger.getLogger(ProjectileFactory.class.getName());
        
        /** crossStateList is a temporary list that is passed to the MapEntity constructor.
         * This list is used to track the same MapEntity instance across states.
         * This attribute (MapEntity.crossStateList) references the same array object 
         * for each instance.
         * 
         * projectile index -> crossStateList (state id -> map entity)
        */
        private ArrayList<MapEntity[]> crossStateLists;
        
        /** The entities this operation has spawned.
         * This list contains exactly the same data as "crossStateLists", however it 
         * is local to this object. If a projectile is removed, this list is not modified.
         * (Unlike the arrays in crossStateLists).
         */
        private ArrayList<Projectile[]> constructedEntitiesList;
        
        
        public Projectile[] constructProjectiles(
                @Nonnull State state,
                @Nullable Actor owner,
                long createdAt_tick,
                @Nonnull WeaponConfig config,
                int projectile_count,
                @Nullable Projectile chainedBy,
                long syncKey
                )
        {
                MapEntity[] crossStateList;
                Projectile[] spawnedEntities;
                
                if (crossStateLists == null)
                {
                        hintProjectileCount(4);
                }
                
                LinkedListEntry<Projectile> coupled_last = null;
                
                Projectile[] ret = new Projectile[projectile_count];
                
                for (int p = 0; p < projectile_count; ++p)
                {
                        // Ensure there is enough room in crossStateLists 
                        // (projectile count might change due to config change)
                        if (p < crossStateLists.size())
                        {
                                assert p < constructedEntitiesList.size();
                                crossStateList = crossStateLists.get(p);
                                spawnedEntities = constructedEntitiesList.get(p);
                        }
                        else
                        {
                                crossStateList = new MapEntity[state.econfig.TRAILING_STATES];
                                crossStateLists.add(crossStateList);

                                spawnedEntities = new Projectile[state.econfig.TRAILING_STATES];
                                constructedEntitiesList.add(spawnedEntities);

                                assert p == crossStateLists.size() - 1;
                                assert p == constructedEntitiesList.size() - 1;
                        }

                        Projectile projectile;

                        if (spawnedEntities[state.id] != null && spawnedEntities[state.id].removedDuringReset)
                        {
                                projectile = spawnedEntities[state.id];
                                projectile.resetToEmpty(createdAt_tick);
                                projectile.removedDuringReset = false;
                        }
                        else
                        {
                                ProjectileKey key;
                                
                                if (syncKey != 0)
                                {
                                        key = new ProjectileKey(syncKey, p);
                                }
                                else if (chainedBy == null)
                                {
                                        key = new ProjectileKey(createdAt_tick, p, owner == null ? 0 : owner.pid);
                                }
                                else
                                {
                                        key = new ProjectileKey(chainedBy.key, p, owner == null ? 0 : owner.pid);
                                }
                                
                                projectile = new Projectile(
                                        key,
                                        state, 
                                        crossStateList, 
                                        owner, 
                                        createdAt_tick, 
                                        config,
                                        p);
                        }
                        
                        // Set up the circular linked list which references all projectiles in a single state
                        // that have been triggered by the same operation or event.
                        // This is kept as a Link attribute (Projectile.coupled)
                        if (coupled_last == null)
                        {
                                projectile.coupled.beginCircular();
                        }
                        else
                        {
                                coupled_last.append(projectile.coupled);
                        }
                        coupled_last = projectile.coupled;

                        
                        assert projectile.state == state;
                        assert projectile.owner == owner;
                        assert projectile.config == config;
                        assert projectile.configIndex == p;
                        assert crossStateList[state.id] == projectile;

                        spawnedEntities[state.id] = projectile;
                        ret[p] = projectile;
                }
                
                return ret;
        }
        
        /** Slight optimization to avoid recreating internal arrays.
         * @param projectileCount 
         */
        public void hintProjectileCount(int projectileCount)
        {
                if (crossStateLists == null)
                {
                        assert constructedEntitiesList == null;
                        crossStateLists = new ArrayList(projectileCount);
                        constructedEntitiesList = new ArrayList(projectileCount);
                }
                else
                {
                        assert constructedEntitiesList != null;
                        crossStateLists.ensureCapacity(projectileCount);
                        constructedEntitiesList.ensureCapacity(projectileCount);
                }
        }
}
