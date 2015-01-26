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

import aphelion.shared.gameconfig.*;



/**
 *
 * @author Joris
 */
public final class WeaponConfig
{
        public final Actor actor;
        public final ConfigSelection selection;
        public final String weaponKey;

        public long nextWeaponFire_tick;

        public final GCInteger fireEnergy;
        public final GCInteger fireDelay;
        public final GCInteger switchDelay;
        public final GCIntegerList fireProjectileLimit;
        public final GCIntegerList fireProjectileLimitGroup;

        public final GCInteger projectiles;
        public final GCIntegerList projectile_offsetX;
        public final GCIntegerList projectile_offsetY;
        public final GCIntegerList projectile_angle;
        public final GCBooleanList projectile_angleRelative;
        public final GCIntegerList projectile_bounceFriction;
        public final GCIntegerList projectile_bounceOtherAxisFriction;
        public final GCIntegerList projectile_speed;
        public final GCIntegerList projectile_bounces;
        public final GCIntegerList projectile_activateBounces;
        public final GCBooleanList projectile_speedRelative;
        public final GCIntegerList projectile_limitGroup;

        public final GCIntegerList projectile_expirationTicks;
        public final GCBooleanList projectile_expirationExplode;
        public final GCStringList  projectile_expirationChainWeapon;

        public final GCBooleanList projectile_hitTile;
        public final GCBooleanList projectile_hitTileCoupled;
        public final GCStringList  projectile_hitTileChainWeapon;

        public final GCBooleanList projectile_hitShip;
        public final GCBooleanList projectile_hitShipCoupled;
        public final GCStringList  projectile_hitShipChainWeapon;

        public final GCIntegerList projectile_proxDistance;
        public final GCIntegerList projectile_proxExplodeTicks;
        public final GCStringList projectile_proxChainWeapon;

        public final GCIntegerList projectile_damage;
        public final GCIntegerList projectile_damageSplash;
        public final GCBooleanList projectile_damageTeam;
        public final GCBooleanList projectile_damageSelf;
        public final GCBooleanList projectile_damageTeamKill;
        public final GCBooleanList projectile_damageSelfKill;

        public final GCIntegerList projectile_empTime;
        public final GCIntegerList projectile_empSplash;
        public final GCBooleanList projectile_empTeam;
        public final GCBooleanList projectile_empSelf;

        public final GCIntegerList projectile_forceDistanceShip;
        public final GCIntegerList projectile_forceVelocityShip;
        public final GCIntegerList projectile_forceDistanceProjectile;
        public final GCIntegerList projectile_forceVelocityProjectile;


        public WeaponConfig(Actor actor, String weaponKey)
        {
                this.actor = actor;
                this.weaponKey = weaponKey;
                this.selection = actor.state.config.newSelection();
                
                this.nextWeaponFire_tick = actor.createdAt_tick;
                selection.selection.setWeapon(weaponKey);
                selection.selection.setShip(actor.ship);

                fireEnergy = selection.getInteger("weapon-fire-energy");
                fireDelay = selection.getInteger("weapon-fire-delay");
                switchDelay = selection.getInteger("weapon-switch-delay");
                projectiles = selection.getInteger("weapon-projectiles");
                fireProjectileLimit = selection.getIntegerList("weapon-fire-projectile-limit");
                fireProjectileLimitGroup = selection.getIntegerList("weapon-fire-projectile-limit-group");

                projectile_offsetX = selection.getIntegerList("projectile-offset-x");
                projectile_offsetY = selection.getIntegerList("projectile-offset-y");
                projectile_angle = selection.getIntegerList("projectile-angle");
                projectile_angleRelative = selection.getBooleanList("projectile-angle-relative");
                projectile_bounceFriction = selection.getIntegerList("projectile-bounce-friction");
                projectile_bounceOtherAxisFriction = selection.getIntegerList("projectile-bounce-friction-other-axis");
                projectile_speed = selection.getIntegerList("projectile-speed");
                projectile_speedRelative = selection.getBooleanList("projectile-speed-relative");
                projectile_bounces = selection.getIntegerList("projectile-bounces");
                projectile_activateBounces = selection.getIntegerList("projectile-activate-bounces");
                projectile_limitGroup =  selection.getIntegerList("projectile-limit-group");

                projectile_expirationTicks = selection.getIntegerList("projectile-expiration-ticks");
                projectile_expirationExplode = selection.getBooleanList("projectile-expiration-explode");
                projectile_expirationChainWeapon = selection.getStringList("projectile-expiration-chain-weapon");

                projectile_hitTile = selection.getBooleanList("projectile-hit-tile");
                projectile_hitTileCoupled = selection.getBooleanList("projectile-hit-tile-coupled");
                projectile_hitTileChainWeapon = selection.getStringList("projectile-hit-tile-chain-weapon");

                projectile_hitShip = selection.getBooleanList("projectile-hit-ship");
                projectile_hitShipCoupled = selection.getBooleanList("projectile-hit-ship-coupled");
                projectile_hitShipChainWeapon = selection.getStringList("projectile-hit-ship-chain-weapon");

                projectile_proxDistance = selection.getIntegerList("projectile-prox-distance");
                projectile_proxExplodeTicks = selection.getIntegerList("projectile-prox-explode-ticks");
                projectile_proxChainWeapon = selection.getStringList("projectile-prox-chain-weapon");

                projectile_damage = selection.getIntegerList("projectile-damage");
                projectile_damageSplash = selection.getIntegerList("projectile-damage-splash");
                projectile_damageTeam = selection.getBooleanList("projectile-damage-team");
                projectile_damageSelf = selection.getBooleanList("projectile-damage-self");
                projectile_damageTeamKill = selection.getBooleanList("projectile-damage-team-kill");
                projectile_damageSelfKill = selection.getBooleanList("projectile-damage-self-kill");

                projectile_empTime = selection.getIntegerList("projectile-emp-time");
                projectile_empSplash = selection.getIntegerList("projectile-emp-splash");
                projectile_empTeam = selection.getBooleanList("projectile-emp-team");
                projectile_empSelf = selection.getBooleanList("projectile-emp-self");
                
                projectile_forceDistanceShip = selection.getIntegerList("projectile-force-distance-ship");
                projectile_forceVelocityShip = selection.getIntegerList("projectile-force-velocity-ship");
                projectile_forceDistanceProjectile = selection.getIntegerList("projectile-force-distance-projectile");
                projectile_forceVelocityProjectile = selection.getIntegerList("projectile-force-velocity-projectile");
        }
}
