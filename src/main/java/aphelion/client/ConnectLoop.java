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

package aphelion.client;


import aphelion.client.graphics.Graph;
import aphelion.client.net.NetworkedGame;
import aphelion.client.net.SingleGameConnection;
import aphelion.client.resource.AsyncTexture;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.event.ClockSource;
import aphelion.shared.event.TickedEventLoop;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.lwjgl.opengl.Display;
import org.newdawn.slick.Image;

/**
 *
 * @author Joris
 */
public class ConnectLoop
{
        private static final Logger log = Logger.getLogger("aphelion.client");
        
        private final URI wsUri;
        private final URL httpUrl;
        private final ResourceDB resourceDB;
        private final TickedEventLoop loop;
        private final String nickname;
        
        private SingleGameConnection connection;
        private NetworkedGame networkedGame;
        
        

        public ConnectLoop(URI wsUri, ResourceDB resourceDB, TickedEventLoop loop, String nickname) throws MalformedURLException
        {
                this.wsUri = wsUri;
                this.resourceDB = resourceDB;
                this.loop = loop;
                this.nickname = nickname;
                
                this.httpUrl = SwissArmyKnife.websocketURItoHTTP(wsUri);
        }
        
        
        /** Connect to the server and execute the initial data exchange.
         * Returns when the connection is successfully established
         * @return false if the connection failed and the game should quit (or go back to the menu)
         */
        public boolean loop()
        {
                networkedGame = new NetworkedGame(resourceDB, loop, httpUrl, nickname);
                
                AsyncTexture loadingTex = resourceDB.getTextureLoader().getTexture("gui.loading.connecting");
                
                
                connection = new SingleGameConnection(wsUri, loop);
                connection.addListener(networkedGame);
                connection.connect();
                
                while(!loop.isInterruped() && networkedGame.isConnecting())
                {
                        Display.update();
                        
                        if (Display.isCloseRequested())
                        {
                                log.log(Level.WARNING, "Close requested in connect loop");
                                return false;
                        }
                        
                        Graph.graphicsLoop();
                        
                        Client.initGL();
                        
                        loop.loop();
                        
                        Image loadingBanner = loadingTex.getCachedImage();
                        if (loadingBanner != null)
                        {
                                loadingBanner.drawCentered(Display.getWidth() / 2, Display.getHeight() / 2);
                        }
                        
                        Display.sync(120);
                }
                
                if (loop.isInterruped())
                {
                        log.log(Level.WARNING, "Connect loop interrupted");
                        return false;
                }
                
                return !networkedGame.isConnecting() && !networkedGame.isDisconnected();
        }
        
        public ClockSource getSyncedClockSource()
        {
                return networkedGame.getSyncedClockSource();
        }
        
        public SingleGameConnection getConnection()
        {
                return connection;
        }
        
        public NetworkedGame getNetworkedGame()
        {
                return networkedGame;
        }
}
