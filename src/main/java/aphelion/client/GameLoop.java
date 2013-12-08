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


import aphelion.client.graphics.nifty.chat.AphelionChatControl;
import aphelion.client.graphics.Graph;
import aphelion.client.graphics.RenderDelay;
import aphelion.client.graphics.nifty.*;
import aphelion.client.net.NetworkedGame;
import aphelion.client.net.SingleGameConnection;
import aphelion.shared.resource.ResourceDB;
import aphelion.client.graphics.screen.Gauges;
import aphelion.client.graphics.screen.NiftyCameraImpl;
import aphelion.client.graphics.world.*;
import aphelion.client.graphics.world.event.ActorDiedTracker;
import aphelion.client.graphics.world.event.EventTracker;
import aphelion.client.graphics.world.event.ProjectileExplosionTracker;
import aphelion.shared.event.TickedEventLoop;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.events.pub.EventPublic;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.swissarmyknife.Point;
import aphelion.shared.map.MapClassic;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.events.Event;
import aphelion.shared.physics.events.pub.ActorDiedPublic;
import aphelion.shared.physics.events.pub.ProjectileExplosionPublic;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.swissarmyknife.AttachmentConsumer;
import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.controls.Controller;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.elements.render.TextRenderer;
import de.lessvoid.nifty.renderer.lwjgl.input.LwjglInputSystem;
import de.lessvoid.nifty.screen.Screen;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.lwjgl.opengl.Display;

/**
 *
 * @author Joris
 */
public class GameLoop
{
        private static final Logger log = Logger.getLogger("aphelion.client");
        
        private final ResourceDB resourceDB;
        private final TickedEventLoop loop;
        private boolean connectionError = false;
        
        // Network:
        private final SingleGameConnection connection;
        private final NetworkedGame networkedGame;
        private LocalChat localChat;
        
        // Input:
        private final LwjglInputSystem inputSystem;
        private MyKeyboard myKeyboard;
        
        // Physics:
        private final PhysicsEnvironment physicsEnv;
        private ActorPublic localActor;
        
        // Map:
        private final MapClassic mapClassic;
        
        // Graphics:
        private final StarField stars;
        private final MapEntities mapEntities;
        private final RenderDelay renderDelay;
        
        private static final AttachmentConsumer<EventPublic, EventTracker> eventTrackers 
                = new AttachmentConsumer<>(Event.attachmentManager);
        
        // Screen Graphics
        private final Nifty nifty;
        private final NiftyCameraImpl niftyCameraImpl;
        private final Screen mainScreen;   
        private EnergyBar[] energyBars;
        private Element[] energyTexts;
        private Gauges gauges;
        private AphelionChatControl[] chatLocals;
        private Element gameMenuPopup;
       
        
        // Graphics statistics
        private long frames;
        private long lastFrameReset;
        private long lastFps;

        public GameLoop(InitializeLoop initializeLoop)
        {
                this.resourceDB = initializeLoop.resourceDB;
                this.loop = initializeLoop.loop;
                this.connection = initializeLoop.connection;
                this.networkedGame = initializeLoop.networkedGame;
                this.mapClassic = initializeLoop.mapClassic;
                this.physicsEnv = initializeLoop.physicsEnv;
                this.inputSystem = initializeLoop.inputSystem;
                this.stars = initializeLoop.stars;
                this.mapEntities = initializeLoop.mapEntities;
                this.renderDelay = initializeLoop.renderDelay;
                this.nifty = initializeLoop.nifty;
                this.mainScreen = initializeLoop.mainScreen;
                this.niftyCameraImpl = initializeLoop.niftyCameraImpl;
        }
        
        public boolean isConnectionError()
        {
                return connectionError;
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
                gameMenuPopup = nifty.createPopup("gameMenuPopup");
        }
        
        public void loop()
        {
                lookUpNiftyElements();

                localChat = new LocalChat(networkedGame, Collections.unmodifiableList(Arrays.asList(chatLocals)));
                localChat.subscribeListeners(networkedGame, mainScreen);
                
                
                
                lastFrameReset = System.nanoTime();
                frames = 60;
                
                boolean tickingPhysics = false;
                
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
                                if (renderDelay != null)
                                {
                                        renderDelay.init(localActor);
                                }
                        }
                        
                        if (myKeyboard == null && localActor != null)
                        {
                                myKeyboard = new MyKeyboard(
                                        inputSystem, 
                                        mainScreen, 
                                        networkedGame, 
                                        physicsEnv,
                                        localActor, 
                                        physicsEnv.getGlobalConfigStringList(0, "ships"),
                                        gameMenuPopup
                                );
                                loop.addTickEvent(myKeyboard);
                                
                                GameMenuController gameMenuControl = gameMenuPopup.getControl(GameMenuController.class);
                                if (gameMenuControl != null)
                                {
                                        gameMenuControl.aphelionBind(networkedGame);
                                }
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
                        
                        if (Client.wasResized())
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

                        updateGraphicsFromPhysics();

                        if (localShip == null || localActor == null)
                        {
                                niftyCameraImpl.setDefaultCameraPosition(8192, 8192);
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

                                niftyCameraImpl.setDefaultCameraPosition(localShip.pos);
                        }

                        nifty.render(false);

                        
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
                
                if (renderDelay == null)
                {
                        actorShip.renderingAt_tick = physicsEnv.getTick();
                }
                else
                {
                        renderDelay.calculateRenderAtTick(actorShip);
                }
                
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
                        actorShip.setRealPositionFromPhysics(actorPos.x, actorPos.y);
                        
                        if (actorShip.isLocalPlayer())
                        {
                                actorShip.setPositionFromPhysics(actorPos.x, actorPos.y);
                        }
                        else
                        {
                                actorShip.setPositionFromPhysics(actorPos.smooth_x, actorPos.smooth_y);
                        }
                        actorShip.setRotationFromPhysics(actorPos.rot_snapped);
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
                
                if (renderDelay == null)
                {
                        projectile.renderingAt_tick = physicsEnv.getTick();
                }
                else
                {
                        renderDelay.calculateRenderAtTick(projectile);
                }
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
