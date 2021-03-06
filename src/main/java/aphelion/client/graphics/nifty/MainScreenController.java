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
package aphelion.client.graphics.nifty;

import aphelion.client.net.NetworkedGame;
import aphelion.shared.net.COMMAND_SOURCE;
import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.controls.FocusHandler;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.input.NiftyInputEvent;
import de.lessvoid.nifty.input.NiftyInputMapping;
import de.lessvoid.nifty.input.keyboard.KeyboardInputEvent;
import de.lessvoid.nifty.screen.KeyInputHandler;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;

/**
 *
 * @author Joris
 */
public class MainScreenController implements ScreenController, NiftyInputMapping, KeyInputHandler
{
        private Nifty nifty;
        private Screen screen;
        private NetworkedGame netGame;

        @Override
        public void bind(Nifty nifty, Screen screen)
        {
                this.nifty = nifty;
                this.screen = screen;

                screen.addPreKeyboardInputHandler(this, this);
        }

        public void aphelionBind(NetworkedGame netGame)
        {
                this.netGame = netGame;
        }

        @Override
        public void onStartScreen()
        {
        }

        @Override
        public void onEndScreen()
        {
        }

        private boolean sendCommand(String command, String... args)
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

        
        @Override
        public NiftyInputEvent convert(KeyboardInputEvent inputEvent)
        {
                if (inputEvent.isKeyDown())
                {
                        if (inputEvent.getKey() == KeyboardInputEvent.KEY_NEXT && inputEvent.isShiftDown())
                        {
                                return NiftyInputEvent.NextInputElement;
                        }
                        else if (inputEvent.getKey() == KeyboardInputEvent.KEY_PRIOR && inputEvent.isShiftDown())
                        {
                                return NiftyInputEvent.PrevInputElement;
                        }
                        else if (inputEvent.getKey() == KeyboardInputEvent.KEY_TAB)
                        {
                                // disable the default nifty behaviour for tab
                                // otherwise we have to provide an inputMapping for every focusable element.
                                
                                NiftyInputEvent.Character.setCharacter((char) 0);
                                return NiftyInputEvent.Character;
                        }
                }
                
                
                return null;
        }

        @Override
        public boolean keyEvent(NiftyInputEvent inputEvent)
        {
                FocusHandler focusHandler = screen.getFocusHandler();
                if (inputEvent == NiftyInputEvent.Character && NiftyInputEvent.Character.getCharacter() == 0)
                {
                        // nop
                        return true;
                }
                else if (inputEvent == NiftyInputEvent.NextInputElement)
                {
                        if (focusHandler != null)
                        {
                                Element el = focusHandler.getNext(focusHandler.getKeyboardFocusElement());
                                if (el != null)
                                {
                                        el.setFocus();
                                }
                                return true;
                        }
                }
                else if (inputEvent == NiftyInputEvent.PrevInputElement)
                {
                        if (focusHandler != null)
                        {
                                Element el = focusHandler.getPrev(focusHandler.getKeyboardFocusElement());
                                if (el != null)
                                {
                                        el.setFocus();
                                }
                                return true;
                        }
                }
                
                return false;
        }
}
