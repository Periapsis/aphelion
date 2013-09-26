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

/** Calculate a rolling average.
 * Calling addValue(double) updates the average with the added value.
 * When the size limit specified in the constructor is reached, 
 * the oldest value is discarded and will no longer be part of the average.
 *
 * @author Joris
 */
public class RollingAverage
{
        final int size;
        final double[] values;
        int current_index = -1;
        int value_count = 0;
        
        double total;
        
        public RollingAverage(int size)
        {
                this.size = size;
                values = new double[size];
        }
        
        public void addValue(double value)
        {
                double oldValue;
                
                ++current_index;
                if (current_index >= size)
                {
                        current_index = 0;
                }
                
                if (value_count == size)
                {
                        oldValue = values[current_index];
                }
                else
                {
                        oldValue = 0;
                        ++value_count;
                }
                
                values[current_index] = value;
                total -= oldValue;
                total += value;
                
        }
        
        public double getAverage()
        {
                return total / value_count;
        }
}
