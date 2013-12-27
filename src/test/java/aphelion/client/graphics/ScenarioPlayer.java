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
package aphelion.client.graphics;

import aphelion.client.Client;
import aphelion.client.RENDER_LAYER;
import aphelion.client.graphics.screen.Camera;
import aphelion.client.graphics.world.ActorShip;
import aphelion.client.graphics.world.MapEntities;
import aphelion.client.graphics.world.StarField;
import aphelion.server.ServerConfigException;
import aphelion.shared.event.TickedEventLoop;
import aphelion.shared.gameconfig.GameConfig;
import aphelion.shared.map.MapClassic;
import aphelion.shared.map.tile.TileType;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.newdawn.slick.Color;

/**
 *
 * @author Joris
 */
public class ScenarioPlayer
{
        private final TickedEventLoop loop;
        private Scenario scene;
        private ResourceDB resourceDB;
        private Camera camera;
        private MapClassic mapClassic;
        private StarField stars;
        private boolean setup;
        
        // Recreated for each scenario:
        private MapEntities mapEntities;
        private PhysicsEnvironment env;
        
        public ScenarioPlayer(TickedEventLoop loop)
        {
                this.loop = loop;
        }
        
        public void run() throws LWJGLException, IOException, ServerConfigException
        {
                Display.setTitle("Aphelion Scenario");
                Display.setFullscreen(false);
                Display.setVSyncEnabled(false);
                Display.setResizable(true);
                Display.setInitialBackground(0f, 0f, 0f);
                Display.setDisplayMode(new DisplayMode(1024, 768));
                Display.create();
                
                
                resourceDB = new ResourceDB(loop);
                loop.addLoopEvent(resourceDB);

                resourceDB.addZip(new File("assets/gui.zip"));
                resourceDB.addZip(new File("assets/classic.zip"));
                
                {
                        InputStream in = ScenarioPlayer.class.getResourceAsStream("/client/graphics/scenario.lvl");
                        assert in != null;

                        mapClassic = new MapClassic(resourceDB, true);
                        byte[] b = SwissArmyKnife.inputStreamToBytes(in);
                        mapClassic.read(b, true);
                }
                
                camera = new Camera(resourceDB);
                camera.setScreenPosition(0, 0);
                
                stars = new StarField(1234, resourceDB);
                
                loop.setup();
                
                setup = true;
                while (!loop.isInterruped())
                {
                        Display.update();
                        if (Display.isCloseRequested())
                        {
                                loop.interrupt();
                                break;
                        }
                        
                        camera.setDimension(Display.getWidth(), Display.getHeight());
                        
                        loop.loop();
                        
                        Graph.graphicsLoop();
                        Client.initGL();
                        
                        if (scene == null)
                        {
                                camera.setPosition(512 * 16, 512 * 16);
                        }
                        else
                        {
                                mapEntities.updateGraphicsFromPhysics();
                                
                                ActorShip localShip = mapEntities.getLocalShip();
                                if (localShip != null)
                                {
                                        camera.setPosition(localShip.pos);
                                }
                        }
                        
                        stars.render(camera);
                        
                        if (scene != null)
                        {
                                camera.renderEntities(mapEntities.animations(RENDER_LAYER.BACKGROUND, camera));
                        }
                        
                        camera.renderTiles(mapClassic, TileType.TILE_LAYER.PLAIN);
                        camera.renderTiles(mapClassic, TileType. TILE_LAYER.ANIMATED);
                        
                        if (scene == null)
                        {
                                Graph.g.setColor(Color.white);
                                Graph.g.drawString("No scene selected", 
                                                   Display.getWidth() / 2 - Graph.g.getFont().getWidth("No scene selected") / 2, 
                                                   Display.getHeight() / 2 - Graph.g.getFont().getLineHeight() / 2);
                        }
                        else
                        {
                                camera.renderEntities(mapEntities.animations(RENDER_LAYER.AFTER_TILES, camera));
                                camera.renderEntities(mapEntities.projectiles(false));
                                camera.renderEntities(mapEntities.animations(RENDER_LAYER.AFTER_PROJECTILES, camera));
                                camera.renderEntities(mapEntities.shipsNoLocal());
                                camera.renderEntities(mapEntities.animations(RENDER_LAYER.AFTER_SHIPS, camera));
                                camera.renderEntity(mapEntities.getLocalShip());
                        }
                        
                        camera.renderTiles(mapClassic, TileType.TILE_LAYER.PLAIN_OVER_SHIP);
                        
                        if (scene != null)
                        {
                                camera.renderEntities(mapEntities.animations(RENDER_LAYER.AFTER_LOCAL_SHIP, camera));
                        }
                }
                
                
                breakdown();
        }
        
        private void removeLoopEvents()
        {
                loop.removeTickEvent(env);       
                loop.removeTickEvent(mapEntities);
                loop.removeLoopEvent(mapEntities);
                loop.removeTickEvent(scene);
        }
        
        public void setScenario(Scenario scene)
        {
                if (!setup)
                {
                        throw new IllegalArgumentException();
                }
                this.scene = scene;
                
                removeLoopEvents();
                
                mapEntities = null;
                env = null;
                
                if (scene != null)
                {
                        env = new PhysicsEnvironment(false, mapClassic);
                        mapEntities = new MapEntities(resourceDB);
                        mapEntities.tryInitialize(env, null);
                        
                        loop.addTickEvent(env);
                        loop.addTickEvent(mapEntities);
                        loop.addLoopEvent(mapEntities);
                        
                        scene.doSetup(env, mapEntities, resourceDB);
                        loop.addTickEvent(scene);
                }
        }
        
        public void addConfig(String config)
        {
                List<Object> yamlDocuments;
                try
                {
                        yamlDocuments = GameConfig.loadYaml(config);
                }
                catch (Exception ex)
                {
                        throw new Error(ex);
                }

                env.loadConfig(env.getTick() - env.econfig.HIGHEST_DELAY, "player", yamlDocuments);
        }
        
        public void interrupt()
        {
                loop.interrupt();
        }
        
        public void breakdown()
        {
                Display.destroy();
                
                if (loop != null)
                {
                        loop.breakdown();
                }
        }
        
        public static void main(String[] args) throws Exception
        {
                final TickedEventLoop loop = new TickedEventLoop(10, 2, null);
                final ScenarioPlayer player = new ScenarioPlayer(loop);
                
                ScenarioSelector frame = new ScenarioSelector(new ScenarioSelector.ScenarioSelectorListener()
                {
                        @Override
                        public void selected(Class klass, final String config)
                        {
                                try
                                {
                                        final Scenario scene = (Scenario) klass.newInstance();
                                        
                                        loop.runOnMain(new Runnable()
                                        {
                                                @Override
                                                public void run()
                                                {
                                                        player.setScenario(scene);
                                                        player.addConfig(config);
                                                }
                                        });
                                }
                                catch (InstantiationException | IllegalAccessException ex)
                                {
                                        throw new Error(ex);
                                }
                        }
                });
                
                frame.addWindowListener(new WindowAdapter()
                {
                        @Override
                        public void windowClosed(WindowEvent e)
                        {
                                player.interrupt();
                        }   
                });
                
                frame.setVisible(true);
                
                player.run();
                frame.dispose();
        }

        

       
}
