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


import aphelion.shared.resource.ResourceDB;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class ConfigSelection
{
        private static final Logger log = Logger.getLogger("aphelion.config");
        private final GameConfig config;
        public final Selector selection = new Selector();
        
        // This map contains a list so that adopted values continue to be updated.
        // otherwise only the first entry is returned
        private final HashMap<String, List<WeakReference<WrappedValueAbstract>>> usedValues = new HashMap<>();
        
        ConfigSelection(GameConfig config)
        {
                this.config = config;
        }
        
        public void adoptAllValues(ConfigSelection other)
        {
                if (other == null) { return; }
                if (other.usedValues.entrySet() == null) // wtf java
                {
                        return;
                }
                
                Iterator <Map.Entry<String,List<WeakReference<WrappedValueAbstract>>>> it 
                        = other.usedValues.entrySet().iterator();
                
                while (it.hasNext())
                {
                        Map.Entry<String, List<WeakReference<WrappedValueAbstract>>> entry = it.next();
                        
                        List<WeakReference<WrappedValueAbstract>> myList = usedValues.get(entry.getKey());
                        if (myList == null)
                        {
                                usedValues.put(entry.getKey(), entry.getValue());
                        }
                        else
                        {
                                myList.addAll(entry.getValue());
                        }
                }
                
                other.usedValues.clear();
                cleanup();
        }
        
        private List<WeakReference<WrappedValueAbstract>> getListOrNew(String name)
        {
                List<WeakReference<WrappedValueAbstract>> list = usedValues.get(name);
                
                if (list == null)
                {
                        list = new LinkedList<>();
                        usedValues.put(name, list);
                }
                
                return list;
        }
        
        private WrappedValueAbstract getValue(String name, 
                Class klass, 
                WrappedValueAbstract.Factory factory)
        {
                List<WeakReference<WrappedValueAbstract>> list = getListOrNew(name);
                
                WrappedValueAbstract existingValue = list.isEmpty() ? null : list.get(0).get();
                
                if (existingValue == null)
                {
                        WrappedValueAbstract value = factory.create(this, name);
                        assert klass.isInstance(value);
                        list.add(new WeakReference<>(value));
                        resolveValue(value);
                        return value;
                }
                else
                {
                        if (klass.isInstance(existingValue))
                        {
                                return existingValue;
                        }
                        else
                        {
                                // mixing getInteger, getString etc for the same value...
                                // this is not supported at the moment
                                throw new IllegalStateException();
                        }
                }
        }
        
        public GCInteger getInteger(String name)
        {
                return (GCInteger) getValue(name, GCInteger.class, GCInteger.factory);
        }
        
        public GCIntegerList getIntegerList(String name)
        {       
                return (GCIntegerList) getValue(name, GCIntegerList.class, GCIntegerList.factory);
        }
        
        public GCString getString(String name)
        {
                return (GCString) getValue(name, GCString.class, GCString.factory);
        }
        
        public GCStringList getStringList(String name)
        {
                return (GCStringList) getValue(name, GCStringList.class, GCStringList.factory);
        }
        
        public GCBoolean getBoolean(String name)
        {
                return (GCBoolean) getValue(name, GCBoolean.class, GCBoolean.factory);
        }
        public GCBooleanList getBooleanList(String name)
        {
                return (GCBooleanList) getValue(name, GCBooleanList.class, GCBooleanList.factory);
        }
        
        
        public GCImage getImage(String name, ResourceDB db)
        {
                List<WeakReference<WrappedValueAbstract>> list = getListOrNew(name);
                
                WrappedValueAbstract existingValue = list.isEmpty() ? null : list.get(0).get();
                
                if (existingValue == null)
                {
                        GCImage value = new GCImage(this, name, db);                 
                        list.add(new WeakReference<WrappedValueAbstract>(value));
                        resolveValue(value);
                        return value;
                }
                else
                {
                        if (existingValue instanceof GCImage)
                        {
                                assert ((GCImage) existingValue).db == db;
                                return (GCImage) existingValue;
                        }
                        else
                        {
                                // mixing getInteger, getString etc for the same value...
                                // this is not supported at the moment
                                throw new IllegalStateException();
                        }
                }
        }
        
        public GCColour getColour(String name)
        {
                List<WeakReference<WrappedValueAbstract>> list = getListOrNew(name);
                
                WrappedValueAbstract existingValue = list.isEmpty() ? null : list.get(0).get();
                
                if (existingValue == null)
                {
                        GCColour value = new GCColour(this, name);
                        list.add(new WeakReference<WrappedValueAbstract>(value));
                        resolveValue(value);
                        return value;
                }
                else
                {
                        if (existingValue instanceof GCColour)
                        {
                                return (GCColour) existingValue;
                        }
                        else
                        {
                                // mixing getInteger, getString etc for the same value...
                                // this is not supported at the moment
                                throw new IllegalStateException();
                        }
                }
        }
        
        /** Update the value for a single wrapped value.
         * Called by resolveValues()
         * @param value 
         */
        public void resolveValue(WrappedValueAbstract value)
        {
                resolveValue(value, 0);
        }
        
        public void resolveValue(WrappedValueAbstract value, int n)
        {
                try
                {
                        // "rules" is ordered by highest specificity first
                        for (Rule rule : config.rules)
                        {
                                if (rule.selector.selectorAppliesToSelection(this.selection))
                                {
                                        Object yamlValue = rule.attributes.get(value.key);
                                        if (yamlValue != null)
                                        {
                                                value.newValue(yamlValue); // might fire an event listener!

                                                return;
                                        }
                                }
                        }

                        value.newValue(null);
                }
                catch (ConcurrentModificationException ex)
                {
                        // An event listener might have called GameConfig.addRule()
                        // try again.
                        
                        // Note: because we are not dealing with threads in this code,
                        // it is safe to rely ConcurrentModificationException
                        
                        if (n == 100000)
                        {
                                throw new Error("Too many concurrent modifications. Probably caused by an infinite callback loop");
                        }
                        
                        resolveValue(value, n + 1);
                }
        }
        
        /** This method is called whenever the values returned from this object need updating.
         * If you change this.selection, this method must be called afterwards. In other cases 
         * this method is called by GameConfig when needed.
         */
        public void resolveAllValues()
        {
                resolveAllValues(0);
        }
        
        private void resolveAllValues(int n)
        {
                try
                {
                        Iterator <List<WeakReference<WrappedValueAbstract>>> it = usedValues.values().iterator();
                        while (it.hasNext())
                        {
                                List<WeakReference<WrappedValueAbstract>> list = it.next();

                                Iterator<WeakReference<WrappedValueAbstract>> listIt = list.iterator();

                                while (listIt.hasNext())
                                {
                                        WrappedValueAbstract value = listIt.next().get();
                                        if (value == null)
                                        {
                                                listIt.remove();
                                                continue;
                                        }

                                        resolveValue(value);
                                }

                                if (list.isEmpty())
                                {
                                        it.remove();
                                }
                        }
                }
                catch (ConcurrentModificationException ex)
                {
                        // An event listener might have called getValue()
                        // try again.
                        
                        // Note: because we are not dealing with threads in this code,
                        // it is safe to rely ConcurrentModificationException
                        
                        if (n == 100000)
                        {
                                throw new Error("Too many concurrent modifications. Probably caused by an infinite callback loop");
                        }
                        
                        resolveAllValues(n + 1);
                }
        }

        public void cleanup()
        {
                Iterator<List<WeakReference<WrappedValueAbstract>>> it = usedValues.values().iterator();
                while (it.hasNext())
                {
                        List<WeakReference<WrappedValueAbstract>> list = it.next();
                        
                        Iterator<WeakReference<WrappedValueAbstract>> listIt = list.iterator();
                        
                        while (listIt.hasNext())
                        {
                                WrappedValueAbstract value = listIt.next().get();
                                if (value == null)
                                {
                                        listIt.remove();
                                        continue;
                                }
                        }

                        if (list.isEmpty())
                        {
                                it.remove();
                        }
                }
        }
}
