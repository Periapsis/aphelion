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

package aphelion.shared.resource;

import aphelion.client.resource.AsyncTextureLoader;
import aphelion.shared.event.LoopEvent;
import aphelion.shared.event.Workable;
import aphelion.shared.event.WorkerTask;
import aphelion.shared.event.promise.AbstractPromise;
import aphelion.shared.event.promise.PromiseException;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import aphelion.shared.swissarmyknife.ThreadSafe;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Represents a local key value database for aphelion resources.
 * All resources have a key which is used throughout the aphelion graphics code.
 * The key might be used statically in the source code or concatenated using dots,
 * for example "ship." + myShip.key + ".shipRoll"
 * 
 * Resources are read from zip files on the local file system. Each zip file should 
 * contain a text file called "resources.manifest", this is a java property file (java.util.Properties).
 * This database reads all entries from the manifest that begin with "resource.".
 * For example to define the resource "ship.warbird.shipRoll" the following entry could be used:
 * "ship.warbird.shipRoll = ships/warbird.png"
 * 
 * A (cached) byte array may be returned using a callback, or an InputStream may be returned.
 * 
 * @author Joris
 */

// At the moment the header of the zip file is reread for every resource file that is read.
// This could be optimized if needed

public class ResourceDB implements LoopEvent
{
        private static final Logger log = Logger.getLogger("aphelion.resource");
        
        private AsyncTextureLoader textureLoader;
        
        private LinkedHashMap<String, FileEntry> entries = new LinkedHashMap<>();
        private final Workable workable;
        private HashMap<String, SoftReference<byte[]>> byteCache = new HashMap<>();
        private LinkedList<LoadSynchronizer> loadSynchronizers = new LinkedList<>();
        private int cleanupCounter = 0;

        public ResourceDB(Workable workable)
        {
                this.workable = workable;
        }

        @Override
        public void loop(long systemNanoTime, long sourceNanoTime)
        {
                textureLoader.loop(systemNanoTime, sourceNanoTime);
        }
        
        public AsyncTextureLoader getTextureLoader()
        {
                if (textureLoader == null)
                {
                        textureLoader = new AsyncTextureLoader(this, workable);
                }
                
                return textureLoader;
        }
        
        @ThreadSafe
        public boolean resourceExists(String key)
        {
                synchronized(this)
                {
                        return entries.containsKey(key);
                }
        }
        
        @ThreadSafe
        public List<String> getKeysByPrefix(String prefix)
        {
                LinkedList<String> ret = new LinkedList<>();
                synchronized(this)
                {
                        for (FileEntry entry : entries.values())
                        {
                                if (entry.key.startsWith(prefix))
                                {
                                        ret.add(entry.key);
                                }
                        }
                }
                
                return ret;
        }
        
        /** Returns the resource as a byte array. 
         * This call may block for a while.
         * @param key The resource key
         * @return A byte array that should be considered immutable. 
         * Or null if the resource was not defined.
         */
        @ThreadSafe
        public byte[] getBytesSync(String key)
        {
                InputStream is = getInputStreamSync(key);
                if (is == null)
                {
                        return null;
                }
                
                byte[] bytes = null;
                
                synchronized(this)
                {
                        SoftReference<byte[]> ref = byteCache.get(key);
                        if (ref != null)
                        {
                                bytes = ref.get();
                        }
                }
                
                if (bytes == null)
                {
                        // not found in cache
                        
                        LoadSynchronizer loadSync = null;
                        
                        // synchronize the loading so that the same file is not read from disk multiple times
                        // The second thread that attempts to load a file will block until the first thread is done
                        /// TODO test
                        synchronized(this)
                        {
                                for (LoadSynchronizer loadSyncOther : loadSynchronizers)
                                {
                                        if (loadSyncOther.resourceKey.equals(key))
                                        {
                                                loadSync = loadSyncOther;
                                                break;
                                        }
                                }
                                
                                if (loadSync == null)
                                {
                                        loadSync = new LoadSynchronizer(key);
                                        loadSynchronizers.add(loadSync);
                                }
                        }
                        
                        synchronized(loadSync)
                        {
                                // reattempt cache

                                synchronized(this)
                                {
                                        SoftReference<byte[]> ref = byteCache.get(key);
                                        if (ref != null)
                                        {
                                                bytes = ref.get();
                                        }
                                }
                                
                                if (bytes == null)
                                {
                                        // this thread is the first one to acquire the load lock
                                        try
                                        {
                                                bytes = SwissArmyKnife.inputStreamToBytes(is);
                                        }
                                        catch (IOException ex)
                                        {
                                                log.log(Level.SEVERE, "Error while reading resource", ex);
                                                return null;
                                        }

                                        synchronized(this)
                                        {
                                                byteCache.put(key, new SoftReference<>(bytes));
                                        }
                                }
                        }
                        
                        synchronized(this)
                        {
                                loadSynchronizers.remove(loadSync);
                        }
                }
                
                // TODO: use the cache class from google guave instead?
                synchronized(this)
                {
                        if (++cleanupCounter > 10)
                        {
                                cleanupCounter = 0;
                                Iterator<SoftReference<byte[]>> it = byteCache.values().iterator();
                                while (it.hasNext())
                                {
                                        if (it.next().get() == null)
                                        {
                                                it.remove();
                                        }
                                }
                        }
                }
                
                return bytes;
        }
        
        /** Returns the resource as a byte array asynchronously using a callback.
         * @param key The resource key
         * @return The promise that will be fired on the main thread 
         * when the resource has been read. The byte array given should be 
         * considered immutable. If the byte array is null, the resource does 
         * not exist or an unexpected error occurred.
         * @throws IllegalStateException When a workable object was not given 
         * during construction
         */
        @ThreadSafe
        public AbstractPromise getBytes(String key)
        {
                if (workable == null)
                {
                        throw new IllegalStateException();
                }
                return workable.addWorkerTask(new GetBytesTask(), key);
        }
        
        /** Returns the location of a resource on the filesystem.
         * @param key The resource key
         * @return null if no such resource was defined
         */
        @ThreadSafe
        public FileEntry getFileEntry(String key)
        {
                synchronized(this)
                {
                        return entries.get(key);
                }
        }
        
        /** Returns the resource as an InputStream without doing any reading.
         * This avoids the resource cache (unlike getWrappedInputStream)
         * This might block execution for a little as java queries the filesystem.
         * @param key The resource key
         * @return 
         */
        @ThreadSafe
        public InputStream getInputStreamSync(String key)
        {
                FileEntry file = getFileEntry(key);
                if (file == null)
                {
                        return null;
                }
                
                try
                {
                        if (file.zipEntry == null)
                        {
                                return new FileInputStream(file.file);
                        }
                        else
                        {
                                ZipFile zip = new ZipFile(file.file);
                                return zip.getInputStream(zip.getEntry(file.zipEntry));
                        }
                }
                catch (SecurityException | IOException ex)
                {
                        log.log(Level.SEVERE, "Unable to read resource", ex);
                        return null;
                }
        }
        
        /** Read the file as byte array and then wrap that byte array in an InputStream.
         * This lets you read the file in a worker thread while still providing an 
         * InputStream to API's that require it.
         * This also takes advantage of our resource cache.
         * @param key The resource key
         * @return 
         */
        public AbstractPromise getWrappedInputStream(String key)
        {
                if (workable == null)
                {
                        throw new IllegalStateException();
                }
                return workable.addWorkerTask(new WrappedInputStreamTask(), key);
        }
        
        /** Add a resource as a file that is not part of a zip file.
         * This method immediately checks if the file is readable to help 
         * avoid errors from occurring later on.
         * @param key The resource key
         * @param path The path to the file that should represent the resource.
         * @throws FileNotFoundException
         * @throws SecurityException  
         */
        @ThreadSafe
        public void addResource(String key, File path) throws FileNotFoundException, SecurityException
        {
                if (!path.exists())
                {
                        throw new FileNotFoundException();
                }
                
                SecurityManager security = System.getSecurityManager();
                if (security != null)
                {
                        security.checkRead(path.getPath());
                }
                
                if (!path.canRead())
                {
                        throw new FileNotFoundException("Unable to read this file");
                }
                
                synchronized(this)
                {
                        entries.put(key, new FileEntry(key, path, null));
                }
        }
        
        /** Add resources using a zip file. 
         * The zip file should contain a resource manifest file denoting what resources it should add.
         * The file "manifest" is read as a java property file. A key beginning with 
         * "resource." is used as the resource key (excluding the "resource." prefix).
         * The value should be the path within the zip file to the resource file.
         * @param file The zip file, a .zip file extension is not required.
         * @throws ZipException
         * @throws IOException  
         */
        @ThreadSafe
        public void addZip(File file) throws ZipException, IOException
        {
                ZipFile zip = new ZipFile(file);
                
                ZipEntry zipManifest = zip.getEntry("resources.manifest");
                Properties manifest = new Properties();
                manifest.load(zip.getInputStream(zipManifest));
                
                synchronized(this)
                {
                        if (manifest.entrySet() != null)
                        {
                                Iterator<Entry<Object, Object>> it = manifest.entrySet().iterator();
                                while (it.hasNext())
                                {
                                        Entry<Object, Object> entry = it.next();
        
                                        String key = (String) entry.getKey();
                                        String value = (String) entry.getValue();
                                        
                                        ZipEntry zipEntry = zip.getEntry(value);
                                        if (zipEntry == null)
                                        {
                                                log.log(Level.SEVERE, "Entry in zip manifest does not exist. Zip: {0}. Entry: {1}", new Object[] {file.getPath(), value});
                                        }
                                        else
                                        {
                                                // (replace old mapping)
                                                entries.put(key, new FileEntry(key, file, value));
                                        }
                                }
                        }
                }
        }
        
        private class GetBytesTask extends WorkerTask<String, byte[]>
        {
                @Override
                public byte[] work(String argument) throws PromiseException
                {
                        return getBytesSync(argument);
                }
        }
        
        private class WrappedInputStreamTask extends WorkerTask<String, InputStream>
        {
                @Override
                public InputStream work(String argument) throws PromiseException
                {
                        byte[] bytes = getBytesSync(argument);
                        if (bytes == null)
                        {
                                return null;
                        }
                        
                        return new ByteArrayInputStream(bytes);
                }
        }
        
        public static class FileEntry
        {
                final String key;
                
                /** The path to the resource file (if this.zipEntry is null), or the path to the zip file. */
                final public File file;
                
                /** The path of the resource within the zip file (this.file) */
                final public String zipEntry;

                FileEntry(String key, File file, String zipEntry)
                {
                        this.key = key;
                        this.file = file;
                        this.zipEntry = zipEntry;
                        assert file != null;
                }
        }
        
        private static class LoadSynchronizer
        {
                final String resourceKey;

                LoadSynchronizer(String resourceKey)
                {
                        this.resourceKey = resourceKey;
                }
        }
}
