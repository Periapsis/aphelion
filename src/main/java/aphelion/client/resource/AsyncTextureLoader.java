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

import aphelion.shared.resource.ResourceDB;
import aphelion.shared.event.LoopEvent;
import aphelion.shared.event.Workable;
import aphelion.shared.event.WorkerTask;
import aphelion.shared.event.promise.PromiseException;
import aphelion.shared.event.promise.PromiseRejected;
import aphelion.shared.event.promise.PromiseResolved;
import aphelion.shared.swissarmyknife.ThreadSafe;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.newdawn.slick.opengl.ImageDataFactory;
import org.newdawn.slick.opengl.InternalTextureLoader;
import org.newdawn.slick.opengl.LoadableImageData;

/**
 * Loads (slick!) textures asynchronously.
 * An attempt to load a texture will return an AsyncTexture object immediately.
 * When attempting to draw a blank placeholder texture will be used if loading is not yet complete.
 * 
 * This class lets you get a lot of references to a bunch of textures while the game is 
 * running without having to worry about large delays in frame rendering.
 * 
 * This object must be added as a loop event to the loop doing the OpenGL rendering.
 * 
 * Note that the final step (loading into texture memory) still has to be done on the render thread.
 * 
 * Only use this object in the thread doing the opengl rendering.
 * 
 * @author Joris
 */
public class AsyncTextureLoader implements LoopEvent
{
        private static final Logger log = Logger.getLogger("aphelion.client.graphics");
        
        // TODO: perhaps a better way of expiring items in the cache
        private final HashMap<String, WeakReference<AsyncTexture>> textureCache = new HashMap<String, WeakReference<AsyncTexture>>();
        
        final private ResourceDB db;
        final private Workable workable;
        private AtomicInteger pending = new AtomicInteger(0);
        
        private ConcurrentLinkedQueue<Integer> releaseQueue = new ConcurrentLinkedQueue<Integer>();
        private int cleanupCounter = 0;
        
        /**
         * 
         * @param db
         * @param workable The main thread of this workable should be 
         * the thread that is doing the opengl rendering. 
         */
        public AsyncTextureLoader(ResourceDB db, Workable workable)
        {
                this.db = db;
                this.workable = workable;
        }
        
        public ResourceDB getResourceDB()
        {
                return this.db;
        }
        
        @ThreadSafe
        void finalizeTexture(AsyncTexture texture)
        {
                // Make sure not to reference texture outside of this method!
                // this method is also used in a finalizer
                if (texture.isLoaded() && !texture.isReleased())
                {
                        texture.released = true;
                        releaseQueue.add(texture.textureID);
                }
        }
        
        /** Are one or more textures currently being loaded?.
         * This could be used to present a load screen while important 
         * textures are being loaded (such as ui elements, tileset, etc).
         * 
         * @return 
         */
        @ThreadSafe
        public boolean isLoadingSomething()
        {
                return pending.get() > 0;
        }
        
        
        /** Asynchronously load a texture. 
         * While the texture is not yet loaded, a placeholder texture will be displayed instead.
         * Note that this means getWidth(), getTextureWidth() etc might return different values 
         * after the loading has completed.
         * @param resourceKey A ResourceDB key. If the key does not exist, an AsyncTexture instance 
         * will still be returned (that will never complete loading). An error will be logged however. 
         * Also see db.resourceExists()
         * @return  
         */
        @ThreadSafe
        public AsyncTexture getTexture(String resourceKey)
        {
                AsyncTexture texture = null;
                
                synchronized(textureCache)
                {
                        WeakReference<AsyncTexture> cacheValue = textureCache.get(resourceKey);
                        if (cacheValue != null)
                        {
                                texture = cacheValue.get();
                                if (texture != null)
                                {
                                        return texture;
                                }
                        }

                        if (texture == null)
                        {
                                texture = new AsyncTexture(this, resourceKey, GL11.GL_TEXTURE_2D);
                                texture.target = GL11.GL_TEXTURE_2D;
                                TextureCallback cb = new TextureCallback(texture);
                                workable.addWorkerTask(new TextureWorker(db), resourceKey).then(cb, cb);
                                pending.incrementAndGet();
                                textureCache.put(resourceKey, new WeakReference<>(texture));
                        }

                        if (++cleanupCounter > 10)
                        {
                                cleanupCounter = 0;
                                Iterator<WeakReference<AsyncTexture>> it = textureCache.values().iterator();
                                while (it.hasNext())
                                {
                                        if (it.next().get() == null)
                                        {
                                                it.remove();
                                        }
                                }
                        }
                
                }
                
                return texture;
        }
        
        @ThreadSafe
        public void removeFromCache(AsyncTexture texture)
        {
                synchronized(textureCache)
                {
                        WeakReference<AsyncTexture> cacheValue = textureCache.get(texture.resourceKey);

                        if (cacheValue != null && cacheValue.get() == texture)
                        {
                                textureCache.remove(texture.resourceKey);
                        }
                }
        }

        @Override
        public void loop(long systemNanoTime, long sourceNanoTime)
        {
                Integer textureID;
                while ( (textureID = releaseQueue.poll()) != null)
                {
                        ByteBuffer temp = ByteBuffer.allocateDirect(4);
                        temp.order(ByteOrder.nativeOrder());
                        IntBuffer texBuf = temp.asIntBuffer(); 
                        texBuf.put(textureID);
                        texBuf.flip();

                        GL11.glDeleteTextures(texBuf);
                }
        }
        
        
        private static class TextureWorker extends WorkerTask<String, Object[]>
        {
                private ResourceDB db;

                TextureWorker(ResourceDB db)
                {
                        this.db = db;
                }
                
                @Override
                public Object[] work(String resourceKey) throws PromiseException
                {
                        // avoid the resource cache, AsyncTextureLoader has its own cache
                        InputStream in = db.getInputStreamSync(resourceKey); 
                        ResourceDB.FileEntry fileEntry = db.getFileEntry(resourceKey);
                        
                        if (in == null || fileEntry == null)
                        {
                                log.log(Level.SEVERE, "Unable to load texture, the given resource key ({0}) does not exist", resourceKey);
                                throw new InvalidResourceKeyException();
                        }
                        
                        String fileName = fileEntry.zipEntry == null ? fileEntry.file.getName() : fileEntry.zipEntry;
                        
                        LoadableImageData imageData = ImageDataFactory.getImageDataFor(fileName);
                        
                        try
                        {
                                ByteBuffer textureBytes = imageData.loadImage(new BufferedInputStream(in), false, null);
                                
                                if (textureBytes == null)
                                {
                                        throw new IOException("loadImage returned null");
                                }
                                
                                log.log(Level.INFO, "Texture data for {0} read. {1} {2} {3} {4} {5}", new Object[] {
                                        resourceKey,
                                        imageData.getWidth(),
                                        imageData.getHeight(),
                                        imageData.getDepth(),
                                        imageData.getTexWidth(),
                                        imageData.getTexHeight()
                                });
                                
                                return new Object[] {
                                        textureBytes,
                                        imageData.getWidth(),
                                        imageData.getHeight(),
                                        imageData.getDepth(),
                                        imageData.getTexWidth(),
                                        imageData.getTexHeight()
                                };
                                
                        }
                        catch (IOException | UnsatisfiedLinkError ex)
                        {
                                log.log(Level.SEVERE, "Exception while parsing image for texture " + resourceKey, ex);
                                throw new PromiseException(ex);
                        }
                }       
        }
        
        private class TextureCallback implements PromiseResolved, PromiseRejected
        {
                private final AsyncTexture texture;

                TextureCallback(AsyncTexture texture)
                {
                        this.texture = texture;
                }
                
                @Override
                public Object resolved(Object ret_) throws PromiseException
                {
                        pending.decrementAndGet();
                        
                        Object[] ret = (Object[]) ret_;
                        
                        ByteBuffer textureBytes = (ByteBuffer) ret[0];
                        int imageWidth = (Integer) ret[1];
                        int imageHeight = (Integer) ret[2];
                        boolean hasAlpha = ((Integer) ret[3]) == 32;
                        int texWidth = (Integer) ret[4];
                        int texHeight = (Integer) ret[5];
                        
                        int srcPixelFormat = hasAlpha ? GL11.GL_RGBA : GL11.GL_RGB;
                        int componentCount = hasAlpha ? 4 : 3;
                        
                        IntBuffer temp = BufferUtils.createIntBuffer(16);
                        GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE, temp);
                        int max = temp.get(0);
                        if ((texWidth > max) || (texHeight > max))
                        {
                                texture.error = true;
                                log.log(Level.SEVERE, "Attempt to allocate a texture too big for the current hardware");
                                return null;
                        }
                        
                        texture.textureID = InternalTextureLoader.createTextureID();
                        GL11.glBindTexture(texture.target, texture.textureID); 
                        
                        // todo: different filters?
                        GL11.glTexParameteri(texture.target, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR); 
                        GL11.glTexParameteri(texture.target, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                        
                        GL11.glTexImage2D(
                                texture.target, 
                                0, 
                                GL11.GL_RGBA8, 
                                InternalTextureLoader.get2Fold(imageWidth), 
                                InternalTextureLoader.get2Fold(imageHeight), 
                                0, 
                                srcPixelFormat, 
                                GL11.GL_UNSIGNED_BYTE, 
                                textureBytes);
                        
                        texture.imageWidth = imageWidth;
                        texture.imageHeight = imageHeight;
                        texture.texWidth = texWidth;
                        texture.texHeight = texHeight;
                        texture.alpha = hasAlpha;
                        texture.widthRatio = (float)imageWidth / texWidth;
                        texture.heightRatio = (float)imageHeight / texHeight;
                        texture.error = false;
                        texture.loaded();
                        
                        log.log(Level.INFO, "Texture {0} loaded. {1}", new Object[] { texture.getResourceKey(), hasAlpha});
                        
                        return null;
                }

                @Override
                public void rejected(PromiseException error)
                {
                        pending.decrementAndGet();
                        texture.error = true;
                }
                
        }
        
        @SuppressWarnings("serial")
        public static class InvalidResourceKeyException extends PromiseException { }
}
