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
package aphelion.client.graphics;


import aphelion.client.graphics.world.ActorShip;
import aphelion.client.graphics.world.MapEntities;
import aphelion.client.graphics.world.Projectile;
import aphelion.shared.gameconfig.*;
import aphelion.shared.gameconfig.GCDocumentation.GCDOCUMENTATION_TYPE;
import aphelion.shared.net.game.GameProtocolConnection;
import aphelion.shared.net.game.GameS2CListener;
import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.net.protobuf.GameS2C;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.swissarmyknife.Point;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import java.lang.ref.WeakReference;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class RenderDelay implements GameS2CListener
{
        private static final Logger log = Logger.getLogger(RenderDelay.class.getName());
        
        private final MapEntities mapEntities;
        private final PhysicsEnvironment physicsEnv;
        
        private GCInteger delay = GCIntegerFixed.ZERO;
        private GCInteger latencyFactor = GCIntegerFixed.ZERO;
        private GCBoolean projectiles = GCBooleanFixed.FALSE;
        private GCBoolean maximizeLocalTime = GCBooleanFixed.FALSE;
        private GCInteger updateShipEvery = GCIntegerFixed.ZERO;
        private GCInteger updateProjectileEvery = GCIntegerFixed.ZERO;

        public RenderDelay(PhysicsEnvironment physicsEnv, MapEntities mapEntities)
        {
                this.physicsEnv = physicsEnv;
                this.mapEntities = mapEntities;
        }
        
        static
        {
                GCDocumentation.put(
                        "render-delay", 
                        GCDOCUMENTATION_TYPE.TICK_NON_NEGATIVE, 
                        false, 
                        "A fixed amount of render delay to always apply to ships.");
                
                GCDocumentation.put(
                        "render-delay-latency-factor", 
                        GCDOCUMENTATION_TYPE.PERMILLE, 
                        false, 
                        "Adds a permille of the remote ships latency to its render delay.");
                
                GCDocumentation.put(
                        "render-delay-projectiles", 
                        GCDOCUMENTATION_TYPE.BOOLEAN, 
                        false, 
                        "If set, projectiles will use a dynamically calculated render delay based on nearby ships.");
                
                GCDocumentation.put(
                        "render-delay-maximize-local-time", 
                        GCDOCUMENTATION_TYPE.BOOLEAN, 
                        false, 
                        "If set, the render delay of projectiles will climb towards zero whenever possible. " +
                        "If not set the render delay will remain high after having passed a remote ship.");
                
                GCDocumentation.put(
                        "render-delay-update-ship-delay-every-ticks", 
                        GCDOCUMENTATION_TYPE.TICK_NON_NEGATIVE, 
                        false, 
                        "When the desired render delay of a ship suddenly changes, this setting will help smooth it out over time. " +
                        "Every x ticks, add/substract one tick to the actual render delay. Use x = zero to disable.");
                
                GCDocumentation.put(
                        "render-delay-update-projectile-delay-every-ticks", 
                        GCDOCUMENTATION_TYPE.TICK_NON_NEGATIVE, 
                        false, 
                        "When the desired render delay of a projectile suddenly changes, this setting will help smooth it out over time. " +
                        "Every x ticks, add/substract one tick to the actual render delay. Use x = zero to disable. " +
                        "If this setting is set too high, projectiles might appear to pass through remote ships.");
        }
        
        public void init(ActorPublic localActor)
        {
                if (localActor == null)
                {
                        delay = GCIntegerFixed.ZERO;
                        latencyFactor = GCIntegerFixed.ZERO;
                        projectiles = GCBooleanFixed.FALSE;
                        maximizeLocalTime = GCBooleanFixed.FALSE;
                        updateShipEvery = GCIntegerFixed.ZERO;
                        updateProjectileEvery = GCIntegerFixed.ZERO;
                        return;
                }
                
                delay = localActor.getActorConfigInteger("render-delay");
                latencyFactor = localActor.getActorConfigInteger("render-delay-latency-factor");
                projectiles = localActor.getActorConfigBoolean("render-delay-projectiles");
                maximizeLocalTime = localActor.getActorConfigBoolean("render-delay-maximize-local-time");
                updateShipEvery = localActor.getActorConfigInteger("render-delay-update-ship-delay-every-ticks");
                updateProjectileEvery = localActor.getActorConfigInteger("render-delay-update-projectile-delay-every-ticks");
        }
        
        public void calculateRenderAtTick(ActorShip ship)
        {
                ActorPublic actor = ship.getActor();
                
                ship.renderDelay_value.setUpdateDelay(updateShipEvery.get());
                
                if (this.delay.get() <= 0 && this.latencyFactor.get() <= 0)
                {
                        ship.renderDelay_value.set(0);
                }
                
                
                long createdAgo = physicsEnv.getTick() - actor.getCreatedAt();
                
                ship.renderDelay_current = SwissArmyKnife.clip(
                        ship.renderDelay_value.get(),
                        0, 
                        physicsEnv.econfig.HIGHEST_DELAY);
                
                if (ship.renderDelay_current > createdAgo)
                {
                        ship.renderDelay_current = (int) createdAgo;
                        if (ship.renderDelay_current < 0)
                        {
                                ship.renderDelay_current = 0;
                        }
                }
                        
                ship.renderingAt_tick = physicsEnv.getTick() - ship.renderDelay_current;
        }
        
        public void calculateRenderAtTick(Projectile projectile)
        {
                projectile.renderDelay_value.setUpdateDelay(updateProjectileEvery.get());
                ActorShip localShip = mapEntities.getLocalShip();
                
                if (!this.projectiles.get())
                {
                        projectile.renderDelay_value.set(0);
                }
                else
                {
                        // the closest ship excluding the local one
                        // all actors should have been updated at this point
                        ActorShip closest = mapEntities.findNearestActor(projectile.pos, false);
                        if (closest == null || localShip == null)
                        {
                                projectile.renderDelay_value.set(0);
                        }
                        else
                        {
                                boolean switchedShip = false;

                                if (projectile.renderDelay_basedOn == null 
                                    || projectile.renderDelay_basedOn.get() != closest)
                                {
                                        switchedShip = true;
                                        projectile.renderDelay_basedOn = new WeakReference<>(closest);
                                }

                                /* p = local player
                                 * r = remote player
                                 * e = entity (projectile)
                                 * r' = the shadow of the player r (the position that is 
                                 *      dead reckoned up the current time)
                                 * 
                                 * δ(x, y) is the distance between x en y
                                 * d(x, y) is the render delay of y on the screen of x
                                 * d(p, e) = 0       if δ(p , e') = 0
                                 * d(p, e) = d(p,r)  if δ(r', e') = 0
                                 * 
                                 * d(p, e) = d(p, r) * max(0, 1 - δ(r', e') / δ(p, r) )
                                 * sqrt(a) / sqrt(b) = sqrt(a / b)
                                 */

                                Point diff = new Point();
                                diff.set(closest.shadowPosition);
                                diff.sub(projectile.shadowPosition);
                                float distSq_rShadow_e = diff.distanceSquared();

                                diff.set(localShip.pos);
                                diff.sub(closest.pos);
                                float distSq_p_r = diff.distanceSquared();

                                double renderDelay = 
                                        closest.renderDelay_value.get() * 
                                        Math.max(0, 1 - Math.sqrt(distSq_rShadow_e / distSq_p_r));

                                if (Double.isNaN(renderDelay))
                                {
                                        renderDelay = 0;
                                }

                                renderDelay = Math.round(renderDelay);

                                if (maximizeLocalTime.get())
                                {
                                        projectile.renderDelay_value.set((int) renderDelay);
                                }
                                else
                                {
                                        Point prevPos = new Point(projectile.shadowPosition_prev);
                                        prevPos.sub(localShip.pos);
                                        
                                        Point nextPos = new Point(projectile.shadowPosition);
                                        nextPos.sub(localShip.pos);
                                        
                                        boolean movingAway = nextPos.distanceSquared() > prevPos.distanceSquared();
                                        
                                        if (movingAway)
                                        {
                                                // if the distance to the local ship is increasing:
                                                // only increase the render delay, do not decrease it.
                                                // unless the calculation has switched to a different ship
                                                if (switchedShip || renderDelay > projectile.renderDelay_value.getDesired())
                                                {
                                                        projectile.renderDelay_value.set((int) renderDelay);
                                                }
                                        }
                                        else
                                        {
                                                projectile.renderDelay_value.set((int) renderDelay);
                                        }
                                }
                        }
                }
                
                // The smoothed render delay
                projectile.renderDelay_current = SwissArmyKnife.clip(
                        projectile.renderDelay_value.get(),
                        0, 
                        physicsEnv.econfig.HIGHEST_DELAY);
                        
                projectile.renderingAt_tick = physicsEnv.getTick() - projectile.renderDelay_current;
        }
        
        @Override
        public void gameS2CMessage(GameProtocolConnection game, GameS2C.S2C s2c, long receivedAt)
        {
                // Calculate the render delay for ships

                for (GameOperation.ActorMove msg : s2c.getActorMoveList())
                {
                        if (msg.getDirect())
                        {
                                ActorShip ship = mapEntities.getActorShip(msg.getPid());
                                if (ship == null)
                                {
                                        continue;
                                }

                                if (ship.isLocalPlayer())
                                {
                                        ship.renderDelay_value.set(0);
                                }
                                else
                                {
                                        // Use the tick of the first move
                                        // This way the render delay does not continuesly drift because 
                                        // of the delayed move update mechanism (SEND_MOVE_DELAY, 
                                        // which is very similar to Nagle's algorithm).
                                        long positionTick = msg.getTick();

                                        if (!ship.renderDelay_value.hasBeenSet() || positionTick > ship.renderDelay_mostRecentMove)
                                        {
                                                long desired = physicsEnv.getTick() - positionTick; // the latency
                                                desired = desired * latencyFactor.get() / 1000;
                                                desired += this.delay.get();
                                                if (desired < 0) { desired = 0; }

                                                ship.renderDelay_value.set((int) desired);
                                                ship.renderDelay_mostRecentMove = positionTick;
                                        }
                                }
                        }
                }
        }
}
