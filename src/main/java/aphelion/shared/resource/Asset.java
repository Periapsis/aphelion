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

import aphelion.server.ServerConfigException;
import aphelion.shared.net.protobuf.GameS2C.ResourceRequirement;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import com.google.protobuf.ByteString;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class Asset
{
        private static final Logger log = Logger.getLogger("aphelion.server");

        public final String configPath;
        public final File file;
        public final long size;
        public final byte[] hash;
        public final ByteString hash_protobuf;
        private List<Mirror> mirrors;

        public Asset(File assetDirectory, Object yamlEntry) throws ServerConfigException
        {
                try
                {
                        Map<String, Object> config = (Map<String, Object>) yamlEntry;
                        configPath = (String) config.get("path");
                        file = new File(assetDirectory + File.separator + configPath).getCanonicalFile();

                        if (!file.canRead() || !file.isFile())
                        {
                                throw new ServerConfigException("Asset is not readable or not a file: " + file);
                        }

                        size = file.length();
                        hash = SwissArmyKnife.fileHash("SHA-256", file);
                        hash_protobuf = ByteString.copyFrom(hash);
                        
                        List yamlMirrors = (List) config.get("mirrors");
                        if (yamlMirrors != null)
                        {
                                this.mirrors = new ArrayList<>(yamlMirrors.size());
                                
                                for (Object yamlMirror : yamlMirrors)
                                {
                                        mirrors.add(new Mirror(yamlMirror));
                                }
                        }
                }
                catch (ClassCastException | IOException ex)
                {
                        throw new ServerConfigException(ex);
                }
        }
        
        public void toProtoBuf(ResourceRequirement.Builder builder)
        {
                builder.setSha256(hash_protobuf);
                builder.setSize(size);
                
                {
                        // always add our own server
                        ResourceRequirement.Mirror.Builder mirrorBuilder = builder.addMirrorsBuilder();
                        mirrorBuilder.setUrl("/assets/" + configPath);
                        mirrorBuilder.setPriority(0);
                }
                
                if (mirrors != null)
                {
                        for (Mirror mirror : mirrors)
                        {
                                ResourceRequirement.Mirror.Builder mirrorBuilder = builder.addMirrorsBuilder();
                                mirrorBuilder.setUrl(mirror.url.toExternalForm());
                                mirrorBuilder.setPriority(mirror.priority);
                                if (mirror.refererHeader != null && !mirror.refererHeader.isEmpty())
                                {
                                        mirrorBuilder.setRefererHeader(mirror.refererHeader);
                                }
                        }
                }
        }
        

        public static class Mirror
        {
                public final URL url;
                public final int priority;
                public final String refererHeader;

                public Mirror(Object configMirror) throws ServerConfigException
                {
                        try
                        {
                                Map<String, Object> config = (Map<String, Object>) configMirror;
                                url = new URL((String) config.get("url"));
                                priority = (int) config.get("priority");
                                refererHeader = (String) config.get("referer"); // null is okay
                        }
                        catch (ClassCastException | MalformedURLException ex)
                        {
                                throw new ServerConfigException(ex);
                        }
                }       
        }
}
