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
import aphelion.shared.gameconfig.GCIntegerFixed;
import aphelion.shared.gameconfig.GCIntegerList;
import aphelion.shared.gameconfig.GCString;
import aphelion.shared.gameconfig.GCStringList;
import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.physics.*;
import aphelion.shared.physics.events.ProjectileExplosion;
import aphelion.shared.physics.events.pub.ProjectileExplosionPublic;
import aphelion.shared.physics.events.pub.ProjectileExplosionPublic.EXPLODE_REASON;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsPositionVector;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.AttachmentData;
import aphelion.shared.swissarmyknife.AttachmentManager;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import aphelion.shared.swissarmyknife.LoopFilter;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import static aphelion.shared.swissarmyknife.SwissArmyKnife.max;
import static aphelion.shared.swissarmyknife.SwissArmyKnife.min;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

/**
 *
 * @author Joris
 */
public final class Projectile extends MapEntity implements ProjectilePublic
{
        public static final AttachmentManager attachmentManager = new AttachmentManager();
        private final AttachmentData attachments = attachmentManager.getNewDataContainer();
        
        public final ProjectileKey key;
        
        public final LinkedListEntry<Projectile> projectileListLink_state = new LinkedListEntry<>(this);
        public final LinkedListEntry<Projectile> forceEmitterListLink_state = new LinkedListEntry<>(this);
        public final LinkedListEntry<Projectile> projectileListLink_actor = new LinkedListEntry<>(this);
        // circular headless linked list
        final public LinkedListEntry<Projectile> coupled = new LinkedListEntry<>(this);
        
        /** Has initFire or initFromSync been called?. */
        private boolean initialized;
        public Actor owner;
        public WeaponConfig config;
        public final int configIndex; // the config index
        
        // IF AN ATTRIBUTE IS ADDED, DO NOT FORGET TO UPDATE resetTo()
        // (except config, etc)

        public long expiresAt_tick;
        public int bouncesLeft; // -1 for infinite
        public int activateBouncesLeft;
        
        // Settings that are read when the projectile is spawned, instead of on usage.
        // This mainly affects settings that are randomized based on tick.
        // This is because of performance.
        // All of these attributes will have to go into the WeaponSync message
        public boolean collideTile;
        public boolean collideShip;
        public boolean damageSelf;
        public boolean damageTeam;
        public int bounceFriction;
        public int bounceOtherAxisFriction;
        public int proxDist;
        public int proxExplodeDelay;
        public int forceDistanceShip;
        public int forceVelocityShip;
        public int forceDistanceProjectile;
        public int forceVelocityProjectile;
        
        /** If set, we hit a tile during during performDeadReckoning() and an event should be fired soon. */
        private final PhysicsPoint hitTile = new PhysicsPoint();
        public Actor proxActivatedBy;
        public long proxLastSeenDist;
        public long proxLastSeenDist_tick;
        public long proxActivatedAt_tick;

        public Projectile(
                ProjectileKey key,
                State state, 
                MapEntity[] crossStateList, 
                Actor owner, 
                long createdAt_tick, 
                WeaponConfig config, 
                int projectile_index)
        {
                super(state, 
                      crossStateList, 
                      createdAt_tick, 
                      (state.isLast ? 0 : state.econfig.TRAILING_STATE_DELAY) + state.econfig.MINIMUM_HISTORY_TICKS);
                
                this.key = key;
                this.owner = owner; // note may be null for now, will be resolved by resetTo
                this.config = config; // note may be null for now, will be resolved by resetTo
                this.configIndex = projectile_index;
                this.radius = GCIntegerFixed.ZERO;
        }
        
        @Override
        public void getSync(GameOperation.WeaponSync.Projectile.Builder p)
        {
                p.setIndex(configIndex);
                p.setX(pos.pos.x); 
                p.setY(pos.pos.y);
                p.setXVel(pos.vel.x); 
                p.setYVel(pos.vel.y);
                p.setExpiresAt(this.expiresAt_tick);
                p.setBouncesLeft(bouncesLeft); 
                p.setActivateBouncesLeft(activateBouncesLeft);
                p.setCollideTile(collideTile); 
                p.setCollideShip(collideShip); 
                p.setDamageSelf(damageSelf); 
                p.setDamageTeam(damageTeam);
                p.setBounceFriction(bounceFriction); 
                p.setBounceOtherAxisFriction(bounceOtherAxisFriction);
                p.setProxDist(proxDist); 
                p.setProxExplodeDelay(proxExplodeDelay); 
                p.setProxActivatedBy(proxActivatedBy == null || proxActivatedBy.isRemoved() ? 0 : proxActivatedBy.pid); 
                p.setProxLastSeenDist(proxLastSeenDist); 
                p.setProxLastSeenDistTick(proxLastSeenDist_tick); 
                p.setProxActivatedAtTick(proxActivatedAt_tick);
                p.setForceDistanceShip(forceDistanceShip);
                p.setForceVelocityShip(forceVelocityShip);
                p.setForceDistanceProjectile(forceDistanceProjectile);
                p.setForceVelocityProjectile(forceVelocityProjectile);
        }
        
        public void initFromSync(GameOperation.WeaponSync.Projectile s, long tick_now)
        {
                initialized = true;
                assert configIndex == s.getIndex();
                pos.pos.x = s.getX(); 
                pos.pos.y = s.getY();
                pos.vel.x = s.getXVel(); 
                pos.vel.y = s.getYVel();
                expiresAt_tick = s.getExpiresAt();
                bouncesLeft = s.getBouncesLeft(); 
                activateBouncesLeft = s.getActivateBouncesLeft();
                collideTile = s.getCollideTile();
                collideShip = s.getCollideShip();
                damageSelf = s.getDamageSelf();
                damageTeam = s.getDamageTeam();
                bounceFriction = s.getBounceFriction();
                bounceOtherAxisFriction = s.getBounceOtherAxisFriction();
                proxDist = s.getProxDist();
                proxExplodeDelay = s.getProxExplodeDelay(); 
                proxActivatedBy = s.getProxActivatedBy() == 0 ? null : state.actors.get(new ActorKey(s.getProxActivatedBy()));
                proxLastSeenDist = s.getProxLastSeenDist();
                proxLastSeenDist_tick = s.getProxLastSeenDistTick();
                proxActivatedAt_tick = s.getProxActivatedAtTick();
                forceDistanceShip = s.getForceDistanceShip();
                forceVelocityShip = s.getForceVelocityShip();
                forceDistanceProjectile = s.getForceDistanceProjectile();
                forceVelocityProjectile = s.getForceVelocityProjectile();
        }
        
        public void register()
        {
                Projectile oldValue = state.projectiles.put(this.key, this);
                assert oldValue == null;
                state.projectilesList.append(this.projectileListLink_state);
                this.owner.projectiles.append(this.projectileListLink_actor);
                if (this.isForceEmitter())
                {
                        state.forceEmitterList.append(this.forceEmitterListLink_state);
                }
        }
        
        @Override
        public void hardRemove(long tick)
        {
                super.hardRemove(tick);
                
                projectileListLink_actor.remove();
                projectileListLink_state.remove();
                forceEmitterListLink_state.remove();
                state.projectiles.remove(this.key);
                coupled.remove();
                
                // only modify the entry for _this_ projectile in the state list!
                // this method may be called while looping over this state list
        }
        
        public void initFire(long tick, PhysicsShipPosition actorPos)
        {
                initialized = true;
                pos.pos.set(actorPos.x, actorPos.y);
                        
                int rot = 0;
                if (cfg(config.projectile_angleRelative, tick))
                {
                        rot = actorPos.rot_snapped;
                }
                rot += cfg(config.projectile_angle, tick);

                // relative to rot 0
                int offsetX = cfg(config.projectile_offsetX, tick);

                int offsetY = config.projectile_offsetY.isIndexSet(configIndex)? 
                        cfg(config.projectile_offsetY, tick) : 
                        owner.radius.get();

                PhysicsPoint offset = new PhysicsPoint(0, 0);
                
                PhysicsMath.rotationToPoint(
                        offset,
                        rot + EnvironmentConf.ROTATION_1_4TH,
                        offsetX);
                
                PhysicsMath.rotationToPoint(
                        offset,
                        rot,
                        offsetY);
                
                Collision collision = state.collision;
                collision.reset();
                collision.setPreviousPosition(pos.pos);
                if (cfg(config.projectile_hitTile, tick))
                {
                        collision.setMap(state.env.getMap());
                }
                collision.setVelocity(offset);
                collision.setRadius(radius.get());
                collision.setBouncesLeft(0); // 0 bounces, the resulting position will be set at the collide position
                
                collision.tickMap(tick);
                collision.getNewPosition(pos.pos);
                
                
                // make sure the projectile does not spawn inside of a tile (unless the ship is inside a tile too)


                if (cfg(config.projectile_speedRelative, tick))
                {
                        pos.vel.set(actorPos.x_vel, actorPos.y_vel);
                }

                PhysicsMath.rotationToPoint(
                        pos.vel,
                        rot,
                        cfg(config.projectile_speed, tick));

                pos.enforceOverflowLimit();
                
                collideTile = cfg(config.projectile_hitTile, tick);
                collideShip = cfg(config.projectile_hitShip, tick);
                damageSelf = cfg(config.projectile_damageSelf, tick);
                damageTeam = cfg(config.projectile_damageTeam, tick);
                expiresAt_tick = tick + cfg(config.projectile_expirationTicks, tick);
                bouncesLeft = cfg(config.projectile_bounces, tick);
                activateBouncesLeft = cfg(config.projectile_activateBounces, tick);
                bounceFriction = cfg(config.projectile_bounceFriction, tick);
                bounceOtherAxisFriction = cfg(config.projectile_bounceOtherAxisFriction, tick);
                proxDist = cfg(config.projectile_proxDistance, tick);
                proxExplodeDelay = config.projectile_proxExplodeTicks.isSet()
                        ? cfg(config.projectile_proxExplodeTicks, tick)
                        : -1;
                forceDistanceShip = cfg(config.projectile_forceDistanceShip, tick);
                forceVelocityShip = cfg(config.projectile_forceVelocityShip, tick);
                forceDistanceProjectile = cfg(config.projectile_forceDistanceProjectile, tick);
                forceVelocityProjectile = cfg(config.projectile_forceVelocityProjectile, tick);
        }
        
        public void attemptCorrectionForLateProjectile(long operation_tick, boolean operation_late)
        {
                // dead reckon current position so that it is no longer late
                // the position at the tick of this operation should not be dead reckoned, therefor +1

                // First update the projectile up to the expiry...
                long expiry_tick = max(operation_tick, this.getExpiry());
                long untilExpiry_ticks = min(expiry_tick - operation_tick, state.tick_now - operation_tick);
                long afterExpiry_ticks = max(state.tick_now - expiry_tick, 0);
                this.performDeadReckoning(state.env.getMap(), operation_tick + 1, untilExpiry_ticks);

                if (this.isForceEmitter() && operation_late)
                {
                        // however a force emitter should emit for the current tick also ( <= ).
                        // If late=false, State.tickForceEmitters will be called soons
                        long t;
                        for (t = 0; t <= untilExpiry_ticks; ++t)
                        {
                                this.emitForce(operation_tick + t);
                        }
                        assert t == state.tick_now || t == expiry_tick;
                }

                if (expiry_tick <= state.tick_now)
                {
                        // (performDeadReckoning and emitForce check for isRemoved(tick))
                        this.explodeWithoutHit(expiry_tick, EXPLODE_REASON.EXPIRATION);
                        this.softRemove(expiry_tick);

                        // The expiry itself has already been simulated
                        this.performDeadReckoning(state.env.getMap(), expiry_tick + 1, afterExpiry_ticks);
                }

                assert this.posHistory.getHighestTick() == state.tick_now;
        }
        
        @SuppressWarnings("unchecked")
        @Override
        public void performDeadReckoning(PhysicsMap map, long tick_now, long reckon_ticks)
        {
                Collision collision = state.collision;
                collision.reset();
                collision.setMap(this.collideTile ? map : null);
                collision.setRadius(this.radius.get());
                
                final PhysicsPoint prevForce = new PhysicsPoint();
                
                for (long t = 0; t < reckon_ticks; ++t)
                {
                        long tick = tick_now + t;
                        
                        dirtyPositionPathTracker.resolved(tick);
                        assert !dirtyPositionPathTracker.isDirty(tick) : "performDeadReckoning: Skipped a tick!";
                        
                        if (this.isRemoved(tick) || hitTile.set)
                        {
                                updatedPosition(tick);
                                continue;
                        }
                        
                        // force for _this_ tick is added AFTER we dead reckon.
                        // so wait for the next tick to do the force for _this_ tick.
                        // otherwise all entities have to be looped an extra time.
                        this.forceHistory.get(prevForce, tick - 1);
                        this.pos.vel.add(prevForce);
                        
                        collision.setPreviousPosition(pos.pos);
                        collision.setVelocity(pos.vel);
                        collision.setBouncesLeft(bouncesLeft);
                        collision.tickMap(tick);
                        collision.getNewPosition(pos.pos);
                        collision.getVelocity(pos.vel);
                        
                        if (bouncesLeft >= 0)
                        {
                                bouncesLeft -= collision.getBounces();
                                if (bouncesLeft < 0) bouncesLeft = 0;
                        }
                        
                        if (activateBouncesLeft > 0)
                        {
                                activateBouncesLeft -= collision.getBounces();
                                if (activateBouncesLeft < 0) activateBouncesLeft = 0;
                        }
                        
                        updatedPosition(tick);
                        
                        // hit a tile?

                        if (collision.hasExhaustedBounces())
                        {
                                collision.getHitTile(hitTile);
                                hitTile.set = true;
                                
                                // The event is not really executed until actors have ticked (see tickProjectileAfterActor)
                                // This is so that hitting an actor is prioritized over hitting a tile.
                        }        
                }
        }
        
        public void hitByActor(long tick, Actor actor, PhysicsPoint location)
        {
                // note: we may execute the event multiple times on the same state
                // This is valid. (it happens when a timewarp occurs)
                // The event should discard the previous consistency information.
                
                if (location.set)
                {
                        this.pos.pos.set(location);
                        updatedPosition(tick);
                }
                
                // Do not execute the hit tile event if it was planned.
                hitTile.unset();

                ProjectileExplosion.Key eventKey = new ProjectileExplosion.Key(this.key);
                ProjectileExplosion event = (ProjectileExplosion) state.env.findEvent(eventKey);
                if (event == null)
                {
                        event = new ProjectileExplosion(state.env, eventKey);
                }
                
                state.env.registerEvent(event);
                event.execute(tick, this.state, this, ProjectileExplosionPublic.EXPLODE_REASON.HIT_SHIP, actor, null);
        }
        
        public void tickProjectileAfterActor(long tick)
        {
                if (this.isRemoved(tick))
                {
                        return;
                }
                
                if (this.hitTile.set)
                {
                        // note: we may execute the event multiple times on the same state
                        // This is valid. (it happens when a timewarp occurs)
                        // The event should discard the previous consistency information.

                        ProjectileExplosion.Key eventKey = new ProjectileExplosion.Key(this.key);
                        ProjectileExplosion event = (ProjectileExplosion) state.env.findEvent(eventKey);
                        if (event == null)
                        {
                                event = new ProjectileExplosion(state.env, eventKey);
                        }
                        
                        if (event.hasOccurred(this.state.id))
                        {
                                System.out.println("already occured??");
                        }

                        state.env.registerEvent(event);
                        event.execute(tick, this.state, this, ProjectileExplosionPublic.EXPLODE_REASON.HIT_TILE, null, hitTile);
                        assert this.isRemoved(tick); // Otherwise this event fires over and over and over
                        return;
                }
                
                // TODO: Use state.entityGrid if it is much faster?
                // Proximity bombs 
                // (unlike continuum prox bombs still take part in regular collision unless 
                //  disabled by config)
                if (proxDist > 0 && (this.proxActivatedBy == null || this.proxActivatedBy.isRemoved(tick)))
                {
                        long proxDistSq = proxDist * (long) proxDist;

                        for (Actor actor : state.actorsList)
                        {
                                if (this.collidesWithFilter.loopFilter(actor, tick))
                                {
                                        continue;
                                }

                                // easy case
                                long distSq = this.pos.pos.distanceSquared(actor.pos.pos);
                                if (distSq > proxDistSq)
                                {
                                        continue;
                                }


                                long dist = this.pos.pos.distance(actor.pos.pos, distSq);
                                if (dist > proxDist)
                                {
                                        continue;
                                }

                                this.proxActivatedBy = actor;
                                this.proxActivatedAt_tick = tick;
                                this.proxLastSeenDist = dist;
                                this.proxLastSeenDist_tick = tick;
                                break;
                        }
                }

                if (this.proxActivatedBy != null)
                {
                        long dist = this.pos.pos.distance(this.proxActivatedBy.pos.pos);

                        if (tick <= this.proxLastSeenDist_tick)
                        {
                                // Reexecuting moves, reset the last seen distance
                        }
                        else
                        {
                                if (dist > this.proxLastSeenDist)
                                {
                                        // moving away! detonate

                                        explodeWithoutHit(tick, EXPLODE_REASON.PROX_DIST);
                                        assert this.isRemoved(tick); // Otherwise this event fires over and over and over
                                        return;
                                }
                        }

                        this.proxLastSeenDist = dist;
                        this.proxLastSeenDist_tick = tick;

                        if (proxExplodeDelay >= 0 && tick - this.proxActivatedAt_tick >= proxExplodeDelay)
                        {
                                explodeWithoutHit(tick, EXPLODE_REASON.PROX_DELAY);
                                assert this.isRemoved(tick); // Otherwise this event fires over and over and over
                                return;
                        }
                }
        }
        
        public void explodeWithoutHit(long tick, ProjectileExplosionPublic.EXPLODE_REASON reason)
        {
                assert !this.isRemoved(tick);
                
                ProjectileExplosion.Key eventKey = new ProjectileExplosion.Key(this.key);
                ProjectileExplosion event = (ProjectileExplosion) state.env.findEvent(eventKey);
                if (event == null)
                {
                        event = new ProjectileExplosion(state.env, eventKey);
                }

                state.env.registerEvent(event);
                event.execute(tick, this.state, this, reason, null, null);
        }
        
        public @Nullable Projectile findInOtherState(State otherState)
        {
                if (this.state.isForeign(otherState))
                {
                        return otherState.projectiles.get(this.key);
                }
                else
                {
                        return (Projectile) this.crossStateList[otherState.id];
                }
        }
        
        @Override
        public void resetTo(State myState, MapEntity other_)
        {
                super.resetTo(myState, other_);
                
                Projectile other = (Projectile) other_;
                assert this.key.equals(other.key);
                
                this.initialized = other.initialized;
                
                if (this.owner == null)
                {
                        if (other.owner != null)
                        {
                                this.owner = (Actor) other.owner.findInOtherState(state);
                                this.config = this.owner.config.getWeaponConfig(other.config.weaponKey);
                        }
                }
                else
                {
                        assert this.owner.pid == other.owner.pid : "Projectiles should never change owners in a timewarp";
                }
               
                
                
                this.coupled.previous = null;
                this.coupled.next = null;
                
                
                if (other.coupled.previous != null && other.coupled.next != null)
                {
                        Projectile otherPrev = other.coupled.previous.data;
                        Projectile otherNext = other.coupled.next.data;
                        Projectile prev = otherPrev.findInOtherState(this.state);
                        Projectile next = otherNext.findInOtherState(this.state);
                        
                        // Note that the coupled projectile might not exist yet!
                        // it will be created at a different moment in the timewarp
                        // therefor prev or next (or both) might be null.
                        // State.resetTo has an assertion check to make sure the code below is proper
                        
                        if (prev != null)
                        {
                                this.coupled.previous = prev.coupled;
                                prev.coupled.next = this.coupled;
                        }
                        
                        if (next != null)
                        {
                                this.coupled.next = next.coupled;
                                next.coupled.previous = this.coupled;
                        }
                }
                else
                {
                        assert other.coupled.previous == null;
                        assert other.coupled.next == null;
                }
                
                this.expiresAt_tick = other.expiresAt_tick;
                this.bouncesLeft = other.bouncesLeft;
                this.activateBouncesLeft = other.activateBouncesLeft;
                
                if (other.proxActivatedBy  == null)
                {
                        this.proxActivatedBy = null;
                }
                else
                {
                        this.proxActivatedBy = (Actor) other.proxActivatedBy.findInOtherState(state);
                }
                
                this.proxLastSeenDist = other.proxLastSeenDist;
                this.proxLastSeenDist_tick = other.proxLastSeenDist_tick;
                this.proxActivatedAt_tick = other.proxActivatedAt_tick;
                
                // do not reset references to events
        }
        
        public void resetToEmpty(long tick)
        {
                Projectile dummy = new Projectile(this.key, this.state, crossStateList, this.owner, tick, this.config, this.configIndex);
                
                crossStateList[this.state.id] = null; // skip assertion in resetTo
                this.resetTo(this.state, dummy);
                crossStateList[this.state.id] = (MapEntity) this;
        }
        
        public int getSplashDamage(Actor actor, long tick, int damage, int range, long rangeSq)
        {
                final PhysicsPositionVector myPos = new PhysicsPositionVector();
                final PhysicsPositionVector actorPos = new PhysicsPositionVector();
                
                if (!this.getHistoricPosition(myPos, tick, false))
                {
                        return 0;
                }
                
                if (!actor.getHistoricPosition(actorPos, tick, false))
                {
                        return 0;
                }
                
                long distSq = myPos.pos.distanceSquared(actorPos.pos);
                if (distSq >= rangeSq)
                {
                        return 0; // out of range
                }

                long ldist = myPos.pos.distance(actorPos.pos, distSq);
                assert ldist >= 0;
                int dist = ldist > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ldist;
                assert range >= dist;

                // if dist == splash do 0 damage
                return (int) ((long) (range - dist) * damage / range);
        }
        
        public void doSplashDamage(Actor except, long tick, Collection<Integer> killed)
        {
                int splash = cfg(config.projectile_damageSplash, tick) * 1024;
                if (splash == 0)
                {
                        return;
                }
                int damage = cfg(config.projectile_damage, tick) * 1024;
                
                long splashSq = splash * (long) splash;
                
                boolean damageSelfKill = cfg(config.projectile_damageSelfKill, tick);
                boolean damageTeamKill = cfg(config.projectile_damageTeamKill, tick);
                
                for (Actor actor : state.actorsList)
                {
                        if (actor.isRemoved(tick) || actor.isDead(tick)) { continue; }
                        if (actor == except) { continue; }
                        if (actor == this.owner && !damageSelf) { continue; }
                        
                        // todo team

                        int effectiveDamage = getSplashDamage(actor, tick, damage, splash, splashSq);
                        
                        actor.energy.addRelativeValue(
                                Actor.ENERGY_SETTER.OTHER.id, 
                                tick, 
                                -effectiveDamage);
                        
                        if (actor.energy.get(tick) <= 0)
                        {
                                if (!damageSelfKill && actor == this.owner)
                                {
                                }
                                else
                                {
                                        actor.died(tick);
                                        killed.add(actor.pid);
                                }
                        }
                }
        }
        
        public void doSplashEmp(Actor except, long tick)
        {
                int p = this.configIndex;
                int splash = cfg(config.projectile_empSplash, tick) * 1024;
                if (splash == 0)
                {
                        return;
                }
                int damage = cfg(config.projectile_empTime, tick);
                
                long splashSq = splash * (long) splash;
                
                boolean empSelf = cfg(config.projectile_empSelf, tick);
                boolean empTeam = cfg(config.projectile_empTeam, tick);
                
                for (Actor actor : state.actorsList)
                {
                        if (actor.isRemoved(tick) || actor.isDead(tick)) { continue; }
                        if (actor == except) { continue; }
                        if (actor == this.owner && !empSelf) { continue; }
                        
                        // todo team

                        actor.applyEmp(tick, getSplashDamage(actor, tick, damage, splash, splashSq));
                }
        }

        @Override
        public int getStateId()
        {
                return state.id;
        }

        @Override
        public int getOwner()
        {
                return owner.pid;
        }

        @Override
        public long getExpiry()
        {
                return expiresAt_tick;
        }

        @Override
        public AttachmentData getAttachments()
        {
                return attachments;
        }
        
        public List<MapEntity> getCollidesWith()
        {
                return (List<MapEntity>) (Object) state.actorsList;
        }

        // second arg = tick
        public final LoopFilter<MapEntity, Long> collidesWithFilter = new LoopFilter<MapEntity, Long>()
        {
                @Override
                public boolean loopFilter(MapEntity en, Long arg)
                {
                        if (en instanceof Actor)
                        {
                                Actor actor = (Actor) en;
                                
                                if (actor.isRemoved(arg) || actor.isDead(arg))
                                {
                                        return true;
                                }

                                // never hit your own weapons...
                                if (owner != null && owner.pid == actor.pid)
                                {
                                        return true;
                                }
                                
                                // todo freqs
                        }
                        
                        return false;
                }
        };

        @Override
        public int getBouncesLeft()
        {
                return this.bouncesLeft;
        }
        
        @Override
        public int getActivateBouncesLeft()
        {
                return this.activateBouncesLeft;
        }
        
        @Override
        public boolean isActive()
        {
                if (this.activateBouncesLeft > 0)
                {
                        return false;
                }
                
                return true;
        }
        
        public boolean isForceEmitter()
        {
                if (!initialized)
                {
                        throw new IllegalStateException();
                }
                final boolean doShip = this.forceDistanceShip > 0 && this.forceVelocityShip != 0;
                final boolean doProjectile = this.forceDistanceProjectile > 0 && this.forceVelocityProjectile != 0;
                return doShip || doProjectile;
        }
        
        /** Emit the configured force on the "forceHistory" of other entities, without updating the velocity or position.
         * The velocity and position must be updated in a separate step.
         * @param tick
         */
        public void emitForce(long tick)
        {
                final boolean doShip = this.forceDistanceShip > 0 && this.forceVelocityShip != 0;
                final boolean doProjectile = this.forceDistanceProjectile > 0 && this.forceVelocityProjectile != 0;
                
                if (!doShip && !doProjectile)
                {
                        return;
                }
                
                if (this.isRemoved(tick))
                {
                        return;
                }

                final PhysicsPositionVector myPos = new PhysicsPositionVector();
                this.getHistoricPosition(myPos, tick, false);
                final PhysicsPoint forcePoint = new PhysicsPoint(myPos.pos);

                Iterator<MapEntity> it = state.entityGrid.iterator(
                        forcePoint,
                        SwissArmyKnife.max(this.forceDistanceShip, this.forceDistanceProjectile));

                final PhysicsPositionVector otherPosition = new PhysicsPositionVector();
                final PhysicsPoint velocity = new PhysicsPoint();
                final PhysicsPoint forceHist = new PhysicsPoint();

                while (it.hasNext())
                {
                        MapEntity en = it.next();
                        if (en == this) { continue; }
                        
                        if (doShip && en instanceof Actor)
                        {
                                Actor actor = (Actor) en;
                                if (actor.isDead(tick)) { continue; }
                                if (!damageSelf && en == this.owner)
                                {
                                        continue;
                                }
                        }
                        else if (doProjectile && en instanceof Projectile)
                        {
                                Projectile proj = (Projectile) en;
                                if (!damageSelf && proj.owner == this.owner)
                                {
                                        continue;
                                }
                                
                                // Force emitters never affect other force emitters
                                // (or, at least for now, it causes too many inconsistencies)
                                if (proj.isForceEmitter())
                                {
                                        continue;
                                }
                        }
                        
                        en.getHistoricPosition(otherPosition, tick, false);
                        PhysicsMath.force(velocity, otherPosition.pos, forcePoint, forceDistanceShip, forceVelocityShip);
                        if (!velocity.isZero())
                        {
                                en.markDirtyPositionPath(tick + 1);
                                
                                en.forceHistory.get(forceHist, tick);
                                forceHist.add(velocity);
                                forceHist.enforceOverflowLimit();
                                en.forceHistory.setHistory(tick, forceHist);
                        }
                }
        }

        @Override
        public GCInteger getWeaponConfigInteger(String name)
        {
                return config.selection.getInteger(name);
        }

        @Override
        public GCString getWeaponConfigString(String name)
        {
                return config.selection.getString(name);
        }

        @Override
        public GCBoolean getWeaponConfigBoolean(String name)
        {
                return config.selection.getBoolean(name);
        }
        
        @Override
        public GCIntegerList getWeaponConfigIntegerList(String name)
        {
                return config.selection.getIntegerList(name);
        }

        @Override
        public GCStringList getWeaponConfigStringList(String name)
        {
                return config.selection.getStringList(name);
        }

        @Override
        public GCBooleanList getWeaponConfigBooleanList(String name)
        {
                return config.selection.getBooleanList(name);
        }

        @Override
        public GCImage getWeaponConfigImage(String name, ResourceDB db)
        {
                return config.selection.getImage(name, db);
        }
        
        @Override
        public GCColour getWeaponConfigColour(String name)
        {
                return config.selection.getColour(name);
        }

        @Override
        public String getWeaponKey()
        {
                return config.weaponKey;
        }

        @Override
        public int getProjectileIndex()
        {
                return this.configIndex;
        }

        @Override
        public Iterator<ProjectilePublic> getCoupledProjectiles()
        {
                return (Iterator<ProjectilePublic>) (Object) coupled.iteratorReadOnly();
        }

        public int configSeed(long tick)
        {
                assert owner != null;
                return owner.seed_low ^ ((int) tick);
        }
        
        // some short hands to save typing
        public int cfg(GCIntegerList configValue, long tick)
        {
                return configValue.get(this.configIndex, configSeed(tick));
        }
        
        public boolean cfg(GCBooleanList configValue, long tick)
        {
                return configValue.get(this.configIndex, configSeed(tick));
        }
        
        public String cfg(GCStringList configValue, long tick)
        {
                return configValue.get(this.configIndex, configSeed(tick));
        }

        
}
