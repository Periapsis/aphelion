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

import aphelion.shared.swissarmyknife.RollingHistorySerialInteger;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Joris
 */
public class RollingHistorySerialIntegerTest
{
        public RollingHistorySerialIntegerTest()
        {
        }

        @Test
        public void test()
        {
                RollingHistorySerialInteger hist = new RollingHistorySerialInteger(100, 4, 2);
                
                assertEquals(0, hist.get(100));
                
                hist.setRelativeValue(0, 100, 10);
                assertEquals(10, hist.get(100));
                assertEquals(10, hist.get(101));
                
                hist.setRelativeValue(0, 101, 20);
                assertEquals(10, hist.get(100));
                assertEquals(30, hist.get(101));
                
                // test setter IDs
                hist.setRelativeValue(0, 102, 5);
                hist.setRelativeValue(0, 102, 5);
                assertEquals(35, hist.get(102));
                hist.setAbsoluteValue(1, 102, 1);
                assertEquals(6, hist.get(102));
                
                hist.setRelativeValue(0, 103, 40);
                assertEquals(46, hist.get(103));
                
                hist.setRelativeValue(0, 104, 11);
                assertEquals(57, hist.get(104));
                hist.setRelativeValue(0, 105, 11);
                assertEquals(68, hist.get(105));
                hist.setRelativeValue(0, 106, 11);
                assertEquals(79, hist.get(106));
                hist.setRelativeValue(0, 107, 11);
                assertEquals(90, hist.get(107));
                hist.setRelativeValue(0, 108, 11);
                assertEquals(101, hist.get(108));
                
                // setAbsolute may be 0. it must still include relative values 
                // (with other setter id's) for the same tick
                hist.setAbsoluteValue(1, 107, 0);
                assertEquals(11, hist.get(107));
                assertEquals(22, hist.get(108));
                
                // gap small than history_length (last set is 108)
                hist.setRelativeValue(0, 111, 1);
                assertEquals(0, hist.get(107)); // just outside history (edge value)
                assertEquals(22, hist.get(108));
                assertEquals(22, hist.get(109));
                assertEquals(22, hist.get(110));
                assertEquals(23, hist.get(111));
                
                // gap greater than history_length
                hist.setRelativeValue(0, 200, 1);
                assertEquals(24, hist.get(200));
                assertEquals(23, hist.get(199));
                assertEquals(23, hist.get(198));
                assertEquals(23, hist.get(197));
                assertEquals(0, hist.get(196)); // too old

                
                hist.setMinimum(199, 1000);
                assertEquals(1000, hist.get(199));
                assertEquals(1001, hist.get(200));
                
                hist.setMaximum(200, 5);
                assertEquals(5, hist.get(200));
                hist.setMinimum(199, 0);
                hist.setMaximum(200, 100000);
                assertEquals(24, hist.get(200));
        }
}