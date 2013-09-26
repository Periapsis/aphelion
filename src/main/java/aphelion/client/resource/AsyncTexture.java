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

package aphelion.client.resource;


import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.LinkedList;
import java.util.logging.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.newdawn.slick.Color;
import org.newdawn.slick.Image;
import org.newdawn.slick.opengl.SlickLastBindHack;
import org.newdawn.slick.opengl.Texture;
import org.newdawn.slick.opengl.TextureImpl;
import org.newdawn.slick.opengl.renderer.Renderer;
import org.newdawn.slick.opengl.renderer.SGL;

/** An asynchronously loaded texture to be bound within JOGL.
 * Based on org.newdawn.slick.opengl.TextureImpl, however this 
 * implementation uses a worker to do as much work as possible during 
 * loading. Until loading is complete this object will return 
 * placeholder values. This avoids having to use complex loading 
 * logic in other parts of the project.
 * 
 * This object is responsible for 
 * keeping track of a given OpenGL texture and for calculating the
 * texturing mapping coordinates of the full image.
 * 
 * Since textures need to be powers of 2 the actual texture may be
 * considerably bigger that the source image and hence the texture
 * mapping coordinates need to be adjusted to match up drawing the
 * sprite against the texture.
 *
 * @author Kevin Glass
 * @author Brian Matzon
 * @author Joris
 */
public class AsyncTexture implements Texture
{
        private static final Logger log = Logger.getLogger(AsyncTexture.class.getName());
        private static SGL GL = Renderer.get();
        
        public static Texture getLastBind()
        {
                return TextureImpl.getLastBind();
	}
        
        public static void setLastBind(Texture texture)
        {
                SlickLastBindHack.setLastBind(texture);
        }
        
        /** Clear the bind cache. Call me at the start of each render loop.
         */
        public static void unbind()
        {
                TextureImpl.unbind();
        }
        
        public static void bindNone()
        {
                TextureImpl.bindNone();
        }
        
        private AsyncTextureLoader loader;
        
        /** The ResourceDB key. */
        String resourceKey;
        /** The GL texture target type. For example GL11.GL_TEXTURE_2D */
        int target;
        int textureID;
        private boolean loaded = false;
        boolean error = false;
        boolean released = false;
        
        int imageHeight = 1;
        int imageWidth = 1;
        int texWidth = 1;
        int texHeight = 1;
        float widthRatio = 1;
        float heightRatio = 1;
        boolean alpha = false;
        
        private LinkedList<WeakReference<Image>> imagesBeforeLoad = new LinkedList<WeakReference<Image>>();

        public AsyncTexture(AsyncTextureLoader loader, String resourceKey, int target)
        {
                this.loader = loader;
                this.resourceKey = resourceKey;
                this.target = target;
                // GL11.GL_TEXTURE_2D
        }

        @Override
        protected void finalize() throws Throwable
        {
                loader.finalizeTexture(this);
                super.finalize();
        }
        
        /** Has the actual texture been loaded into the GPU
         * @return 
         */
        public boolean isLoaded()
        {
                return loaded;
        }
        
        /** Was there an error during loading?
         * @return If true, this texture will never complete loading
         */
        public boolean isError()
        {
                return error;
        }
        
        public boolean isReleased()
        {
                return released;
        }
        
        @Override
        public void bind()
        {
                if (getLastBind() == this)
                {
                        return;
                }
                
                if (isLoaded())
                {
                        setLastBind(this);
                        GL.glEnable(SGL.GL_TEXTURE_2D);
                        GL.glBindTexture(target, textureID);
                }
                else
                {
                        GL.glDisable(SGL.GL_TEXTURE_2D);
                        GL.glColor4f(0, 0, 0, 0);
                }
                
                setLastBind(this);
        }

        @Override
        public boolean hasAlpha()
        {
                return alpha;
        }

        @Override
        public String getTextureRef()
        {
                return resourceKey;
        }
        
        public String getResourceKey()
        {
                return resourceKey;
        }

        /**
	 * Get the height of the original image
	 *
	 * @return The height of the original image
	 */
        @Override
        public int getImageHeight()
        {
                 return imageHeight;
        }

        /**
	 * Get the width of the original image
	 *
	 * @return The height of the original image
	 */
        @Override
        public int getImageWidth()
        {
                return imageWidth;
        }

        /**
	 * Get the ratio between the texture height and the image height.
	 *
	 * @return imageHeight / textureHeight (between 0 and 1)
	 */
        @Override
        public float getHeight()
        {
                return heightRatio;
        }

        /**
	 * Get the ratio between the texture width and the image width.
	 *
	 * @return imageWidth / textureWidth (between 0 and 1)
	 */
        @Override
        public float getWidth()
        {
                return widthRatio;
        }

        /**
	 * Get the height of the actual texture. 
         * This height may differ from the image height since texture sizes are usually a power of 2
	 * 
	 * @return The height of the actual texture
	 */
        @Override
        public int getTextureHeight()
        {
                return texHeight;
        }

        /**
	 * Get the width of the actual texture. 
         * This height may differ from the image height since texture sizes are usually a power of 2
	 * 
	 * @return The height of the actual texture
	 */
        @Override
        public int getTextureWidth()
        {
                return texWidth;
        }

        /** Explicit release */
        @Override
        public void release()
        {
                ByteBuffer temp = ByteBuffer.allocateDirect(4);
                temp.order(ByteOrder.nativeOrder());
                IntBuffer texBuf = temp.asIntBuffer(); 
                texBuf.put(textureID);
                texBuf.flip();

                GL.glDeleteTextures(texBuf);

                if (getLastBind() == this)
                {
                        GL.glDisable(SGL.GL_TEXTURE_2D);
                        setLastBind(null);
                }

                released = true;
                loader.removeFromCache(this);
        }

        @Override
        public int getTextureID()
        {
                return textureID;
        }

        @Override
        public byte[] getTextureData()
        {
                if (isLoaded())
                {
                        ByteBuffer buffer = BufferUtils.createByteBuffer((hasAlpha() ? 4 : 3) * texWidth * texHeight);
                        bind();
                        GL.glGetTexImage(SGL.GL_TEXTURE_2D, 0, hasAlpha() ? SGL.GL_RGBA : SGL.GL_RGB, SGL.GL_UNSIGNED_BYTE, buffer);
                        byte[] data = new byte[buffer.limit()];
                        buffer.get(data);
                        buffer.clear();

                        return data;
                }
                else
                {
                        return null;
                }
        }
        
        @Override
        public void setTextureFilter(int textureFilter)
        {
                bind();
                GL.glTexParameteri(target, SGL.GL_TEXTURE_MIN_FILTER, textureFilter); 
                GL.glTexParameteri(target, SGL.GL_TEXTURE_MAG_FILTER, textureFilter); 
	}
        
        void loaded()
        {
                for (WeakReference<Image> imageRef : this.imagesBeforeLoad)
                {
                        Image image = imageRef.get();
                        if (image != null)
                        {
                                 // reinit width and height values as these are not known before loading is complete.
                                image.setTexture(this);
                        }
                }
                
                this.imagesBeforeLoad = null;
                
                loaded = true;
        }
        
        /** Get a slick Image instance for this texture.
         * Use this method instead of using the Image constructors or copy methods directly.
         * @return 
         */
        public Image getImage()
        {
                // horrible test until kevin can find something more suitable
                try
                {
                        GL11.glGetError();
                }
                catch (NullPointerException e)
                {
                        throw new RuntimeException("getImage() may only be called as part of the render loop.");
                }
                
                
                Image image = new MyImage(this);
                if (!this.isLoaded())
                {
                        imagesBeforeLoad.add(new WeakReference<>(image));
                }
                return image;
        }
        
        private static class MyImage extends Image
        {
                MyImage(Texture texture)
                {
                        super(texture);
                }                
                
                @Override
                public Color getColor(int x, int y)
                {
                        if (pixelData == null)
                        {
                                pixelData = texture.getTextureData();
                        }
                        
                        if (pixelData == null)
                        {
                                return null;
                        }
                        return super.getColor(x, y);
                }
        }
}
