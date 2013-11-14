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
import aphelion.client.graphics.nifty.*;
import aphelion.client.net.NetworkedGame;
import aphelion.client.net.SingleGameConnection;
import aphelion.shared.resource.ResourceDB;
import aphelion.client.graphics.screen.CameraNiftyController;
import aphelion.client.graphics.screen.Gauges;
import aphelion.client.graphics.screen.Camera;
import aphelion.client.graphics.screen.CameraNiftyController.CameraForNifty;
import aphelion.client.graphics.world.*;
import aphelion.client.graphics.world.event.ActorDiedTracker;
import aphelion.client.graphics.world.event.EventTracker;
import aphelion.client.graphics.world.event.ProjectileExplosionTracker;
import aphelion.client.resource.AsyncTexture;
import aphelion.client.resource.DBNiftyResourceLocation;
import aphelion.shared.event.TickedEventLoop;
import aphelion.shared.event.promise.*;
import aphelion.shared.gameconfig.LoadYamlTask;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.events.pub.EventPublic;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.swissarmyknife.Point;
import aphelion.shared.map.MapClassic;
import aphelion.shared.map.tile.TileType;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.events.Event;
import aphelion.shared.physics.events.pub.ActorDiedPublic;
import aphelion.shared.physics.events.pub.ProjectileExplosionPublic;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.resource.Asset;
import aphelion.shared.resource.DownloadAssetsTask;
import aphelion.shared.swissarmyknife.AttachmentConsumer;
import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.controls.Controller;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.elements.render.TextRenderer;
import de.lessvoid.nifty.nulldevice.NullSoundDevice;
import de.lessvoid.nifty.renderer.lwjgl.input.LwjglInputSystem;
import de.lessvoid.nifty.renderer.lwjgl.render.LwjglRenderDevice;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.spi.time.impl.AccurateTimeProvider;
import de.lessvoid.nifty.tools.resourceloader.ClasspathLocation;
import de.lessvoid.nifty.tools.resourceloader.NiftyResourceLoader;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.newdawn.slick.Image;

/**
 *
 * @author Joris
 */
public class GameLoop
{
        private static final Logger log = Logger.getLogger("aphelion.client");
        
        private final ResourceDB resourceDB;
        private final TickedEventLoop loop;
        private boolean loadedResources = false;
        private boolean connectionError = false;
        
        // Network:
        private final SingleGameConnection connection;
        private final NetworkedGame networkedGame;
        private LocalChat localChat;
        
        // Input:
        private LwjglInputSystem inputSystem;
        private MyKeyboard myKeyboard;
        
        // Physics:
        private PhysicsEnvironment physicsEnv;
        private ActorPublic localActor;
        
        // Map:
        private MapClassic mapClassic;
        
        // Graphics:
        private StarField stars;
        private MapEntities mapEntities;
        private static final AttachmentConsumer<EventPublic, EventTracker> eventTrackers 
                = new AttachmentConsumer<>(Event.attachmentManager);
        
        // Screen Graphics
        private Nifty nifty;
        private final Point defaultCameraPosition = new Point();
        private Screen mainScreen;        
        private EnergyBar[] energyBars;
        private Element[] energyTexts;
        private Gauges gauges;
        private AphelionChatControl[] chatLocals;
       
        
        // Graphics statistics
        private long frames;
        private long lastFrameReset;
        private long lastFps;
        
        final RENDER_DELAY_METHOD renderDelayMethod = RENDER_DELAY_METHOD.MINIMIZE_DELAY_CHANGES;

        public GameLoop(ResourceDB resourceDB, TickedEventLoop loop, SingleGameConnection connection, NetworkedGame networkedGame)
        {
                this.resourceDB = resourceDB;
                this.loop = loop;
                this.connection = connection;
                this.networkedGame = networkedGame;
        }
        
        public boolean isLoadingResources()
        {
                return !loadedResources;
        }
        
        public boolean isConnectionError()
        {
                return connectionError;
        }
        
        private void initLoop()
        {
                AbstractPromise downloadPromise = loop.addWorkerTask(new DownloadAssetsTask(), networkedGame.getRequiredAssets());
                
                downloadPromise.then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
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
                                
                                AbstractPromise loadMapPromise = 
                                loop.addWorkerTask(new MapClassic.LoadMapTask(resourceDB, true), networkedGame.mapResource)
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

                                AbstractPromise loadConfigPromise = 
                                loop.addWorkerTask(new LoadYamlTask(resourceDB), Collections.unmodifiableList(networkedGame.gameConfigResources))
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

                                return new All(loop, loadMapPromise, loadConfigPromise);
                        }
                })
                .then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret_) throws PromiseException
                        {
                                List ret = (List) ret_;
                                List<LoadYamlTask.Return> loadYamlResult = (List<LoadYamlTask.Return>) ret.get(1);
                                
                                physicsEnv = new PhysicsEnvironment(false, mapClassic);
                                mapEntities.setPhysicsEnv(physicsEnv);

                                for (LoadYamlTask.Return yamlResult : loadYamlResult)
                                {
                                        physicsEnv.loadConfig(
                                                -PhysicsEnvironment.TOTAL_HISTORY,
                                                yamlResult.fileIdentifier, 
                                                yamlResult.yamlDocuments);
                                }
                                
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
                                        try
                                        {
                                                nifty.validateXml(res);
                                        }
                                        catch (Exception ex)
                                        {
                                                throw new PromiseException(ex);
                                        }
                                        
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
                                
                                networkedGame.arenaLoaded(physicsEnv, mapEntities);
                                loadedResources = true;
                                
                                mainScreen = nifty.getScreen("aphelion-main");
                                if (mainScreen == null)
                                {
                                        throw new PromiseException("Missing nifty-gui screen: aphelion-main");
                                }
                                nifty.gotoScreen("aphelion-main");
                                
                                lookUpNiftyElements();
                                
                                localChat = new LocalChat(networkedGame.getGameConn(), Collections.unmodifiableList(Arrays.asList(chatLocals)));
                                localChat.subscribeListeners(mainScreen);
                                
                                return null;
                        }
                }).then(new PromiseRejected()
                {
                        @Override
                        public void rejected(PromiseException error)
                        {
                                log.log(Level.SEVERE, "Error while loading the arena", error);
                                loop.interrupt();
                                JOptionPane.showMessageDialog(Display.getParent(), 
                                                              "Error while loading the arena:\n" 
                                                              + error.getMessage() 
                                                              + "\nSee log for further details");
                        }
                });
                
                mapEntities = new MapEntities(resourceDB);
                loop.addTickEvent(mapEntities);
                loop.addLoopEvent(mapEntities);
                
                
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
                
                CameraNiftyController.registerControl(nifty, cameraForNifty);
                SpriteAnimationEffect.registerEffect(nifty);
                BackgroundColorSpriteEffect.registerEffect(nifty);
                ClockTextEffect.registerEffect(nifty);
        }
        
        /** Find all controls that begin with the given prefix by adding up numbers.
         * e.x. findControls("bla", ...) looks for:
         * bla (optional)
         * bla0
         * bla1
         * bla2
         * bla3
         */
        private <T extends Controller> T[] findControls(String elementNamePrefix, Class<T> requestedControlClass, T[] emptyArray)
        {
                LinkedList<Controller> ret = new LinkedList<>();
                
                T control = mainScreen.findControl(elementNamePrefix, requestedControlClass);
                if (control != null)
                {
                        ret.add(control);
                }
                
                int i = 0;
                while (true)
                {
                        control = mainScreen.findControl(elementNamePrefix + "-" + i, requestedControlClass);
                        ++i;
                        
                        if (control == null)
                        {
                                break;
                        }
                        
                        ret.add(control);
                }
                
                return ret.toArray(emptyArray);
        }
        
        private Element[] findElements(String elementNamePrefix)
        {
                LinkedList<Element> ret = new LinkedList<>();
                
                Element element = mainScreen.findElementByName(elementNamePrefix);
                if (element != null)
                {
                        ret.add(element);
                }
                
                int i = 0;
                while (true)
                {
                        element = mainScreen.findElementByName(elementNamePrefix + "-" + i);
                        ++i;
                        
                        if (element == null)
                        {
                                break;
                        }
                        
                        ret.add(element);
                }
                
                return ret.toArray(new Element[]{});
        }
        
        private void lookUpNiftyElements()
        {
                energyBars = findControls("energy-bar", EnergyBar.class, new EnergyBar[]{});
                energyTexts = findElements("energy-text");
                chatLocals = findControls("chat-local", AphelionChatControl.class, new AphelionChatControl[]{});
        }
        
        public void loop()
        {
                initLoop();
                
                lastFrameReset = System.nanoTime();
                frames = 60;
                
                boolean tickingPhysics = false;
                
                AsyncTexture loadingTex = resourceDB.getTextureLoader().getTexture("gui.loading.graphics");
                
                while (!loop.isInterruped())
                {
                        long begin = System.nanoTime();
                        
                        Display.update();
                        if (Display.isCloseRequested())
                        {
                                log.log(Level.WARNING, "Close requested in game loop");
                                loop.interrupt();
                                break;
                        }       
                        
                        Graph.graphicsLoop();
                        
                        if (!tickingPhysics && networkedGame.hasArenaSynced())
                        {
                                // Do not tick() on physics until ArenaSync has been received.
                                // The server tick count is not known until ArenaSync has been received.
                                
                                // must come before keyboard input, which generates new events, therefor prepend
                                loop.prependTickEvent(physicsEnv); 
                                tickingPhysics = true;
                        }
                        
                        if (networkedGame.isReady() && localActor == null)
                        {
                                localActor = physicsEnv.getActor(networkedGame.getMyPid());
                        }
                        
                        if (myKeyboard == null && localActor != null)
                        {
                                myKeyboard = new MyKeyboard(
                                        inputSystem, 
                                        mainScreen, 
                                        networkedGame, 
                                        physicsEnv,
                                        localActor, 
                                        physicsEnv.getGlobalConfigStringList(0, "ships"));
                                loop.addTickEvent(myKeyboard);
                        }
                        
                        if (nifty.update())
                        {
                                log.log(Level.WARNING, "Close by nifty in game loop");
                                loop.interrupt();
                                break;
                        }
                        
                        if (gauges != null && myKeyboard != null)
                        {
                                myKeyboard.poll();
                                gauges.setMultiFireGun(myKeyboard.isMultiFireGun());
                        }
                        
                        Client.initGL();
                        
                        if (Display.wasResized())
                        {
                                nifty.resolutionChanged();
                        }
                        
                        loop.loop(); // logic
                        
                        if (networkedGame.isDisconnected())
                        {
                                connectionError = true;
                                return;
                        }
                        
                        
                        
                        ActorShip localShip = mapEntities.getLocalShip();
                        
                        
                        if (!networkedGame.isReady())
                        {
                                if (loadingTex != null)
                                {
                                        Image loadingBanner = loadingTex.getCachedImage();
                                        if (loadingBanner != null)
                                        {
                                                loadingBanner.drawCentered(Display.getWidth() / 2, Display.getHeight() / 2);
                                        }
                                }
                        }
                        else
                        {
                                loadingTex = null;
                                
                                updateGraphicsFromPhysics();
                                
                                if (localShip == null || localActor == null)
                                {
                                        defaultCameraPosition.set(8192, 8192);
                                }
                                else
                                {
                                        int energy = localShip.getEnergy(false);
                                        float energyProgress = energy / (float) localShip.getMaxEnergy();
                                        
                                        for (EnergyBar energyBar : this.energyBars)
                                        {
                                                energyBar.setProgress(energyProgress);
                                        }
                                        
                                        for (Element energyText : this.energyTexts)
                                        {
                                                energyText.getRenderer(TextRenderer.class).setText(energy + "");
                                        }
                                        
                                        if (gauges == null)
                                        {
                                                assert mainScreen != null;
                                                gauges = new Gauges(mainScreen, localActor);
                                        }
                                        
                                        
                                        defaultCameraPosition.set(localShip.pos);
                                }
                                
                                nifty.render(false);
                        }
                        
                        // statistics
                        long now = System.nanoTime();
                        ++frames;
                        long frameTimeDelta = (now - begin) / 1_000_000;
                        
                        Element dbg = mainScreen == null ? null : mainScreen.findElementByName("debug-info");
                        if (physicsEnv != null && dbg != null)
                        {
                                String text = String.format("%d (%2dms) %4d %d %3dms",
                                        lastFps, 
                                        frameTimeDelta, 
                                        physicsEnv.getTick(),
                                        physicsEnv.getTimewarpCount(),
                                        networkedGame.getlastRTTNano() / 1000_000L);
                                
                                if (localShip != null)
                                {
                                        text += "\n("+((int) localShip.pos.x / 16)+","+((int) localShip.pos.y / 16)+")";
                                        
                                        if (localShip.getActor() != null)
                                        {
                                                text += " " + localShip.getActor().getShip();
                                        }
                                }
                                
                                dbg.getRenderer(TextRenderer.class).setText(text);
                        }
                        
                        Display.sync(60);
                        
                        if (now - lastFrameReset > 1000000000L)
                        {
                                lastFps = frames;
                                frames = 0;
                                lastFrameReset = now;
                        }
                }       
        }
        
        private final CameraForNifty cameraForNifty = new CameraNiftyController.CameraForNifty()
        {

                @Override
                public ResourceDB getResourceDB()
                {
                        return resourceDB;
                }

                @Override
                public void renderCamera(Camera camera, boolean renderStars)
                {
                        GL11.glColor3f(1, 1, 1);
                        
                        camera.setPosition(defaultCameraPosition);
                        camera.clipPosition(0, 0, 1024*16, 1024*16);
                        
                        if (renderStars)
                        {
                                stars.render(camera);
                        }

                        camera.renderEntities(mapEntities.animations(RENDER_LAYER.BACKGROUND, camera));
                        camera.renderTiles(mapClassic, TileType.TILE_LAYER.PLAIN);
                        // rendered in a seperate iteration so that we do not have to switch between textures as often
                        // (tile set is one big texture)
                        camera.renderTiles(mapClassic, TileType. TILE_LAYER.ANIMATED);
                        camera.renderEntities(mapEntities.animations(RENDER_LAYER.AFTER_TILES, camera));
                        camera.renderEntities(mapEntities.projectiles(false));
                        camera.renderEntities(mapEntities.animations(RENDER_LAYER.AFTER_PROJECTILES, camera));
                        camera.renderEntities(mapEntities.shipsNoLocal());
                        camera.renderEntities(mapEntities.animations(RENDER_LAYER.AFTER_SHIPS, camera));
                        camera.renderEntity(mapEntities.getLocalShip());
                        camera.renderTiles(mapClassic, TileType.TILE_LAYER.PLAIN_OVER_SHIP);
                        camera.renderEntities(mapEntities.animations(RENDER_LAYER.AFTER_LOCAL_SHIP, camera));
                }
        };
        
        private void updateGraphicsFromPhysics()
        {
                Iterator<ActorShip> shipIt = mapEntities.shipIterator();
                while (shipIt.hasNext())
                {
                        updateShipFromPhysics(shipIt.next());
                }
                
                Iterator<Projectile> projectileIt = mapEntities.projectileIterator(true);
                while (projectileIt.hasNext())
                {
                        updateProjectileFromPhysics(projectileIt.next());
                }
                
                for (EventPublic event : physicsEnv.eventIterable())
                {
                        if (event instanceof ProjectileExplosionPublic)
                        {
                                ProjectileExplosionTracker tracker = (ProjectileExplosionTracker) eventTrackers.get(event);
                
                                if (tracker == null)
                                {
                                        tracker = new ProjectileExplosionTracker(resourceDB, physicsEnv, mapEntities);
                                        eventTrackers.set(event, tracker);
                                }

                                tracker.update((ProjectileExplosionPublic) event);
                        }
                        else if (event instanceof ActorDiedPublic)
                        {
                                ActorDiedTracker tracker = (ActorDiedTracker) eventTrackers.get(event);
                                
                                if (tracker == null)
                                {
                                        tracker = new ActorDiedTracker(resourceDB, physicsEnv, mapEntities);
                                        eventTrackers.set(event, tracker);
                                }

                                tracker.update((ActorDiedPublic) event);
                        }
                }
        }
        
        private void updateShipFromPhysics(ActorShip actorShip)
        {
                PhysicsShipPosition actorPos = new PhysicsShipPosition();
                Point localActorPos = new Point();
                
                ActorShip localShip = mapEntities.getLocalShip();
                ActorPublic physicsActor = actorShip.getActor();
                
                if (localShip != null && localShip.getActor() != null 
                    && localShip.getActor().getPosition(actorPos))
                {
                        localActorPos.set(actorPos.x, actorPos.y);
                }
                
                if (renderDelayMethod == RENDER_DELAY_METHOD.DISABLED)
                {
                        actorShip.renderDelay.set(0);
                }

                actorShip.calculateRenderAtTick(physicsEnv);
                actorShip.exists = true;

                if (physicsActor.isRemoved(actorShip.renderingAt_tick))
                {
                        actorShip.exists = false;
                }

                if (physicsActor.isDead(actorShip.renderingAt_tick))
                {
                        actorShip.exists = false;
                }  

                if (physicsActor.getHistoricPosition(actorPos, actorShip.renderingAt_tick, true))
                {
                        actorShip.setPositionFromPhysics(actorPos.x, actorPos.y);
                        actorShip.setRotationFromPhysics(actorPos.rot_snapped);
                        actorShip.setNameFromPhysics(physicsActor.getName());
                }
                else
                {
                        actorShip.exists = false;
                }


                if (physicsActor.getPosition(actorPos))
                {
                        actorShip.setShadowPositionFromPhysics(actorPos.x, actorPos.y);
                }

                if (actorShip != localShip)
                {
                        actorShip.updateDistanceToLocal(localActorPos);
                }
        }
        
        private void updateProjectileFromPhysics(Projectile projectile)
        {
                ProjectilePublic.Position projectilePos = new ProjectilePublic.Position();
                PhysicsPoint historicProjectilePos = new PhysicsPoint();
                
                ProjectilePublic physicsProjectile = projectile.getPhysicsProjectile();
                ActorShip localShip = mapEntities.getLocalShip();
                        
                if (physicsProjectile.getPosition(projectilePos))
                {
                        projectile.setShadowPositionFromPhysics(projectilePos.x, projectilePos.y);
                }
                
                if (renderDelayMethod == RENDER_DELAY_METHOD.DISABLED 
                    || renderDelayMethod == RENDER_DELAY_METHOD.PROJECTILE_DISABLED)
                {
                        projectile.renderDelay.set(0);
                }
                else
                {
                        // the closest ship excluding the local one
                        // all actors should have been updated at this point
                        ActorShip closest = mapEntities.findNearestActor(projectile.pos, false);
                        if (closest == null || localShip == null)
                        {
                                projectile.renderDelay.set(0);
                        }
                        else
                        {
                                boolean switchedShip = false;

                                if (projectile.renderDelayBasedOn == null 
                                    || projectile.renderDelayBasedOn.get() != closest)
                                {
                                        switchedShip = true;
                                        projectile.renderDelayBasedOn = new WeakReference<>(closest);
                                }

                                /* p = local player
                                 * r = remote player
                                 * e = entity (projectile)
                                 * r' = the shadow of the player r (the position that is 
                                 *      dead reckoned up the current time)
                                 * 
                                 * δ(x, y) is the distance between x en y
                                 * d(x, y) is the render delay of y on the screen of x
                                 * d(p, e) = 0       if δ(p , e') = 0
                                 * d(p, e) = d(p,r)  if δ(r', e') = 0
                                 * 
                                 * d(p, e) = d(p, r) * max(0, 1 - δ(r', e') / δ(p, r) )
                                 * sqrt(a) / sqrt(b) = sqrt(a / b)
                                 */

                                Point diff = new Point();
                                diff.set(closest.shadowPosition);
                                diff.sub(projectile.shadowPosition);
                                float distSq_rShadow_e = diff.distanceSquared();

                                diff.set(localShip.pos);
                                diff.sub(closest.pos);
                                float distSq_p_r = diff.distanceSquared();

                                double renderDelay = 
                                        closest.renderDelay.get() * 
                                        Math.max(0, 1 - Math.sqrt(distSq_rShadow_e / distSq_p_r));

                                if (Double.isNaN(renderDelay))
                                {
                                        renderDelay = 0;
                                }

                                renderDelay = Math.round(renderDelay);

                                if (renderDelayMethod == RENDER_DELAY_METHOD.MAXIMIZE_LOCAL_TIME)
                                {
                                        projectile.renderDelay.set((int) renderDelay);
                                }
                                else if (renderDelayMethod == RENDER_DELAY_METHOD.MINIMIZE_DELAY_CHANGES)
                                {
                                        Point prevPos = new Point(projectile.shadowPosition_prev);
                                        prevPos.sub(localShip.pos);
                                        
                                        Point nextPos = new Point(projectile.shadowPosition);
                                        nextPos.sub(localShip.pos);
                                        
                                        boolean movingAway = nextPos.distanceSquared() > prevPos.distanceSquared();
                                        
                                        if (movingAway)
                                        {
                                                // if the distance to the local ship is increasing:
                                                // only increase the render delay, do not decrease it.
                                                // unless the calculation has switched to a different ship
                                                if (switchedShip || renderDelay > projectile.renderDelay.getDesired())
                                                {
                                                        projectile.renderDelay.set((int) renderDelay);
                                                }
                                        }
                                        else
                                        {
                                                projectile.renderDelay.set((int) renderDelay);
                                        }
                                }
                        }
                }

                // get the actual current smoothed render delay
                projectile.calculateRenderAtTick(physicsEnv);

                projectile.exists = true;

                if (physicsProjectile.isRemoved(projectile.renderingAt_tick))
                {
                        projectile.exists = false;
                }

                if (physicsProjectile.getHistoricPosition(
                        historicProjectilePos, 
                        projectile.renderingAt_tick, 
                        true))
                {
                        projectile.setPositionFromPhysics(historicProjectilePos.x, historicProjectilePos.y);
                }
                else
                {
                        projectile.exists = false;
                }
        }
}
