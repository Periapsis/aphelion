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


import aphelion.client.graphics.world.event.ProjectileExplosionTracker;
import aphelion.client.graphics.Graph;
import aphelion.client.net.NetworkedGame;
import aphelion.client.net.SingleGameConnection;
import aphelion.shared.resource.ResourceDB;
import aphelion.client.graphics.screen.Camera;
import aphelion.client.graphics.screen.EnergyBar;
import aphelion.client.graphics.screen.Gauges;
import aphelion.client.graphics.screen.StatusDisplay;
import aphelion.client.graphics.world.*;
import aphelion.client.graphics.world.event.ActorDiedTracker;
import aphelion.client.graphics.world.event.EventTracker;
import aphelion.client.resource.AsyncTexture;
import aphelion.shared.event.TickEvent;
import aphelion.shared.event.TickedEventLoop;
import aphelion.shared.event.promise.*;
import aphelion.shared.gameconfig.GCStringList;
import aphelion.shared.gameconfig.LoadYamlTask;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.events.pub.EventPublic;
import aphelion.shared.physics.events.pub.ProjectileExplosionPublic;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.physics.WEAPON_SLOT;
import aphelion.shared.swissarmyknife.Point;
import aphelion.shared.map.MapClassic;
import aphelion.shared.map.tile.TileType;
import aphelion.shared.physics.events.Event;
import aphelion.shared.physics.events.pub.ActorDiedPublic;
import aphelion.shared.swissarmyknife.AttachmentConsumer;
import java.lang.ref.WeakReference;
import java.util.Arrays;

import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.newdawn.slick.Color;
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
        
        // Input:
        private MyKeyboard myKeyboard;
        
        // Physics:
        private PhysicsEnvironment physicsEnv;
        private ActorPublic localActor;
        private GCStringList ships;
        
        // Map:
        private MapClassic mapClassic;
        
        // Graphics:        
        private StarField stars;
        private Camera mainCamera;
        private Camera radarCamera;
        private Camera bigMapCamera;
        private MapEntities mapEntities;
        private static final AttachmentConsumer<EventPublic, EventTracker> eventTrackers 
                = new AttachmentConsumer<>(Event.attachmentManager);
        
        // Screen Graphics
        private EnergyBar energyBar;
        private StatusDisplay statusDisplay;
        private Gauges gauges;
       
        
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
        
        public void loop()
        {
                AbstractPromise loadMapPromise = 
                loop.addWorkerTask(new MapClassic.LoadMapTask(resourceDB, true), "singleplayer.map")
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
                loop.addWorkerTask(new LoadYamlTask(resourceDB), Arrays.asList(new String[] {"singleplayer.gameconfig"}))
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
                
                
                All allLoadedPromise = new All(loop, loadMapPromise, loadConfigPromise);
                
                allLoadedPromise.then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret_) throws PromiseException
                        {
                                List ret = (List) ret_;
                                List<LoadYamlTask.Return> loadYamlResult = (List<LoadYamlTask.Return>) ret.get(1);
                                
                                physicsEnv = new PhysicsEnvironment(false, mapClassic);
                                mapEntities.setPhysicsEnv(physicsEnv);
                                loop.addTickEvent(myKeyboard);

                                for (LoadYamlTask.Return yamlResult : loadYamlResult)
                                {
                                        physicsEnv.loadConfig(
                                                -PhysicsEnvironment.TOTAL_HISTORY,
                                                yamlResult.fileIdentifier, 
                                                yamlResult.yamlDocuments);
                                }

                                ships = physicsEnv.getGlobalConfigStringList(0, "ships");

                                networkedGame.arenaLoaded(physicsEnv, mapEntities);
                                loadedResources = true;
                                
                                return null;
                        }
                }, new PromiseRejected()
                {
                        @Override
                        public void rejected(PromiseException error)
                        {
                                log.log(Level.SEVERE, "Error while loading the arena", error);
                                loop.interrupt();
                                // TODO?
                        }
                });
                
                AsyncTexture loadingTex = resourceDB.getTextureLoader().getTexture("gui.loading.graphics");
                
                myKeyboard = new MyKeyboard();
                
                mainCamera = new Camera(resourceDB);
                
                radarCamera = new Camera(resourceDB);
                radarCamera.setZoom(1/12f);
                radarCamera.setPosition(512 * 16, 512 * 16);
                
                bigMapCamera = new Camera(resourceDB);
                bigMapCamera.setPosition(512 * 16, 512 * 16);
                
                mapEntities = new MapEntities(resourceDB);
                loop.addTickEvent(mapEntities);
                loop.addLoopEvent(mapEntities);
                
                boolean first = true;
                boolean tickingPhysics = false;
                lastFrameReset = System.nanoTime();
                frames = 60;
                
                
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
                        
                        Client.initGL();
                        
                        int displayWidth = Display.getWidth();
                        int displayHeight = Display.getHeight();
                        
                        if (first || Display.wasResized())
                        {
                                mainCamera.setDimension(displayWidth, displayHeight);
                                
                                radarCamera.setDimension((displayHeight / 3.5f), (displayHeight / 3.5f));
                                radarCamera.setScreenPosition(
                                        displayWidth - radarCamera.dimension.x - 8, 
                                        displayHeight - radarCamera.dimension.y - 8);
                                
                                bigMapCamera.setDimension((displayHeight * 0.6f), (displayHeight * 0.6f));
                                //bigMapCamera.setZoom(1/16f);
                                bigMapCamera.setZoom(bigMapCamera.dimension.y / 1024f / 16f); 
                                bigMapCamera.setScreenPosition(
                                        displayWidth - bigMapCamera.dimension.x - 8, 
                                        displayHeight - bigMapCamera.dimension.y - 8);
                        }
                        
                        Keyboard.poll();
                        myKeyboard.pollStates();
                        
                        loop.loop(); // logic
                        
                        if (networkedGame.isDisconnected())
                        {
                                connectionError = true;
                                return;
                        }
                        
                        
                        
                        ActorShip localShip = mapEntities.getLocalShip();
                        
                        
                        if (!networkedGame.isReady())
                        {
                                Image loadingBanner = loadingTex.getCachedImage();
                                if (loadingBanner != null)
                                {
                                        loadingBanner.drawCentered(Display.getWidth() / 2, Display.getHeight() / 2);
                                }
                        }
                        else
                        {
                                loadingTex = null;
                                
                                updateGraphicsFromPhysics();
                                
                                if (localShip == null || localActor == null)
                                {
                                        // TODO spec
                                        mainCamera.setPosition(8192, 8192);
                                        energyBar = null;
                                        statusDisplay = null;
                                        gauges = null;
                                }
                                else
                                {
                                        if (energyBar == null)
                                        {
                                                energyBar = new EnergyBar(resourceDB, localShip);
                                        }
                                        
                                        if(statusDisplay == null)
                                        {
                                        	statusDisplay = new StatusDisplay(resourceDB, localShip);
                                        }
                                        
                                        if(gauges == null)
                                        {
                                        	gauges = new Gauges(resourceDB, localActor);
                                        }
                                        
                                        
                                        mainCamera.setPosition(localShip.pos);
                                }
                                
                                render();
                        }
                        
                        // statistics
                        long now = System.nanoTime();
                        ++frames;
                        long frameTimeDelta = (now - begin) / 1_000_000;
                        
                        Graph.g.setColor(Color.yellow);
                        if (physicsEnv != null)
                        {
                                Graph.g.setFont(Fonts.normal);
                                Graph.g.drawString(String.format("%d (%2dms) %4d %d %3dms",
                                        lastFps, 
                                        frameTimeDelta, 
                                        physicsEnv.getTick(),
                                        physicsEnv.getTimewarpCount(),
                                        networkedGame.getlastRTTNano() / 1000_000L), 0, 0);
                                
                                if (localShip != null)
                                {
                                        String s = "("+((int) localShip.pos.x / 16)+","+((int) localShip.pos.y / 16)+")";
                                        
                                        if (localShip.getActor() != null)
                                        {
                                                s += " " + localShip.getActor().getShip();
                                        }
                                        
                                        Graph.g.drawString(s, 0, 20);
                                }
                        }
                        
                        Display.sync(60);
                        first = false;
                        
                        if (now - lastFrameReset > 1000000000L)
                        {
                                lastFps = frames;
                                frames = 0;
                                lastFrameReset = now;
                        }
                }       
        }
        
        private void render()
        {
                renderCamera(mainCamera);
                
                // GUI
                if (energyBar != null)
                {
                        energyBar.render(mainCamera);
                }
                if(statusDisplay != null)
                {
                	statusDisplay.render(mainCamera);
                }
                if(gauges != null)
                {
                	gauges.render(mainCamera, myKeyboard.multiFireGun);
                }
                
                if (myKeyboard.altMap)
                {
                        bigMapCamera.renderCameraBox();
                        bigMapCamera.renderTiles(this.mapClassic, TileType.TILE_LAYER.PLAIN);
                        renderCamera(bigMapCamera);
                }
                else
                {
                        radarCamera.setPosition(mainCamera.pos);
                        radarCamera.clipPosition(0, 0, 1024*16, 1024*16);
                        renderCamera(radarCamera);
                        
                        Graph.g.setColor(Color.white);
                        String text = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + ":" + Calendar.getInstance().get(Calendar.MINUTE);
                        
                        Graph.g.drawString(text, 
                                Display.getWidth() - Graph.g.getFont().getWidth(text) - 5, 
                                radarCamera.screenPosY - Graph.g.getFont().getLineHeight());
                }
        }
        
        @SuppressWarnings("unchecked")
        private void renderCamera(Camera camera)
        {
                GL11.glColor3f(1, 1, 1);
                
                if (camera != this.mainCamera)
                {
                        // draw camera edges and background unless this is the main camera
                        camera.renderCameraBox();
                }
                
                if (camera == mainCamera)
                {
                        camera.setGraphicsClip();
                        stars.render(camera);
                }
                
                camera.renderEntities(mapEntities.animations(RENDER_LAYER.BACKGROUND, camera));
                camera.renderTiles(this.mapClassic, TileType.TILE_LAYER.PLAIN);
                // rendered in a seperate iteration so that we do not have to switch between textures as often
                // (tile set is one big texture)
                camera.renderTiles(this.mapClassic, TileType. TILE_LAYER.ANIMATED);
                camera.renderEntities(mapEntities.animations(RENDER_LAYER.AFTER_TILES, camera));
                camera.renderEntities(mapEntities.projectiles(false));
                camera.renderEntities(mapEntities.animations(RENDER_LAYER.AFTER_PROJECTILES, camera));
                camera.renderEntities(mapEntities.shipsNoLocal());
                camera.renderEntities(mapEntities.animations(RENDER_LAYER.AFTER_SHIPS, camera));
                camera.renderEntity(mapEntities.getLocalShip());
                camera.renderTiles(this.mapClassic, TileType.TILE_LAYER.PLAIN_OVER_SHIP);
                camera.renderEntities(mapEntities.animations(RENDER_LAYER.AFTER_LOCAL_SHIP, camera));
        }
        
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

        
        private class MyKeyboard implements TickEvent
        {
                private long tick;
                private boolean up, down, left, right, boost;
                private boolean altMap;
                private boolean multiFireGun;
                private boolean fireGun;
                private boolean fireBomb;
                private boolean fireMine;
                private boolean fireThor;
                private boolean fireBurst;
                private boolean fireRepel;
                private boolean fireDecoy;
                private boolean fireRocket;
                private boolean fireBrick;
                
                private long lastShipChangeRequest;
                
                public void pollStates()
                {
                        // Keyboard.poll(); should have just been called.
                        
                        if (!Display.isActive())
                        {
                                up = false;
                                down = false;
                                left = false;
                                right = false;
                                boost = false;
                                altMap = false;
                                fireGun = false;
                                fireBomb = false;
                                fireThor = false;
                                fireBurst = false;
                                fireRepel = false;
                                fireDecoy = false;
                                fireRocket = false;
                                fireBrick = false;
                                return;
                        }
                        
                        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) 
                                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                        
                        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) 
                                || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
                        
                        up    = Keyboard.isKeyDown(Keyboard.KEY_UP);
                        down  = Keyboard.isKeyDown(Keyboard.KEY_DOWN);
                        left  = Keyboard.isKeyDown(Keyboard.KEY_LEFT);
                        right = Keyboard.isKeyDown(Keyboard.KEY_RIGHT);
                        boost = shift;
                        
                        altMap = Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
                        
                        fireGun = !shift && ctrl;
                        fireBomb = !shift && Keyboard.isKeyDown(Keyboard.KEY_TAB);
                        fireMine = shift && Keyboard.isKeyDown(Keyboard.KEY_TAB);
                        fireThor = Keyboard.isKeyDown(Keyboard.KEY_F6);
                        fireBurst = Keyboard.isKeyDown(Keyboard.KEY_DELETE) && shift;
                        fireRepel = shift && ctrl;
                        fireDecoy = Keyboard.isKeyDown(Keyboard.KEY_F5);
                        fireRocket = Keyboard.isKeyDown(Keyboard.KEY_F3);
                        fireBrick = Keyboard.isKeyDown(Keyboard.KEY_F4);
                        
                        
                        // keys as events:
                        
                        while (Keyboard.next())
                        {
                                int key = Keyboard.getEventKey();
                                char chr = Keyboard.getEventCharacter();
                                
                                if (!Keyboard.getEventKeyState())
                                {
                                        // released a key
                                        if (key == Keyboard.KEY_DELETE)
                                        {
                                                multiFireGun = !multiFireGun;
                                        }
                                }
                                
                                if (!Keyboard.isRepeatEvent())
                                {
                                        if (!shift && chr >= '0' && chr <= '9' && 
                                                tick - lastShipChangeRequest > 10 &&
                                                localActor.canChangeShip()
                                                )
                                        {
                                                int s = chr - '0' - 1;

                                                if (s >= 0 && s < ships.getValuesLength())
                                                {
                                                        String ship = ships.get(s);
                                                        if (!ship.equals(localActor.getShip()))
                                                        {
                                                                networkedGame.sendCommand("ship", ship);
                                                                lastShipChangeRequest = tick;
                                                        }
                                                }
                                                continue;
                                        }
                                }
                        }
                }
                
                
                @Override
                public void tick(long tick)
                {
                        this.tick = tick;
                        // PhysicsEnvironment should have ticked before this one.
                        int localPid;
                        
                        if (!networkedGame.isReady())
                        {
                                // esc to cancel connecting?
                                return;
                        }
                        
                        localPid = networkedGame.getMyPid();
                        
                        if (localActor == null || localActor.isRemoved())
                        {
                                return;
                        }
                        
                        if (up || down || left || right)
                        {
                                boost = boost && (up || down);
                                // do not bother sending boost over the network if we can not use it 
                                // (to prevent unnecessary timewarps)
                                boost = boost && localActor.canBoost(); 
                                PhysicsMovement move = PhysicsMovement.get(up, down, left, right, boost);
                                physicsEnv.actorMove(physicsEnv.getTick(), localPid, move);
                                networkedGame.sendMove(physicsEnv.getTick(), move);
                        }
                        
                        if (fireGun)  { tryWeapon(this.multiFireGun ? WEAPON_SLOT.GUN_MULTI : WEAPON_SLOT.GUN , localActor); }
                        if (fireBomb) { tryWeapon(WEAPON_SLOT.BOMB, localActor); }
                        if (fireMine) { tryWeapon(WEAPON_SLOT.MINE, localActor); }
                        if (fireThor) { tryWeapon(WEAPON_SLOT.THOR, localActor); }
                        if (fireBurst) { tryWeapon(WEAPON_SLOT.BURST, localActor); }
                        if (fireRepel) { tryWeapon(WEAPON_SLOT.REPEL, localActor); }
                        if (fireDecoy) { tryWeapon(WEAPON_SLOT.DECOY, localActor); }
                        if (fireRocket) { tryWeapon(WEAPON_SLOT.ROCKET, localActor); }
                        if (fireBrick) { tryWeapon(WEAPON_SLOT.BRICK, localActor); }
                        
                }
                
                private void tryWeapon(WEAPON_SLOT weapon, ActorPublic localActor)
                {
                        if (!localActor.canFireWeapon(weapon))
                        {
                                return;
                        }
                        
                        PhysicsShipPosition weaponHint = new PhysicsShipPosition();
                        localActor.getPosition(weaponHint);
                        
                        physicsEnv.actorWeapon(
                                physicsEnv.getTick(), 
                                networkedGame.getMyPid(), 
                                weapon, 
                                true, weaponHint.x, weaponHint.y,
                                weaponHint.x_vel, weaponHint.y_vel,
                                weaponHint.rot_snapped);
                        
                        networkedGame.sendActorWeapon(physicsEnv.getTick(), weapon, weaponHint);
                }
                
        }
}
