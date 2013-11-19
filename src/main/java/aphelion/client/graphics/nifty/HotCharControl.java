/* Copyright (c) 2013, Joris van der Wel
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package aphelion.client.graphics.nifty;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.controls.AbstractController;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.elements.ElementInteraction;
import de.lessvoid.nifty.input.NiftyInputEvent;
import de.lessvoid.nifty.input.NiftyMouseInputEvent;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.xml.xpp3.Attributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author Joris
 */
public class HotCharControl extends AbstractController
{
        private char chr;
        
        @Override
        public void bind(Nifty nifty, Screen screen, Element element, Properties parameter,
                         Attributes controlDefinitionAttributes)
        {
                bind(element);
                
                try 
                {
                        chr = controlDefinitionAttributes.get("char").charAt(0);
                }
                catch (NullPointerException | IndexOutOfBoundsException ex)
                {
                        throw new IllegalArgumentException(ex);
                }
        }

        public char getChar()
        {
                return chr;
        }

        @Override
        public void onStartScreen()
        {
        }

        @Override
        public boolean inputEvent(NiftyInputEvent inputEvent)
        {
                return false;
        }
        
        public void doPrimaryMouse()
        {
                Element el = this.getElement();
                if (el == null)
                {
                        return;
                }
                ElementInteraction interact = el.getElementInteraction();
                
                System.out.println("doPrimaryMouse");
                
                long now = el.getNifty().getTimeProvider().getMsTime();
                
                NiftyMouseInputEvent ev = new NiftyMouseInputEvent();
                ev.initialize(el.getX(), el.getY(), 0, true, false, false);
                ev.setButton0InitialDown(true);
                ev.setButton0Release(true);
                
                interact.process(ev, now, true, true, true);
        }
        
        public static Map<Character, HotCharControl> findControls(Element parent)
        {
                HashMap<Character, HotCharControl> ret = new HashMap<>();
                doFind(ret, parent);
                return ret;
        }
        
        private static void doFind(HashMap<Character, HotCharControl> ret, Element element)
        {
                HotCharControl control = element.getControl(HotCharControl.class);
                if (control != null)
                {
                        ret.put(control.getChar(), control);
                }
                
                for (Element el : element.getElements())
                {
                        doFind(ret, el);
                }
        }
        
        public static boolean doPrimaryMouse(Map<Character, HotCharControl> controls, char c)
        {
                HotCharControl control = controls.get(c);
                if (control == null)
                {
                        if (Character.isUpperCase(c))
                        {
                                control = controls.get(Character.toLowerCase(c));
                        }
                        else if (Character.isLowerCase(c))
                        {
                                control = controls.get(Character.toUpperCase(c));
                        }
                }

                if (control != null)
                {
                        control.doPrimaryMouse();
                        return true;
                }
                
                return false;
        }
}
