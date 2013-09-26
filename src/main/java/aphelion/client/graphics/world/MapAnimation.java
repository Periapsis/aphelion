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
import aphelion.shared.event.TickEvent;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import aphelion.shared.swissarmyknife.Point;


/**
 *
 * @author Joris
 */
public abstract class MapAnimation extends MapEntity implements TickEvent
{
        final LinkedListEntry<MapAnimation> link = new LinkedListEntry<>(null, this);
        public Camera camera;
        boolean animating;
        final public Point vel = new Point(0, 0);
                
        public MapAnimation(ResourceDB db)
        {
                super(db);
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
                vel.x = x / 1024f;
                vel.y = y / 1024f;
        }
        
        public void setVelocityFromPhysics(PhysicsPoint pos)
        {
                setVelocityFromPhysics(pos.x, pos.y);
        }
        
        

        @Override
        public void tick(long tick)
        {
                pos.add(vel);
        }
}
