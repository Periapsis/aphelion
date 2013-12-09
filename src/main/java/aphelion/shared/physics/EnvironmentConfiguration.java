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
package aphelion.shared.physics;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public final class EnvironmentConfiguration
{
        /** Running as a server or as a client?. 
         * This matters only for log messages, setting up the EnvironmentConfiguration, and some
         * assertions. Server or client specific code should be avoided!
         */
        public final boolean server;
        
        /** Each state trails this much time (tick) behind the next state. 
         * 16 is a power of 2 which gives a small speed benefit */
        public final int TRAILING_STATE_DELAY;
        
        
        /** The delay of the first state.
         * For clients that render graphics this should probably be 0
         */
        public final int FIRST_STATE_DELAY;
        
        /** How many trailing states are being run. 
         * The first state is always 0 (lowest delay) and the last state is always TRAILING_STATES-1 (highest delay).
         */
        public final int TRAILING_STATES;
        
        /** The highest delay we simulate for.
         * Do not accept operations that are older than this many ticks. (unless the operation is nog ignorable) */
        public final int HIGHEST_DELAY;
        
        /** If two timewarps need to be executed in rapid succession, wait this many ticks. */
        public final int TIMEWARP_EVERY_TICKS;

        /** The last state must _at least_ keep history for this many ticks.
         * (including the current tick).
         * This is needed because sometimes it is need to look a little into the past.
         * (for example dead reckoning needs the move of the previous tick)
         */
        public final int MINIMUM_HISTORY_TICKS = 2;
        
        public final int KEEP_EVENTS_FOR_TICKS;

        public EnvironmentConfiguration(boolean server)
        {
                this.server = server;
                
                TRAILING_STATE_DELAY = 32;
                TIMEWARP_EVERY_TICKS = 10;
                
                if (server)
                {
                        FIRST_STATE_DELAY = 4 * TRAILING_STATE_DELAY;
                        TRAILING_STATES = 1;
                }
                else
                {
                        FIRST_STATE_DELAY = 0;
                        TRAILING_STATES = 8;
                }
                
                HIGHEST_DELAY = FIRST_STATE_DELAY + (TRAILING_STATES-1) * TRAILING_STATE_DELAY;
                
                // +1 to ensure that an event that is generated in the state with the highest delay,
                // will be readable before removal.
                KEEP_EVENTS_FOR_TICKS = HIGHEST_DELAY + 1;
                
                assert MINIMUM_HISTORY_TICKS >= 1 : "Histories must overlap by atleast 1 tick!";
        }
        
        public void log()
        {
                Logger.getLogger("aphelion.shared.physics").log(Level.INFO, 
                        "EnvironmentConfiguration: "
                        + "server: {0}; "
                        + "TRAILING_STATE_DELAY: {1}; "
                        + "TIMEWARP_EVERY_TICKS: {2}; "
                        + "FIRST_STATE_DELAY: {3}; "
                        + "TRAILING_STATES: {4}; "
                        + "MAX_OPERATION_AGE: {5}; ",
                        new Object[] {
                                server,
                                TRAILING_STATE_DELAY,
                                TIMEWARP_EVERY_TICKS,
                                FIRST_STATE_DELAY,
                                TRAILING_STATES,
                                HIGHEST_DELAY
                        }
                );
        }
}
