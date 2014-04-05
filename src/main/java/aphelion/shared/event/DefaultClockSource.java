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

package aphelion.shared.event;

import aphelion.shared.swissarmyknife.ThreadSafe;

/**
 * The clock source that is used by default. This simply returns the local nanoTime()
 * @author Joris
 */
public class DefaultClockSource implements ClockSource
{

        @Override
        @ThreadSafe
        public long nanoTime()
        {
                return System.nanoTime();
                /* https://blogs.oracle.com/dholmes/entry/inside_the_hotspot_vm_clocks
                The default mechanism used by QPC is determined by the Hardware 
                Abstraction layer(HAL), but some systems allow you to 
                explicitly control it using options in boot.ini, such as /usepmtimer 
                that explicitly requests use of the power management timer. 
                This default changes not only across hardware but also across OS versions. 
                For example Windows XP Service Pack 2 changed things to use the power 
                management timer (PMTimer) rather than the processor timestamp-counter 
                (TSC) due to problems with the TSC not being synchronized on different 
                processors in SMP systems, and due the fact its frequency can vary 
                (and hence its relationship to elapsed time) based on power-management 
                settings
                */
        }
}
