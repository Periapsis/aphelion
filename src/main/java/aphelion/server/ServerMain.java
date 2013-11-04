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

package aphelion.server;

import aphelion.shared.resource.Asset;
import aphelion.server.game.ServerGame;
import aphelion.server.http.HttpServer;
import aphelion.shared.event.*;
import aphelion.shared.event.promise.PromiseException;
import aphelion.shared.gameconfig.LoadYamlTask;
import aphelion.shared.map.MapClassic;
import aphelion.shared.map.MapClassic.LoadMapTask;
import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.net.protobuf.GameS2C;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.WEAPON_SLOT;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.resource.AssetCache;
import aphelion.shared.resource.FileStorage;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 *
 * @author Joris
 */
public class ServerMain implements LoopEvent, TickEvent
{
        private static final Logger log = Logger.getLogger("aphelion.server");
        private final ServerSocketChannel listen;
        private final Map<String, Object> config;
        
        private TickedEventLoop loop;
        private AphelionServer server;
        private ServerGame serverGame;
        private PhysicsEnvironment physicsEnv;
        
        private AssetCache assetCache;
        private List<Asset> assets;
        private String mapResource;
        private List<String> gameConfigResources;
        private List<String> niftyGuiResources;
        
        private static final int DUMMY_1_PID = -1;
        private static final int DUMMY_2_PID = -2;
        
        public ServerMain(ServerSocketChannel listen, Map<String, Object> config)
        {                
                this.listen = listen;
                this.config = config;
        }
        
        public void setup() throws IOException, ServerConfigException
        {
                int processors = Runtime.getRuntime().availableProcessors();
                if (processors < 2) { processors = 2; } // minimum of two workers
                loop = new TickedEventLoop(10, processors, null);
                
                server = new AphelionServer(listen, new File("./www"), loop);
                
                
                try
                {
                        FileStorage assetCacheStorage = new FileStorage(new File((String) config.get("assets-cache-path")).getCanonicalFile());
                        
                        if (!assetCacheStorage.isUseable())
                        {
                                throw new ServerConfigException("assets-cache-path is not readable/writeable or not a directory: " + assetCacheStorage);
                        }
                        
                        assetCache = new AssetCache(assetCacheStorage);
                        
                }
                catch (ClassCastException | NullPointerException ex)
                {
                        throw new ServerConfigException("Missing or invalid server config entry: assets-cache-path");
                }
                catch (IOException ex)
                {
                        throw new ServerConfigException("The given assets-cache-path is not a valid directory: " + config.get("assets-cache-path"), ex);
                }
                
                try
                {
                        // todo: multiple arenas (seperate directories with arena config?)
                        Map<String, Object> arena = (Map<String, Object>) config.get("arena");

                        List configAssets = (List) arena.get("assets");
                        this.assets = new ArrayList<>(configAssets.size());
                        for (Object c : configAssets)
                        {
                                Asset ass = new Asset(assetCache, c);
                                this.assets.add(ass);
                        }

                        mapResource = (String) arena.get("map");

                        gameConfigResources = new ArrayList<>((List<String>) arena.get("game-config"));
                        niftyGuiResources = new ArrayList<>((List<String>) arena.get("nifty-gui"));
                }
                catch (ClassCastException | NullPointerException ex)
                {
                        throw new ServerConfigException("Invalid config for 'arena'", ex);
                }
                
                
                server.addHttpRouteStatic("assets", assetCache.getStorage().getDirectory());
                
                loop.addLoopEvent(server);
                
                ResourceDB resourceDB = new ResourceDB(loop);
                
                for (Asset ass : this.assets)
                {
                        try
                        {
                                ass.storeAsset(ass.configFile, true);
                        }
                        catch (AssetCache.InvalidContentException ex)
                        {
                                throw new AssertionError(ex);
                        }
                        
                        // ass.file is now valid
                        resourceDB.addZip(ass.file);
                }
                
                if (!resourceDB.resourceExists(mapResource))
                {
                        throw new ServerConfigException("Resource does not exist: " + mapResource);
                }
                
                for (String key : gameConfigResources)
                {
                        if (!resourceDB.resourceExists(key))
                        {
                                throw new ServerConfigException("Resource does not exist: " + mapResource);
                        }
                }
                
                
                MapClassic map;
                List<LoadYamlTask.Return> gameConfig;
                
                try
                {
                        map = new LoadMapTask(resourceDB, false).work(mapResource);
                        gameConfig = new LoadYamlTask(resourceDB).work(gameConfigResources);
                }
                catch (PromiseException ex)
                {
                        log.log(Level.SEVERE, null, ex);
                        throw (IOException) ex.getCause();
                }
                
                physicsEnv = new PhysicsEnvironment(true, map);
                
                for (LoadYamlTask.Return ret : gameConfig)
                {
                        physicsEnv.loadConfig(physicsEnv.getTick() - PhysicsEnvironment.TOTAL_HISTORY, ret.fileIdentifier, ret.yamlDocuments);
                }
                gameConfig = null;
                
                // dummy for testing
                physicsEnv.actorNew(0, DUMMY_1_PID, "Dummy", 1, "javelin");
                physicsEnv.actorWarp(0, DUMMY_1_PID, false, 512 * 16 * 1024, 448 * 16 * 1024, 0, 10000, 0);
                
                physicsEnv.actorNew(0, DUMMY_2_PID, "Dummy 2", 1, "terrier");
                physicsEnv.actorWarp(0, DUMMY_2_PID, false, 512 * 16 * 1024, 448 * 16 * 1024, 0, -10000, 0); 
                
                serverGame = new ServerGame(physicsEnv, loop, assets, mapResource, gameConfigResources, niftyGuiResources);
                loop.addLoopEvent(serverGame);
                loop.addTickEvent(serverGame);
                server.setGameClientListener(serverGame);
                
                loop.addLoopEvent(this);
                loop.addTickEvent(this);
                server.setup();
        }
        
        public int getHTTPListeningPort()
        {
                return server.getHTTPListeningPort();
        }
        
        public void run()
        {
                loop.run();
        }
        
        public void stop()
        {
                loop.interrupt();
                server.stop();
                log.log(Level.INFO, "ServerMain has stopped");
        }
        
        @Override
        public void loop(long systemNanoTime, long sourceNanoTime)
        {
                if (serverGame == null)
                {
                        server.setPingPlayerCount(-1, -1);
                }
                else
                {
                        server.setPingPlayerCount(serverGame.getPlayerCount(), -1);
                        // todo playing
                }
        }

        @Override
        public void tick(long tick)
        {
                if (tick % 20 == 0) // dummy
                {
                        GameS2C.S2C.Builder s2c = GameS2C.S2C.newBuilder();
                        
                        {
                                GameOperation.ActorMove.Builder moveBuilder = s2c.addActorMoveBuilder();

                                moveBuilder.setTick(physicsEnv.getTick()-20);
                                moveBuilder.setPid(DUMMY_1_PID);
                                moveBuilder.setDirect(true);

                                PhysicsMovement move = PhysicsMovement.get(SwissArmyKnife.random.nextInt(16));
                                for (int i = 20; i > 0; --i)
                                {
                                        physicsEnv.actorMove(physicsEnv.getTick()-i, DUMMY_1_PID, move);
                                        moveBuilder.addMove(move.bits);
                                }
                                
                                
                                if (tick % 1000 == 0 && SwissArmyKnife.random.nextInt(3) == 0)
                                {
                                        physicsEnv.actorWeapon(physicsEnv.getTick(), DUMMY_1_PID, WEAPON_SLOT.BOMB, false, 0, 0, 0, 0, 0);
                                        GameOperation.ActorWeapon.Builder weaponBuilder = s2c.addActorWeaponBuilder();
                                        weaponBuilder.setTick(physicsEnv.getTick());
                                        weaponBuilder.setPid(DUMMY_1_PID);
                                        weaponBuilder.setSlot(WEAPON_SLOT.BOMB.id);
                                }
                        }
                        
                        {
                                GameOperation.ActorMove.Builder moveBuilder = s2c.addActorMoveBuilder();

                                moveBuilder.setTick(physicsEnv.getTick()-20);
                                moveBuilder.setPid(DUMMY_2_PID);
                                moveBuilder.setDirect(true);

                                PhysicsMovement move = tick % 1000 < 500 
                                                       ? PhysicsMovement.get(false, false, false, true, false) 
                                                       : PhysicsMovement.get(false, false, true, false, false);
                                
                                for (int i = 20; i > 0; --i)
                                {
                                        physicsEnv.actorMove(physicsEnv.getTick()-i, DUMMY_2_PID, move);
                                        moveBuilder.addMove(move.bits);
                                }
                        }
                        
                        serverGame.broadcast(s2c);
                }
        }
        
        public static void main(String[] args) throws IOException, ServerConfigException
        {
                if (args.length < 1)
                {
                        throw new IllegalArgumentException("The first argument should be the path to a yaml config file");
                }
                
                Yaml yaml = new Yaml(new SafeConstructor());
                
                Map<String, Object> config;
                String address;
                int port;
                
                try
                {
                        config = (Map<String, Object>) yaml.load(new FileInputStream(args[0])); 
                }
                catch (FileNotFoundException | ClassCastException | YAMLException ex)
                {
                        // Note: YAMLException is a RunTimeException
                        throw new ServerConfigException("Unable to read server config", ex);
                }
                
                try
                {
                        address = config.containsKey("bind-address") ? (String) config.get("bind-address") : "0.0.0.0";
                        port = config.containsKey("bind-port") ? (int) config.get("bind-port") : 80;
                }
                catch (ClassCastException ex)
                {
                        throw new ServerConfigException("Invalid bind-address or bind-port", ex);
                }
                
                try (ServerSocketChannel ssChannel = HttpServer.openServerChannel(new InetSocketAddress(address, port)))
                {
                        Deadlock.start(false, null);
                        ServerMain main = new ServerMain(ssChannel, config);
                        main.setup();
                        main.run();
                        Deadlock.stop();
                }
        }
}
