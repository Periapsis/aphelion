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
package aphelion.client.graphics.world;

import aphelion.client.graphics.screen.Camera;
import aphelion.client.RENDER_LAYER;
import aphelion.client.graphics.RenderDelay;
import aphelion.client.graphics.world.event.ActorDiedTracker;
import aphelion.client.graphics.world.event.EventTracker;
import aphelion.client.graphics.world.event.ProjectileExplosionTracker;
import aphelion.client.net.SingleGameConnection;
import aphelion.shared.event.LoopEvent;
import aphelion.shared.event.TickEvent;
import aphelion.shared.net.game.ActorListener;
import aphelion.shared.net.game.NetworkedActor;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.events.Event;
import aphelion.shared.physics.events.pub.ActorDiedPublic;
import aphelion.shared.physics.events.pub.EventPublic;
import aphelion.shared.physics.events.pub.ProjectileExplosionPublic;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.AttachmentConsumer;
import aphelion.shared.swissarmyknife.EmptyIterator;
import aphelion.shared.swissarmyknife.FilteredIterator;
import aphelion.shared.swissarmyknife.LinkedListHead;
import aphelion.shared.swissarmyknife.Point;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Takes care of tracking graphics state for entities (ships, projetiles).
 * @author Joris
 */
public class MapEntities implements TickEvent, LoopEvent, Animator, ActorListener
{
        private static final AttachmentConsumer<ProjectilePublic, Projectile> projectileAttachment 
                = new AttachmentConsumer<>(aphelion.shared.physics.entities.Projectile.attachmentManager);
        
        /** pid -> ActorShip */
        private final HashMap<Integer,ActorShip> actorShips = new HashMap<>(64);
        private PhysicsEnvironment physicsEnv;
        private final ResourceDB resourceDB;
        private ActorShip localShip;
        private final LinkedListHead<MapAnimation> animations[] = new LinkedListHead[RENDER_LAYER.values().length];
        private RenderDelay renderDelay;
        private static final AttachmentConsumer<EventPublic, EventTracker> eventTrackers 
                = new AttachmentConsumer<>(Event.attachmentManager);

        public MapEntities(ResourceDB db)
        {
                this.resourceDB = db;
                
                for (int i = 0; i < animations.length; i++)
                {
                        animations[i] = new LinkedListHead<>();
                }
        }

        
        public void addShip(ActorShip en)
        {
                actorShips.put(en.pid, en);
                if (en.isLocalPlayer())
                {
                        assert localShip == null; // since the getter is singular
                        localShip = en;
                }
        }
        
        public void removeShip(ActorShip en)
        {
                if (en == null) { return; }
                actorShips.remove(en.pid);
                if (localShip == en)
                {
                        localShip = null;
                }
        }
        
        public ActorShip getLocalShip()
        {
                return localShip; // may be null
        }
        
        public Iterator<ActorShip> shipIterator()
        {
                return actorShips.values().iterator();
        }
        
        public Iterator<ActorShip> shipNoLocalIterator()
        {
                Iterator<ActorShip> it = new Iterator<ActorShip>() 
                {
                        Iterator<ActorShip> wrapped;
                        ActorShip next;
                        
                        {
                                wrapped = actorShips.values().iterator();
                                advanceUntilCorrect();
                        }
                        
                        @Override
                        public boolean hasNext()
                        {
                                return next != null;
                        }

                        @Override
                        public ActorShip next()
                        {
                                ActorShip ret = next;
                                advanceUntilCorrect();
                                return ret;
                        }

                        @Override
                        public void remove()
                        {
                                wrapped.remove();
                        }
                        
                        private void advanceUntilCorrect()
                        {
                                while (wrapped.hasNext())
                                {
                                        next = wrapped.next();
                                        if (!next.isLocalPlayer())
                                        {
                                                return;
                                        }
                                }
                                
                                next = null;
                        }
                };
                return it;
        }
        
        public Iterable<ActorShip> shipsNoLocal()
        {
                return new Iterable<ActorShip>()
                {
                        @Override
                        public Iterator<ActorShip> iterator()
                        {
                                return shipNoLocalIterator();
                        }
                };
        }
        
        public ActorShip getActorShip(int pid)
        {
                return actorShips.get(pid);
        }
        
        public Iterator<Projectile> projectileIterator(final boolean includeNonExist)
        {
                if (physicsEnv == null)
                {
                        return new EmptyIterator<>();
                }
                
                Iterator<Projectile> it = new FilteredIterator<Projectile, ProjectilePublic>(physicsEnv.projectileIterator(0)) 
                {
                        @Override
                        public Projectile filter(ProjectilePublic next)
                        {
                                Projectile projectile = physicsProjectileToGraphics(next);
                                
                                if (!includeNonExist && !projectile.exists)
                                {
                                        return null;
                                }
                                
                                return projectile;
                        }
                };
                
                return it;
        }
        
        public Iterable<Projectile> projectiles(final boolean includeNonExist)
        {
                return new Iterable<Projectile>()
                {
                        @Override
                        public Iterator<Projectile> iterator()
                        {
                                return projectileIterator(includeNonExist);
                        }
                };
        }
        
        public Projectile physicsProjectileToGraphics(ProjectilePublic proj)
        {
                if (proj == null) { return null; }
                Projectile projectile = projectileAttachment.get(proj);
                if (projectile == null)
                {
                        projectile = new Projectile(resourceDB, proj);
                        projectileAttachment.set(proj, projectile);
                        // caveat: if a timewarp destroys and recreates a projectile, 
                        // this data is lost
                }
                
                return projectile;
        }
        
        public ActorShip findNearestActor(Point pos, boolean includeLocal)
        {
                Iterator<ActorShip> it = actorShips.values().iterator();
                
                Point diff = new Point();
                
                ActorShip nearest = null;
                float nearest_dist = 0;
                
                while (it.hasNext())
                {
                        ActorShip ship = it.next();
                        
                        if (ship.isLocalPlayer() && !includeLocal)
                        {
                                continue;
                        }
                        
                        if (!ship.exists)
                        {
                                continue;
                        }
                        
                        diff.set(ship.pos);
                        diff.sub(pos);
                        float dist = diff.distanceSquared();
                        
                        if (nearest == null || dist < nearest_dist)
                        {
                                nearest_dist = dist;
                                nearest = ship;
                        }
                }
                
                return nearest;
        }
        
        @Override
        public void addAnimation(RENDER_LAYER layer, MapAnimation animation, Camera camera)
        {
                animation.animating = true;
                animations[layer.id].append(animation.link);
                animation.camera = camera;
        }
        
        public Iterator<MapAnimation> animationIterator(final RENDER_LAYER layer, final Camera camera)
        {
                return new FilteredIterator<MapAnimation, MapAnimation>(animations[layer.id].iterator())
                {
                        @Override
                        public MapAnimation filter(MapAnimation next)
                        {
                                if (next.camera == null || next.camera == camera)
                                {
                                        return next;
                                }

                                return null;
                        }
                };
        }
        
        public Iterable<MapAnimation> animations(final RENDER_LAYER layer, final Camera camera)
        {
                return new Iterable<MapAnimation>()
                {
                        @Override
                        public Iterator<MapAnimation> iterator()
                        {
                                return animationIterator(layer, camera);
                        }
                };
        }

        @Override
        public void tick(long tick)
        {
                Iterator<ActorShip> itActor = actorShips.values().iterator();
                
                while (itActor.hasNext())
                {
                        itActor.next().tick(tick);
                }
                
                Iterator<Projectile> itProj = projectileIterator(true);
                while (itProj.hasNext())
                {
                        itProj.next().tick(tick);
                }
                
                
                for (LinkedListHead<MapAnimation> animationList : animations)
                {
                        for (MapAnimation anim : animationList)
                        {
                                anim.tick(tick);
                        }
                }
        }

        @Override
        public void loop(long systemNanoTime, long sourceNanoTime)
        {
                for (int i = 0; i < animations.length; ++i)
                {
                        Iterator<MapAnimation> animIt = animations[i].iterator();
                        while (animIt.hasNext())
                        {
                                MapAnimation animation = animIt.next();

                                if (animation.isDone())
                                {
                                        animation.animating = false;
                                        animIt.remove();
                                }
                        }
                }
        }

        @Override
        public void newActor(NetworkedActor actor)
        {
                this.addShip(new ActorShip(this.resourceDB, actor, physicsEnv.getActor(actor.pid, 0, true), this));
        }

        @Override
        public void actorModified(NetworkedActor actor)
        {
        }

        @Override
        public void removedActor(NetworkedActor actor)
        {
                ActorShip ship = this.getActorShip(actor.pid);
                assert ship != null;
                this.removeShip(ship);
        }
        
        public void tryInitialize(PhysicsEnvironment physicsEnv, SingleGameConnection connection)
        {
                if (physicsEnv == null || connection == null)
                {
                        throw new IllegalArgumentException();
                }
                
                this.physicsEnv = physicsEnv;
                
                if (renderDelay == null)
                {
                        renderDelay = new RenderDelay(physicsEnv, this);
                        renderDelay.subscribeListeners(connection);
                }
                
                if (!renderDelay.isInitialized() && localShip != null && localShip.getActor() != null)
                {
                        renderDelay.init(localShip.getActor());
                }
        }
        
        public void updateGraphicsFromPhysics()
        {
                if (this.physicsEnv == null)
                {
                        throw new IllegalStateException();
                }
                
                Iterator<ActorShip> shipIt = this.shipIterator();
                while (shipIt.hasNext())
                {
                        updateShipFromPhysics(shipIt.next());
                }
                
                Iterator<Projectile> projectileIt = this.projectileIterator(true);
                while (projectileIt.hasNext())
                {
                        updateProjectileFromPhysics(projectileIt.next());
                }
                
                for (EventPublic event : physicsEnv.eventIterable())
                {
                        if (event instanceof ProjectileExplosionPublic)
                        {
                                ProjectileExplosionTracker tracker = (ProjectileExplosionTracker) eventTrackers.get(event);
                
                                if (tracker == null)
                                {
                                        tracker = new ProjectileExplosionTracker(resourceDB, physicsEnv, this);
                                        eventTrackers.set(event, tracker);
                                }

                                tracker.update((ProjectileExplosionPublic) event);
                        }
                        else if (event instanceof ActorDiedPublic)
                        {
                                ActorDiedTracker tracker = (ActorDiedTracker) eventTrackers.get(event);
                                
                                if (tracker == null)
                                {
                                        tracker = new ActorDiedTracker(resourceDB, physicsEnv, this);
                                        eventTrackers.set(event, tracker);
                                }

                                tracker.update((ActorDiedPublic) event);
                        }
                }
        }
        
        private void updateShipFromPhysics(ActorShip actorShip)
        {
                PhysicsShipPosition actorPos = new PhysicsShipPosition();
                Point localActorPos = new Point();
                
                ActorPublic physicsActor = actorShip.getActor();
                
                if (localShip != null && localShip.getActor() != null 
                    && localShip.getActor().getPosition(actorPos))
                {
                        localActorPos.set(actorPos.x, actorPos.y);
                }
                
                if (renderDelay == null)
                {
                        actorShip.renderingAt_tick = physicsEnv.getTick();
                }
                else
                {
                        renderDelay.calculateRenderAtTick(actorShip);
                }
                
                actorShip.exists = true;

                if (physicsActor.isRemoved(actorShip.renderingAt_tick))
                {
                        actorShip.exists = false;
                }

                if (physicsActor.isDead(actorShip.renderingAt_tick))
                {
                        actorShip.exists = false;
                }  

                if (physicsActor.getHistoricPosition(actorPos, actorShip.renderingAt_tick, true))
                {
                        actorShip.setRealPositionFromPhysics(actorPos.x, actorPos.y);
                        
                        if (actorShip.isLocalPlayer())
                        {
                                actorShip.setPositionFromPhysics(actorPos.x, actorPos.y);
                        }
                        else
                        {
                                actorShip.setPositionFromPhysics(actorPos.smooth_x, actorPos.smooth_y);
                        }
                        actorShip.setRotationFromPhysics(actorPos.rot_snapped);
                }
                else
                {
                        actorShip.exists = false;
                }


                if (physicsActor.getPosition(actorPos))
                {
                        actorShip.setShadowPositionFromPhysics(actorPos.x, actorPos.y);
                }

                if (actorShip != localShip)
                {
                        actorShip.updateDistanceToLocal(localActorPos);
                }
        }
        
        private void updateProjectileFromPhysics(Projectile projectile)
        {
                ProjectilePublic.Position projectilePos = new ProjectilePublic.Position();
                PhysicsPoint historicProjectilePos = new PhysicsPoint();
                
                ProjectilePublic physicsProjectile = projectile.getPhysicsProjectile();
                        
                if (physicsProjectile.getPosition(projectilePos))
                {
                        projectile.setShadowPositionFromPhysics(projectilePos.x, projectilePos.y);
                }
                
                if (renderDelay == null)
                {
                        projectile.renderingAt_tick = physicsEnv.getTick();
                }
                else
                {
                        renderDelay.calculateRenderAtTick(projectile);
                }
                projectile.exists = true;

                if (physicsProjectile.isRemoved(projectile.renderingAt_tick))
                {
                        projectile.exists = false;
                }

                if (physicsProjectile.getHistoricPosition(
                        historicProjectilePos, 
                        projectile.renderingAt_tick, 
                        true))
                {
                        projectile.setPositionFromPhysics(historicProjectilePos.x, historicProjectilePos.y);
                }
                else
                {
                        projectile.exists = false;
                }
        }
}
