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
        private static final Logger log = Logger.getLogger("aphelion.resource");

        /** Where the file should be loaded from. 
         * If null, this file must be downloaded first (from a mirror).
         * Do not use this file to add to the ResourceDB, 
         * this file might be changed by the user. Use this.file for that.
         */
        public final File configFile;
        
        /** The location of the asset within the asset cache.
         * This file might not exist yet if it has to be copied or downloaded first.
         */
        public final File file;
        
        /** The file size in bytes. */
        public final long size;
        
        /** The sha-256 hash of the file contents. */
        public final byte[] sha256_hash;
        public final ByteString sha256_protobuf;
        
        /** A list of mirrors the file could be downloaded from. */
        private final List<Mirror> mirrors;

        public Asset(AssetCache assetCache, Object yamlEntry) throws ServerConfigException
        {
                // Server
                try
                {
                        Map<String, Object> config = (Map<String, Object>) yamlEntry;
                        configFile = new File((String) config.get("path")).getCanonicalFile();

                        if (!configFile.canRead() || !configFile.isFile())
                        {
                                throw new ServerConfigException("Asset is not readable or not a file: " + configFile);
                        }

                        size = configFile.length();
                        sha256_hash = SwissArmyKnife.fileHash("SHA-256", configFile);
                        sha256_protobuf = ByteString.copyFrom(sha256_hash);
                        
                        this.file = assetCache.getAsset(sha256_hash, size);
                        
                        List yamlMirrors = (List) config.get("mirrors");
                        if (yamlMirrors != null)
                        {
                                this.mirrors = new ArrayList<>(yamlMirrors.size());
                                
                                for (Object yamlMirror : yamlMirrors)
                                {
                                        mirrors.add(new Mirror((Map<String, Object>) yamlMirror));
                                }
                        }
                        else
                        {
                                mirrors = null;
                        }
                }
                catch (ClassCastException | IOException ex)
                {
                        throw new ServerConfigException(ex);
                }
        }

        public Asset(AssetCache assetCache, ResourceRequirement protobuf) throws MalformedURLException
        {
                // client
                sha256_protobuf = protobuf.getSha256();
                sha256_hash = sha256_protobuf.toByteArray();
                size = protobuf.getSize();
                this.configFile = null;
                this.file = assetCache.getAsset(sha256_hash, size);
                
                this.mirrors = new ArrayList<>(protobuf.getMirrorsCount());
                for (ResourceRequirement.Mirror m : protobuf.getMirrorsList())
                {
                        this.mirrors.add(new Mirror(m));
                }
        }
        
        
        
        public void toProtoBuf(ResourceRequirement.Builder builder)
        {
                builder.setSha256(sha256_protobuf);
                builder.setSize(size);
                
                {
                        // always add our own server
                        ResourceRequirement.Mirror.Builder mirrorBuilder = builder.addMirrorsBuilder();
                        mirrorBuilder.setUrl("/assets/" + this.file.getName());
                        mirrorBuilder.setPriority(0);
                }
                
                if (mirrors != null)
                {
                        for (Mirror mirror : mirrors)
                        {
                                mirror.toProtoBuf(builder.addMirrorsBuilder());
                        }
                }
        }
        

        public static class Mirror
        {
                public final URL url;
                public final int priority;
                public final String refererHeader;

                public Mirror(Map<String, Object> config) throws ServerConfigException
                {
                        // server
                        try
                        {
                                url = new URL((String) config.get("url"));
                                priority = (int) config.get("priority");
                                refererHeader = (String) config.get("referer"); // null is okay
                        }
                        catch (ClassCastException | MalformedURLException ex)
                        {
                                throw new ServerConfigException(ex);
                        }
                }
                
                public Mirror(ResourceRequirement.Mirror protobuf) throws MalformedURLException
                {
                        // client
                        
                        url = new URL(protobuf.getUrl());
                        priority = protobuf.getPriority();
                        refererHeader = protobuf.hasRefererHeader() ? protobuf.getRefererHeader() : null;
                }
                
                public void toProtoBuf(ResourceRequirement.Mirror.Builder builder)
                {
                        builder.setUrl(url.toExternalForm());
                        builder.setPriority(priority);
                        if (refererHeader != null && !refererHeader.isEmpty())
                        {
                                builder.setRefererHeader(refererHeader);
                        }
                }
        }
}