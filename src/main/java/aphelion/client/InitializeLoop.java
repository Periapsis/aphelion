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
import aphelion.client.graphics.RenderDelay;
import aphelion.client.graphics.nifty.*;
import aphelion.client.graphics.screen.Camera;
import aphelion.client.graphics.screen.CameraNiftyController;
import aphelion.client.graphics.screen.NiftyCameraImpl;
import aphelion.client.graphics.world.MapEntities;
import aphelion.client.graphics.world.StarField;
import aphelion.client.net.NetworkedGame;
import aphelion.client.net.SingleGameConnection;
import aphelion.client.resource.DBNiftyResourceLocation;
import aphelion.shared.event.TickEvent;
import aphelion.shared.event.TickedEventLoop;
import aphelion.shared.event.promise.AbstractPromise;
import aphelion.shared.event.promise.All;
import aphelion.shared.event.promise.PromiseException;
import aphelion.shared.event.promise.PromiseRejected;
import aphelion.shared.event.promise.PromiseResolved;
import aphelion.shared.gameconfig.LoadYamlTask;
import aphelion.shared.map.MapClassic;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.resource.Asset;
import aphelion.shared.resource.DownloadAssetsTask;
import aphelion.shared.resource.ResourceDB;
import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.nulldevice.NullSoundDevice;
import de.lessvoid.nifty.renderer.lwjgl.input.LwjglInputSystem;
import de.lessvoid.nifty.renderer.lwjgl.render.LwjglRenderDevice;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;
import de.lessvoid.nifty.spi.time.impl.AccurateTimeProvider;
import de.lessvoid.nifty.tools.resourceloader.ClasspathLocation;
import de.lessvoid.nifty.tools.resourceloader.NiftyResourceLoader;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.lwjgl.opengl.Display;
import org.newdawn.slick.Color;

/**
 * Download assets, load assets, etc.
 * @author Joris
 */
public class InitializeLoop implements TickEvent
{
        private static final Logger log = Logger.getLogger("aphelion.client");
        
        final ResourceDB resourceDB;
        final TickedEventLoop loop;
        
        private boolean initComplete = false;
        final Camera loadingCamera;
        final StarField loadingStarfield;
        int loadingCameraY;
        
        // Network:
        private boolean failure = false;
        final SingleGameConnection connection;
        final NetworkedGame networkedGame;
        DownloadAssetsTask downloadAssetsTask;
        
        // Map:
        MapClassic mapClassic;
        
        // Physics:
        PhysicsEnvironment physicsEnv;
        
        // Input: 
        LwjglInputSystem inputSystem;
        
        // Graphics:
        StarField stars;
        MapEntities mapEntities;
        RenderDelay renderDelay;
        
        // Screen Graphics
        Nifty nifty;
        Screen mainScreen;
        NiftyCameraImpl niftyCameraImpl;
        

        public InitializeLoop(ConnectLoop connectLoop)
        {
                this.resourceDB = connectLoop.resourceDB;
                this.loop =  connectLoop.loop;
                this.connection =  connectLoop.connection;
                this.networkedGame =  connectLoop.networkedGame;
                this.loadingCamera = connectLoop.loadingCamera;
                this.loadingStarfield = connectLoop.loadingStarfield;
                this.loadingCameraY = connectLoop.loadingCameraY;
        }
        
        private AbstractPromise downloadAssets()
        {
                downloadAssetsTask = new DownloadAssetsTask();
                return loop.addWorkerTask(downloadAssetsTask, networkedGame.getRequiredAssets());
        }
        
        private final PromiseResolved loadAssets = new PromiseResolved()
        {
                @Override
                public Object resolved(Object ret) throws PromiseException
                {
                        downloadAssetsTask = null;
                        
                        List<Asset> assets = (List<Asset>) ret;
                                
                        for (Asset ass : assets)
                        {
                                try
                                {
                                        // ass.file is now valid
                                        // (the DownloadAssetsTask fails even if only 1 asset fails)
                                        resourceDB.addZip(ass.file);
                                }
                                catch (IOException ex)
                                {
                                        log.log(Level.SEVERE, "Received an asset file from the server that is not a zip", ex);
                                        throw new PromiseException("Received an asset file from the server that is not a zip", ex);
                                }
                        }

                        return new All(loop, loadMap(), loadConfig());
                }
        };
        
        private AbstractPromise loadMap()
        {
                return loop.addWorkerTask(new MapClassic.LoadMapTask(resourceDB, true), networkedGame.mapResource)
                .then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                log.log(Level.INFO, "Map loaded");
                                mapClassic = (MapClassic) ret;
                                // should work fine for lvl files < 2 GiB
                                stars = new StarField((int) mapClassic.getLevelSize(), resourceDB);
                                return mapClassic;
                        }
                });
        }
        
        private AbstractPromise loadConfig()
        {
                return loop.addWorkerTask(new LoadYamlTask(resourceDB), Collections.unmodifiableList(networkedGame.gameConfigResources))
                .then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                 log.log(Level.INFO, "Game config read");

                                 // ret is List<LoadYamlTask.Return>
                                 return ret;
                        }
                });
        }
        
        private void initializePhysics(List<LoadYamlTask.Return> loadYamlResult)
        {
                physicsEnv = new PhysicsEnvironment(false, mapClassic);
                mapEntities.setPhysicsEnv(physicsEnv);

                for (LoadYamlTask.Return yamlResult : loadYamlResult)
                {
                        physicsEnv.loadConfig(
                                physicsEnv.getTick()-physicsEnv.econfig.HIGHEST_DELAY,
                                yamlResult.fileIdentifier, 
                                yamlResult.yamlDocuments);
                }
                
                renderDelay = new RenderDelay(physicsEnv, mapEntities);
                renderDelay.subscribeListeners(connection);
        }
        
        private void initializeNifty() throws PromiseException
        {
                inputSystem = new LwjglInputSystem();
                try
                {
                        inputSystem.startup();
                }
                catch (Exception ex)
                {
                        throw new Error(ex);
                }
                
                nifty = new Nifty(new LwjglRenderDevice(), new NullSoundDevice(), inputSystem, new AccurateTimeProvider());
                NiftyResourceLoader niftyResourceLoader = nifty.getResourceLoader();
                niftyResourceLoader.removeAllResourceLocations();
                // nifty first tries stuff on the class path (needed for its internal files)
                niftyResourceLoader.addResourceLocation(new ClasspathLocation());
                // Then try the same reference as a resource key 
                // (add this second so that zones can not override nifty build-ins)
                niftyResourceLoader.addResourceLocation(new DBNiftyResourceLocation(resourceDB));


                TriggerOnShowEffect.registerEffect(nifty);
                SpriteAnimationEffect.registerEffect(nifty);
                BackgroundColorSpriteEffect.registerEffect(nifty);
                BackgroundColorAnimated.registerEffect(nifty);
                ClockTextEffect.registerEffect(nifty);
                niftyCameraImpl = new NiftyCameraImpl(resourceDB, mapEntities, mapClassic, stars);
                CameraNiftyController.registerControl(nifty, niftyCameraImpl);
                
                boolean first = true;

                /*{
                        Logger logger = Logger.getLogger("de.lessvoid.nifty");
                        logger.setLevel(Level.FINER);

                        ConsoleHandler handler = new ConsoleHandler();
                        handler.setLevel(Level.FINER);
                        logger.addHandler(handler);
                }*/

                for (String res : networkedGame.niftyGuiResources)
                {
                        if (first)
                        {
                                nifty.fromXmlWithoutStartScreen(res);
                                first = false;
                        }
                        else
                        {
                                nifty.addXml(res);
                        }
                }
                
                mainScreen = nifty.getScreen("aphelion-main");
                if (mainScreen == null)
                {
                        throw new PromiseException("Missing nifty-gui screen: aphelion-main");
                }
                ScreenController screenControl = mainScreen.getScreenController();
                if (screenControl instanceof MainScreenController)
                {
                        ((MainScreenController) screenControl).aphelionBind(networkedGame);
                }
                nifty.gotoScreen("aphelion-main");
        }
        
        private final PromiseResolved assetsLoaded = new PromiseResolved()
        {
                @Override
                public Object resolved(Object ret_) throws PromiseException
                {
                        List ret = (List) ret_;
                        // argument 0 is the result of loadMap()
                        // argument 1 is the result of loadConfig()

                        initializePhysics((List<LoadYamlTask.Return>) ret.get(1));
                        initializeNifty();
                        
                        // The server will now sync the physics data, send chats, et cetera
                        networkedGame.arenaLoaded(physicsEnv);

                        return null;
                }
        };
        
        private final PromiseResolved initializeComplete = new PromiseResolved()
        {
                @Override
                public Object resolved(Object ret) throws PromiseException
                {
                        initComplete = true;
                        return null;
                }
        };
        
        private final PromiseRejected initializeError = new PromiseRejected()
        {
                @Override
                public void rejected(PromiseException error)
                {
                        downloadAssetsTask = null;
                        failure = true;
                        log.log(Level.SEVERE, "Error while loading the arena", error);
                        JOptionPane.showMessageDialog(Display.getParent(), 
                                                      "Error while loading the arena:\n" 
                                                      + error.getMessage() 
                                                      + "\nSee log for further details");
                }
        };
        
        /** Download assets, parse them and set up the graphics, physics, input, etc.
         * Returns when completed
         * @return false if the loading failed and the game should quit (or go back to the menu)
         */
        public boolean loop()
        {
                mapEntities = new MapEntities(resourceDB);
                networkedGame.addActorListener(mapEntities, true);
                
                loop.addTickEvent(mapEntities);
                loop.addLoopEvent(mapEntities);
                
                loadingCamera.setPosition(0, loadingCameraY);
                
                // Asynchronous:
                downloadAssets().then(loadAssets).then(assetsLoaded).then(initializeComplete).then(initializeError);
                
                loop.addTickEvent(this);
                try
                {
                        while (!loop.isInterruped())
                        {
                                Display.update();
                                if (Display.isCloseRequested())
                                {
                                        log.log(Level.WARNING, "Close requested in game loop");
                                        loop.interrupt();
                                        break;
                                }

                                Client.initGL();

                                if (Client.wasResized() && nifty != null)
                                {
                                        nifty.resolutionChanged();
                                }

                                loop.loop(); // logic, call Promise callbacks

                                if (failure)
                                {
                                        return false;
                                }

                                if (networkedGame.isDisconnected())
                                {
                                        failure = true;
                                        return false;
                                }

                                if (initComplete && networkedGame.isReady())
                                {
                                        // continue on to GameLoop
                                        return true;
                                }

                                loadingStarfield.render(loadingCamera);

                                if (downloadAssetsTask != null && downloadAssetsTask.getTotalFiles() > 0)
                                {
                                        long totalBytes = downloadAssetsTask.getTotalBytes();
                                        long completedBytes = downloadAssetsTask.getCompletedBytes();
                                        double percentageComplete = (double) completedBytes / (double) totalBytes * 100.0;
                                        double speedMiB = downloadAssetsTask.getSpeed() / 1024.0;

                                        String line1 = String.format("Downloading: %2.1f%% (%3.1f KiB/s)", 
                                                                     percentageComplete,
                                                                     speedMiB);

                                        String line2 = String.format("File %d of %d; %d of %d bytes", 
                                                                     downloadAssetsTask.getVerifiedFiles() + 1,
                                                                     downloadAssetsTask.getTotalFiles(),
                                                                     completedBytes,
                                                                     totalBytes);

                                        // use the default slick font

                                        int line1_width = Graph.g.getFont().getWidth(line1);
                                        int line2_width = Graph.g.getFont().getWidth(line2);
                                        int line_height = Graph.g.getFont().getLineHeight();

                                        float y = loadingCamera.dimension.y * 0.75f;
                                        Graph.g.setColor(Color.white);
                                        Graph.g.drawString(line1, loadingCamera.dimensionHalf.x - line1_width / 2, y);
                                        Graph.g.drawString(line2, loadingCamera.dimensionHalf.x - line2_width / 2, y + line_height);
                                }
                                else
                                {
                                        String line = "Loading...";
                                        int line_width = Graph.g.getFont().getWidth(line);
                                        Graph.g.setColor(Color.white);
                                        Graph.g.drawString(line, loadingCamera.dimensionHalf.x - line_width / 2, loadingCamera.dimension.y * 0.75f);
                                }

                                loadingCamera.setPosition(0, loadingCameraY);

                                Display.sync(60);
                        }

                        return false;
                }
                finally
                {
                        loop.removeTickEvent(this);
                }
        }

        @Override
        public void tick(long tick)
        {
                loadingCameraY -= 5;
        }
}
