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

import aphelion.shared.event.TickEvent;

/**
 *
 * @author Joris
 */
public class RenderDelay implements TickEvent
{
        private int updateDelay;
        
        private int set = 0; // 0 = not set; 1 = set, but tick has not seen it yet; 2 = set
        private int desired = 0;
        private int renderDelay = 0;
        private long lastUpdate = 0;
        private long mostRecentUpdate;

        /** @param updateDelay move the render delay by 1 tick every this many ticks. */
        public RenderDelay(int updateDelay)
        {
                this.updateDelay = updateDelay;
        }
        
        @Override
        public void tick(long tick)
        {
                if (set == 1)
                {
                        // make sure the begin value is not changed immediately when a large updateDelay is used.
                        lastUpdate = tick - 1;
                        set = 2;
                }
                
                if (renderDelay == desired)
                {
                        lastUpdate = tick;
                }
                else if (updateDelay == 0)
                {
                        lastUpdate = tick;
                        renderDelay = desired;
                }
                else
                {
                        if (tick - lastUpdate > updateDelay)
                        {
                                lastUpdate = tick;
                                
                                if (renderDelay < desired)
                                {
                                        ++renderDelay;
                                }
                                else
                                {
                                        --renderDelay;
                                }
                        }
                }
        }
        
        public int get()
        {
                return renderDelay;
        }
        
        public int getDesired()
        {
                return desired;
        }
        
        public void set(int renderDelay)
        {
                this.desired = renderDelay;
                if (set == 0)
                {
                        this.renderDelay = renderDelay;
                        set = 1;
                }
        }
        
        public void setImmediate(int renderDelay)
        {
                this.desired = renderDelay;
                this.renderDelay = renderDelay;
                if (set == 0)
                {
                        set = 1;
                }
                
        }
        
        public boolean hasBeenSet()
        {
                return set > 0;
        }
        
        /** Call this method whenever a new position update for an actor has been received.
         * @param tick The current physics ticks.
         * @param positionTick The physics tick value of the position (move, warp) message. 
         */
        public void setByPositionUpdate(long tick, long positionTick)
        {
                int RENDER_DELAY_BASE = 5; // something related to send move delay?
                
                if (set == 0 || positionTick > mostRecentUpdate)
                {
                        desired = RENDER_DELAY_BASE;
                        desired += tick - positionTick;

                        // render delay has not been set before
                        if (set == 0)
                        {
                                renderDelay = desired;
                        }
                        
                        mostRecentUpdate = positionTick;
                }
                
                if (set == 0) set = 1;
        }
}
