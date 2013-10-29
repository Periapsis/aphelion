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
package aphelion.client;


import aphelion.shared.resource.Asset;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.BitSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class AssetCache extends ClientStorage
{
        private static final Logger log = Logger.getLogger("aphelion.client");

        public AssetCache() throws IOException
        {
                super("assets");
        }
        
        // 5-bit index
        private static final char[] ENCODE_TABLE = {
                'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
                'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
                '2', '3', '4', '5', '6', '7',
        };
        
        private static String encodeFileName(byte[] sha_256, long fileSize)
        {
                if (sha_256.length != 32)
                {
                        throw new IllegalArgumentException();
                }
                
                assert ENCODE_TABLE.length == 32;
                
                ByteBuffer buf = ByteBuffer.allocate(32 + 8);
                buf.put(sha_256);
                buf.putLong(fileSize);
                buf.rewind();
                
                BitSet bits = BitSet.valueOf(buf); //note: little endian
                StringBuilder ret = new StringBuilder(64); // (32+8)*8/5
                
                for (int i = 0; i < (32+8)*8; i += 5)
                {
                        int n = 0;
                        n |= bits.get(i+0) ?   1: 0;
                        n |= bits.get(i+1) ?   2: 0;
                        n |= bits.get(i+2) ?   4: 0;
                        n |= bits.get(i+3) ?   8: 0;
                        n |= bits.get(i+4) ?  16: 0;
                        ret.append(ENCODE_TABLE[n]);
                }
                
                return ret.toString();
        }
        
        public File getAsset(Asset ass)
        {
                return getAsset(ass.sha256_hash, ass.size);
        }
        
        public File getAsset(byte[] sha_256, long fileSize)
        {
                File file = getFile(encodeFileName(sha_256, fileSize));
                if (file.exists())
                {
                        if (!file.setLastModified(System.currentTimeMillis()))
                        {
                                log.log(Level.WARNING, "Unable to touch file {0}", file);
                        }
                }
                
                return file;
        }
        
        public void storeAsset(File tmpFile, Asset ass) throws IOException, InvalidContentException
        {
                storeAsset(tmpFile, ass.sha256_hash, ass.size);
        }
        
        /**
         * @param tmpFile The temp file to be moved
         * @param sha_256 The expected hash
         * @param fileSize The expected file size
         * @throws java.io.IOException The file can not be read
         * @throws aphelion.client.AssetCache.InvalidContentException The downloaded content is invalid, it does not match the expected hash and size
         */
        public void storeAsset(File tmpFile, byte[] sha_256, long fileSize) throws IOException, InvalidContentException
        {
                byte[] actual_hash = SwissArmyKnife.fileHash("sha-256", tmpFile);
                long actual_size = tmpFile.length();
                
                if (!Arrays.equals(actual_hash, sha_256) || fileSize != actual_size)
                {
                        throw new InvalidContentException("The given asset file does not match the expected hash or file size.");
                }
                
                File target = getFile(encodeFileName(sha_256, fileSize));
                Files.move(tmpFile.toPath(), target.toPath());
        }
        
        
        // todo clean up old assets
        
        public static class InvalidContentException extends Exception
        {

                public InvalidContentException(String message)
                {
                        super(message);
                }
                
        }
}
