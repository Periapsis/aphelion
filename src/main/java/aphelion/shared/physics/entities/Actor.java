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

import aphelion.shared.gameconfig.*;
import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.physics.*;
import aphelion.shared.physics.valueobjects.PhysicsMoveable;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsPointHistory;
import aphelion.shared.physics.valueobjects.PhysicsPointHistoryDetailed;
import aphelion.shared.physics.valueobjects.PhysicsRotation;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.physics.valueobjects.PhysicsWarp;
import aphelion.shared.physics.valueobjects.*;
import aphelion.shared.physics.valueobjects.PhysicsPointHistorySmooth.SMOOTHING_ALGORITHM;
import aphelion.shared.swissarmyknife.*;
import java.util.Iterator;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 *
 * @author Joris
 */
public class Actor extends MapEntity
{
        private static final Logger log = Logger.getLogger("aphelion.shared.physics");
        public ActorPublicImpl publicWrapper;
        public final LinkedListHead<Projectile> projectiles = new LinkedListHead<>(); // fired by the actor
        
        public final int pid;
        public final ActorKey key;
        public long seed;
        public int seed_high;
        public int seed_low;
        public String ship;
        
        public final PhysicsRotation rot = new PhysicsRotation();
        
        public final PhysicsPointHistoryDetailed posHistoryDetailed;
        public final PhysicsPointHistory rotHistory; // x = rotation, y = rotation snapped
        public final RollingHistory<PhysicsMoveable> moveHistory;
        private long mostRecentMove_tick;
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
        
        public final ActorConfig config;

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
                key = new ActorKey(this.pid);
                
                moveHistory = new RollingHistory<>(createdAt_tick, HISTORY_LENGTH);
                this.mostRecentMove_tick = this.createdAt_tick;
                rotHistory = new PhysicsPointHistory(createdAt_tick, HISTORY_LENGTH);
                smoothHistory = new PhysicsPointHistorySmooth(createdAt_tick, posHistory, velHistory);
                
                // Never smooth on the last state (unless this is the only state that exists) 
                // so that any inconsistencies will always be resolved
                if (state.isLast && state.id > 0)
                {
                        smoothHistory.setAlgorithm(SMOOTHING_ALGORITHM.NONE);
                }
                else
                {
                        smoothHistory.setAlgorithm(state.econfig.POSITION_SMOOTHING ? SMOOTHING_ALGORITHM.LINEAR : SMOOTHING_ALGORITHM.NONE);
                }
                
                
                energy = new RollingHistorySerialInteger(createdAt_tick, HISTORY_LENGTH, ENERGY_SETTER.values().length);
                energy.setMinimum(createdAt_tick, 0);
                dead = new RollingHistorySerialInteger(createdAt_tick, HISTORY_LENGTH, 1);
                dead.setMinimum(createdAt_tick, 0);
                dead.setMaximum(createdAt_tick, 1);
                
                config = new ActorConfig(this);
                this.radius = config.radius;
                config.smoothingAlgorithm.addWeakChangeListenerAndFire(smoothingChangeListener);
                config.smoothingLookAheadTicks.addWeakChangeListenerAndFire(smoothingChangeListener);
                config.smoothingStepRatio.addWeakChangeListenerAndFire(smoothingChangeListener);
                config.smoothingDistanceLimit.addWeakChangeListenerAndFire(smoothingChangeListener);
                
                publicWrapper = new ActorPublicImpl(this, state);
        }
        
        private final WrappedValueAbstract.ChangeListener smoothingChangeListener = new WrappedValueAbstract.ChangeListener()
        {
                @Override
                public void gameConfigValueChanged(WrappedValueAbstract val)
                {
                        if (val == config.smoothingAlgorithm)
                        {
                                SMOOTHING_ALGORITHM algo = SMOOTHING_ALGORITHM.NONE;
                                String algoStr = config.smoothingAlgorithm.get();
                                try
                                {
                                        if (!algoStr.isEmpty())
                                        {
                                                algo = SMOOTHING_ALGORITHM.valueOf(algoStr);
                                        }
                                }
                                catch (IllegalArgumentException ex)
                                {
                                        log.log(Level.WARNING, "Given smoothing-algorithm algorithm {0} is not a known algorithm", config.smoothingAlgorithm.get());
                                }

                                smoothHistory.setAlgorithm(algo);
                        }
                        
                        if (val == config.smoothingLookAheadTicks)
                        {
                                smoothHistory.setLookAheadTicks(config.smoothingLookAheadTicks.get());
                        }
                        
                        if (val == config.smoothingStepRatio)
                        {
                                smoothHistory.setStepRatio(config.smoothingStepRatio.get());
                        }
                        
                        if (val == config.smoothingDistanceLimit)
                        {
                                smoothHistory.setSmoothLimitDistance(config.smoothingDistanceLimit.get() * 1024);
                        }
                }
        };

        @Override
        public void hardRemove(long tick)
        {
                super.hardRemove(tick);
                state.actors.remove(this.key);
                state.actorsList.remove(this);
        }
        
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
                
                for (WeaponConfig c : config.weapons.values())
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
                        this.updatedPosition(operation_tick);
                        
                        // dead reckon current position so that it is no longer late
                        // the position at the tick of this operation should not be dead reckoned, therefor +1
                        this.performDeadReckoning(state.env.getMap(), operation_tick + 1, state.tick_now - operation_tick);
                }
                
                for (int i = 0; i < s.getNextWeaponFireKeyCount(); ++i)
                {
                        WeaponConfig c = config.getWeaponConfig(s.getNextWeaponFireKey(i));
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
                        this.lastWeaponFire = initFromSync_set(this.lastWeaponFire, config.getWeaponConfig(s.getLastWeaponFire()));
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
                spawnAt_tick = tick + config.respawnDelay.get();
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
        @Override
        public void performDeadReckoning(PhysicsMap map, long tick_now, long reckon_ticks)
        {
                Collision collision = state.collision;
                collision.reset();
                collision.setMap(map);
                collision.setRadius(this.radius.get());
                collision.setCollideGrid(state.entityGrid);
                collision.setCollideFilter(collideFilter);
                collision.setBounceFriction(config.bounceFriction.get());
                collision.setOtherAxisFriction(config.bounceOtherAxisFriction.get());
                
                final PhysicsPoint prevForce = new PhysicsPoint();
                
                for (long t = 0; t < reckon_ticks; ++t)
                {
                        long tick = tick_now + t;
                        
                        dirtyPositionPathTracker.resolved(tick);
                        assert !dirtyPositionPathTracker.isDirty(tick) : "performDeadReckoning: Skipped a tick!";
                        
                        final boolean dead = this.isDead(tick);
                        
                        int prevEnergy = this.energy.get(tick - 1);
                        PhysicsMoveable prevMove = this.getHistoricMovement(tick - 1, false);
                        
                        // force for _this_ tick is added AFTER we dead reckon.
                        // so wait for the next tick to do the force for _this_ tick.
                        // otherwise all entities have to be looped an extra time.
                        this.forceHistory.get(prevForce, tick - 1);
                        
                        
                        boolean prevBoost = 
                                prevMove instanceof PhysicsMovement
                                && ((PhysicsMovement) prevMove).isValidBoost()
                                && prevEnergy >= config.boostEnergy.get();
                        
                        this.pos.vel.add(prevForce);
                        
                        this.pos.vel.limitLength(
                                prevBoost && config.boostSpeed.isSet() 
                                ? config.boostSpeed.get() 
                                : config.speed.get());
                        
                        this.pos.vel.enforceOverflowLimit();
                        
                        collision.setPreviousPosition(pos.pos);
                        collision.setPosHistoryDetails(this.posHistoryDetailed);
                        collision.setVelocity(pos.vel);
                        collision.tickMap(tick);
                        collision.getNewPosition(pos.pos);
                        collision.getVelocity(pos.vel);
                        
                        if (!dead)
                        {
                                if (this.useSmoothForCollision(tick))
                                {
                                        final PhysicsPoint point = new PhysicsPoint();

                                        this.getHistoricSmoothPosition(point, tick - 1, false);
                                        collision.setPreviousPosition(point);

                                        collision.setPosHistoryDetails(null); // this would not work properly when smoothed

                                        this.getHistoricSmoothPosition(point, tick, false);
                                        collision.setNewPosition(point);
                                }
                                else
                                {
                                        // the prevPos, newPos and posHistoryDetails are still set correctly on Collision,
                                        // in order to properly execute tickEntityCollision
                                }
                                collision.tickEntityCollision(tick);

                                Iterator<Collision.HitData> it = collision.getHitEntities();
                                while (it.hasNext())
                                {
                                        Collision.HitData hit = it.next();
                                        
                                        if (hit.entity instanceof Projectile)
                                        {
                                                Projectile proj = (Projectile)hit.entity;
                                                proj.hitByActor(tick, this, hit.location);
                                                
                                                if (this.isDead(tick))
                                                {
                                                        break; // just died, no more hits
                                                }
                                        }
                                        else
                                        {
                                                log.log(Level.WARNING, "Hit something unexpected");
                                        }
                                }

                                // Any speed increase applied during this tick is not used until the next tick
                                applyMoveable(moveHistory.get(tick), tick);
                        }
                        
                        updatedPosition(tick);
                }
        }
        
        private final LoopFilter<MapEntity, Long> collideFilter = new LoopFilter<MapEntity, Long>()
        {
                @Override
                public boolean loopFilter(MapEntity en, Long tick)
                {
                        if (!(en instanceof Projectile))
                        {
                                return true; // skip
                        }
                        
                        Projectile proj = (Projectile) en;
                        
                        return !proj.collideShip 
                            || proj.owner == Actor.this
                            || proj.activateBouncesLeft > 0;
                }
        };
        
        public void setShip(String ship)
        {
                if (Objects.equals(ship, this.ship))
                {
                        return;
                }
                
                this.ship = ship;
                
                config.selection.selection.setShip(ship);
                config.selection.resolveAllValues();
                for (WeaponConfig weapon : config.weapons.values())
                {
                        weapon.selection.selection.setShip(ship);
                        weapon.selection.resolveAllValues();
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
                
                if (tick > mostRecentMove_tick)
                {
                        mostRecentMove_tick = tick;
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
                                rot.snap(config.rotationPoints.get());
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
                                        this.rot.points -= config.rotationSpeed.get();
                                }

                                if (move.right)
                                {
                                        this.rot.points += config.rotationSpeed.get();
                                }

                                this.rot.points %= EnvironmentConf.ROTATION_POINTS;

                                if (this.rot.points < 0)
                                {
                                        this.rot.points += EnvironmentConf.ROTATION_POINTS;
                                }

                                this.rot.snapped = PhysicsMath.snapRotation(this.rot.points, config.rotationPoints.get());
                        }

                        if (move.up || move.down)
                        {
                                int thrust;

                                // Base the boost requirement on the previous tick (tick - 1),
                                // because the energy for this tick may not have been defined fully yet.
                                // Using "tick - 0" could result in inconsistencies with different states
                                // if this movement is not executed in the same order in relation to other 
                                // energy modifiers.
                                if (move.boost && this.energy.get(tick - 1) >= config.boostEnergy.get())
                                {
                                        thrust = config.boostThrust.isSet() ? config.boostThrust.get() : config.thrust.get();

                                        this.energy.setRelativeValue(
                                                ENERGY_SETTER.BOOST_COST.id,
                                                tick,
                                                -config.boostEnergy.get()
                                                );

                                        //System.out.println(tick + " " + state.id + " " + this.energy.get(tick));
                                }
                                else
                                {
                                        thrust = config.thrust.get();
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
        public void updatedPosition(long tick)
        {
                try
                {
                        super.updatedPosition(tick);
                }
                catch(IllegalStateException ex)
                {
                        log.log(Level.SEVERE, "Error setting the history for the actor {0}", this.pid);
                        throw ex;
                }
                rotHistory.setHistory(tick, rot.points, rot.snapped);
        }

        public @Nullable Actor findInOtherState(State otherState)
        {
                if (this.state.isForeign(otherState))
                {
                        return otherState.actors.get(this.key);
                }
                else
                {
                        return (Actor) this.crossStateList[otherState.id];
                }
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
                
                rotHistory.set(other.rotHistory);
                moveHistory.set(other.moveHistory);
                mostRecentMove_tick = other.mostRecentMove_tick;

                if (pid != publicWrapper.pid)
                {
                        publicWrapper = new ActorPublicImpl(this, myState);
                }
                
                
                nextSwitchedWeaponFire_tick = other.nextSwitchedWeaponFire_tick;
                this.lastWeaponFire = null;
                for (WeaponConfig otherConfig : other.config.weapons.values())
                {
                        WeaponConfig myConfig = config.getWeaponConfig(otherConfig.weaponKey); // never returns null
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
                return canFireWeapon(weapon, tick, false);
        }
        
        /** Check if the actor is able to fire thew weapon at the given tick.
         * 
         * @param weapon
         * @param tick
         * @param weakCheck If set, do not check for conditions that are likely to be 
         *        (temporarily) incorrect when dealing with networking. 
         *        For example when energy of an enemy is decreased by hitting him, 
         *        his weapon might not be able to fire. A timewarp then decides the
         *        enemy was not hit after all, and the weapon is spawned. Therefor 
         *        energy is not included if weakCheck=true.
         *                   
         * @return true if the given weapon is able to fire.
         */
        public boolean canFireWeapon(WEAPON_SLOT weapon, long tick, boolean weakCheck)
        {
                ActorConfig.WeaponSlotConfig slot = this.config.weaponSlots[weapon.id];
                
                if (isDead(tick))
                {
                        return false;
                }

                if (!slot.isValidWeapon())
                {
                        return false;
                }
                
                if (slot.config.projectile_expirationTicks.get(0, this.configSeed(tick)) <= 0)
                {
                        return false;
                }
                
                // History is not tracked for fire delay; this is fine, use a timewarp for late weapons.
                // (this is very rare because the input code does not send a weapon fire packet
                //  to the server if the player does not have the proper amount of energy)
                
                if (tick < slot.config.nextWeaponFire_tick)
                {
                        // reload per weapon
                        return false;
                }
                
                if (slot.config != this.lastWeaponFire)
                {
                        if (tick < this.nextSwitchedWeaponFire_tick)
                        {
                                // weapon switch delay
                                return false;
                        }
                }
                
                if (weakCheck)
                {
                        return true;
                }
                
                if (this.energy.get(tick - 1) < this.config.weaponSlots[weapon.id].config.fireEnergy.get() * 1024)
                {
                        return false;
                }
                
                if (slot.config.fireProjectileLimit.isSet())
                {
                        for (int i = 0; i < slot.config.fireProjectileLimit.getValuesLength(); ++i)
                        {
                                int count = findProjectileCount(tick, slot.config.fireProjectileLimitGroup.get(i, this.configSeed(tick)), null);
                                if (count >= slot.config.fireProjectileLimit.get(i, this.configSeed(tick)))
                                {
                                        return false; // too many
                                }
                        }
                }
                
                return true;
        }
        
        public int getMaxEnergy()
        {
                int nrg = config.maxEnergy.get() * 1024;
                if (nrg < 1) { nrg = 1; }
                return nrg;
        }
        
        public void tickEnergy()
        {
                int effectiveRecharge = config.recharge.get();
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
        
        /** {@inheritDoc} */
        @Override
        public boolean getHistoricSmoothPosition(PhysicsPoint pos, long tick, boolean lookAtOtherStates)
        {
                pos.set = false;
                
                Actor actor = getOlderActor(tick, false, lookAtOtherStates);

                if (actor == null)
                {
                        return false; // deleted or too far in the past
                }
                
                actor.smoothHistory.getSmooth(pos, tick);
                return pos.set;
        }
        
        private boolean useSmoothForCollision(long tick)
        {
                if (state.isLast)
                {
                        // The last state should consistent across all servers and clients
                        // Smoothed position is not consistent because it does its magic by
                        // relying on the order moves are received.
                        return false;
                }
                
                // No need to use smoothed if we have up to date movement data
                // This implementation might cause jumpiness, for example, if:
                // 1. now = tick 120
                // 2. A move has been received at tick 100
                // Moments later:
                // 3. A move has been received at tick 90
                // This should not occur often because move packets overlap each other.
                if (tick > mostRecentMove_tick)
                {
                        return false;
                }
                
                return config.smoothingProjectileCollisions.get();
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
        
        public int randomRotation(long tick)
        {
                int tick_low = (int) tick; // low bits
                
                int hash = SwissArmyKnife.jenkinMix(seed_high, seed_low, tick_low);
                
                return Math.abs(hash) % EnvironmentConf.ROTATION_POINTS;
        }
        
        public void findSpawnPoint(PhysicsPoint resultTile, long tick)
        {
                int tileRadius = radius.get() * 2 / PhysicsMap.TILE_PIXELS;
                
                state.findRandomPointOnMap(
                        resultTile, 
                        tick, 
                        seed, 
                        config.spawnX.get(), config.spawnY.get(), config.spawnRadius.get(), tileRadius);
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
