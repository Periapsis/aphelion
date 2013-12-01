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

import aphelion.shared.gameconfig.ConfigSelection;
import aphelion.shared.gameconfig.GCBooleanList;
import aphelion.shared.gameconfig.GCInteger;
import aphelion.shared.gameconfig.GCIntegerList;
import aphelion.shared.gameconfig.GCString;
import aphelion.shared.gameconfig.GCStringList;
import aphelion.shared.gameconfig.WrappedValueAbstract;
import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.PhysicsMap;
import aphelion.shared.physics.PhysicsMath;
import aphelion.shared.physics.State;
import aphelion.shared.physics.valueobjects.PhysicsMoveable;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsPointHistory;
import aphelion.shared.physics.valueobjects.PhysicsPointHistoryDetailed;
import aphelion.shared.physics.valueobjects.PhysicsRotation;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.physics.valueobjects.PhysicsWarp;
import aphelion.shared.physics.WEAPON_SLOT;
import aphelion.shared.physics.valueobjects.*;
import aphelion.shared.physics.valueobjects.PhysicsPointHistorySmooth.SMOOTHING_ALGORITHM;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import aphelion.shared.swissarmyknife.LinkedListHead;
import aphelion.shared.swissarmyknife.RollingHistory;
import aphelion.shared.swissarmyknife.RollingHistorySerialInteger;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class Actor extends MapEntity
{
        private static final Logger log = Logger.getLogger("aphelion.shared.physics");
        public ActorPublicImpl publicWrapper;
        public final LinkedListHead<Projectile> projectiles = new LinkedListHead<>(); // fired by the actor
        
        public int pid;
        public long seed;
        public int seed_high;
        public int seed_low;
        public String ship;
        
        public final PhysicsRotation rot = new PhysicsRotation();
        
        public final PhysicsPointHistoryDetailed posHistoryDetailed;
        public final PhysicsPointHistory velHistory;
        public final PhysicsPointHistory rotHistory; // x = rotation, y = rotation snapped
        public final RollingHistory<PhysicsMoveable> moveHistory;
        public final PhysicsPointHistorySmooth smoothHistory;
        
        public long nextSwitchedWeaponFire_tick; // >=
        public WeaponConfig lastWeaponFire;
        
        
        // RollingHistorySerialInteger is a very specific optimalization for
        // a value that is updated very often! (energy). This way we can prevent
        // making a timewarp anytime something involving with energy is a bit late.
        
        // Use get(-1) for any energy requirement checking (this is so that a different 
        // execution order of energy modifications does not cause a timewarp)
        // use setXXX(-0) to substract this requirement
        public final RollingHistorySerialInteger energy; // * 1024
        public static enum ENERGY_SETTER
        {
                OTHER(0), // for stuff that is only resetable using a timewarp
                RECHARGE(1),
                BOOST_COST(2);
                
                public final int id; // array index

                private ENERGY_SETTER(int id)
                {
                        this.id = id;
                }
        }
        
        
        public Long empUntil_tick;
        
        public final RollingHistorySerialInteger dead;
        public Long spawnAt_tick;
        
        // IF AN ATTRIBUTE IS ADDED, DO NOT FORGET TO UPDATE resetTo()
        // (except config, publicWrapper, etc)
        
        // CONFIG
        public final ConfigSelection actorConfigSelection;
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
        public GCInteger smoothingDistanceLimit;
        
        public WeaponSlotConfig[] weaponSlots = new WeaponSlotConfig[WEAPON_SLOT.values().length];
        
        // Note: at the moment old weapon definitions that no longer exist are not removed
        public Map<String, WeaponConfig> weapons = new HashMap<>();
        
        public class WeaponSlotConfig implements WrappedValueAbstract.ChangeListener
        {
                public final WEAPON_SLOT slot;
                public GCString weaponKey;
                public WeaponConfig config;

                public WeaponSlotConfig(WEAPON_SLOT slot)
                {
                        this.slot = slot;
                        weaponKey = actorConfigSelection.getString(slot.settingName);
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
        
        public class WeaponConfig
        {
                public final ConfigSelection configSelection;
                public final String weaponKey;
                
                public long nextWeaponFire_tick = createdAt_tick;
                
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
                
                
                
                

                public WeaponConfig(String weaponKey)
                {
                        this.weaponKey = weaponKey;
                        this.configSelection = state.config.newSelection();
                        configSelection.selection.setWeapon(weaponKey);
                        configSelection.selection.setShip(ship);
                        
                        fireEnergy = configSelection.getInteger("weapon-fire-energy");
                        fireDelay = configSelection.getInteger("weapon-fire-delay");
                        switchDelay = configSelection.getInteger("weapon-switch-delay");
                        projectiles = configSelection.getInteger("weapon-projectiles");
                        fireProjectileLimit = configSelection.getIntegerList("weapon-fire-projectile-limit");
                        fireProjectileLimitGroup = configSelection.getIntegerList("weapon-fire-projectile-limit-group");
                        
                        projectile_offsetX = configSelection.getIntegerList("projectile-offset-x");
                        projectile_offsetY = configSelection.getIntegerList("projectile-offset-y");
                        projectile_angle = configSelection.getIntegerList("projectile-angle");
                        projectile_angleRelative = configSelection.getBooleanList("projectile-angle-relative");
                        projectile_bounceFriction = configSelection.getIntegerList("projectile-bounce-friction");
                        projectile_bounceOtherAxisFriction = configSelection.getIntegerList("projectile-bounce-friction-other-axis");
                        projectile_speed = configSelection.getIntegerList("projectile-speed");
                        projectile_speedRelative = configSelection.getBooleanList("projectile-speed-relative");
                        projectile_bounces = configSelection.getIntegerList("projectile-bounces");
                        projectile_activateBounces = configSelection.getIntegerList("projectile-activate-bounces");
                        projectile_limitGroup =  configSelection.getIntegerList("projectile-limit-group");
                        
                        projectile_expirationTicks = configSelection.getIntegerList("projectile-expiration-ticks");
                        projectile_expirationExplode = configSelection.getBooleanList("projectile-expiration-explode");
                        projectile_expirationChainWeapon = configSelection.getStringList("projectile-expiration-chain-weapon");
                        
                        projectile_hitTile = configSelection.getBooleanList("projectile-hit-tile");
                        projectile_hitTileCoupled = configSelection.getBooleanList("projectile-hit-tile-coupled");
                        projectile_hitTileChainWeapon = configSelection.getStringList("projectile-hit-tile-chain-weapon");
                        
                        projectile_hitShip = configSelection.getBooleanList("projectile-hit-ship");
                        projectile_hitShipCoupled = configSelection.getBooleanList("projectile-hit-ship-coupled");
                        projectile_hitShipChainWeapon = configSelection.getStringList("projectile-hit-ship-chain-weapon");
                        
                        projectile_proxDistance = configSelection.getIntegerList("projectile-prox-distance");
                        projectile_proxExplodeTicks = configSelection.getIntegerList("projectile-prox-explode-ticks");
                        projectile_proxChainWeapon = configSelection.getStringList("projectile-prox-chain-weapon");
                        
                        projectile_damage = configSelection.getIntegerList("projectile-damage");
                        projectile_damageSplash = configSelection.getIntegerList("projectile-damage-splash");
                        projectile_damageTeam = configSelection.getBooleanList("projectile-damage-team");
                        projectile_damageSelf = configSelection.getBooleanList("projectile-damage-self");
                        projectile_damageTeamKill = configSelection.getBooleanList("projectile-damage-team-kill");
                        projectile_damageSelfKill = configSelection.getBooleanList("projectile-damage-self-kill");
                        
                        projectile_empTime = configSelection.getIntegerList("projectile-emp-time");
                        projectile_empSplash = configSelection.getIntegerList("projectile-emp-splash");
                        projectile_empTeam = configSelection.getBooleanList("projectile-emp-team");
                        projectile_empSelf = configSelection.getBooleanList("projectile-emp-self");
                        
                        
                }
        }

        public Actor(State state, MapEntity[] crossStateList, int pid, long createdAt_tick)
        {
                // Move history is used so that reordered move operations (which occurs a lot)
                // do not cause continues time warps.
                // We have to store enough history for moves so that any delayed move can recalculate your position
                // Suppose there are 4 trailing states. State 0 (the most recent) would need to store 3 trailing states
                // worth of move history. 4 * TRAILING_STATE_DELAY is the maximum age of any operation executed on state 0.
                // 3 * TRAILING_STATE_DELAY would the max on state 1, et cetera.
                
                super(state, 
                        crossStateList, 
                        createdAt_tick, 
                        (state.econfig.TRAILING_STATES - state.id - 1) * state.econfig.TRAILING_STATE_DELAY + state.econfig.MINIMUM_HISTORY_TICKS);
                
                posHistoryDetailed = (PhysicsPointHistoryDetailed) this.posHistory;
                
                this.nextSwitchedWeaponFire_tick = createdAt_tick;
                
                this.pid = pid;
                
                moveHistory = new RollingHistory<>(createdAt_tick, HISTORY_LENGTH);
                velHistory = new PhysicsPointHistory(createdAt_tick, HISTORY_LENGTH);
                rotHistory = new PhysicsPointHistory(createdAt_tick, HISTORY_LENGTH);
                smoothHistory = new PhysicsPointHistorySmooth(posHistory, velHistory);
                
                // Do not smooth on the last state so that any inconsistencies will always be resolved
                smoothHistory.setAlgorithm(state.isLast ? SMOOTHING_ALGORITHM.NONE : SMOOTHING_ALGORITHM.LINEAR);
                
                energy = new RollingHistorySerialInteger(createdAt_tick, HISTORY_LENGTH, ENERGY_SETTER.values().length);
                energy.setMinimum(createdAt_tick, 0);
                dead = new RollingHistorySerialInteger(createdAt_tick, HISTORY_LENGTH, 1);
                dead.setMinimum(createdAt_tick, 0);
                dead.setMaximum(createdAt_tick, 1);
                
                actorConfigSelection = state.config.newSelection();
                
                this.radius = actorConfigSelection.getInteger("ship-radius");
                this.bounceFriction = actorConfigSelection.getInteger("ship-bounce-friction");
                this.bounceOtherAxisFriction = actorConfigSelection.getInteger("ship-bounce-friction-other-axis");
                this.rotationSpeed = actorConfigSelection.getInteger("ship-rotation-speed");
                this.rotationPoints = actorConfigSelection.getInteger("ship-rotation-points");
                this.speed = actorConfigSelection.getInteger("ship-speed");
                this.thrust = actorConfigSelection.getInteger("ship-thrust");
                this.boostSpeed = actorConfigSelection.getInteger("ship-boost-speed");
                this.boostThrust = actorConfigSelection.getInteger("ship-boost-thrust");
                this.boostEnergy = actorConfigSelection.getInteger("ship-boost-energy");
                this.maxEnergy = actorConfigSelection.getInteger("ship-energy");
                this.recharge = actorConfigSelection.getInteger("ship-recharge");
                this.spawnX = actorConfigSelection.getInteger("ship-spawn-x");
                this.spawnY = actorConfigSelection.getInteger("ship-spawn-y");
                this.spawnRadius = actorConfigSelection.getInteger("ship-spawn-radius");
                this.respawnDelay = actorConfigSelection.getInteger("ship-respawn-delay");
                this.smoothingAlgorithm = actorConfigSelection.getString("smoothing-algorithm");
                this.smoothingLookAheadTicks = actorConfigSelection.getInteger("smoothing-look-ahead-ticks");
                this.smoothingDistanceLimit = actorConfigSelection.getInteger("smoothing-distance-limit");
                
                smoothingAlgorithm.addWeakChangeListenerAndFire(smoothingChangeListener);
                smoothingLookAheadTicks.addWeakChangeListenerAndFire(smoothingChangeListener);
                smoothingDistanceLimit.addWeakChangeListenerAndFire(smoothingChangeListener);
                
                for (int slotId = 0; slotId < weaponSlots.length; ++slotId)
                {
                        weaponSlots[slotId] = new WeaponSlotConfig(WEAPON_SLOT.byId(slotId));
                }
                
                
                publicWrapper = new ActorPublicImpl(this, state);
        }
        
        private final WrappedValueAbstract.ChangeListener smoothingChangeListener = new WrappedValueAbstract.ChangeListener()
        {
                @Override
                public void gameConfigValueChanged(WrappedValueAbstract val)
                {
                        if (val == smoothingAlgorithm)
                        {
                                SMOOTHING_ALGORITHM algo = SMOOTHING_ALGORITHM.NONE;
                                String algoStr = smoothingAlgorithm.get();
                                try
                                {
                                        if (!algoStr.isEmpty())
                                        {
                                                algo = SMOOTHING_ALGORITHM.valueOf(algoStr);
                                        }
                                }
                                catch (IllegalArgumentException ex)
                                {
                                        log.log(Level.WARNING, "Given smoothing-algorithm algorithm {0} is not a known algorithm", smoothingAlgorithm.get());
                                }

                                smoothHistory.setAlgorithm(algo);
                        }
                        
                        if (val == smoothingLookAheadTicks)
                        {
                                smoothHistory.setLookAheadTicks(smoothingLookAheadTicks.get());
                        }
                        
                        if (val == smoothingDistanceLimit)
                        {
                                smoothHistory.setSmoothLimitDistance(smoothingDistanceLimit.get() * 1024);
                        }
                }
        };
        
        public void getSync(GameOperation.ActorSync.Builder s)
        {
                s.setPid(pid);
                s.setTick(state.tick_now);
                
                s.setX(pos.pos.x);
                s.setY(pos.pos.y);
                s.setXVel(pos.vel.x);
                s.setYVel(pos.vel.y);
                s.setRotation(rot.points);
                
                // position is synced using a warp packet
                s.setNextSwitchedWeaponFireTick(this.nextSwitchedWeaponFire_tick);
                
                for (WeaponConfig c : weapons.values())
                {
                        if (c.nextWeaponFire_tick < state.tick_now)
                        {
                                continue; // no need to send
                        }
                        
                        
                        s.addNextWeaponFireKey(c.weaponKey);
                        s.addNextWeaponFireTick(c.nextWeaponFire_tick);
                }
                
                
                if (lastWeaponFire == null)
                {
                        s.clearLastWeaponFire();
                }
                else
                {
                        s.setLastWeaponFire(lastWeaponFire.weaponKey);
                }
                
                s.setEnergy(energy.get(state.tick_now));
                
                if (empUntil_tick == null)
                {
                        s.clearEmpUntilTick();
                }
                else
                {
                        s.setEmpUntilTick(this.empUntil_tick);
                }
                
                s.setDead(dead.get(state.tick_now) == 1);
                
                if (this.spawnAt_tick == null)
                {
                        s.clearSpawnAtTick();
                }
                else
                {
                        s.setSpawnAtTick(this.spawnAt_tick);
                }
                
        }
        
        private boolean tmp_initFromSync_dirty;
        private <T> T initFromSync_set(T oldVal, T newVal)
        {
                if (!tmp_initFromSync_dirty)
                {
                        boolean changed = !Objects.equals(oldVal, newVal);
                        tmp_initFromSync_dirty = changed;
                }
                return newVal;
        }
        
        public boolean initFromSync(GameOperation.ActorSync s)
        {
                tmp_initFromSync_dirty = false;
                long operation_tick = s.getTick();
                assert operation_tick <= state.tick_now;
                
                // make sure  the energy used for matching does not get modified by a side effect
                int myEnergy = this.energy.get(operation_tick);
                
                this.nextSwitchedWeaponFire_tick = initFromSync_set(this.nextSwitchedWeaponFire_tick, s.getNextSwitchedWeaponFireTick());
                
                
                PhysicsWarp warp = new PhysicsWarp(s.getX(), s.getY(), s.getXVel(), s.getYVel(), s.getRotation());
                PhysicsShipPosition currentPos = new PhysicsShipPosition();
                this.getHistoricPosition(currentPos, operation_tick, false);
                if (!warp.equalsShipPosition(currentPos))
                {
                        // incorrect position
                        tmp_initFromSync_dirty = true;
                        
                        this.moveHistory.setHistory(operation_tick, warp);
                        this.applyMoveable(warp, operation_tick); // sets the current position
                        this.updatePositionHistory(operation_tick);
                        
                        // dead reckon current position so that it is no longer late
                        // the position at the tick of this operation should not be dead reckoned, therefor +1
                        this.performDeadReckoning(state.env.getMap(), operation_tick + 1, state.tick_now - operation_tick);
                }
                
                for (int i = 0; i < s.getNextWeaponFireKeyCount(); ++i)
                {
                        WeaponConfig c = this.getWeaponConfig(s.getNextWeaponFireKey(i));
                        try
                        {
                                c.nextWeaponFire_tick = initFromSync_set(c.nextWeaponFire_tick, s.getNextWeaponFireTick(i));
                        }
                        catch (IndexOutOfBoundsException ex)
                        {
                                log.log(Level.SEVERE, "", ex);
                        }
                }
                
                if (s.hasLastWeaponFire())
                {
                        this.lastWeaponFire = initFromSync_set(this.lastWeaponFire, this.getWeaponConfig(s.getLastWeaponFire()));
                }
                else
                {
                        this.lastWeaponFire = initFromSync_set(this.lastWeaponFire, null);
                }
                
                if (myEnergy != s.getEnergy())
                {
                        this.energy.setAbsoluteOverrideValue(ENERGY_SETTER.OTHER.id, operation_tick, s.getEnergy());
                        tmp_initFromSync_dirty = true;
                }
                
                if (s.hasEmpUntilTick())
                {
                        this.empUntil_tick = initFromSync_set(this.empUntil_tick, s.getEmpUntilTick());
                }
                else
                {
                        this.empUntil_tick = initFromSync_set(this.empUntil_tick, null);
                }
                
                
                this.dead.setAbsoluteValue(0, operation_tick, s.getDead() ? 1 : 0);
                
                if (s.hasSpawnAtTick())
                {
                        this.spawnAt_tick = initFromSync_set(this.spawnAt_tick, s.getSpawnAtTick());
                }
                else
                {
                        this.spawnAt_tick = initFromSync_set(this.spawnAt_tick, null);
                }
                
                return tmp_initFromSync_dirty;
        }
        
        public boolean isDead(long tick)
        {
                return dead.get(tick) == 1;
        }
        
        public void died(long tick)
        {
                dead.setAbsoluteValue(0, tick, 1);
                spawnAt_tick = tick + respawnDelay.get();
                if (spawnAt_tick <= tick)
                {
                        // can not be respawned in this tick (or in the past)
                        spawnAt_tick = tick + 1;
                }
        }
        
        /** Perform dead reckoning on this actor for the specified number of ticks.
         * 
         * @param map
         * @param tick_now The first tick to apply to
         * @param reckon_ticks
         */
        public void performDeadReckoning(PhysicsMap map, long tick_now, long reckon_ticks)
        {        
                for (long t = 0; t < reckon_ticks; ++t)
                {
                        long tick = tick_now + t;
                        
                        int prevEnergy = this.energy.get(tick - 1);
                        PhysicsMoveable prevMove = this.getHistoricMovement(tick - 1, false);
                        
                        
                        boolean prevBoost = 
                                prevMove instanceof PhysicsMovement
                                && ((PhysicsMovement) prevMove).isValidBoost()
                                && prevEnergy >= this.boostEnergy.get();
                        
                        this.pos.vel.limitLength(
                                prevBoost && this.boostSpeed.isSet() 
                                ? this.boostSpeed.get() 
                                : this.speed.get());
                        
                        this.pos.vel.enforceOverflowLimit();
                        
                        state.collision.deadReckonTick(
                                tick, 
                                posHistory,
                                pos.pos, pos.vel, 
                                map, true,
                                null, null,
                                radius.get(), 
                                bounceFriction.get(), 
                                bounceOtherAxisFriction.get(),
                                -1, 0);
                        
                        // Any speed increase applied during this tick is not used until the next tick
                        
                        
                        applyMoveable(moveHistory.get(tick), tick);
                        
                        updatePositionHistory(tick);
                }
        }
        
        public void setShip(String ship)
        {
                if (Objects.equals(ship, this.ship))
                {
                        return;
                }
                
                this.ship = ship;
                
                this.actorConfigSelection.selection.setShip(ship);
                this.actorConfigSelection.resolveAllValues();
                for (WeaponConfig weapon : this.weapons.values())
                {
                        weapon.configSelection.selection.setShip(ship);
                        weapon.configSelection.resolveAllValues();
                }
        }
        
        public void applyMoveable(PhysicsMoveable move_, long tick)
        {
                // reset the boost cost for this tick
                this.energy.setRelativeValue(
                        ENERGY_SETTER.BOOST_COST.id,
                        tick,
                        0
                        );
                
                if (move_ == null)
                {
                        return;
                }
                
                if (move_ instanceof PhysicsWarp)
                {
                        PhysicsWarp warp = (PhysicsWarp) move_;
                        
                        if (warp.has_x)
                        {
                                pos.pos.x = warp.x;
                        }

                        if (warp.has_y)
                        {
                                pos.pos.y = warp.y;
                        }

                        if (warp.has_x_vel)
                        {
                                pos.vel.x = warp.x_vel;
                        }

                        if (warp.has_y_vel)
                        {
                                pos.vel.y = warp.y_vel;
                        }

                        if (warp.has_rotation)
                        {
                                rot.points = warp.rotation;
                                rot.snap(this.rotationPoints.get());
                        }
                }
                else if (move_ instanceof PhysicsMovement)
                {
                        PhysicsMovement move = (PhysicsMovement) move_;
                        if (move.left || move.right)
                        {
                                // rotationSpeed is never larger than ROTATION_POINTS
                                if (move.left)
                                {
                                        this.rot.points -= this.rotationSpeed.get();
                                }

                                if (move.right)
                                {
                                        this.rot.points += this.rotationSpeed.get();
                                }

                                this.rot.points %= PhysicsEnvironment.ROTATION_POINTS;

                                if (this.rot.points < 0)
                                {
                                        this.rot.points += PhysicsEnvironment.ROTATION_POINTS;
                                }

                                this.rot.snapped = PhysicsMath.snapRotation(this.rot.points, this.rotationPoints.get());
                        }

                        if (move.up || move.down)
                        {
                                int thrust;

                                // Base the boost requirement on the previous tick (tick - 1),
                                // because the energy for this tick may not have been defined fully yet.
                                // Using "tick - 0" could result in inconsistencies with different states
                                // if this movement is not executed in the same order in relation to other 
                                // energy modifiers.
                                if (move.boost && this.energy.get(tick - 1) >= this.boostEnergy.get())
                                {
                                        thrust = this.boostThrust.isSet() ? this.boostThrust.get() : this.thrust.get();

                                        this.energy.setRelativeValue(
                                                ENERGY_SETTER.BOOST_COST.id,
                                                tick,
                                                -this.boostEnergy.get()
                                                );

                                        //System.out.println(tick + " " + state.id + " " + this.energy.get(tick));
                                }
                                else
                                {
                                        thrust = this.thrust.get();
                                }

                                if (move.up)
                                {
                                        PhysicsMath.rotationToPoint(this.pos.vel, this.rot.points, thrust);
                                }

                                if (move.down)
                                {
                                        PhysicsMath.rotationToPoint(this.pos.vel, this.rot.points, -thrust);
                                }


                                // vel may overflow here if thrust is extremely high, but that is ok
                                // just make sure other stuff does not overflow as a result
                                this.pos.vel.enforceOverflowLimit();
                        }
                }
                else
                {
                        assert false;
                }
        }

        @Override
        public void updatePositionHistory(long tick)
        {
                try
                {
                        super.updatePositionHistory(tick);
                }
                catch(IllegalStateException ex)
                {
                        log.log(Level.SEVERE, "Error setting the history for the actor {0}", this.pid);
                        throw ex;
                }
                velHistory.setHistory(tick, pos.vel);
                rotHistory.setHistory(tick, rot.points, rot.snapped);
        }

        @Override
        public void resetTo(State myState, MapEntity other_)
        {
                super.resetTo(myState, other_);
                
                Actor other = (Actor) other_;
                
                assert pid == other.pid;
                
                seed = other.seed;
                seed_high = other.seed_high;
                seed_low = other.seed_low;
                
                rot.set(other.rot);
                
                setShip(other.ship);
                
                velHistory.set(other.velHistory);
                rotHistory.set(other.rotHistory);
                moveHistory.set(other.moveHistory);

                if (pid != publicWrapper.pid)
                {
                        publicWrapper = new ActorPublicImpl(this, myState);
                }
                
                
                nextSwitchedWeaponFire_tick = other.nextSwitchedWeaponFire_tick;
                this.lastWeaponFire = null;
                for (WeaponConfig otherConfig : other.weapons.values())
                {
                        WeaponConfig myConfig = this.getWeaponConfig(otherConfig.weaponKey); // never returns null
                        myConfig.nextWeaponFire_tick = otherConfig.nextWeaponFire_tick;
                        
                        if (otherConfig == other.lastWeaponFire)
                        {
                                this.lastWeaponFire = myConfig;
                        }
                        
                }
                
                energy.set(other.energy);
                empUntil_tick = other.empUntil_tick;
                dead.set(other.dead);
                spawnAt_tick = other.spawnAt_tick;
        }
        
        public void resetToEmpty(long tick)
        {
                // use an empty actor to reset everything to default values
                
                Actor dummy = new Actor(this.state, crossStateList, pid, tick);
                crossStateList[this.state.id] = null; // skip assertion in resetTo
                this.resetTo(this.state, dummy);
                crossStateList[this.state.id] = (MapEntity) this;
        }
        
        public boolean canFireWeapon(WEAPON_SLOT weapon, long tick)
        {
                Actor.WeaponConfig config = this.weaponSlots[weapon.id].config;
                if (isDead(tick))
                {
                        return false;
                }

                if (!this.weaponSlots[weapon.id].isValidWeapon())
                {
                        return false;
                }
                
                if (weaponSlots[weapon.id].config.projectile_expirationTicks.get(0, this.configSeed(tick)) <= 0)
                {
                        return false;
                }
                
                // History is not tracked for fire delay; this is fine, use a timewarp for late weapons.
                // (this is very rare because the input code does not send a weapon fire packet
                //  to the server if the player does not have the proper amount of energy)
                
                if (tick < this.weaponSlots[weapon.id].config.nextWeaponFire_tick)
                {
                        // reload per weapon
                        return false;
                }
                
                if (this.weaponSlots[weapon.id].config != this.lastWeaponFire)
                {
                        if (tick < this.nextSwitchedWeaponFire_tick)
                        {
                                // weapon switch delay
                                return false;
                        }
                }
                
                if (this.energy.get(tick - 1) < weaponSlots[weapon.id].config.fireEnergy.get() * 1024)
                {
                        return false;
                }
                
                if (config.fireProjectileLimit.isSet())
                {
                        for (int i = 0; i < config.fireProjectileLimit.getValuesLength(); ++i)
                        {
                                int count = findProjectileCount(tick, config.fireProjectileLimitGroup.get(i, this.configSeed(tick)), null);
                                if (count >= config.fireProjectileLimit.get(i, this.configSeed(tick)))
                                {
                                        return false; // too many
                                }
                        }
                }
                
                return true;
        }
        
        public int getMaxEnergy()
        {
                int nrg = this.maxEnergy.get() * 1024;
                if (nrg < 1) { nrg = 1; }
                return nrg;
        }
        
        public void tickEnergy()
        {
                int effectiveRecharge = this.recharge.get();
                if (isDead(state.tick_now))
                {
                        effectiveRecharge = 0;
                }
                
                if (this.empUntil_tick != null && state.tick_now <= this.empUntil_tick)
                {
                        effectiveRecharge = 0;
                }
                
                energy.setRelativeValue(ENERGY_SETTER.RECHARGE.id, state.tick_now, effectiveRecharge);
                energy.setMinimum(state.tick_now, 0);
                energy.setMaximum(state.tick_now, this.getMaxEnergy());
        }
        
        public Actor getOlderActor(long tick, boolean ignoreSoftDelete, boolean lookAtOtherStates)
        {
                return (Actor) getOlderEntity(tick, ignoreSoftDelete, lookAtOtherStates);
        }
        
        public boolean getHistoricPosition(PhysicsShipPosition pos, long tick, boolean lookAtOlderStates)
        {
                pos.set = false;
                
                Actor actor = getOlderActor(tick, false, lookAtOlderStates);

                if (actor == null)
                {
                        return false; // deleted or ticks go back way too far
                }
                
                pos.x           = actor.posHistory.getX(tick);
                pos.y           = actor.posHistory.getY(tick);
                pos.x_vel       = actor.velHistory.getX(tick);
                pos.y_vel       = actor.velHistory.getY(tick);
                pos.smooth_x    = actor.smoothHistory.getX(tick);
                pos.smooth_y    = actor.smoothHistory.getY(tick);
                pos.rot         = actor.rotHistory.getX(tick);
                pos.rot_snapped = actor.rotHistory.getY(tick);
                pos.set = true;
                return true;  
        }
        
        public int getHistoricEnergy(long tick, boolean lookAtOlderStates)
        {
                Actor actor = getOlderActor(tick, false, lookAtOlderStates);

                if (actor == null)
                {
                        return 0; // deleted or ticks go back way too far
                }
                
                return actor.energy.get(tick);
        }
        
        public PhysicsMoveable getHistoricMovement(long tick, boolean lookAtOlderStates)
        {
                Actor actor = getOlderActor(tick, false, lookAtOlderStates);

                if (actor == null)
                {
                        return null; // deleted or ticks go back way too far
                }
                
                return actor.moveHistory.get(tick);
        }
        
        public WeaponConfig getWeaponConfig(String key)
        {
                WeaponConfig config = weapons.get(key);
                if (config == null)
                {
                        config = new WeaponConfig(key);
                        weapons.put(key, config);
                }
                return config;
        }
        
        public int randomRotation(long tick)
        {
                int tick_low = (int) tick; // low bits
                
                int hash = SwissArmyKnife.jenkinMix(seed_high, seed_low, tick_low);
                
                return Math.abs(hash) % PhysicsEnvironment.ROTATION_POINTS;
        }
        
        public void findSpawnPoint(PhysicsPoint resultTile, long tick)
        {
                int tileRadius = radius.get() * 2 / PhysicsMap.TILE_PIXELS;
                
                state.findRandomPointOnMap(
                        resultTile, 
                        tick, 
                        seed, 
                        spawnX.get(), spawnY.get(), spawnRadius.get(), tileRadius);
        }
        
        public void applyEmp(long tick, int empTime)
        {
                if (empTime > 0)
                {
                        long until = tick + empTime;
                        if (this.empUntil_tick == null || until > this.empUntil_tick)
                        {
                                this.empUntil_tick = until;
                        }
                        
                        
                        // remove recharge
                        for (long t = tick; t <= this.empUntil_tick && t <= energy.getMostRecentTick(); ++t)
                        {
                                energy.setRelativeValue(ENERGY_SETTER.RECHARGE.id, t, 0);
                        }
                }
        }
        
        public int findProjectileCount(long tick, int projectile_limit_group, Projectile[] oldest)
        {
                int count = 0;
                
                for (LinkedListEntry<Projectile> e = this.projectiles.first;e != null; e = e.next)
                {
                        Projectile proj = e.data;
                        int group = proj.cfg(proj.config.projectile_limitGroup, tick);
                        if (group != projectile_limit_group)
                        {
                                continue;
                        }
                        
                        if (proj.createdAt_tick > tick)
                        {
                                continue;
                        }
                        
                        if (proj.isRemoved(tick))
                        {
                                continue;
                        }
                        
                        ++count;

                        if (oldest != null)
                        {
                                for (int i = 0; i < oldest.length; ++i)
                                {
                                        if (oldest[i] == null)
                                        {
                                                oldest[i] = proj;
                                        }
                                        else if (proj.createdAt_tick < oldest[i].createdAt_tick)
                                        {
                                                oldest[i] = proj;
                                        }
                                }
                        }

                }
                
                return count;
        }
        
        public int configSeed(long tick)
        {
                return this.seed_low ^ ((int) tick);
        }
}
