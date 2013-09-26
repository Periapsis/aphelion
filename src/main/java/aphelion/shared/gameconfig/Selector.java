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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 *
 * @author Joris
 */
public class Selector implements Comparable<Selector>, Cloneable
{
        private HashSet<String> weapon;
        private HashSet<String> ship;
        private HashSet<Integer> freq;
        private HashSet<String> perk;
        private int importance;
        private long specificity;

        public boolean hasWeapon()
        {
                return weapon != null;
        }
        
        public Set<String> getWeapon()
        {
                return weapon;
        }

        /** Set multiple matches to this selector.
         * These are matched to another selector as a boolean OR.
         * The set should not be modified after calling this method.
         */
        public void setWeapon(HashSet<String> weapon)
        {
                this.weapon = weapon;
                if (weapon != null && weapon.isEmpty()) { throw new IllegalArgumentException(); }
                calculateSpecificity();
        }
        
        public void setWeapon(String newWeapon)
        {
                if (newWeapon == null || newWeapon.length() == 0)
                {
                        this.weapon = null;
                }
                else
                {
                        this.weapon = new HashSet<>(1);
                        this.weapon.add(newWeapon);
                }
                calculateSpecificity();
        }
        
        public void setWeaponYaml(Object yaml) throws ClassCastException
        {
                if (yaml == null) return;
                if (yaml instanceof String)
                {
                        this.weapon = new HashSet<>(1);
                        this.weapon.add((String) yaml );
                }
                else
                {
                        List list = (List) yaml;
                        if (list.size() < 1) return;
                        
                        this.weapon = new HashSet<>(list.size());
                        this.weapon.addAll(list);
                }
                
                calculateSpecificity();
        }
        
        public boolean hasShip()
        {
                return ship != null;
        }

        public Set<String> getShip()
        {
                return ship;
        }

        /** Set multiple matches to this selector.
         * These are matched to another selector as a boolean OR.
         * The set should not be modified after calling this method.
         */
        public void setShip(HashSet<String> ship)
        {
                this.ship = ship;
                if (ship != null && ship.isEmpty()) { throw new IllegalArgumentException(); }
                calculateSpecificity();
        }
        
        public void setShip(String newShip)
        {
                if (newShip == null || newShip.length() == 0)
                {
                        this.ship = null;
                }
                else
                {
                        this.ship = new HashSet<>(1);
                        this.ship.add(newShip);
                }
                calculateSpecificity();
        }
        
        public void setShipYaml(Object yaml) throws ClassCastException
        {
                if (yaml == null) return;
                if (yaml instanceof String)
                {
                        this.ship = new HashSet<>(1);
                        this.ship.add((String) yaml );
                }
                else
                {
                        List list = (List) yaml;
                        if (list.size() < 1) return;
                        
                        this.ship = new HashSet<>(list.size());
                        this.ship.addAll(list);
                }
                
                calculateSpecificity();
        }
        
        public boolean hasFreq()
        {
                return freq != null;
        }

        public Set<Integer> getFreq()
        {
                return freq;
        }

        /** Set multiple matches to this selector.
         * These are matched to another selector as a boolean OR.
         * The set should not be modified after calling this method.
         */
        public void setFreq(HashSet<Integer> freq)
        {
                this.freq = freq;
                if (freq != null && freq.isEmpty()) { throw new IllegalArgumentException(); }
                calculateSpecificity();
        }
        
        public void setFreq(Integer newFreq)
        {
                if (newFreq == null)
                {
                        this.freq = null;
                }
                else
                {
                        this.freq = new HashSet<>(1);
                        this.freq.add(newFreq);
                }
                calculateSpecificity();
        }
        
        public void setFreqYaml(Object yaml) throws ClassCastException
        {
                if (yaml == null) return;
                if (yaml instanceof Integer)
                {
                        this.freq = new HashSet<>(1);
                        this.freq.add((Integer) yaml );
                }
                else
                {
                        List list = (List) yaml;
                        if (list.size() < 1) return;
                        
                        this.freq = new HashSet<>(list.size());
                        this.freq.addAll(list);
                }
                
                calculateSpecificity();
        }
        
        public boolean hasPerk()
        {
                return perk != null;
        }

        public Set<String> getPerk()
        {
                return perk;
        }

        /** Set multiple matches to this selector.
         * These are matched to another selector as a boolean OR.
         * The set should not be modified after calling this method.
         */
        public void setPerk(HashSet<String> perk)
        {
                this.perk = perk;
                if (perk != null && perk.isEmpty()) { throw new IllegalArgumentException(); }
                calculateSpecificity();
        }
        
        public void setPerk(String netPerk)
        {
                if (netPerk == null || netPerk.length() == 0)
                {
                        this.perk = null;
                }
                else
                {
                        this.perk = new HashSet<>(1);
                        this.perk.add(netPerk);
                }
                calculateSpecificity();
        }
        
        public void setPerkYaml(Object yaml) throws ClassCastException
        {
                if (yaml == null) return;
                if (yaml instanceof String)
                {
                        this.perk = new HashSet<>(1);
                        this.perk.add((String) yaml );
                }
                else
                {
                        List list = (List) yaml;
                        if (list.size() < 1) return;
                        
                        this.perk = new HashSet<>(list.size());
                        this.perk.addAll(list);
                }
                
                calculateSpecificity();
        }

        public int getImportance()
        {
                return importance;
        }

        public void setImportance(int importance)
        {
                this.importance = importance;
                calculateSpecificity();
        }
        
        public void setImportance(Object yaml) throws ClassCastException
        {
                if (yaml == null) return;
                
                this.importance = (int) yaml;
                
                calculateSpecificity();
        }

        public long getSpecificity()
        {
                return specificity;
        }

     
        
        private static final long SPECIFICITY_VALUE_BITS = 12;
        private static final long SPECIFICITY_MAX_VAL = (1L << SPECIFICITY_VALUE_BITS) - 1;
        
        private long spec_list(Collection list, int i)
        {
                // i want lambda ffs
                
                assert list.size() > 0;
                
                // the more it matches, the less specific it is
                
                long val = SPECIFICITY_MAX_VAL - (list.size() - 1);
                if (val < 0) val = 0;
                
                return val << (i * SPECIFICITY_VALUE_BITS);
        }
        
        private long spec_importance(long val, int i)
        {
                if (val > SPECIFICITY_MAX_VAL)
                {
                        val = SPECIFICITY_MAX_VAL;
                }
                
                return val << (i * SPECIFICITY_VALUE_BITS);
        }
        
        
        private void calculateSpecificity()
        {
                // Least important to most important:
                // - weapon
                // - ship
                // - freq
                // - perk
                // - importance number
                
                // Multiple values of weapon are less important than fewer values
                
                specificity = 0;
                if (weapon != null)
                {
                        assert !weapon.isEmpty();
                        specificity += spec_list(weapon, 0);
                }
                
                if (ship != null)
                {
                        assert !ship.isEmpty();
                        specificity += spec_list(ship, 1);
                }
                
                if (freq != null)
                {
                        assert !freq.isEmpty();
                        specificity += spec_list(freq, 2);
                }
                
                if (perk != null)
                {
                        assert !perk.isEmpty();
                        specificity += spec_list(perk, 3);
                }
                if (importance > 0)
                {
                        specificity += spec_importance(importance, 4);
                }
        }

        @Override
        public int compareTo(Selector o)
        {
                return Long.compare(this.specificity, o.specificity);
        }
        
        private static boolean containsAny(Iterable left, Collection right)
        {
                for (Object val : left)
                {
                        if (right.contains(val))
                        {
                                return true;
                        }
                }
                
                return false;
        }
        
        public boolean selectorAppliesToSelection(Selector selection)
        {
                // If this selection has a weapon definition
                // The other selection must have atleast 1 weapon we also have.
                // If we do not have a weapon definition, this selection matches 
                // every weapon or non-weapon.
                // same goes for the other definitions (perk, ship, etc)
                
                if (hasWeapon())
                {
                        if (!selection.hasWeapon() || !containsAny(weapon, selection.weapon))
                        {
                                return false;
                        }
                }
                
                if (hasShip())
                {
                        if (!selection.hasShip() || !containsAny(ship, selection.ship))
                        {
                                return false;
                        }
                }
                
                if (hasFreq())
                {
                        if (!selection.hasFreq() || !containsAny(freq, selection.freq))
                        {
                                return false;
                        }
                }
                
                if (hasPerk())
                {
                        if (!selection.hasPerk() || !containsAny(perk, selection.perk))
                        {
                                return false;
                        }
                }
                
                return true;
        }

        @Override
        public int hashCode()
        {
                int hash = 7;
                hash = 19 * hash + Objects.hashCode(this.weapon);
                hash = 19 * hash + Objects.hashCode(this.ship);
                hash = 19 * hash + Objects.hashCode(this.freq);
                hash = 19 * hash + Objects.hashCode(this.perk);
                hash = 19 * hash + this.importance;
                return hash;
        }

        @Override
        public boolean equals(Object obj)
        {
                if (obj == null)
                {
                        return false;
                }
                if (!(obj instanceof Selector))
                {
                        return false;
                }
                final Selector other = (Selector) obj;
                if (!Objects.equals(this.weapon, other.weapon))
                {
                        return false;
                }
                if (!Objects.equals(this.ship, other.ship))
                {
                        return false;
                }
                if (!Objects.equals(this.freq, other.freq))
                {
                        return false;
                }
                if (!Objects.equals(this.perk, other.perk))
                {
                        return false;
                }
                if (this.importance != other.importance)
                {
                        return false;
                }
                return true;
        }

        @Override
        protected Selector clone() throws CloneNotSupportedException
        {
                Selector ret = (Selector) super.clone();
                
                if (weapon != null)
                {
                        ret.weapon = (HashSet<String>) weapon.clone();
                }
                
                if (ship != null)
                {
                        ret.ship = (HashSet<String>) ship.clone();
                }
                
                if (freq != null)
                {
                        ret.freq = (HashSet<Integer>) freq.clone();
                }
                
                if (perk != null)
                {
                        ret.perk = (HashSet<String>) perk.clone();
                }
                
                return ret;
        }
        
        public void set(Selector other)
        {
                weapon = other.weapon;
                ship = other.ship;
                freq = other.freq;
                perk = other.perk;
                importance = other.importance;
                specificity = other.specificity;
                
                if (weapon != null)
                {
                        weapon = (HashSet<String>) weapon.clone();
                }
                
                if (ship != null)
                {
                        ship = (HashSet<String>) ship.clone();
                }
                
                if (freq != null)
                {
                        freq = (HashSet<Integer>) freq.clone();
                }
                
                if (perk != null)
                {
                        perk = (HashSet<String>) perk.clone();
                }
        }

        @Override
        public String toString()
        {
                return "Selector{" + "weapon=" + weapon + ", ship=" + ship + ", freq=" + freq + ", perk=" + perk + ", importance=" + importance + ", specificity=" + specificity + '}';
        }
}
