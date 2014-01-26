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

import aphelion.shared.resource.ResourceDB;
import aphelion.client.graphics.screen.Camera;
import aphelion.shared.event.TickEvent;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.swissarmyknife.Point;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import javax.annotation.Nonnull;
import org.newdawn.slick.Color;

/**
 *
 * @author Joris
 */
public abstract class MapEntity implements TickEvent
{
	protected ResourceDB db;
        
        public boolean exists = false;
        final public Point pos = new Point(0,0);
        final public PhysicsPoint physicsPos = new PhysicsPoint(0, 0);
        
        protected float alpha = 1f;
        protected float alphaVelocity;
        protected final org.newdawn.slick.Color alphaFilter = new Color(1f, 1f, 1f, 1f);
        
        public MapEntity(@Nonnull ResourceDB db)
	{
		this.db = db;
	}
        
        /** Do a render iteration for this entity.
         * 
         * @param camera
         * @param iteration The index of this render iteration, starting at 0.
         *        Do not render if the index is too high for your entity.
         * @return true, if this entity needs another render iteration 
         */
        public abstract boolean render(@Nonnull Camera camera, int iteration);
        
        /** Called if the entity is outside of the visible screen area. */
        public void noRender()
        {
        }
        
        /** Does this entity intersect the specified camera range?.
         * Low and high are defined such that low.x &lt;= high.x && low.y &lt;= high.y
         * @param low 
         * @param high
         * @return 
         */
        public boolean isWithinCameraRange(@Nonnull Point low, @Nonnull Point high)
        {
                return SwissArmyKnife.isPointInsideRectangle(low, high, pos);
        }
        
        public final void setPositionFromPhysics(int x, int y)
        {
                physicsPos.set(x, y);
                setPositionFromPhysics();
        }
        
        public final void setPositionFromPhysics(@Nonnull PhysicsPoint pos)
        {
                physicsPos.set(pos);
                setPositionFromPhysics();
        }
        
        public final void setPositionFromPhysics()
        {
                pos.x = physicsPos.x / 1024f;
                pos.y = physicsPos.y / 1024f;
        }
        
        public void setPosition(@Nonnull MapEntity other)
        {
                this.pos.set(other.pos);
                this.physicsPos.set(other.physicsPos);
        }
        
        public void setAlpha(float alpha)
        {
                this.alpha = alpha;
                alphaFilter.a = this.alpha;
        }
        
        public void setAlpha(float alpha, float alphaVelocity)
        {
                this.alpha = alpha;
                this.alphaVelocity = alphaVelocity;
                alphaFilter.a = this.alpha;
        }
        
        public void setAlphaVelocity(float alphaVelocity)
        {
                this.alphaVelocity = alphaVelocity;
        }

        public float getAlpha()
        {
                return alpha;
        }
        
        @Override
        public void tick(long tick)
        {
                this.alpha = SwissArmyKnife.clip(this.alpha + this.alphaVelocity, 0f, 1f);
                alphaFilter.a = this.alpha;
        }
}
