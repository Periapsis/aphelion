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

import aphelion.server.game.ServerGame;
import aphelion.server.http.HttpServer;
import aphelion.shared.event.Deadlock;
import aphelion.shared.event.LoopEvent;
import aphelion.shared.event.TickedEventLoop;
import aphelion.shared.event.WorkerTask;
import aphelion.shared.gameconfig.LoadYamlTask;
import aphelion.shared.map.MapClassic;
import aphelion.shared.map.MapClassic.LoadMapTask;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.resource.ResourceDB;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class ServerMain implements LoopEvent
{
        private static final Logger log = Logger.getLogger("aphelion.server");
        private TickedEventLoop loop;
        private ServerSocketChannel listen;
        private AphelionServer server;
        private ServerGame serverGame;
        
        public ServerMain(ServerSocketChannel listen)
        {                
                this.listen = listen;
        }
        
        public void setup() throws IOException
        {
                loop = new TickedEventLoop(10, Runtime.getRuntime().availableProcessors(), null);
                server = new AphelionServer(listen, new File("./www"), loop);
                loop.addLoopEvent(server);
                
                ResourceDB resourceDB = new ResourceDB(loop);
                resourceDB.addZip(new File("assets/singleplayer.zip"));
                MapClassic map;
                List<LoadYamlTask.Return> gameConfig;
                try
                {
                        map = new LoadMapTask(resourceDB, false).work("level.map");
                        gameConfig = new LoadYamlTask(resourceDB).work(resourceDB.getKeysByPrefix("gameconfig."));
                }
                catch (WorkerTask.WorkerException ex)
                {
                        log.log(Level.SEVERE, null, ex);
                        throw (IOException) ex.getCause();
                }
                
                PhysicsEnvironment physicsEnv = new PhysicsEnvironment(true, map);
                
                for (LoadYamlTask.Return ret : gameConfig)
                {
                        physicsEnv.loadConfig(physicsEnv.getTick() - PhysicsEnvironment.TOTAL_HISTORY, ret.fileIdentifier, ret.yamlDocuments);
                }
                gameConfig = null;
                
                // TMP:
                physicsEnv.actorNew(0, -1, "Dummy", 1, "warbird");
                physicsEnv.actorWarp(0, -1, false, 512 * 16 * 1024, 448 * 16 * 1024, 0, 10000, 0);
                
                serverGame = new ServerGame(physicsEnv, loop);
                loop.addLoopEvent(serverGame);
                loop.addTickEvent(serverGame);
                server.setGameClientListener(serverGame);
                
                loop.addLoopEvent(this);
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
        
        public static void main(String[] args) throws IOException
        {
                String hostname;
                int port;
                
                if (args.length >= 1)
                {
                        port = Integer.parseInt(args[0]); // may throw
                }
                else
                {
                        port = 80;
                }
                
                if (args.length >= 2)
                {
                        hostname = args[1];
                }
                else
                {
                        hostname = "0.0.0.0";
                }
                
                try (ServerSocketChannel ssChannel = HttpServer.openServerChannel(new InetSocketAddress(hostname, port)))
                {
                        Deadlock.start(false, null);
                        ServerMain main = new ServerMain(ssChannel);
                        main.setup();
                        main.run();
                        Deadlock.stop();
                }
        }

        
        
}
