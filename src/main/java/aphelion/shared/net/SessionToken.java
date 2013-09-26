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
package aphelion.shared.net;

import aphelion.shared.swissarmyknife.Attachable;
import aphelion.shared.swissarmyknife.AttachmentData;
import aphelion.shared.swissarmyknife.AttachmentManager;
import aphelion.shared.swissarmyknife.MySecureRandom;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import java.util.Arrays;

/**
 * A immutable value object for sessions tokens used to associate multiple sockets with a single client.
 * 
 * 
 * @author Joris
 */
public class SessionToken implements Attachable
{
        public static final int SIZE = 32;
        
        public static final AttachmentManager attachmentManager = new AttachmentManager();
        private AttachmentData attachments = attachmentManager.getNewDataContainer();
        
        private final byte[] token = new byte[SIZE];
        private int hashCode = 0;
        

        public SessionToken(byte[] bytes)
        {
                if (bytes.length != SIZE)
                {
                        throw new IllegalArgumentException("Argument bytes should be " + SIZE + "in length");
                }
                
                for (int a = 0; a < SIZE; ++a)
                {
                        token[a] = bytes[a];
                }
                
                updateHashCode();
        }
        
        private SessionToken()
        {
        }
        
        public static SessionToken generate()
        {
                SessionToken ret = new SessionToken();
                MySecureRandom.nextBytes(ret.token);
                ret.updateHashCode();
                return ret;
        }
        
        
        
        private void updateHashCode()
        {
                hashCode = Arrays.hashCode(this.token);
        }

        @Override
        public boolean equals(Object obj)
        {
                if (this == obj)
                {
                        return true;
                }
                
                if (!(obj instanceof SessionToken))
                {
                        return false;
                }
                
                return Arrays.equals(token, ((SessionToken) obj).token);
        }

        @Override
        public int hashCode()
        {
                return hashCode;
        }
        
        public void get(byte[] bytes)
        {
                if (bytes.length != SIZE)
                {
                        throw new IllegalArgumentException("Argument bytes should be " + SIZE + "in length");
                }
                
                for (int a = 0; a < SIZE; ++a)
                {
                        bytes[a] = token[a];
                }
        }

        @Override
        public AttachmentData getAttachments()
        {
                return attachments;
        }
        
        
        @Override
        public String toString()
        {
                return SwissArmyKnife.bytesToHex(token);
        }
}
