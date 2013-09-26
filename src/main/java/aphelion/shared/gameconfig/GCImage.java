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

package aphelion.shared.gameconfig;

import aphelion.client.resource.AsyncTexture;
import aphelion.shared.resource.ResourceDB;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.newdawn.slick.Animation;
import org.newdawn.slick.Image;
import org.newdawn.slick.SpriteSheet;
import org.newdawn.slick.SpriteSheetCounted;



/**
 *
 * @author Joris
 */
public class GCImage extends WrappedValueAbstract
{
        private static final Logger log = Logger.getLogger(GCImage.class.getName());
        
        final ResourceDB db;
        private String resourceKey;
        private int offsetX, offsetY, offsetWidth, offsetHeight;
        private int tilesHorizontal = 1, tilesVertical = 1;
        private int[] frameDuration;
        
        private AsyncTexture texture;
        private Image image; // only set if used
        private SpriteSheetCounted spriteSheet; // only set if used
        
        private boolean tmpDirty = false;
        /*resource: classic.bombs
          offset: [0, 0, 208, 16] # x, y, width, height
          tiles: [10, 1]
          frame-duration: 60
        */
        
        public GCImage(ConfigSelection selection, String key, ResourceDB db)
        {
                super(selection, key);
                this.db = db;
        }

        private void unset()
        {
                if (set) tmpDirty = true;
                
                resourceKey = null;
                offsetX = offsetY = offsetWidth = offsetHeight = 0;
                tilesHorizontal = tilesVertical = 1;
                frameDuration = new int[]{100};
                
                texture = null;
                image = null;
                spriteSheet = null;
                
                
                set = false;
        }
        
        @Override
        boolean newValue(Object value_)
        {
                tmpDirty = false;
                
                if (value_ == null)
                {
                        unset();
                }
                else
                {
                        try
                        {
                                Map value = (Map) value_;
                                setResourceKey((String) value.get("resource"));
                                List offset = (List) value.get("offset");
                                List animation = (List) value.get("tiles");

                                Object frameDurationObj = value.get("frame-duration");
                                if (frameDurationObj == null)
                                {
                                        setFrameDuration(new int[]{100});
                                }
                                else if (frameDurationObj instanceof Integer)
                                {
                                        setFrameDuration(new int[]{(Integer) frameDurationObj});
                                }
                                else
                                {
                                        List frameDurationList = (List) frameDurationObj;
                                        int[] arr = new int[frameDurationList.size()];
                                        for (int i = 0; i < frameDurationList.size(); ++i)
                                        {
                                                arr[i] = (Integer) frameDurationList.get(i);
                                        }

                                        setFrameDuration(arr);
                                }

                                if (offset != null && offset.size() >= 2)
                                {
                                        setOffsetX((Integer) offset.get(0));
                                        setOffsetY((Integer) offset.get(1));
                                }
                                else
                                {
                                        setOffsetX(0);
                                        setOffsetY(0);
                                }

                                if (offset != null && offset.size() >= 4)
                                {
                                        setOffsetWidth((Integer) offset.get(2));
                                        setOffsetHeight((Integer) offset.get(3));
                                }
                                else
                                {
                                        setOffsetWidth(0);
                                        setOffsetHeight(0);
                                }

                                if (animation != null && animation.size() >= 2)
                                {
                                        setTilesHorizontal((Integer) animation.get(0));
                                        setTilesVertical((Integer) animation.get(1)); 
                                }
                                else
                                {
                                        setTilesHorizontal(0);
                                        setTilesVertical(0);
                                }

                                set = true;
                        }
                        catch (ClassCastException | NullPointerException ex)
                        {
                                log.log(Level.WARNING, "Malformed GCImage value with key " + this.key, ex);
                                unset();
                        }
                }
                
                if (tmpDirty)
                {
                        tmpDirty = false;
                        fireChangeListener();
                        return true;
                }
                
                return false;
        }
        
        public boolean isTextureError()
        {
                if (texture == null)
                {
                        return true;
                }
                
                return texture.isError();
        }
        
        public AsyncTexture getTexture()
        {
                return texture;
        }
        
        public Image getImage()
        {
                if (texture == null || !texture.isLoaded())
                {
                        return null;
                }
                
                if (image == null)
                {
                        image = texture.getImage();

                        if (offsetX > 0 || offsetY > 0 || offsetWidth > 0 || offsetHeight > 0)
                        {
                                int width = this.offsetWidth;
                                int height = this.offsetHeight;

                                if (width == 0) { width = texture.getImageWidth() - 1; }
                                if (height == 0) { height = texture.getImageHeight() - 1; }

                                image = image.getSubImage(
                                        offsetX, 
                                        offsetY, 
                                        width,
                                        height);
                        }
                }
                
                return image;
        }
        
        public SpriteSheetCounted getSpriteSheet()
        {
                if (spriteSheet == null)
                {
                        Image image = getImage();
                        
                        if (image == null)
                        {
                                return null;
                        }
                        
                        int tileWidth = image.getWidth() / this.tilesHorizontal;
                        int tileHeight = image.getHeight() / this.tilesVertical;
                        if (tileWidth == 0)
                        {
                                tileWidth = 1;
                        }
                        if (tileHeight == 0)
                        {
                                tileHeight = 1;
                        }
                        spriteSheet = new SpriteSheetCounted(image, tileWidth, tileHeight);
                }
                
                return spriteSheet;
        }
        
        public Animation newAnimation()
        {
                SpriteSheetCounted sprite = getSpriteSheet();
                        
                if (sprite == null)
                {
                        return null;
                }

                Animation anim = new Animation();
                anim.setAutoUpdate(true);
                
                for (int tile = 0; tile < sprite.getTilesCount(); tile++)
                {
                        int duration;
                        if (tile < this.frameDuration.length)
                        {
                                duration = this.frameDuration[tile];
                        }
                        else
                        {
                                duration = this.frameDuration[this.frameDuration.length-1];
                        }
                        anim.addFrame(sprite.getSubImage(tile), duration);
                }
                
                return anim;
                
        }

        public String getResourceKey()
        {
                return resourceKey;
        }
        
        
        public int getOffsetX()
        {
                return offsetX;
        }

        public int getOffsetY()
        {
                return offsetY;
        }

        /** .
         * @return if 0, use the highest x value for the image
         */
        public int getOffsetWidth()
        {
                return offsetWidth;
        }

        /** .
         * @return if 0, use the highest y value for the image
         */
        public int getOffsetHeight()
        {
                return offsetHeight;
        }

        public int getTilesHorizontal()
        {
                return tilesHorizontal;
        }

        public int getTilesVertical()
        {
                return tilesVertical;
        }
        
        public int[] getFrameDuration()
        {
                return frameDuration;
        }

        private void setResourceKey(String resourceKey)
        {
                if (!Objects.equals(this.resourceKey, resourceKey))
                {
                        tmpDirty = true;
                        texture = null;
                        if (resourceKey != null)
                        {
                                texture = db.getTextureLoader().getTexture(resourceKey);
                        }
                        
                        this.image = null;
                        this.spriteSheet = null;
                }
                this.resourceKey = resourceKey;
        }

        private void setOffsetX(int x)
        {
                if (this.offsetX != x)
                {
                        tmpDirty = true;
                        this.image = null;
                        this.spriteSheet = null;
                }
                this.offsetX = x;
                if (this.offsetX < 0) this.offsetX = 0;
        }

        private void setOffsetY(int y)
        {
                if (this.offsetY != y)
                {
                        tmpDirty = true;
                        this.image = null;
                        this.spriteSheet = null;
                }
                this.offsetY = y;
                if (this.offsetY < 0) this.offsetY = 0;
        }

        private void setOffsetWidth(int width)
        {
                if (this.offsetWidth != width)
                {
                        tmpDirty = true;
                        this.image = null;
                        this.spriteSheet = null;
                }
                this.offsetWidth = width;
                if (this.offsetWidth < 0) this.offsetWidth = 0;
        }

        private void setOffsetHeight(int height)
        {
                if (this.offsetHeight != height)
                {
                        tmpDirty = true;
                        this.image = null;
                        this.spriteSheet = null;
                }
                this.offsetHeight = height;
                if (this.offsetHeight < 0) this.offsetHeight = 0;
        }

        private void setTilesHorizontal(int tilesHorizontal)
        {
                if (this.tilesHorizontal != tilesHorizontal)
                {
                        tmpDirty = true;
                        this.spriteSheet = null;
                }
                this.tilesHorizontal = tilesHorizontal;
                if (this.tilesHorizontal < 1) this.tilesHorizontal = 1;
        }

        private void setTilesVertical(int tilesVertical)
        {
                if (this.tilesVertical != tilesVertical)
                {
                        tmpDirty = true;
                        this.spriteSheet = null;
                }
                this.tilesVertical = tilesVertical;
                if (this.tilesVertical < 1) this.tilesVertical = 1;
        }

        private void setFrameDuration(int frameDuration[])
        {
                if (!Arrays.equals(this.frameDuration, frameDuration))
                {
                        tmpDirty = true;
                }
                this.frameDuration = frameDuration;
        }
        
        
}
