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


import aphelion.shared.event.WorkerTask;
import aphelion.shared.event.promise.PromiseException;
import aphelion.shared.swissarmyknife.None;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A task that downloads each required asset file.
 * For now there is no support for parallel downloads.
 * @author Joris
 */
public class DownloadAssetsTask extends WorkerTask<List<Asset>, List<Asset>>
{
        private static final Logger log = Logger.getLogger("aphelion.resource");
        private final int READ_TIMEOUT_MILLIS = 10 * 1000;
        private final int BUFFER_SIZE = 128 * 1024;

        @Override
        public List<Asset> work(List<Asset> argument) throws PromiseException
        {
                // todo randomize mirror with same prio
                
                ASSETS_LOOP: for (Asset ass : argument)
                {
                        if (ass.validateCachedEntry())
                        {
                                // We already have the file cached, no need to download
                                continue;
                        }
                        
                        // ass.mirrors is sorted by higest priority first
                        for (Asset.Mirror mirror : ass.mirrors)
                        {
                                if (tryMirror(ass, mirror))
                                {
                                        continue ASSETS_LOOP;
                                }
                        }
                        
                        log.log(Level.SEVERE, "Exhausted mirror list for asset, unable to download asset. {0}", ass.cachedName);
                        throw new PromiseException("Exhausted mirror list for asset " + ass.cachedName);
                }
                
                return argument;
        }
        
        private File newTmpFile(Asset ass) throws PromiseException
        {
                try
                {
                        return File.createTempFile(ass.cachedName, null);
                }
                catch (IOException ex)
                {
                        log.log(Level.SEVERE, "Unable to find a temporary file!", ex);
                        throw new PromiseException("Unable to create a temporary file", ex);
                }

        }
        
        private boolean tryMirror(Asset ass, Asset.Mirror mirror) throws PromiseException
        {
                try
                {
                        log.log(Level.INFO, "Trying to download asset {0} from {1}", new Object[] {ass.cachedName, mirror.url});

                        HttpURLConnection conn = (HttpURLConnection) mirror.url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setReadTimeout(READ_TIMEOUT_MILLIS);
                        conn.setRequestProperty("User-Agent", "Aphelion Game Client");
                        if (mirror.refererHeader != null)
                        {
                                conn.setRequestProperty("Referer", mirror.refererHeader);
                        }

                        File tmpFile;
                        // Implicit connect()
                        try (InputStream in = conn.getInputStream())
                        {
                                int code = conn.getResponseCode();
                                if (code != 200)
                                {
                                        throw new IOException("Server returned status code " + code + ": " + conn.getResponseMessage());
                                }

                                long size = conn.getContentLengthLong();
                                if (size != 0 && size != ass.size)
                                {
                                        throw new IOException("Server returned unexpected content length");
                                }

                                tmpFile = newTmpFile(ass);
                                try (OutputStream out = new FileOutputStream(tmpFile))
                                {
                                        byte buffer[] = new byte[BUFFER_SIZE];
                                        while (true)
                                        {
                                                int read = in.read(buffer);
                                                if (read < 0)
                                                {
                                                        break;
                                                }
                                                out.write(buffer, 0, read);
                                        }
                                }
                                catch (FileNotFoundException ex)
                                {
                                        log.log(Level.SEVERE, "Unable to create the temporary file", ex);
                                        throw new PromiseException("Unable to create the temporary file", ex);
                                }
                                catch (IOException ex)
                                {
                                        tmpFile.delete();
                                        throw ex;
                                }
                        }

                        try
                        {
                                ass.storeAsset(tmpFile, false);

                                // this asset is done
                                return true;
                        }
                        catch (AssetCache.InvalidContentException ex)
                        {
                                log.log(Level.WARNING, "Succesfully downloaded asset, however it has invalid content. Trying next mirror");
                        }
                }
                catch (IOException ex)
                {
                        log.log(Level.WARNING, "Error during file download, trying next mirror", ex);
                }
                
                return false;
        }
}
