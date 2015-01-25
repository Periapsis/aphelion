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
package aphelion.shared.net;


import aphelion.client.net.SingleGameConnection;
import aphelion.server.AphelionServer;
import aphelion.server.http.HttpServer;
import aphelion.shared.event.TickedEventLoop;
import aphelion.shared.net.game.GameProtocolConnection;
import aphelion.shared.net.game.GameProtoListener;
import aphelion.shared.net.protobuf.GameC2S;
import aphelion.shared.net.protobuf.GameC2S.C2S;
import aphelion.shared.net.protobuf.GameOperation.ActorMove;
import aphelion.shared.net.protobuf.GameS2C;
import aphelion.shared.net.protobuf.GameS2C.ArenaSync;
import aphelion.shared.net.protobuf.GameS2C.S2C;
import aphelion.shared.physics.EnvironmentConf;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import com.google.protobuf.ByteString;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.ServerSocketChannel;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Joris
 */
public class WebSocketTest
{
        private static final Logger log = Logger.getLogger(WebSocketTest.class.getName());
        
        TickedEventLoop loop;
        SessionToken sessionToken;
        
        private long sentC2SMove;
        private long receivedC2SMove;
        
        int serverNewClient; int clientNewClient;
        boolean clientRemoved; boolean serverRemoved;
        int serverMessages; int clientMessages;
        int clientConnections; int serverConnections;
        
        int serverPidSum;
        int clientPidSum;
        
        @Before
        public void setUp()
        {
                serverNewClient = 0;
                clientNewClient = 0;
                clientRemoved = false;
                serverRemoved = false;
                serverMessages = 0; 
                clientMessages = 0;
                clientConnections = 0;
                serverConnections = 0;
                clientPidSum = 0;
                serverPidSum = 0;
        }
        
        @After
        public void breakDown()
        {
                loop = null;
                sessionToken = null;
        }
        
        @Test(timeout=2000)
        public void testSingleGameWebSocket() throws IOException, URISyntaxException
        {
                // use the same loop for client and server
                loop = new TickedEventLoop(EnvironmentConf.TICK_LENGTH, 1, null);
                
                // Set up the server
                // use an ephemeral port. aka a temporary port number
                try (ServerSocketChannel ssChannel = HttpServer.openServerChannel(new InetSocketAddress("127.0.0.1", 0)))
                {
                        AphelionServer server = new AphelionServer(ssChannel, new File("./www"), loop);
                        loop.addLoopEvent(server);
                        server.setGameClientListener(new testSingleGameWebSocket_ServerGameListener());

                        // set up the client
                        SingleGameConnection client = new SingleGameConnection(
                                new URI("ws://127.0.0.1:"+server.getHTTPListeningPort()+"/aphelion"), 
                                loop,
                                1); // 1 connection;
                        
                        client.addListener(new testSingleGameWebSocket_ClientGameListener());

                        loop.addLoopEvent(client);
                        server.setup();
                        client.connect();

                        log.log(Level.INFO, "Starting loop");
                        loop.run();
                        assertEquals(1, clientNewClient);
                        assertEquals(1, serverNewClient);
                        assertTrue(clientRemoved);
                        assertTrue(serverRemoved);
                        assertEquals(1, clientMessages);
                        assertEquals(1, serverMessages);

                        double latency = (receivedC2SMove - sentC2SMove) / 1000000d;
                        log.log(Level.INFO, "Latency: {0}ms", latency);

                        client.close(WS_CLOSE_STATUS.NORMAL);
                        server.closeAll(WS_CLOSE_STATUS.NORMAL);
                        server.stop();
                        client.stop();
                }
        }
        
        private class testSingleGameWebSocket_ServerGameListener implements GameProtoListener
        {

                @Override
                public void gameNewClient(GameProtocolConnection game)
                {
                        log.log(Level.INFO, "Server: new client");
                        ++serverNewClient;
                        
                        assertEquals(0, serverConnections);
                        
                        assertEquals(0, clientNewClient);
                        assertEquals(1, serverNewClient);
                        assertTrue(game.server);
                        
                        
                        sessionToken = game.session;
                }

                @Override
                public void gameRemovedClient(GameProtocolConnection game)
                {
                        log.log(Level.INFO, "Server: removed client");
                        
                        assertEquals(0, serverConnections);
                        
                        serverRemoved = true;
                        if (clientRemoved)
                        {
                                loop.interrupt();
                        }
                }

                @Override
                public void gameC2SMessage(GameProtocolConnection game, GameC2S.C2S c2s, long receivedAt)
                {
                        ++serverMessages;
                        log.log(Level.INFO, "Server: C2S message: {0}", serverMessages);

                        assertEquals(1, c2s.getActorMoveCount());
                        assertEquals(1, c2s.getAllFields().size());
                        
                        ActorMove move = c2s.getActorMove(0);
                        receivedC2SMove = System.nanoTime();
                        assertEquals(123, move.getTick());
                        assertEquals(456, move.getPid());
                        
                        List<PhysicsMovement> moves = PhysicsMovement.unserializeListLE(move.getMove().asReadOnlyByteBuffer());
                        assertEquals(4, moves.size());
                        assertEquals(0x1, moves.get(0).bits);
                        assertEquals(0x1 | 0x4, moves.get(1).bits);
                        assertEquals(0x0, moves.get(2).bits);
                        assertEquals(0x8, moves.get(3).bits);
                        
                        
                        S2C.Builder s2c = S2C.newBuilder();
                        
                        ArenaSync.Builder arenaSync = s2c.addArenaSyncBuilder();
                        arenaSync.setName("jowie");
                        arenaSync.setShip("warbird");
                        arenaSync.setCurrentTicks(5021034);
                        arenaSync.setCurrentNanoTime(164029301000000000L);
                        arenaSync.setYourPid(102);
                        arenaSync.setYourSeed(1234567890);
                        
                        game.send(s2c.build());
                }

                @Override
                public void gameS2CMessage(GameProtocolConnection game, GameS2C.S2C s2c, long receivedAt)
                {
                        assertTrue(false);
                }

                @Override
                public void gameNewConnection(GameProtocolConnection game)
                {
                        ++serverConnections;
                        log.log(Level.INFO, "Server: gameNewConnection: {0}", serverConnections);
                        assertEquals(1, serverNewClient);
                }

                @Override
                public void gameDropConnection(GameProtocolConnection game, WS_CLOSE_STATUS code, String reason)
                {
                        --serverConnections;
                        log.log(Level.INFO, "Server: gameDropConnection: {0}", serverConnections);
                        assertEquals(1, serverNewClient);
                }

                @Override
                public void gameEstablishFailure(WS_CLOSE_STATUS code, String reason)
                {
                        assert false;
                }
        }
        
        
        private class testSingleGameWebSocket_ClientGameListener implements GameProtoListener
        {

                @Override
                public void gameNewClient(GameProtocolConnection game)
                {
                        log.log(Level.INFO, "Client: new client");
                        ++clientNewClient;
                        assertEquals(0, clientConnections);
                        assertEquals(1, serverNewClient);
                        assertEquals(1, clientNewClient);
                        assertFalse(game.server);
                        
                        assertEquals(sessionToken, game.session);
                        
                        C2S.Builder c2s = C2S.newBuilder();
                        
                        ActorMove.Builder actorMove = c2s.addActorMoveBuilder();
                        actorMove.setTick(123);
                        actorMove.setPid(456);
                        
                        actorMove.setMove(ByteString.copyFrom(PhysicsMovement.serializeListLE(Arrays.asList(
                                PhysicsMovement.get(0x1), 
                                PhysicsMovement.get(0x1 | 0x4), 
                                PhysicsMovement.get(0x0), 
                                PhysicsMovement.get(0x8)))));
                        
                        sentC2SMove = System.nanoTime();
                        game.send(c2s.build());
                }

                @Override
                public void gameRemovedClient(GameProtocolConnection game)
                {
                        assertEquals(0, clientConnections);
                        
                        log.log(Level.INFO, "Client: removed client");
                        clientRemoved = true;
                        if (serverRemoved)
                        {
                                loop.interrupt();
                        }
                }

                @Override
                public void gameC2SMessage(GameProtocolConnection game, GameC2S.C2S c2s, long receivedAt)
                {
                        assertTrue(false);
                }

                @Override
                public void gameS2CMessage(GameProtocolConnection game, GameS2C.S2C s2c, long receivedAt)
                {
                        ++clientMessages;
                        log.log(Level.INFO, "Client: S2C message: {0}", clientMessages);

                        assertEquals(1, s2c.getArenaSyncCount());
                        assertEquals(1, s2c.getAllFields().size());
                        
                        ArenaSync arenaSync = s2c.getArenaSync(0);
                        
                        assertEquals("jowie", arenaSync.getName());
                        assertEquals("warbird", arenaSync.getShip());
                        assertEquals(5021034, arenaSync.getCurrentTicks());
                        assertEquals(164029301000000000L, arenaSync.getCurrentNanoTime());
                        assertEquals(102, arenaSync.getYourPid());
                        
                        // done with the test
                        game.requestClose(WS_CLOSE_STATUS.NORMAL);
                }

                @Override
                public void gameNewConnection(GameProtocolConnection game)
                {
                        assertEquals(1, clientNewClient);
                        ++clientConnections;
                        log.log(Level.INFO, "Client: gameNewConnection: {0}", clientConnections);
                }

                @Override
                public void gameDropConnection(GameProtocolConnection game, WS_CLOSE_STATUS code, String reason)
                {
                        assertEquals(1, clientNewClient);
                        --clientConnections;
                        log.log(Level.INFO, "Client: gameDropConnection: {0}", clientNewClient);
                }
                
                @Override
                public void gameEstablishFailure(WS_CLOSE_STATUS code, String reason)
                {
                        assert false;
                }
        }
        
        
        @Test(timeout=2000)
        public void testMultiGameWebSocket() throws IOException, URISyntaxException
        {
                // use the same loop for client and server
                loop = new TickedEventLoop(EnvironmentConf.TICK_LENGTH, 1, null);
                
                // Set up the server
                // use an ephemeral port. aka a temporary port number
                try (ServerSocketChannel ssChannel = HttpServer.openServerChannel(new InetSocketAddress("127.0.0.1", 0)))
                {
                        AphelionServer server = new AphelionServer(ssChannel, new File("./www"), loop);
                        loop.addLoopEvent(server);
                        server.setGameClientListener(new testMultiGameWebSocket_ServerGameListener());
                        server.setup();

                        // set up the client
                        SingleGameConnection client = new SingleGameConnection(
                                new URI("ws://127.0.0.1:"+server.getHTTPListeningPort()+"/aphelion"), 
                                loop, 
                                5); // 5 connections;
                        client.addListener(new testMultiGameWebSocket_ClientGameListener());

                        loop.addLoopEvent(client);
                        client.connect();

                        loop.run();
                        assertEquals(1, clientNewClient);
                        assertEquals(1, serverNewClient);
                        assertTrue(clientRemoved);
                        assertTrue(serverRemoved);
                        assertEquals(18, clientMessages);
                        assertEquals(28, serverMessages);
                        assertEquals(210, clientPidSum);
                        assertEquals(465, serverPidSum);
                        client.close(WS_CLOSE_STATUS.NORMAL);
                        server.closeAll(WS_CLOSE_STATUS.NORMAL);
                        server.stop();
                        client.stop();
                }
        }
        
        private class testMultiGameWebSocket_ServerGameListener implements GameProtoListener
        {

                @Override
                public void gameNewClient(GameProtocolConnection game)
                {
                        log.log(Level.INFO, "Server: new client");
                        ++serverNewClient;
                        
                        assertEquals(0, serverConnections);
                        
                        assertEquals(0, clientNewClient);
                        assertEquals(1, serverNewClient);
                        assertTrue(game.server);
                        
                        
                        sessionToken = game.session;
                        
                        
                }

                @Override
                public void gameRemovedClient(GameProtocolConnection game)
                {
                        log.log(Level.INFO, "Server: removed client");
                        
                        assertEquals(0, serverConnections);
                        
                        serverRemoved = true;
                        if (clientRemoved)
                        {
                                loop.interrupt();
                        }
                }

                @Override
                public void gameC2SMessage(GameProtocolConnection game, GameC2S.C2S c2s, long receivedAt)
                {
                        ++serverMessages;
                        log.log(Level.INFO, "Server: Received message {0}", serverMessages);
                        assertTrue(c2s.getActorMoveCount() > 0);
                        assertEquals(1, c2s.getAllFields().size());
                        
                        for (int a = 0; a < c2s.getActorMoveCount(); ++a)
                        {
                                ActorMove move = c2s.getActorMove(a);
                                assertEquals(123, move.getTick());
                                serverPidSum += move.getPid();
                                
                                List<PhysicsMovement> moves = PhysicsMovement.unserializeListLE(move.getMove().asReadOnlyByteBuffer());
                                assertEquals(4, moves.size());
                                assertEquals(0x1, moves.get(0).bits);
                                assertEquals(0x1 | 0x4, moves.get(1).bits);
                                assertEquals(0x0, moves.get(2).bits);
                                assertEquals(0x8, moves.get(3).bits);
                                
                        }
                        
                        if (clientMessages == 18 && serverMessages == 28)
                        {
                                game.requestClose(WS_CLOSE_STATUS.NORMAL);
                        }
                }

                @Override
                public void gameS2CMessage(GameProtocolConnection game, GameS2C.S2C s2c, long receivedAt)
                {
                        assertTrue(false);
                }

                @Override
                public void gameNewConnection(GameProtocolConnection game)
                {
                        ++serverConnections;
                        
                        if (serverConnections == 5)
                        {
                                S2C.Builder s2c = S2C.newBuilder();
                                for (int a = 0; a < 20; ++a)
                                {
                                        if (a <= 17)
                                        {
                                                // send the last 3 in a batch
                                                s2c = S2C.newBuilder();
                                        }

                                        ActorMove.Builder actorMove = s2c.addActorMoveBuilder();
                                        actorMove.setTick(123);
                                        actorMove.setPid(a + 1);

                                        actorMove.setMove(ByteString.copyFrom(PhysicsMovement.serializeListLE(Arrays.asList(
                                        PhysicsMovement.get(0x1), 
                                        PhysicsMovement.get(0x1 | 0x4), 
                                        PhysicsMovement.get(0x0), 
                                        PhysicsMovement.get(0x8)))));

                                        if (a < 17)
                                        {
                                                game.send(s2c);
                                        }
                                }

                                game.send(s2c);
                        }
                }

                @Override
                public void gameDropConnection(GameProtocolConnection game, WS_CLOSE_STATUS code, String reason)
                {
                        --serverConnections;
                }
                
                @Override
                public void gameEstablishFailure(WS_CLOSE_STATUS code, String reason)
                {
                        assert false;
                }
        }
        
        private class testMultiGameWebSocket_ClientGameListener implements GameProtoListener
        {

                @Override
                public void gameNewClient(GameProtocolConnection game)
                {
                        log.log(Level.INFO, "Client: new client");
                        ++clientNewClient;
                        
                        assertEquals(0, clientConnections);
                        
                        assertEquals(1, serverNewClient);
                        assertEquals(1, clientNewClient);
                        assertFalse(game.server);
                        
                        assertEquals(sessionToken, game.session);
                        
                        
                }

                @Override
                public void gameRemovedClient(GameProtocolConnection game)
                {
                        log.log(Level.INFO, "Client: removed client");
                        
                        assertEquals(0, clientConnections);
                        
                        clientRemoved = true;
                        if (serverRemoved)
                        {
                                loop.interrupt();
                        }
                }

                @Override
                public void gameC2SMessage(GameProtocolConnection game, GameC2S.C2S c2s, long receivedAt)
                {
                        assertTrue(false);
                }

                @Override
                public void gameS2CMessage(GameProtocolConnection game, GameS2C.S2C s2c, long receivedAt)
                {
                        ++clientMessages;
                        log.log(Level.INFO, "Client: received message {0}", clientMessages);
                        
                        
                        
                        assertTrue(s2c.getActorMoveCount() > 0);
                        assertEquals(1, s2c.getAllFields().size());
                        
                        for (int a = 0; a < s2c.getActorMoveCount(); ++a)
                        {
                                ActorMove move = s2c.getActorMove(a);
                                assertEquals(123, move.getTick());
                                
                                clientPidSum += move.getPid();
                                
                                List<PhysicsMovement> moves = PhysicsMovement.unserializeListLE(move.getMove().asReadOnlyByteBuffer());
                                assertEquals(4, moves.size());
                                assertEquals(0x1, moves.get(0).bits);
                                assertEquals(0x1 | 0x4, moves.get(1).bits);
                                assertEquals(0x0, moves.get(2).bits);
                                assertEquals(0x8, moves.get(3).bits);
                        }
                        
                        if (clientMessages == 18 && serverMessages == 28)
                        {
                                game.requestClose(WS_CLOSE_STATUS.NORMAL);
                        }
                }

                @Override
                public void gameNewConnection(GameProtocolConnection game)
                {
                        ++clientConnections;
                        
                        if (clientConnections == 5)
                        {
                                C2S.Builder c2s = C2S.newBuilder();
                                for (int a = 0; a < 30; ++a)
                                {
                                        if (a <= 27)
                                        {
                                                // send the last 3 in a batch
                                                c2s = C2S.newBuilder();
                                        }

                                        ActorMove.Builder actorMove = c2s.addActorMoveBuilder();
                                        actorMove.setTick(123);
                                        actorMove.setPid(a + 1);

                                        actorMove.setMove(ByteString.copyFrom(PhysicsMovement.serializeListLE(Arrays.asList(
                                        PhysicsMovement.get(0x1), 
                                        PhysicsMovement.get(0x1 | 0x4), 
                                        PhysicsMovement.get(0x0), 
                                        PhysicsMovement.get(0x8)))));

                                        if (a < 27)
                                        {
                                                game.send(c2s);
                                        }
                                }
                                game.send(c2s);
                        }
                        
                        
                        
                }

                @Override
                public void gameDropConnection(GameProtocolConnection game, WS_CLOSE_STATUS code, String reason)
                {
                        --clientConnections;
                }
                
                @Override
                public void gameEstablishFailure(WS_CLOSE_STATUS code, String reason)
                {
                        assert false;
                }
        }
}
