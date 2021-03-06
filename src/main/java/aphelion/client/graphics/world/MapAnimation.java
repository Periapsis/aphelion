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
import aphelion.shared.physics.Collision;
import aphelion.shared.physics.PhysicsMap;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import javax.annotation.Nonnull;


/**
 *
 * @author Joris
 */
public abstract class MapAnimation extends MapEntity
{
        final LinkedListEntry<MapAnimation> link = new LinkedListEntry<>(this);
        public Camera camera;
        boolean animating; // set by MapEntities
        protected boolean done;
        
        final public PhysicsPoint physicsVel = new PhysicsPoint(0, 0);
        
        protected int collision_radius = -1; // in physics points, -1 to disable map collision
        protected Collision collision;
        protected PhysicsMap collision_map;
        protected int collision_bounceFriction;
        protected int collision_bounceOtherAxisFriction;
        protected boolean collision_stopOnHit;
        
        public MapAnimation(@Nonnull ResourceDB db)
        {
                super(db);
        }
        
        /** Mark that the animation is done.
         * After calling this method, isDone() should always return true.
         * Using this method to stop animations prematurely.
         */
        public void setDone()
        {
                done = true;
        }
        
        /** Done animating?.
         * @return True if the animation is done and if it should be removed
         */
        public abstract boolean isDone();
        
        /** Reset the animation for the next use. (optional)
         */
        public abstract void reset(); 
        
        public final boolean isAnimating()
        {
                return animating;
        }
        
        public void setVelocityFromPhysics(int x, int y)
        {
                this.physicsVel.set(x, y);
        }
        
        public void setVelocityFromPhysics(@Nonnull PhysicsPoint vel)
        {
                this.physicsVel.set(vel);
        }
        
        public void setMapCollision(@Nonnull Collision collision, @Nonnull PhysicsMap collision_map, 
                                    int collision_radius, 
                                    int collision_bounceFriction, int collision_bounceOtherAxisFriction)
        {
                this.collision = collision;
                this.collision_map = collision_map;
                this.collision_radius = collision_radius;
                this.collision_bounceFriction = collision_bounceFriction;
                this.collision_bounceOtherAxisFriction = collision_bounceOtherAxisFriction;
        }
        
        public void setStopOnHit(boolean collision_stopOnHit)
        {
                this.collision_stopOnHit = collision_stopOnHit;
        }

        @Override
        public void setPosition(@Nonnull MapEntity other)
        {
                super.setPosition(other);
                if (other instanceof MapAnimation)
                {
                        this.physicsVel.set(((MapAnimation)other).physicsVel);
                }
                else
                {
                        this.physicsVel.set(0, 0);
                }
        }

        @Override
        public void tick(long tick)
        {
                if (this.isDone())
                {
                        return; // no need to update stuff
                }
                
                super.tick(tick);
                
                if (collision_radius >= 0)
                {
                        if (!this.physicsVel.isZero())
                        {
                                collision.reset();
                                collision.setPreviousPosition(this.physicsPos);
                                collision.setVelocity(this.physicsVel);
                                collision.setMap(collision_map);
                                collision.setRadius(collision_radius);
                                collision.setBounceFriction(collision_bounceFriction);
                                collision.setOtherAxisFriction(collision_bounceOtherAxisFriction);
                                collision.setBouncesLeft(collision_stopOnHit ? 0 : -1);
                                collision.tickMap(0);
                                
                                collision.getNewPosition(this.physicsPos);
                                collision.getVelocity(this.physicsVel);

                                if (collision.hasExhaustedBounces())
                                {
                                        this.physicsVel.set(0, 0);
                                }
                        }
                }
                else
                {
                        physicsPos.add(physicsVel);
                }
                
                this.setPositionFromPhysics();
        }
}
