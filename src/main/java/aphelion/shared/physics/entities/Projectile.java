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
import aphelion.shared.physics.Collision;
import aphelion.shared.physics.events.ProjectileExplosion;
import aphelion.shared.physics.events.pub.ProjectileExplosionPublic;
import aphelion.shared.physics.events.pub.ProjectileExplosionPublic.EXPLODE_REASON;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.PhysicsMap;
import aphelion.shared.physics.PhysicsMath;
import aphelion.shared.physics.State;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.AttachmentData;
import aphelion.shared.swissarmyknife.AttachmentManager;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import aphelion.shared.swissarmyknife.LoopFilter;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author Joris
 */
public final class Projectile extends MapEntity implements ProjectilePublic
{
        public static final AttachmentManager attachmentManager = new AttachmentManager();
        private AttachmentData attachments = attachmentManager.getNewDataContainer();
        
        public final LinkedListEntry<Projectile> projectileListLink_state = new LinkedListEntry<>(null, this);
        public final LinkedListEntry<Projectile> projectileListLink_actor = new LinkedListEntry<>(null, this);
        // circular headless linked list
        final public LinkedListEntry<Projectile> coupled = new LinkedListEntry<>(null, this);
        
        public Actor owner;
        public Actor.WeaponConfig config;
        public final int projectile_index; // the config index
        
        // IF AN ATTRIBUTE IS ADDED, DO NOT FORGET TO UPDATE resetTo()
        // (except config, etc)

        public long expires_at_tick;
        public int bounces_left; // -1 for infinite
        public int activate_bounces_left;
        
        // These values are used in deadReckon and collision
        // Initialize them when the projectile launches because otherwise these config
        // values would be ready very very often during deadreckon.
        public boolean collideTile;
        public boolean collideShip;
        public int bounceFriction;
        public int bounceOtherAxisFriction;
        public int proxDist;
        public int proxExplodeDelay;
        
        
        
        public Actor proxActivatedBy;
        public long proxLastSeenDist;
        public long proxLastSeenDist_tick;
        public long proxActivatedAt_tick;
        
        public WeakReference<ProjectileExplosion> explosionEvent;

        public Projectile(State state, 
                MapEntity[] crossStateList, 
                Actor owner, 
                long createdAt_tick, 
                Actor.WeaponConfig config, 
                int projectile_index)
        {
                super(state, crossStateList, createdAt_tick, PhysicsEnvironment.TRAILING_STATE_DELAY);
                
                this.owner = owner; // note may be null for now, will be resolved by resetTo
                this.config = config; // note may be null for now, will be resolved by resetTo
                this.projectile_index = projectile_index;
                this.radius = GCIntegerFixed.ZERO;
        }
        
        @Override
        public void getSync(GameOperation.WeaponSync.Projectile.Builder p)
        {
                p.setIndex(projectile_index);
                p.setX(pos.pos.x); 
                p.setY(pos.pos.y);
                p.setXVel(pos.vel.x); 
                p.setYVel(pos.vel.y);
                p.setExpiresAt(this.expires_at_tick);
                p.setBouncesLeft(bounces_left); 
                p.setActivateBouncesLeft(activate_bounces_left);
                p.setCollideTile(collideTile); 
                p.setCollideShip(collideShip); 
                p.setBounceFriction(bounceFriction); 
                p.setBounceOtherAxisFriction(bounceOtherAxisFriction);
                p.setProxDist(proxDist); 
                p.setProxExplodeDelay(proxExplodeDelay); 
                p.setProxActivatedBy(proxActivatedBy == null || proxActivatedBy.removed ? 0 : proxActivatedBy.pid); 
                p.setProxLastSeenDist(proxLastSeenDist); 
                p.setProxLastSeenDistTick(proxLastSeenDist_tick); 
                p.setProxActivatedAtTick(proxActivatedAt_tick);
        }
        
        public void initFromSync(GameOperation.WeaponSync.Projectile s, long tick_now, long tick_offset)
        {
                assert projectile_index == s.getIndex();
                pos.pos.x = s.getX(); 
                pos.pos.y = s.getY();
                pos.vel.x = s.getXVel(); 
                pos.vel.y = s.getYVel();
                expires_at_tick = s.getExpiresAt() - tick_offset;
                bounces_left = s.getBouncesLeft(); 
                activate_bounces_left = s.getActivateBouncesLeft();
                collideTile = s.getCollideTile();
                collideShip = s.getCollideShip();
                bounceFriction = s.getBounceFriction();
                bounceOtherAxisFriction = s.getBounceOtherAxisFriction();
                proxDist = s.getProxDist();
                proxExplodeDelay = s.getProxExplodeDelay(); 
                proxActivatedBy = s.getProxActivatedBy() == 0 ? null : state.actors.get(s.getProxActivatedBy());
                proxLastSeenDist = s.getProxLastSeenDist();
                proxLastSeenDist_tick = s.getProxLastSeenDistTick() - tick_offset;
                proxActivatedAt_tick = s.getProxActivatedAtTick() - tick_offset;
        }
        
        public void hardRemove(long tick)
        {
                if (!removed)
                {
                        removed = true;
                        removedAt_tick = tick;
                }
                
                crossStateList[state.id] = null;
                projectileListLink_actor.remove();
                projectileListLink_state.remove();
                coupled.remove();
                
                // only modify the entry for _this_ projectile in the state list!
                // this method may be called while looping over this state list
        }
        
        public void initFire(long tick, PhysicsShipPosition actorPos)
        {
                pos.pos.set(actorPos.x, actorPos.y);
                        
                int rot = 0;
                if (cfg(config.projectile_angleRelative, tick))
                {
                        rot = actorPos.rot_snapped;
                }
                rot += cfg(config.projectile_angle, tick);

                // relative to rot 0
                int offsetX = cfg(config.projectile_offsetX, tick);

                int offsetY = config.projectile_offsetY.isIndexSet(projectile_index)? 
                        cfg(config.projectile_offsetY, tick) : 
                        owner.radius.get();

                PhysicsPoint offset = new PhysicsPoint(0, 0);
                
                PhysicsMath.rotationToPoint(
                        offset,
                        rot + PhysicsEnvironment.ROTATION_1_4TH,
                        offsetX);
                
                PhysicsMath.rotationToPoint(
                        offset,
                        rot,
                        offsetY);
                
                // make sure the projectile does not spawn inside of a tile
                state.collision.deadReckonTick(
                        tick, 
                        null, // do not record position details
                        pos.pos, 
                        offset, // use the offset for the vel amount, note that Collision might modify this value
                        state.env.getMap(), 
                        cfg(config.projectile_hitTile, tick),
                        null, null,
                        radius.get(),
                        1024, 
                        1024,
                        0, // 0 bounces, the resulting position will be set at the collide position
                        0);

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
                expires_at_tick = tick + cfg(config.projectile_expirationTicks, tick);
                bounces_left = cfg(config.projectile_bounces, tick);
                activate_bounces_left = cfg(config.projectile_activateBounces, tick);
                bounceFriction = cfg(config.projectile_bounceFriction, tick);
                bounceOtherAxisFriction = cfg(config.projectile_bounceOtherAxisFriction, tick);
                proxDist = cfg(config.projectile_proxDistance, tick);
                proxExplodeDelay = config.projectile_proxExplodeTicks.isSet()
                        ? cfg(config.projectile_proxExplodeTicks, tick)
                        : -1;
        }
        
        @SuppressWarnings("unchecked")
        public void performDeadReckoning(PhysicsMap map, long tick_now, long reckon_ticks)
        {
                Collision collision = state.collision;
                for (long t = 0; t < reckon_ticks; ++t)
                {
                        long tick = tick_now + t;
                        
                        if (this.removed)
                        {
                                updatePositionHistory(tick);
                                continue;
                        }
                        
                        List<MapEntity> collidesWith = null;
                        if (this.collideShip)
                        {
                                collidesWith = (List<MapEntity>) (Object)state.actorsList;
                        }
                        
                        
                        
                        boolean hit = collision.deadReckonTick(
                                tick, 
                                null, // do not record position details
                                pos.pos, 
                                pos.vel, 
                                map, 
                                this.collideTile,
                                collidesWith, this.collidesWithFilter,
                                radius.get(), 
                                bounceFriction, 
                                bounceOtherAxisFriction,
                                bounces_left,
                                activate_bounces_left);
                        
                        if (bounces_left >= 0)
                        {
                                bounces_left -= collision.bounces;
                                if (bounces_left < 0) bounces_left = 0;
                        }
                        
                        if (activate_bounces_left > 0)
                        {
                                activate_bounces_left -= collision.bounces;
                                if (activate_bounces_left < 0) activate_bounces_left = 0;
                        }
                        
                        updatePositionHistory(tick);
                        
                        // hit an actor?
                        if (hit && collision.hitEntity != null)
                        {
                                // note: we may execute the event multiple times on the same state
                                // This is valid. (it happens when a timewarp occurs)
                                // The event should discard the previous consistency information.
                                
                                for (int s = 0; s < this.crossStateList.length; ++s)
                                {
                                        Projectile other = (Projectile) this.crossStateList[s];
                                        if (other != null && other.explosionEvent != null)
                                        {
                                                this.explosionEvent = other.explosionEvent;
                                                break;
                                        }
                                }
                                
                                ProjectileExplosion event = explosionEvent == null ? null : explosionEvent.get();
                                if (event == null)
                                {
                                        event = new ProjectileExplosion();
                                        explosionEvent = new WeakReference<>(event);
                                }
                                
                                Actor actorHit = (Actor) collision.hitEntity;
                                state.env.addEvent(event);
                                event.execute(tick, this.state, this, ProjectileExplosionPublic.EXPLODE_REASON.HIT_SHIP, actorHit, null);
                                continue;
                        }
                        
                        // hit a tile?
                        if (hit && collision.hitTile.set)
                        {
                                // note: we may execute the event multiple times on the same state
                                // This is valid. (it happens when a timewarp occurs)
                                // The event should discard the previous consistency information.
                                
                                for (int s = 0; s < this.crossStateList.length; ++s)
                                {
                                        Projectile other = (Projectile) this.crossStateList[s];
                                        if (other != null && other.explosionEvent != null)
                                        {
                                                this.explosionEvent = other.explosionEvent;
                                                break;
                                        }
                                }
                                
                                ProjectileExplosion event = explosionEvent == null ? null : explosionEvent.get();
                                if (event == null)
                                {
                                        event = new ProjectileExplosion();
                                        explosionEvent = new WeakReference<>(event);
                                }
                                
                                state.env.addEvent(event);
                                event.execute(tick, this.state, this, ProjectileExplosionPublic.EXPLODE_REASON.HIT_TILE, null, collision.hitTile);
                                
                                continue;
                        }
                        
                        // Proximity bombs 
                        // (unlike continuum prox bombs still take part in regular collision unless 
                        //  disabled by config)
                        if (proxDist > 0 && (this.proxActivatedBy == null || this.proxActivatedBy.removed))
                        {
                                long proxDistSq = proxDist * (long) proxDist;

                                for (Actor actor : state.actorsList)
                                {
                                        if (this.collidesWithFilter.loopFilter(actor))
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
                                                continue;
                                        }
                                }
                                
                                this.proxLastSeenDist = dist;
                                this.proxLastSeenDist_tick = tick;
                                
                                if (proxExplodeDelay >= 0 && tick - this.proxActivatedAt_tick >= proxExplodeDelay)
                                {
                                        explodeWithoutHit(tick, EXPLODE_REASON.PROX_DELAY);
                                        continue;
                                }
                        }
                }
        }
        
        public void explodeWithoutHit(long tick, ProjectileExplosionPublic.EXPLODE_REASON reason)
        {
                if (this.removed && tick >= this.removedAt_tick)
                {
                        assert false;
                }
                
                for (int s = 0; s < this.crossStateList.length; ++s)
                {
                        Projectile other = (Projectile) this.crossStateList[s];
                        if (other != null && other.explosionEvent != null)
                        {
                                this.explosionEvent = other.explosionEvent;
                                break;
                        }
                }
                
                ProjectileExplosion event = explosionEvent == null ? null : explosionEvent.get();
                if (event == null)
                {
                        event = new ProjectileExplosion();
                        explosionEvent = new WeakReference<>(event);
                }

                state.env.addEvent(event);
                event.execute(tick, this.state, this, reason, null, null);
        }
        
        @Override
        public void resetTo(State myState, MapEntity other_)
        {
                super.resetTo(myState, other_);
                assert myState == this.state;
                
                Projectile other = (Projectile) other_;
                
                if (this.owner == null)
                {
                        if (other.owner != null)
                        {
                                this.owner = (Actor) other.owner.crossStateList[state.id];
                                this.config = this.owner.getWeaponConfig(other.config.weaponKey);
                        }
                }
                else
                {
                        assert this.owner.pid == other.owner.pid : "Projectiles should never change owners in a timewarp";
                }
               
                if (other.coupled.previous == null)
                {
                        coupled.previous = null;
                }
                else
                {
                        // hah
                        Projectile otherPrev = other.coupled.previous.data;
                        Projectile prev = (Projectile) otherPrev.crossStateList[this.state.id];
                        coupled.previous = prev == null ? null : prev.coupled;
                }
                
                if (other.coupled.next == null)
                {
                        coupled.next = null;
                }
                else
                {
                        Projectile otherNext = other.coupled.next.data;
                        Projectile next = (Projectile) otherNext.crossStateList[this.state.id];
                        coupled.next = next == null ? null : next.coupled;
                }
                
                
                if (coupled.previous == null || coupled.next == null)
                {
                        assert coupled.previous == null;
                        assert coupled.next == null;
                }
                
                this.expires_at_tick = other.expires_at_tick;
                this.bounces_left = other.bounces_left;
                this.activate_bounces_left = other.activate_bounces_left;
                
                if (other.proxActivatedBy  == null)
                {
                        this.proxActivatedBy = null;
                }
                else
                {
                        this.proxActivatedBy = (Actor) other.proxActivatedBy.crossStateList[state.id];
                }
                
                this.proxLastSeenDist = other.proxLastSeenDist;
                this.proxLastSeenDist_tick = other.proxLastSeenDist_tick;
                this.proxActivatedAt_tick = other.proxActivatedAt_tick;
                
                // do not reset references to events
        }
        
        public int getSplashDamage(Actor actor, int damage, int range, long rangeSq)
        {
                long distSq = this.pos.pos.distanceSquared(actor.pos.pos);
                if (distSq >= rangeSq)
                {
                        return 0; // out of range
                }

                long ldist = this.pos.pos.distance(actor.pos.pos, distSq);
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
                
                boolean damageSelf = cfg(config.projectile_damageSelf, tick);
                boolean damageSelfKill = cfg(config.projectile_damageSelfKill, tick);
                boolean damageTeam = cfg(config.projectile_damageTeam, tick);
                boolean damageTeamKill = cfg(config.projectile_damageTeamKill, tick);
                
                for (Actor actor : state.actorsList)
                {
                        if (actor.removed || actor.dead) { continue; }
                        if (actor == except) { continue; }
                        if (actor == this.owner && !damageSelf) { continue; }
                        
                        // todo team

                        int effectiveDamage = getSplashDamage(actor, damage, splash, splashSq);
                        
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
                                        actor.dead = true;
                                        actor.spawnAt_tick = tick + actor.respawnDelay.get();
                                        if (actor.spawnAt_tick <= tick)
                                        {
                                                // can not be respawned in this tick (or in the past)
                                                actor.spawnAt_tick = tick + 1;
                                        }
                                        killed.add(actor.pid);
                                }
                        }
                }
        }
        
        public void doSplashEmp(Actor except, long tick)
        {
                int p = this.projectile_index;
                int splash = cfg(config.projectile_empSplash, tick) * 1024;
                if (splash == 0)
                {
                        return;
                }
                int damage = cfg(config.projectile_empTime, tick);
                
                long splashSq = splash * (long) splash;
                
                boolean damageSelf = cfg(config.projectile_empSelf, tick);
                boolean damageTeam = cfg(config.projectile_empTeam, tick);
                
                for (Actor actor : state.actorsList)
                {
                        if (actor.removed || actor.dead) { continue; }
                        if (actor == except) { continue; }
                        if (actor == this.owner && !damageSelf) { continue; }
                        
                        // todo team

                        actor.applyEmp(tick, getSplashDamage(actor, damage, splash, splashSq));
                }
        }

        @Override
        public int getStateId()
        {
                return state.id;
        }
        
        @Override
        public boolean getPosition(Position pos)
        {
                pos.x = this.pos.pos.x;
                pos.y = this.pos.pos.y;
                pos.x_vel = this.pos.vel.x;
                pos.y_vel = this.pos.vel.y;
                return true;
        }

        @Override
        public int getOwner()
        {
                return owner.pid;
        }

        @Override
        public long getExpiry()
        {
                return expires_at_tick;
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

        public final LoopFilter<MapEntity> collidesWithFilter = new LoopFilter<MapEntity>() {

                @Override
                public boolean loopFilter(MapEntity en)
                {
                        if (en instanceof Actor)
                        {
                                Actor actor = (Actor) en;
                                
                                if (actor.removed || actor.dead)
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
                return this.bounces_left;
        }
        
        @Override
        public int getActivateBouncesLeft()
        {
                return this.activate_bounces_left;
        }
        
        @Override
        public boolean isActive()
        {
                if (this.activate_bounces_left > 0)
                {
                        return false;
                }
                
                return true;
        }

        @Override
        public GCInteger getWeaponConfigInteger(String name)
        {
                return config.configSelection.getInteger(name);
        }

        @Override
        public GCString getWeaponConfigString(String name)
        {
                return config.configSelection.getString(name);
        }

        @Override
        public GCBoolean getWeaponConfigBoolean(String name)
        {
                return config.configSelection.getBoolean(name);
        }
        
        @Override
        public GCIntegerList getWeaponConfigIntegerList(String name)
        {
                return config.configSelection.getIntegerList(name);
        }

        @Override
        public GCStringList getWeaponConfigStringList(String name)
        {
                return config.configSelection.getStringList(name);
        }

        @Override
        public GCBooleanList getWeaponConfigBooleanList(String name)
        {
                return config.configSelection.getBooleanList(name);
        }

        @Override
        public GCImage getWeaponConfigImage(String name, ResourceDB db)
        {
                return config.configSelection.getImage(name, db);
        }
        
        @Override
        public GCColour getWeaponConfigColour(String name)
        {
                return config.configSelection.getColour(name);
        }

        @Override
        public String getWeaponKey()
        {
                return config.weaponKey;
        }

        @Override
        public int getProjectileIndex()
        {
                return this.projectile_index;
        }

        @Override
        public Iterator<ProjectilePublic> getCoupledProjectiles()
        {
                return (Iterator<ProjectilePublic>) (Object) coupled.iteratorReadOnly();
        }

        @Override
        public boolean isRemoved()
        {
                return this.removed;
        }

        public int configSeed(long tick)
        {
                assert owner != null;
                return owner.seed_low ^ ((int) tick);
        }
        
        // some short hands to save typing
        public int cfg(GCIntegerList configValue, long tick)
        {
                return configValue.get(this.projectile_index, configSeed(tick));
        }
        
        public boolean cfg(GCBooleanList configValue, long tick)
        {
                return configValue.get(this.projectile_index, configSeed(tick));
        }
        
        public String cfg(GCStringList configValue, long tick)
        {
                return configValue.get(this.projectile_index, configSeed(tick));
        }

        
}
