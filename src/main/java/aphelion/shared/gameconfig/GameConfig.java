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


import aphelion.shared.event.TickEvent;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import aphelion.shared.swissarmyknife.LinkedListHead;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 *
 * @author Joris
 */
public class GameConfig implements TickEvent
{
        private static final Logger log = Logger.getLogger("aphelion.config");
        
        // rule with the highest specificy first!
        final LinkedListHead<Rule> rules = new LinkedListHead<>();
        private final LinkedList<WeakReference<ConfigSelection>> usedSelections = new LinkedList<>();

        public GameConfig()
        {
        }
        
        public ConfigSelection newSelection()
        {
                ConfigSelection sel = new ConfigSelection(this);
                usedSelections.add(new WeakReference<>(sel));
                return sel;
        }
        
        public static List<Object> loadYaml(InputStream in) throws Exception
        {
                // SafeConstructor because we may not trust the server or a moderator
                Yaml yaml = new Yaml(new SafeConstructor());
                List<Object> ret = new ArrayList<>();
                
                // parse the documents immediately (by invoking the iterator)
                for (Object o : yaml.loadAll(in))
                {
                        ret.add(o);
                }
                return ret;
        }
        
        public static List<Object> loadYaml(String str) throws Exception
        {
                // SafeConstructor because we may not trust the server or a moderator
                Yaml yaml = new Yaml(new SafeConstructor());
                List<Object> ret = new ArrayList<>();
                
                // parse the documents immediately (by invoking the iterator)
                for (Object o : yaml.loadAll(str))
                {
                        ret.add(o);
                }
                return ret;
        }
        
        /** Add one or more parsed yaml documents to this instance.
         * Call applyChanges() after having loaded all your documents.
         * 
         * @param documents A list of documents as parsed by yaml.load(). 
         *        The contents are considered read-only; They are never modified by this method, 
         *        and should never be modified by any other method.
         * @param fileIdentifier The file identifier that may be used to undo this configuration using unloadFile()
         */
        public void addFromYaml(List<Object> documents, String fileIdentifier)
        {
                for (Object data : documents)
                {
                        if (!(data instanceof List))
                        {
                                log.log(Level.WARNING, "Error while parsing YAML for {0}, the document is not a List", fileIdentifier);
                                continue;
                        }

                        List list = (List) data;
                        for (Object ruleObj : list )
                        {
                                Map ruleMap;
                                try
                                {
                                        ruleMap = (Map) ruleObj;
                                }
                                catch (ClassCastException ex)
                                {
                                        log.log(Level.WARNING, "Error while parsing YAML for {0}, the rule entry is not a Map. (it is a {1})", 
                                                new Object[]{fileIdentifier, ruleObj.getClass().getName()});
                                        continue;
                                }


                                Object selectorObj = ruleMap.get("selector");
                                Selector selector = new Selector();
                                if (selectorObj != null)
                                {
                                        Map selectorMap;

                                        try
                                        {
                                                selectorMap = (Map) selectorObj;

                                                selector.setWeaponYaml(selectorMap.get("weapon"));
                                                selector.setShipYaml(selectorMap.get("ship"));
                                                selector.setFreqYaml(selectorMap.get("freq"));
                                                selector.setPerkYaml(selectorMap.get("perk"));
                                                selector.setImportance(selectorMap.get("importance"));
                                        }
                                        catch (ClassCastException ex)
                                        {
                                                log.log(Level.WARNING, "Error while parsing YAML for "+fileIdentifier+", invalid type for something in the selector", ex);
                                                continue;
                                        }
                                }

                                Rule rule = null;

                                for (Rule existingRule : rules)
                                {
                                        if (fileIdentifier.equals(existingRule.getFileIdentifier()) && 
                                            selector.equals(existingRule.selector))
                                        {
                                                rule = existingRule;
                                                break;
                                        }
                                }

                                boolean newRule = false;
                                if (rule == null)
                                {
                                        rule = new Rule(fileIdentifier, selector);
                                        newRule = true;
                                }

                                boolean addedSomething = false;
                                if (ruleMap.entrySet() != null)
                                {
                                        Iterator<Map.Entry<Object, Object>> it = ruleMap.entrySet().iterator();
                                        while (it.hasNext())
                                        {
                                                Map.Entry<Object, Object> entry = it.next();
        
                                                String key;
                                                
                                                try
                                                {
                                                        key = (String) entry.getKey();
        
                                                }
                                                catch (ClassCastException ex)
                                                {
                                                        log.log(Level.WARNING, "Error while parsing YAML for "+fileIdentifier+", rule attribute key is not a string", ex);
                                                        continue;
                                                }
                                                
                                                if (key.isEmpty())
                                                {
                                                        continue;
                                                }
                                                
                                                if ("noop".equalsIgnoreCase(key))
                                                {
                                                        continue;
                                                }
        
                                                if ("selector".equals(key))
                                                {
                                                        continue;
                                                }
        
                                                addedSomething = true;
                                                rule.addAttribute(key, entry.getValue());
                                        }
                                }
                                
                                if (addedSomething && newRule)
                                {
                                        addRule(rule);
                                }
                        }                        
                }
                // make sure to call applyChanges after calling this method

        }
        
        private void addRule(Rule rule)
        {
                LinkedListEntry<Rule> entry = this.rules.first;
                while (entry != null)
                {
                        if (rule.compareTo(entry.data) >= 0)
                        {
                                // we have a higher or equal specificy to the looped rule
                                // add me before that rule
                                entry.prependData(rule);
                                return;
                        }
                        
                        entry = entry.next;
                }
                
                rules.appendData(rule);
        }
        
        public void unloadFile(String fileIdentifier)
        {
                Iterator<Rule> it = rules.iterator();
                while (it.hasNext())
                {
                        Rule rule = it.next();
                        
                        if (fileIdentifier.equals(rule.getFileIdentifier()))
                        {
                                it.remove();
                        }
                }
                
                // make sure to call applyChanges after calling this method
        }
        
        public void applyChanges()
        {
                Iterator <WeakReference<ConfigSelection>> it = usedSelections.iterator();
                while (it.hasNext())
                {
                        ConfigSelection selection = it.next().get();
                        if (selection == null)
                        {
                                it.remove();
                                continue;
                        }

                        selection.resolveAllValues();
                }
        }
        
        public void resetTo(GameConfig other)
        {
                rules.clear();
                
                for (Rule otherRule : other.rules)
                {
                        Rule rule = otherRule.clone();
                        addRule(rule);
                }
                
                // make sure to call applyChanges after calling this method
        }
        
        @Override
        public void tick(long tick)
        {
                if (tick % 100 == 0)
                {
                        // cleanup
                        
                        Iterator <WeakReference<ConfigSelection>> it = usedSelections.iterator();
                        while (it.hasNext())
                        {
                                ConfigSelection selection = it.next().get();
                                if (selection == null)
                                {
                                        it.remove();
                                        continue;
                                }

                                selection.cleanup();
                        }
                }
        }
}
