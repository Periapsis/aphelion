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


import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class LocalUserStorage extends FileStorage
{
        private static final Logger log = Logger.getLogger("aphelion.resource");
        
        private static final String OS_NAME = System.getProperty("os.name");
        private static final boolean IS_OS_X = OS_NAME == null ? false : OS_NAME.startsWith("Mac OS X");
        private static final boolean IS_WINDOWS = OS_NAME == null ? false : OS_NAME.startsWith("Windows");
        
        public LocalUserStorage(String subdir) throws IOException
        {
                // It might be useful to also provide for a global directory to store things in.
                // This is not doable without an installer or temporary admin/root privileges however.
                
                if (IS_WINDOWS)
                {
                        // http://blogs.msdn.com/b/patricka/archive/2010/03/18/where-should-i-store-my-data-and-configuration-files-if-i-target-multiple-os-versions.aspx
                        String appdata = System.getenv("LOCALAPPDATA");
                                
                        if (appdata == null || appdata.isEmpty())
                        {
                                // Windows XP
                                // Note that %APPDATA% is roaming (active directory stuff)
                                // LOCALAPPDATA is prefered since ClientStorage is used for 
                                // things like the asset cache and could become pretty big.
                                
                                // Note that the java Preferences API is roaming, so try to avoid
                                // references between prefrences and ClientStorage 
                                // (use a property file instead)
                                
                                appdata = System.getenv("APPDATA");
                        }
                        
                                
                        if (appdata == null || appdata.isEmpty()) // very old windows
                        {
                                appdata = ".";
                        }
                        directory = new File(appdata + "\\.aphelion\\"+subdir);
                }
                else if (IS_OS_X)
                {
                        directory = new File("~/Library/Application Support/.aphelion");
                }
                else
                {
                        // Probably GNU/Linux or some other UNIX flavour
                        directory = new File("~/.aphelion");
                }
                
                log.log(Level.INFO, "Setting up {0} as a client storage directory", directory.getAbsolutePath());
                
                try
                {
                        directory.mkdirs();
                }
                catch (SecurityException ex)
                {
                        throw ex; 
                }

                if (!directory.canRead())
                {
                        throw new IOException("Unable to read from client storage directory: " + directory.getPath());
                }
                
                if (!directory.canWrite())
                {
                        throw new IOException("Unable to write to client storage directory: " + directory.getPath());
                }
                
                if (!directory.isDirectory())
                {
                        throw new IOException("Client storage directory is not really a directory: " + directory.getPath());
                }
        }
}
