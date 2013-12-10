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
import aphelion.client.graphics.screen.Camera;
import aphelion.client.graphics.world.StarField;
import aphelion.client.net.NetworkedGame;
import aphelion.client.net.SingleGameConnection;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.event.ClockSource;
import aphelion.shared.event.TickEvent;
import aphelion.shared.event.TickedEventLoop;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.lwjgl.opengl.Display;
import org.newdawn.slick.Color;

/**
 *
 * @author Joris
 */
public class ConnectLoop implements TickEvent
{
        private static final Logger log = Logger.getLogger("aphelion.client");
        
        final URI wsUri;
        final URL httpUrl;
        final ResourceDB resourceDB;
        final TickedEventLoop loop;
        final String nickname;
        
        SingleGameConnection connection;
        NetworkedGame networkedGame;
        
        Camera loadingCamera;
        StarField loadingStarfield;
        int loadingCameraY = 16777216;
        

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
                networkedGame = new NetworkedGame(loop, httpUrl, nickname);
                
                loadingCamera = new Camera(resourceDB);
                loadingStarfield = new StarField(SwissArmyKnife.random.nextInt(), resourceDB);
                
                loadingCamera.setScreenPosition(0, 0);
                loadingCamera.setPosition(0, loadingCameraY);
                
                connection = new SingleGameConnection(wsUri, loop);
                connection.addListener(networkedGame);
                connection.connect();
                
                loop.addTickEvent(this);
                
                try
                {
                        while(!loop.isInterruped() && networkedGame.isConnecting())
                        {
                                Display.update();

                                if (Display.isCloseRequested())
                                {
                                        log.log(Level.WARNING, "Close requested in connect loop");
                                        return false;
                                }

                                loadingCamera.setDimension(Display.getWidth(), Display.getHeight());

                                Graph.graphicsLoop();

                                Client.initGL();

                                loop.loop();

                                loadingStarfield.render(loadingCamera);

                                String line = "Connecting...";
                                int line_width = Graph.g.getFont().getWidth(line);
                                Graph.g.setColor(Color.white);
                                Graph.g.drawString(line, loadingCamera.dimensionHalf.x - line_width / 2, loadingCamera.dimension.y * 0.75f);

                                loadingCamera.setPosition(0, loadingCameraY);

                                Display.sync(60);
                        }

                        if (loop.isInterruped())
                        {
                                log.log(Level.WARNING, "Connect loop interrupted");
                                return false;
                        }

                        return !networkedGame.isConnecting() && !networkedGame.isDisconnected();
                }
                finally
                {
                        loop.removeTickEvent(this);
                }
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

        @Override
        public void tick(long tick)
        {
                loadingCameraY -= 5;
        }
}
