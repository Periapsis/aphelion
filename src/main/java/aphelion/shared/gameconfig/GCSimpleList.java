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

import aphelion.shared.swissarmyknife.SwissArmyKnife;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @param <GCSIMPLE> 
 * @param <TYPE> 
 * @author Joris
 */
public abstract class GCSimpleList<GCSIMPLE extends SimpleAbstract<TYPE>, TYPE> extends WrappedValueAbstract
{
        protected final WrappedValueAbstract.Factory valueFactory;
        protected SimpleAbstract<TYPE>[] value;
        private final int key_hashCode;
        private Set<TYPE> valueSet;
        private LIST_FUNCTION listFunction = LIST_FUNCTION.REPEAT;
        private boolean needsSeed;
        
        public static enum LIST_FUNCTION
        {
                REPEAT,
                REPEAT_ALL,
                LINEAR,
                SHUFFLE,
                RAND;
        };
        
        // [REPEAT, 1, 2, 4]     = [1, 2,  4,  4,  4,   4]
        // [REPEAT_ALL, 1, 2, 4] = [1, 2,  4,  1,  2,   4]
        // [LINEAR, 1, 2, 4]     = [1, 2,  4,  6,  8,  10]
        // [LINEAR, 1, 4, 2]     = [1, 4,  2,  0, -2,  -4]
        // [LINEAR, 4]           = [4, 8, 12, 16, 20,  24]
        // [SHUFFLE, 1, 2, 3, 4, 5]  = [5, 3, 3, 2, 2, 1, 4]
        // [RAND, 5, 5, 6, 10] = [5, 6, 9, 10, 6, 6, 7] # (min,max,min,max)
        
        // .get(int index)
        // .get(int index, long seed=tick^actor.seed) // jenkinMix(seed, projectile_index, key.hashCode())
        
        GCSimpleList(ConfigSelection selection, String key, WrappedValueAbstract.Factory factory)
        {
                super(selection, key);
                key_hashCode = key == null ? 0 : key.hashCode();
                this.valueFactory = factory;
                this.value = new SimpleAbstract[] { (SimpleAbstract) valueFactory.create(null, null) };
                this.value[0].newValue(null);
        }
        
        protected abstract TYPE calculateLinear(TYPE from, TYPE to, int factor);
        protected abstract TYPE calculateRand(TYPE from, TYPE to, int rand);
        
        public boolean needsSeed()
        {
                return needsSeed;
        }
        
        public TYPE getBoxed(int index)
        {
                return getBoxed(index, 0);
        }
        
        public TYPE getBoxed(int index, int seed)
        {
                assert value.length > 0;
                
                switch (listFunction)
                {
                        case REPEAT:
                                if (index < value.length)
                                {
                                        return value[index].getBoxed();
                                }
                                else
                                {
                                        return value[value.length-1].getBoxed();
                                }
                                
                                
                        case REPEAT_ALL:
                                return value[index % value.length].getBoxed();
                                
                        case LINEAR:
                                if (index < value.length)
                                {
                                        return value[index].getBoxed();
                                }
                                else
                                {
                                        if (value.length > 1)
                                        {
                                                return this.calculateLinear(
                                                        value[value.length-2].getBoxed(),
                                                        value[value.length-1].getBoxed(),
                                                        index - value.length);
                                        }
                                        else
                                        {
                                                return this.calculateLinear(
                                                        null,
                                                        value[value.length-1].getBoxed(),
                                                        index - value.length + 1);
                                        }
                                }
                                
                        case SHUFFLE:
                        {
                                int rnd = SwissArmyKnife.jenkinMix(seed, index, key_hashCode);
                                int i = rnd % value.length;
                                return value[i].getBoxed();
                        }
                                
                        case RAND:
                        {
                                int rnd = SwissArmyKnife.jenkinMix(seed, index, key_hashCode);
                                
                                
                                int from;
                                int to ;
                                
                                if (value.length == 1)
                                {
                                        from = 0;
                                        to = 0;
                                }
                                else
                                {
                                        from = index * 2;
                                        to = from + 1;
                                
                                        if (to >= value.length)
                                        {
                                                // use the last 1 or 2 values

                                                if ((value.length & 1) == 0) // even
                                                {
                                                        from = value.length - 2;
                                                        to = from + 1;
                                                }
                                                else // odd
                                                {
                                                        from = value.length - 1;
                                                        to = from;
                                                }
                                        }
                                }
                                
                                return this.calculateRand(
                                        from == to ? null : value[from].getBoxed(), 
                                        value[to].getBoxed(),
                                        rnd);
                        }
                }
                
                throw new AssertionError();
        }
        
        public boolean hasIndex(int index)
        {
                if (!set)
                {
                        return false;
                }
                
                return index < value.length;
        }
        
        public boolean isIndexSet(int index)
        {
                if (value == null)
                {
                        return false;
                }
                
                if (index < value.length)
                {
                        return value[index].isSet();
                }
                else
                {
                        return value[value.length-1].isSet();
                }
        }
        
        public int getValuesLength()
        {
                return value == null ? 0 : value.length;
        }
        
        public boolean hasValue(TYPE val)
        {
                if (this.valueSet == null)
                {
                        this.valueSet = new HashSet<>(this.value.length);
                        for (SimpleAbstract<TYPE> wrapper : this.value)
                        {
                                this.valueSet.add(wrapper.getBoxed());
                        }
                }
                
                return valueSet.contains(val);
        }

        @Override
        boolean newValue(Object value)
        {
                boolean dirty = false;
                List list;
                LIST_FUNCTION oldListFunc = this.listFunction;
                
                if (value instanceof List)
                {
                        list = (List) value;
                }
                else
                {
                        list = new ArrayList(1);
                        list.add(value);
                }
                
                this.listFunction = LIST_FUNCTION.REPEAT; // default
                boolean skipFirst = false;
                if (list.get(0) instanceof String)
                {
                        try
                        {
                                this.listFunction = LIST_FUNCTION.valueOf((String) list.get(0));
                                skipFirst = true;
                        }
                        catch (IllegalArgumentException ex)
                        {
                                this.listFunction = LIST_FUNCTION.LINEAR;
                        }
                }
                needsSeed = this.listFunction == LIST_FUNCTION.SHUFFLE || this.listFunction == LIST_FUNCTION.RAND;
                
                int neededSize = list.size() - (skipFirst ? 1 : 0);
                if (neededSize < 1) neededSize = 1;

                if (this.value == null || neededSize != this.value.length)
                {
                        this.value = (GCSIMPLE[]) new SimpleAbstract[neededSize];
                        this.value[0] = (GCSIMPLE) valueFactory.create(null, null);
                }

                
                
                
                for (int i = skipFirst ? 1 : 0, valueIndex = 0; 
                i < list.size(); 
                ++i, ++valueIndex)
                {
                        if (this.value[valueIndex] == null)
                        {
                                this.value[valueIndex] = (GCSIMPLE) valueFactory.create(null, null);
                        }                                

                        if (this.value[valueIndex].newValue(list.get(i)))
                        {
                                dirty = true;
                        }
                }
                
                boolean wasSet = this.set;
                set = isIndexSet(this.value.length - 1);
                if (wasSet != set || oldListFunc != this.listFunction)
                {
                        dirty = true;
                }
                
                if (dirty)
                {
                        this.valueSet = null;
                        fireChangeListener();
                }
                
                return dirty;
        }
}
