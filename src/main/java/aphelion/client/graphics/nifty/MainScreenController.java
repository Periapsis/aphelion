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
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;

/**
 *
 * @author Joris
 */
public class MainScreenController implements ScreenController
{
        private Nifty nifty;
        private Screen screen;
        private NetworkedGame netGame;
        
        @Override
        public void bind(Nifty nifty, Screen screen)
        {
                this.nifty = nifty;
                this.screen = screen;
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
}
