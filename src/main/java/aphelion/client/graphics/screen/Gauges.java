/*
 * Aphelion
 * Copyright (c) 2013  Joris van der Wel & Joshua Edwards
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

package aphelion.client.graphics.screen;


import aphelion.shared.gameconfig.GCString;
import aphelion.shared.gameconfig.GCStringList;
import aphelion.shared.gameconfig.WrappedValueAbstract;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.WEAPON_SLOT;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.screen.Screen;
import java.util.HashSet;
import java.util.Set;

public class Gauges
{
        private final ActorPublic localActor;
        private final Screen screen;
        
        private class WeaponSlot implements WrappedValueAbstract.ChangeListener
        {
                private final WEAPON_SLOT slot;
                private final GCString weaponKey;
                private GCStringList niftyShow;
                private final Set<String> visible = new HashSet<>();
                private boolean enabled = true;
                
                WeaponSlot(WEAPON_SLOT slot)
                {
                        this.slot = slot;
                        weaponKey = localActor.getWeaponKey(slot);
                        weaponKey.addWeakChangeListener(this);
                        loadConfig();
                }

                @Override
                public void gameConfigValueChanged(WrappedValueAbstract val)
                {
                        if (val == weaponKey)
                        {
                                // different weapon has been assigned to the weapon slot
                                loadConfig();
                        }
                        else if (val == niftyShow)
                        {
                                updateNiftyElements();
                        }
                }
                
                private void loadConfig()
                {
                        GCStringList newNiftyShow = localActor.getActorConfigStringList(weaponKey.get(), "weapon-nifty-show");
                        if (newNiftyShow != niftyShow)
                        {
                                newNiftyShow.addWeakChangeListener(this);
                                if (niftyShow != null) niftyShow.removeWeakChangeListener(this);
                                niftyShow = newNiftyShow;
                                updateNiftyElements();
                        }
                }
                
                public boolean hasWeapon()
                {
                        return weaponKey.isSet();
                }
                
                public void updateNiftyElements()
                {
                        int len = niftyShow.getValuesLength();
                        
                        if (enabled)
                        {
                                for (int i = 0; i < len; ++i)
                                {
                                        // prevent { el.hide(); el.show(); }
                                        visible.remove(niftyShow.get(i));
                                }
                        }
                        
                        for (String id : visible)
                        {
                                Element el = screen.findElementByName(id);
                                if (el != null)
                                {
                                        el.hide();
                                }
                        }
                        
                        visible.clear();
                        
                        if (enabled)
                        {
                                for (int i = 0; i < len; ++i)
                                {
                                        String id = niftyShow.get(i);
                                        visible.add(id);
                                        Element el = screen.findElementByName(id);
                                        if (el != null)
                                        {
                                                el.show();
                                        }
                                }
                        }
                }
                
                public void setEnabled(boolean enabled)
                {
                        if (this.enabled == enabled) { return; }
                        this.enabled = enabled;
                        updateNiftyElements();
                }
        }
        
        private final WeaponSlot[] slots = new WeaponSlot[WEAPON_SLOT.values().length];
        
        public Gauges(Screen screen, ActorPublic localActor)
        {
                this.screen = screen;
                this.localActor = localActor;
                
                for (int i = 0; i < slots.length; ++i)
                {
                        slots[i] = new WeaponSlot(WEAPON_SLOT.byId(i));
                }
                
                slots[WEAPON_SLOT.GUN_MULTI.id].setEnabled(false);
        }
        
        public void setMultiFireGun(boolean multi)
        {
                slots[WEAPON_SLOT.GUN.id].setEnabled(!multi);
                slots[WEAPON_SLOT.GUN_MULTI.id].setEnabled(multi);
        }
}
