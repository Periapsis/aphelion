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

package aphelion.shared.gameconfig;


import aphelion.shared.swissarmyknife.ThreadSafe;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Provides documentation for game config keys.
 * These are added statically at run time.
 * @author Joris
 */
public class GCDocumentation
{
        private static final Logger log = Logger.getLogger("aphelion.config");
        private GCDocumentation() {}
        
        private static final ConcurrentHashMap<String, GCDocumentationEntry> entries = new ConcurrentHashMap();
        
        @ThreadSafe
        public static void put(String configKey, GCDOCUMENTATION_TYPE type, boolean list, String description)
        {
                entries.put(configKey, new GCDocumentationEntry(type, list, description));
        }
        
        @ThreadSafe
        public static GCDocumentationEntry getDocumentation(String configKey)
        {
                return entries.get(configKey);
        }
        
        
        public static enum GCDOCUMENTATION_TYPE
        {
                INTEGER,
                INTEGER_POSITIVE,
                INTEGER_NON_NEGATIVE,
                BOOLEAN,
                STRING,
                IMAGE,
                TICK,
                TICK_POSITIVE,
                TICK_NON_NEGATIVE,
                /** Ratio between 0 and 1024 */
                RATIO,
                /** Ratio between 0 and 1048576 (1024*1024) */
                RATIO_PRECISE;
        }
        
        public static class GCDocumentationEntry
        {
                public final GCDOCUMENTATION_TYPE type;
                public final boolean list;
                public final String description;

                public GCDocumentationEntry(GCDOCUMENTATION_TYPE type, boolean list, String description)
                {
                        this.type = type;
                        this.list = list;
                        this.description = description;
                }
        }
}
