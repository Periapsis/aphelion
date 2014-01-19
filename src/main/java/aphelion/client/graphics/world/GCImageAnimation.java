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
import aphelion.shared.gameconfig.GCImage;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.Point;
import org.newdawn.slick.Animation;



/**
 *
 * @author Joris
 */
public class GCImageAnimation extends MapAnimation
{
        protected GCImage image;
        protected Animation anim;
        
        public GCImageAnimation(ResourceDB db, GCImage image)
        {
                super(db);
                this.image = image;
        }
        
        @Override
        public boolean isDone()
        {
                if (done)
                {
                        return true;
                }
                
                if (image != null && image.isTextureError())
                {
                        return true;
                }
                
                if (anim != null && anim.isStopped())
                {
                        return true;
                }
                
                return false;
        }

        @Override
        public boolean render(Camera camera, int iteration)
        {
                if (iteration > 0)
                {
                        return false;
                }
                
                if (isDone())
                {
                        return false;
                }
                
                if (anim == null)
                {
                        anim = image.newAnimation();
                }
                
                if (anim == null)
                {
                        // not done loading yet
                        return false;
                }
                
                anim.setLooping(false);
                
                Point screenPos = new Point();
                
                camera.mapToScreenPosition(this.pos, screenPos);
                
                screenPos.x -= anim.getWidth() / 2 * camera.zoom;
                screenPos.y -= anim.getHeight() / 2 * camera.zoom; 
                
                anim.updateNoDraw();
                applyOffset(screenPos, anim.getFrame(), anim.getFrameCount());
                
                anim.draw(
                        screenPos.x, 
                        screenPos.y ,
                        anim.getWidth() * camera.zoom,
                        anim.getHeight() * camera.zoom,
                        this.alphaFilter);
                
                return false;
        }

        @Override
        public void noRender()
        {
                if (isDone())
                {
                        return;
                }
                
                if (anim == null)
                {
                        anim = image.newAnimation();
                }
                
                if (anim == null)
                {
                        // not done loading yet
                        return;
                }
                
                anim.updateNoDraw();
        }
        
        @Override
        public void reset()
        {
                anim = null;
        }
        
        protected void applyOffset(Point drawPosition, int frame, int frameCount)
        {
        }
}
