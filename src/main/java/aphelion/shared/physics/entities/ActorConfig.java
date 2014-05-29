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
 * If you modify this library, you may extend this exception to your version
 * of the library, but you are not obliged to do so. If you do not wish to do
 * so, delete this exception statement from your version.
 */

package aphelion.shared.physics.entities;

import aphelion.shared.gameconfig.*;
import aphelion.shared.physics.WEAPON_SLOT;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;



/**
 *
 * @author Joris
 */
public final class ActorConfig
{
        private static final Logger log = Logger.getLogger("aphelion.shared.physics");
        
        public final Actor actor;
        public final ConfigSelection selection;
        
        public GCInteger radius;
        public GCInteger bounceFriction;
        public GCInteger bounceOtherAxisFriction;
        public GCInteger rotationSpeed;
        public GCInteger rotationPoints;
        public GCInteger speed;
        public GCInteger thrust;
        public GCInteger boostSpeed;
        public GCInteger boostThrust;
        public GCInteger boostEnergy;
        public GCInteger maxEnergy;
        public GCInteger recharge;
        public GCInteger spawnX;
        public GCInteger spawnY;
        public GCInteger spawnRadius;
        public GCInteger respawnDelay;
        public GCString  smoothingAlgorithm;
        public GCInteger smoothingLookAheadTicks;
        public GCInteger smoothingStepRatio;
        public GCInteger smoothingDistanceLimit;
        public GCBoolean smoothingProjectileCollisions;
        
        public WeaponSlotConfig[] weaponSlots = new WeaponSlotConfig[WEAPON_SLOT.values().length];
        
        // Note: at the moment old weapon definitions that no longer exist are not removed
        public Map<String, WeaponConfig> weapons = new HashMap<>();

        public ActorConfig(Actor actor)
        {
                this.actor = actor;
                this.selection = actor.state.config.newSelection();
                
                this.radius = selection.getInteger("ship-radius");
                this.bounceFriction = selection.getInteger("ship-bounce-friction");
                this.bounceOtherAxisFriction = selection.getInteger("ship-bounce-friction-other-axis");
                this.rotationSpeed = selection.getInteger("ship-rotation-speed");
                this.rotationPoints = selection.getInteger("ship-rotation-points");
                this.speed = selection.getInteger("ship-speed");
                this.thrust = selection.getInteger("ship-thrust");
                this.boostSpeed = selection.getInteger("ship-boost-speed");
                this.boostThrust = selection.getInteger("ship-boost-thrust");
                this.boostEnergy = selection.getInteger("ship-boost-energy");
                this.maxEnergy = selection.getInteger("ship-energy");
                this.recharge = selection.getInteger("ship-recharge");
                this.spawnX = selection.getInteger("ship-spawn-x");
                this.spawnY = selection.getInteger("ship-spawn-y");
                this.spawnRadius = selection.getInteger("ship-spawn-radius");
                this.respawnDelay = selection.getInteger("ship-respawn-delay");
                this.smoothingAlgorithm = selection.getString("smoothing-algorithm");
                this.smoothingLookAheadTicks = selection.getInteger("smoothing-look-ahead-ticks");
                this.smoothingStepRatio = selection.getInteger("smoothing-step-ratio");
                this.smoothingDistanceLimit = selection.getInteger("smoothing-distance-limit");
                this.smoothingProjectileCollisions = selection.getBoolean("smoothing-projectile-collisions");
                
                for (int slotId = 0; slotId < weaponSlots.length; ++slotId)
                {
                        weaponSlots[slotId] = new WeaponSlotConfig(WEAPON_SLOT.byId(slotId));
                }
        }
        
        public WeaponConfig getWeaponConfig(String key)
        {
                WeaponConfig config = weapons.get(key);
                if (config == null)
                {
                        config = new WeaponConfig(actor, key);
                        weapons.put(key, config);
                }
                return config;
        }
        
        public final class WeaponSlotConfig implements WrappedValueAbstract.ChangeListener
        {
                public final WEAPON_SLOT slot;
                public GCString weaponKey;
                public WeaponConfig config;

                public WeaponSlotConfig(WEAPON_SLOT slot)
                {
                        this.slot = slot;
                        weaponKey = selection.getString(slot.settingName);
                        weaponKey.addWeakChangeListener(this);
                        config = getWeaponConfig(weaponKey.get());
                }
                
                @Override
                public void gameConfigValueChanged(WrappedValueAbstract val)
                {
                        assert val == weaponKey;
                        config = getWeaponConfig(weaponKey.get());
                }
                
                
                public boolean isValidWeapon()
                {
                        return this.config != null;
                }
        }
}
