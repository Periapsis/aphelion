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

import aphelion.client.graphics.AnimatedColour;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.newdawn.slick.Color;

/**
 *
 * @author Joris
 */
public class GCColour extends WrappedValueAbstract
{
        private static final Logger log = Logger.getLogger(GCColour.class.getName());
        private int[] colours;
        private int[] frameDuration;
        private boolean tmpDirty;
        
        /*
         * projectile-radar-colour:
         *      colour: [0xADADAD, 0x000000] # 0xRRGGBB or 0xAARRGGBB
         *      frame-duration: 80
         */
        public GCColour(ConfigSelection selection, String key)
        {
                super(selection, key);
        }
        
        private void unset()
        {
                if (set)
                {
                        tmpDirty = true;
                }
                
                frameDuration = new int[]{100};
                colours = new int[]{0xFF00FF};
                
                set = false;
        }

        @Override
        boolean newValue(Object value_)
        {
                tmpDirty = false;
                
                if (value_ == null)
                {
                        unset();
                }
                else if (value_ instanceof Integer)
                {
                        int value = (Integer) value_;
                        setColours(new int[]{value});
                        setFrameDuration(new int[]{100});
                        set = true;
                }
                else
                {
                        try
                        {
                                Map value = (Map) value_;

                                Object coloursObj = (List) value.get("colour");
                                if (coloursObj == null)
                                {
                                        setColours(new int[]{0xFF00FF});
                                }
                                else if (coloursObj instanceof Integer)
                                {
                                        setColours(new int[]{(Integer) coloursObj});
                                }
                                else
                                {
                                        List list = (List) coloursObj;
                                        int[] arr = new int[list.size()];
                                        for (int i = 0; i < list.size(); ++i)
                                        {
                                                arr[i] = (Integer) list.get(i);
                                        }

                                        setColours(arr);
                                }

                                Object frameDurationObj = value.get("frame-duration");
                                if (frameDurationObj == null)
                                {
                                        setFrameDuration(new int[]{100});
                                }
                                else if (frameDurationObj instanceof Integer)
                                {
                                        setFrameDuration(new int[]{(Integer) frameDurationObj});
                                }
                                else
                                {
                                        List list = (List) frameDurationObj;
                                        int[] arr = new int[list.size()];
                                        for (int i = 0; i < list.size(); ++i)
                                        {
                                                arr[i] = (Integer) list.get(i);
                                        }

                                        setFrameDuration(arr);
                                }

                                set = true;
                        }
                        catch (ClassCastException | NullPointerException ex)
                        {
                                log.log(Level.WARNING, "Malformed GCColour value with key " + this.key, ex);       
                                unset();
                        }
                }
                
                if (tmpDirty)
                {
                        tmpDirty = false;
                        fireChangeListener();
                        return true;
                }
                
                return false;
        }
        
        public AnimatedColour getAnimation()
        {
                if (!this.set)
                {
                        return null;
                }
                
                AnimatedColour anim = new AnimatedColour(this.colours.length);
                for (int i = 0; i < colours.length; ++i)
                {
                        anim.addFrame(
                                new Color(colours[i]), 
                                i < this.frameDuration.length 
                                ? this.frameDuration[i] 
                                : this.frameDuration[this.frameDuration.length-1]);
                }
                
                return anim;
        }
        
        private void setColours(int colours[])
        {
                if (!Arrays.equals(this.colours, colours))
                {
                        tmpDirty = true;
                }
                this.colours = colours;
        }
        
        private void setFrameDuration(int frameDuration[])
        {
                if (!Arrays.equals(this.frameDuration, frameDuration))
                {
                        tmpDirty = true;
                }
                this.frameDuration = frameDuration;
        }
}
