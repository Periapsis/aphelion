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
package aphelion.client.graphics.world.event;


import aphelion.client.RENDER_LAYER;
import aphelion.client.graphics.world.ActorShip;
import aphelion.client.graphics.world.GCImageAnimation;
import aphelion.client.graphics.world.MapEntities;
import aphelion.shared.gameconfig.GCImage;
import aphelion.shared.gameconfig.GCInteger;
import aphelion.shared.physics.EnvironmentConf;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.events.pub.ActorDiedPublic;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.resource.ResourceDB;
import javax.annotation.Nonnull;

/**
 *
 * @author Joris
 */
public class ActorDiedTracker implements EventTracker
{
        private final ResourceDB resourceDB;
        private final PhysicsEnvironment physicsEnv;
        private final MapEntities mapEntities;
        
        private boolean firstRun = true;
        private ActorDiedPublic event;
        
        /** the latest correct animation.
         * (we might have spawned other animations that are no longer correct) */
        private GCImageAnimation latestAnim;
        /** the anim is playing for this pid. */
        private int latestAnim_died; 
        
        /** Used to track old animations incase a new animation is spawned somewhere else. */
        private int spawnID = 0;
        
        private long renderDelay;
        
        // Todo: setting?:
        private static final float TIMEWARP_ALPHA_VELOCITY = 0.025f;

        public ActorDiedTracker(@Nonnull ResourceDB resourceDB, @Nonnull PhysicsEnvironment physicsEnv, @Nonnull MapEntities mapEntities)
        {
                this.resourceDB = resourceDB;
                this.physicsEnv = physicsEnv;
                this.mapEntities = mapEntities;
        }
        
        public void update(@Nonnull ActorDiedPublic event)
        {
                if (this.event == null)
                {
                        this.event = event;
                }
                
                assert this.event == event;
                
                int pid_state0 = event.getDied(0);
                
                if (firstRun)
                {
                        if (pid_state0 == 0)
                        {
                                return; // try again next tick
                        }
                        
                        ActorShip ship = mapEntities.getActorShip(pid_state0);
                        
                        if (ship == null)
                        {
                                return; // try again next tick
                        }
                        
                        // do not update the render delay after it has been set
                        
                        renderDelay = ship.renderDelay_current;
                        if (renderDelay >= physicsEnv.getConfig().HIGHEST_DELAY)
                        {
                                return; // too old
                        }
                }
                
                firstRun = false;
                
                if (latestAnim != null && this.latestAnim_died != event.getDied(0))
                {
                        // respawn, a different player died
                        latestAnim = null;
                        latestAnim_died = 0;
                }
                
                if (latestAnim == null)
                {
                        if (pid_state0 != 0  &&
                            event.hasOccurred(0) && 
                            event.getOccurredAt(0) <= physicsEnv.getTick() - renderDelay)
                        {
                                spawnAnimations();
                        }
                }
        }
        
        private void spawnAnimations()
        {
                ActorShip ship = mapEntities.getActorShip(event.getDied(0));
                if (ship == null)
                {
                        return;
                }
                
                ++spawnID;
                
                this.latestAnim_died = event.getDied(0);
                ActorPublic actor = ship.getActor();

                GCImage image = actor.getActorConfigImage("ship-explosion-animation", resourceDB);

                final PhysicsShipPosition actorPos = new PhysicsShipPosition();
                if (image != null && actor.getHistoricPosition(actorPos, ship.renderingAt_tick, false))
                {
                        latestAnim = new MyAnimation(spawnID, resourceDB, image);
                        latestAnim.setPositionFromPhysics(actorPos.smooth_x, actorPos.smooth_y);
                        latestAnim.setVelocityFromPhysics(actorPos.x_vel, actorPos.y_vel);
                        latestAnim.setStopOnHit(true);
                        latestAnim.setAlpha(ship.getAlpha());
                        
                        GCInteger radius = actor.getActorConfigInteger("ship-radius");
                        GCInteger bounceFriction = actor.getActorConfigInteger("ship-bounce-friction");
                        GCInteger bounceOtherAxisFriction = actor.getActorConfigInteger("ship-bounce-friction-other-axis");
                        
                        latestAnim.setMapCollision(
                                mapEntities.collision, 
                                physicsEnv.getMap(), 
                                radius.get(),
                                bounceFriction.get(),
                                bounceOtherAxisFriction.get());
                        
                        mapEntities.addAnimation(RENDER_LAYER.AFTER_PROJECTILES, latestAnim, null);
                        ship.activeDeathAnimation = latestAnim;
                }
        }
        
        private class MyAnimation extends GCImageAnimation
        {
                private final int mySpawnID;
                MyAnimation(int spawnID, @Nonnull ResourceDB db, @Nonnull GCImage image)
                {
                        super(db, image);
                        this.mySpawnID = spawnID;
                }

                @Override
                public void tick(long tick)
                {
                        super.tick(tick);
                        
                        // fade out if the event was timewarped (or: fade it back in)
                        
                        // Note: currently three animations are spawned in the case of :
                        // 1. Pid 1 dies
                        // 2. Timewarp: pid 2 dies
                        // 3. Timewarp: pid 1 dies (original situation was correct after all)
                        // Two animations might better, but this issue is probably a rare occurance
                        
                        if (spawnID == this.mySpawnID && event.hasOccurred(0))
                        {
                                this.setAlphaVelocity(TIMEWARP_ALPHA_VELOCITY);
                        }
                        else
                        {
                                this.setAlphaVelocity(-TIMEWARP_ALPHA_VELOCITY);
                        }
                }
        }
}
