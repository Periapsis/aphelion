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

import aphelion.client.net.NetworkedGame;
import aphelion.shared.net.COMMAND_SOURCE;
import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.controls.AbstractController;
import de.lessvoid.nifty.controls.NiftyInputControl;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.input.NiftyInputEvent;
import de.lessvoid.nifty.input.NiftyInputMapping;
import de.lessvoid.nifty.input.keyboard.KeyboardInputEvent;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.xml.xpp3.Attributes;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author Joris
 */
public class GameMenuController extends AbstractController implements NiftyInputMapping
{
        private Nifty nifty;
        private Screen screen;
        private NetworkedGame netGame;
        private Map<Character, HotCharControl> hotChars;
        
        public void aphelionBind(NetworkedGame netGame)
        {
                this.netGame = netGame;
        }
        
        @Override
        public void bind(Nifty nifty, Screen screen, Element element, Properties parameter,
                         Attributes controlDefinitionAttributes)
        {
                bind(element);
                this.nifty = nifty;
                this.screen = screen;
                element.attachInputControl(new NiftyInputControl(this, this));
                hotChars = HotCharControl.findControls(element);
        }
        
        @Override
        public NiftyInputEvent convert(KeyboardInputEvent inputEvent)
        {       
                if (!inputEvent.isKeyDown())
                {
                        switch (inputEvent.getKey())
                        {
                                case KeyboardInputEvent.KEY_ESCAPE:
                                        return NiftyInputEvent.Escape;
                        }
                }
                
                if (!Character.isISOControl(inputEvent.getCharacter()))
                {
                        // this is pretty silly...
                        NiftyInputEvent.Character.setCharacter(inputEvent.getCharacter());
                        return NiftyInputEvent.Character;
                }
                
                return null;
        }

        @Override
        public void onStartScreen()
        {
        }

        @Override
        public boolean inputEvent(NiftyInputEvent inputEvent)
        {
                if (inputEvent == null)
                {
                        return false;
                }
                
                String id = this.getElement().getId();
                
                switch (inputEvent)
                {
                        case Escape:
                                nifty.closePopup(id);
                                return true;
                        case Character:
                                nifty.closePopup(id);
                                return HotCharControl.doPrimaryMouse(hotChars, inputEvent.getCharacter());
                }
                
                return false;
        }
        
        
        
        private boolean sendCommand(String command, String ... args)
        {
                if (netGame == null)
                {
                        return false;
                }
                netGame.sendCommand(COMMAND_SOURCE.USER_FROM_GUI, command, args);
                // todo something to prevent flooding?
                
                return true; // do not try other targets (whatever that means)
        }
        
        // called from nifty
        public boolean sendCommand1(String command)
        {
                return sendCommand(command);
        }
        public boolean sendCommand2(String command, String arg1)
        {
                return sendCommand(command, arg1);
        }
        public boolean sendCommand3(String command, String arg1, String arg2)
        {
                return sendCommand(command, arg1, arg2);
        }
        

        
        public void quit()
        {
                nifty.exit();
        }
        
        public static void main(String[] args) throws Exception
        {
                Method m = GameMenuController.class.getMethod("sendCommand", String.class, String[].class);
                GameMenuController test = new GameMenuController();
                m.invoke(test, new Object[] {"a", "b", "c"});
                
        }
}
