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
package aphelion.shared.swissarmyknife;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A wrapper around java.security.SecureRandom which provides some 
 * functions to easily add seeds (entropy) from various sources.
 * 
 * Entropy can never be decreased by calling one of these functions.
 * It is safe to call these functions often, even on user input. 
 * 
 * No single user should be able to predict all seeds that are added 
 * using these functions.
 * 
 * @author Joris
 */
public class MySecureRandom
{
        private MySecureRandom() {}
        
        static
        {
                initialize();
        }
        
        private static SecureRandom rand;
        
        private static Charset utf16;
        
        private static void initialize()
        {
                // http://www.cigital.com/justice-league-blog/2009/08/14/proper-use-of-javas-securerandom/
                try
                {
                        rand = SecureRandom.getInstance("SHA1PRNG", "SUN");
                }
                catch (NoSuchAlgorithmException ex)
                {
                        Logger.getLogger(MySecureRandom.class.getName()).log(Level.SEVERE, null, ex);
                        throw new Error(ex); // should never happen
                }
                catch (NoSuchProviderException ex)
                {
                        Logger.getLogger(MySecureRandom.class.getName()).log(Level.SEVERE, null, ex);
                        throw new Error(ex); // should never happen
                }

                // Call the built in seed
                rand.nextBoolean();
                
                utf16 = Charset.forName("UTF-16"); // may throw
        }
        
        // A few methods to add entropy:
        
        /** Adds entropy by seeding using the clock */
        @ThreadSafe
        public static void addTimeSeed()
        {
                rand.setSeed(System.currentTimeMillis());
                rand.setSeed(System.nanoTime());
        }
        
        /** Adds entropy by seeding using a hostname
         * @param addr An unresolved or resolved hostname. The port is not used
         */
        @ThreadSafe
        public static void addSeed(InetAddress addr)
        {
                rand.setSeed( addr.getHostAddress().getBytes(utf16) );
        }
        
        /** Adds entropy by seeding using a hostname
         * @param addr An unresolved or resolved hostname. The port is not used
         */
        @ThreadSafe
        public static void addSeed(InetSocketAddress addr)
        {
                addSeed(addr.getAddress());
        }
        
        
        /** Adds entropy by seeding using an integer.
         *  For example, from a config file
         * @param n 
         */
        @ThreadSafe
        public static void addSeed(long n)
        {
                rand.setSeed(n);
        }
        
        @ThreadSafe
        public static void nextBytes(byte[] bytes)
        {
                rand.nextBytes(bytes);
        }
        
        @ThreadSafe
        public static long nextLong()
        {
                return rand.nextLong();
        }
}
