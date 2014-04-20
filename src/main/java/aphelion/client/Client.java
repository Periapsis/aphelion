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

import aphelion.server.AphelionServerThread;
import aphelion.client.graphics.Graph;
import aphelion.client.net.NetworkedGame;
import aphelion.client.resource.AsyncTexture;
import aphelion.server.ServerConfigException;
import aphelion.shared.event.Deadlock;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.event.TickedEventLoop;
import aphelion.shared.net.WS_CLOSE_STATUS;
import aphelion.shared.net.protobuf.GameS2C.AuthenticateResponse;
import aphelion.shared.physics.EnvironmentConf;
import aphelion.shared.resource.LocalUserStorage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.newdawn.slick.Graphics;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 *
 * @author Joris
 */
public class Client
{
        private static final Logger log = Logger.getLogger("aphelion.client");
        public static boolean showDebug = false;
        private TickedEventLoop loop;
        private ResourceDB resourceDB;
        private AphelionServerThread serverThread;
        private ConnectLoop connectLoop;
        private STATE state = STATE.NONE;
        private URI serverUri;

        private static enum STATE
        {
                NONE,
                CONNECTING,
                PLAYING,
                QUITTING
        }

        /** *  @param uri if null, singleplayer
         * @param nickname 
         * @throws LWJGLException
         * @throws IOException
         * @throws aphelion.server.ServerConfigException
         */
        public void run(@Nullable URI uri, @Nonnull final String nickname) throws LWJGLException, IOException, ServerConfigException
        {
                if (uri == null)
                {
                        Yaml yaml = new Yaml(new SafeConstructor());
                        Map<String, Object> singlePlayerConfig;
                        try
                        {
                                singlePlayerConfig = (Map<String, Object>) yaml.load(new FileInputStream("./assets/singleplayer.yaml")); 
                        }
                        catch (FileNotFoundException | ClassCastException | YAMLException ex)
                        {
                                // Note: YAMLException is a RunTimeException
                                throw new ServerConfigException("Unable to read server config", ex);
                        }
                        
                        // ignore bind-address and bind-port
                        
                        // share the assets directory
                        singlePlayerConfig.put("assets-cache-path", new LocalUserStorage("assets").getDirectory().getAbsolutePath());
                        
                        serverThread = new AphelionServerThread(false, singlePlayerConfig);
                        serverThread.start();
                        try
                        {
                                uri = new URI("ws://127.0.0.1:" + serverThread.getHTTPListeningPort() + "/aphelion");
                        }
                        catch (URISyntaxException ex)
                        {
                                log.log(Level.SEVERE, "Malformed URI", ex);
                                throw new Error(ex);
                        }
                }
                this.serverUri = uri;

                Display.setTitle("Aphelion");
                
                ByteBuffer[] icons = new ByteBuffer[6];
                aphelion.launcher.Main.getFrameIcons(icons);
                Display.setIcon(icons);
                Display.setFullscreen(false);
                Display.setVSyncEnabled(false);
                Display.setResizable(true);
                Display.setInitialBackground(0f, 0f, 0f);
                Display.setDisplayMode(new DisplayMode(1024, 768));
                Display.create();

                log.log(Level.INFO, "OpenGL version: {0}", GL11.glGetString(GL11.GL_VERSION));

                Keyboard.create();

                // use the default time source for now
                // availableProcessors is including HT (for example "8" on a quad core)
                int processors = Runtime.getRuntime().availableProcessors();
                if (processors < 2) { processors = 2; } // minimum of two workers
                loop = new TickedEventLoop(EnvironmentConf.TICK_LENGTH, processors, null);

                resourceDB = new ResourceDB(loop);
                loop.addLoopEvent(resourceDB);

                resourceDB.addZip(new File("assets/gui.zip"));

                loop.setup();

                connectLoop = new ConnectLoop(serverUri, resourceDB, loop, nickname);

                state = STATE.NONE;
                if (!connectLoop.loop())
                {
                        log.log(Level.SEVERE, "Connection failed");
                        NetworkedGame networkedGame = connectLoop.getNetworkedGame();
                        AuthenticateResponse.ERROR authError = networkedGame.getAuthError();
                        WS_CLOSE_STATUS closeStatus = networkedGame.getDisconnectCode();
                        
                        breakdown();
                        
                        
                        if (authError != null)
                        {
                                String reason = networkedGame.getAuthErrorDesc();
                                if (reason == null) reason = "";
                                
                                JOptionPane.showMessageDialog(null, "Unable to authenticate to " + uri + " (code:"+authError +")\n\n"+reason, "Aphelion", JOptionPane.ERROR_MESSAGE);
                        }
                        else if (closeStatus != null)
                        {
                                String reason = networkedGame.getDisconnectReason();
                                if (reason == null) reason = "";

                                JOptionPane.showMessageDialog(null, "Unable to connect to " + uri + " (code:"+closeStatus +")\n\n"+reason, "Aphelion", JOptionPane.ERROR_MESSAGE);
                        }
                        return;
                }

                loop.setClockSource(connectLoop.getSyncedClockSource());

                log.log(Level.INFO, "Connected");

                state = STATE.PLAYING;
                NetworkedGame netGame = connectLoop.getNetworkedGame();
                
                InitializeLoop initLoop = new InitializeLoop(connectLoop);
                if (initLoop.loop())
                {
                        GameLoop gameLoop = new GameLoop(initLoop);
                        initLoop = null;
                        gameLoop.loop();
                        breakdown();

                        if (gameLoop.isConnectionError())
                        {
                                WS_CLOSE_STATUS code = netGame.getDisconnectCode();
                                String reason = netGame.getDisconnectReason();
                                if (reason == null) reason = "";

                                JOptionPane.showMessageDialog(null, "Connection to the server suddenly dropped (" + uri + ") (code:"+code +")\n\n"+reason, "Aphelion", JOptionPane.ERROR_MESSAGE);
                        }
                }
                else
                {
                        log.log(Level.SEVERE, "Initialize failed");
                        breakdown();
                }
        }
        
        public void breakdown()
        {
                state = STATE.QUITTING;

                if (loop != null)
                {
                        loop.breakdown();
                }
                
                Display.destroy();
                Keyboard.destroy();
                
                if (connectLoop != null)
                {
                        connectLoop.getConnection().stop();
                }
                if (serverThread != null)
                {
                        serverThread.stopServer();
                }


                log.log(Level.INFO, "Breakdown completed");
        }

        public static void initGL()
        {
                int displayWidth = Display.getWidth();
                int displayHeight = Display.getHeight();
                
                glDisableAll();

                GL11.glViewport(0, 0, displayWidth, displayHeight);

                GL11.glMatrixMode(GL11.GL_PROJECTION); // Apply subsequent matrix operations to the projection matrix stack.
                GL11.glLoadIdentity();
                GL11.glOrtho(0, displayWidth, displayHeight, 0, -1, 1);

                GL11.glMatrixMode(GL11.GL_TEXTURE);
                GL11.glLoadIdentity();
                
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glLoadIdentity();
                
                

                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
                AsyncTexture.unbind();
                
                // Enable alpha channels for images
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glEnable(GL11.GL_BLEND);


                Graph.g.setDimensions(displayWidth, displayHeight);
                Graphics.setCurrent(Graph.g);
                Graph.g.setDrawMode(Graphics.MODE_NORMAL);
        }
        
        private static void glDisableAll()
        {
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_DITHER);
                GL11.glDisable(GL11.GL_LINE_SMOOTH);
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_LINE);
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_POINT);
                GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
        
        public static boolean wasResized()
        {
                boolean ret = Display.wasResized() || wasFullscreenChanged;
                wasFullscreenChanged = false;
                return ret;
        }
        
        private static boolean wasFullscreenChanged = false;
        private static int previousDesktopWidth = 1024;
        private static int previousDesktopHeight = 768;
        private static int previousDesktopX = 0;
        private static int previousDesktopY = 0;
        public static boolean setFullScreen(boolean fullscreen)
        {
                if (Display.isFullscreen() == fullscreen)
                {
                        return true;
                }
                
                wasFullscreenChanged = true;
                if (fullscreen)
                {
                        previousDesktopWidth = Display.getWidth();
                        previousDesktopHeight = Display.getHeight();
                        previousDesktopX = Display.getX();
                        previousDesktopY = Display.getY();
                        
                        DisplayMode[] modes;
                        try
                        {
                                modes = Display.getAvailableDisplayModes();
                        }
                        catch (LWJGLException ex)
                        {
                                log.log(Level.SEVERE, "Unable to determine available display modes!", ex);
                                return false;
                        }
                        
                        Arrays.sort(modes, new Comparator<DisplayMode>()
                        {
                                @Override
                                public int compare(DisplayMode o1, DisplayMode o2)
                                {
                                        // more is better
                                        int size1 = o1.getWidth() * o1.getHeight();
                                        int size2 = o2.getWidth() * o2.getHeight();

                                        if (o1.getBitsPerPixel() == o2.getBitsPerPixel())
                                        {
                                                if (size1 == size2)
                                                {
                                                        return -Integer.compare(o1.getFrequency(), o2.getFrequency());
                                                }

                                                return -Integer.compare(size1, size2);
                                        }
                                        
                                        return -Integer.compare(o1.getBitsPerPixel(), o2.getBitsPerPixel());
                                }
                        });
                        
                        for (DisplayMode mode : modes)
                        {
                                try
                                {
                                        Display.setDisplayModeAndFullscreen(mode);
                                        return true;
                                }
                                catch (LWJGLException ex)
                                {
                                        log.log(Level.WARNING, "Unable to use display mode {0} ({1}). Trying next one", new Object[]{
                                                mode,
                                                ex.getMessage()
                                        });
                                }
                        }
                        
                        log.log(Level.SEVERE, "Exhausted display modes, none work");
                }
                else
                {
                        try
                        {
                                Display.setDisplayMode(new DisplayMode(previousDesktopWidth, previousDesktopHeight));
                                Display.setLocation(previousDesktopX, previousDesktopY);
                                return true;
                        }
                        catch (LWJGLException ex)
                        {
                                log.log(Level.SEVERE, "Unable to exit fullscreen!", ex);
                                return false;
                        }
                }
                
                return false;
        }
        
        public static boolean toggleFullScreen()
        {
                return setFullScreen(!Display.isFullscreen());
        }

        public static void main(String[] args) throws Exception
        {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                
                Deadlock.start(true, null);
                
                Client client;
                client = new Client();
                
                try
                {
                        String uri = null;
                        String nickname = null;
                        
                        if (args.length >= 2)
                        {
                                uri = args[0];
                                nickname = args[1];
                        }
                        
                        if (args.length == 1)
                        {
                                uri = args[0];
                        }
                        
                        if (uri != null)
                        {
                                if (uri.equalsIgnoreCase("null") || uri.equalsIgnoreCase("singleplayer"))
                                {
                                        uri = null;
                                }
                        }
                        
                        if (nickname == null)
                        {
                                nickname = "Player" + (int) (Math.random() * 1000000);
                        }
                        
                        client.run(uri == null ? null : new URI(uri), nickname);
                }
                catch (Throwable ex)
                {
                        client.breakdown();
                        new ErrorDialog().setErrorText(ex);
                        Deadlock.stop();
                        throw ex;
                }
                Deadlock.stop();
        }
}