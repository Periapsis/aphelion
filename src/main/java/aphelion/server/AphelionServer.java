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

import aphelion.shared.net.protocols.GameListener;
import aphelion.shared.net.SessionToken;
import aphelion.server.http.HttpServer;
import aphelion.shared.event.LoopEvent;
import aphelion.shared.event.Workable;
import aphelion.shared.net.PROTOCOL;
import aphelion.shared.net.protocols.GameProtocolConnection;
import aphelion.shared.net.WS_CLOSE_STATUS;
import aphelion.shared.net.WebSocketTransport;
import aphelion.shared.net.WebSocketTransport.NoSuitableConnection;
import aphelion.shared.net.WebSocketTransportListener;
import aphelion.shared.net.protobuf.Ping;
import aphelion.shared.swissarmyknife.AttachmentConsumer;
import aphelion.shared.swissarmyknife.ThreadSafe;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.java_websocket.framing.CloseFrame;

/** 
 * @author Joris
 */
public class AphelionServer implements LoopEvent, WebSocketTransportListener
{
        private static final Logger log = Logger.getLogger(AphelionServer.class.getName());
        private static final AttachmentConsumer<SessionToken, GameProtocolConnection> gameAttachment = new AttachmentConsumer<SessionToken, GameProtocolConnection>(SessionToken.attachmentManager);
        
        private final static int SERVER_PING_PROTO_VERSION = 1;
        private final static int SERVER_GAME_PROTO_VERSION = 1;
        
        private HttpServer httpServer;
        private Workable workable;
        private WebSocketTransport webSocketTransport;
                
        private GameListener gameClientListener;
        
        private boolean hasSetup = false;
        
        private volatile int ping_players = -1;
        private volatile int ping_playing = -1;

        public AphelionServer(InetSocketAddress httpListen, File httpDocs, Workable workManager) throws IOException
        {
                this.workable = workManager;
                webSocketTransport = new WebSocketTransport(this);
                httpServer = new HttpServer(httpListen, httpDocs, webSocketTransport);
        }
        
        public void setGameClientListener(GameListener gameClientListener)
        {
                if (hasSetup)
                {
                        // Otherwise we have to lock
                        throw new IllegalStateException();
                }
                
                this.gameClientListener = gameClientListener;
        }
        
        public void setup() throws IOException
        {
                hasSetup = true;
                httpServer.setup();
        }
        
        /** Returns the TCP port that the HTTP server is currently listening on
         * @return -1 if not yet listening, otherwise the listening port.
         */
        public int getHTTPListeningPort()
        {
                return httpServer.getListeningPort();
        }
        
        @Override
        public void loop()
        {
                httpServer.loop();
                webSocketTransport.loop();
        }
        
        public void closeAll(WS_CLOSE_STATUS code)
        {
                webSocketTransport.closeAll(code);
        }
        
        public void closeAll(WS_CLOSE_STATUS code, String message)
        {
                webSocketTransport.closeAll(code, message);
        }
        
        public void stop()
        {
                closeAll(WS_CLOSE_STATUS.NORMAL);
                httpServer.stop(); // thread join
                log.log(Level.INFO, "AphelionServer has stopped");
        }
        
        /** Set the player count to be reported in the ping protocol.
         * 
         * @param players The total amount of players in ships, spectator, whatever. 
         *                Should not include server generated players.
         *                Pass -1 to disable sending this value (the default).
         * @param playing The total amount of players actually participating in the game.
         *                This means not in spectator and not afk in a safety.
         *                Pass -1 to disable sending this value (the default).
         */
        @ThreadSafe
        public void setPingPlayerCount(int players, int playing)
        {
                ping_players = players;
                ping_playing = playing;
        }

        @Override
        @ThreadSafe
        public boolean wstIsValidProtocol(PROTOCOL protocol)
        {
                if (protocol == PROTOCOL.PING)
                {
                        return true;
                }
                
                if (protocol == PROTOCOL.GAME && this.gameClientListener != null)
                {
                        return true;
                }
                
                return false;
        }

        @Override
        @ThreadSafe
        public int wstIsValidProtocolVersion(PROTOCOL protocol, int protocolVersion)
        {
                if (protocolVersion == 0)
                {
                        // disable version check
                        return 0;
                }
                
                if (protocol == PROTOCOL.PING)
                {
                        return Integer.compare(protocolVersion, SERVER_PING_PROTO_VERSION);
                }
                
                if (protocol == PROTOCOL.GAME)
                {
                        return Integer.compare(protocolVersion, SERVER_GAME_PROTO_VERSION);
                }
                
                return 0;
        }

        @Override
        @ThreadSafe
        public void wstNewProtocol(boolean server, SessionToken sessionToken, PROTOCOL protocol)
        {
                assert server;
                
                if (protocol == PROTOCOL.PING)
                {
                        // nothing to do
                }
                else if (protocol == PROTOCOL.GAME)
                {
                        GameProtocolConnection game;

                        synchronized(sessionToken)
                        {
                                game = gameAttachment.get(sessionToken);
                                if (game != null)
                                {
                                        game.removed();
                                }

                                game = new GameProtocolConnection(workable, webSocketTransport, sessionToken, server, gameClientListener);
                                gameAttachment.set(sessionToken, game);
                        }

                        game.created();
                }
        }

        @Override
        @ThreadSafe
        public void wstDropProtocol(boolean server, SessionToken sessionToken, PROTOCOL protocol)
        {
                assert server;
                
                if (protocol == PROTOCOL.PING)
                {
                        // nothing to do
                }
                else if (protocol == PROTOCOL.GAME)
                {
                        GameProtocolConnection game;

                        synchronized(sessionToken)
                        {
                                game = gameAttachment.get(sessionToken);
                                if (game != null)
                                {
                                        game.removed();
                                        gameAttachment.set(sessionToken, null);
                                }
                        }
                }
                
        }

        @Override
        @ThreadSafe
        public void wstMessage(boolean server, SessionToken sessionToken, PROTOCOL protocol, int protocolVersion, ByteBuffer message, long receivedAt)
        {
                assert server;
                // called from websocket thread
                
                if (protocol == PROTOCOL.PING)
                {
                        try
                        {
                                Ping.PingRequest pingRequest = Ping.PingRequest.parseFrom(new ByteArrayInputStream(message.array(), message.position(), message.remaining()));
                                
                                Ping.PingResponse.Builder responseBuilder = Ping.PingResponse.newBuilder();
                                
                                responseBuilder.setClientTime(pingRequest.getClientTime());
                                responseBuilder.setServerTime(System.nanoTime());
                                if (ping_players >= 0)
                                {
                                        responseBuilder.setPlayers(ping_players);
                                }
                                if (ping_playing >= 0)
                                {
                                        responseBuilder.setPlaying(ping_playing);
                                }
                                
                                Ping.PingResponse response = responseBuilder.build();
                                int size = response.getSerializedSize();
                                byte[] result = new byte[size + WebSocketTransport.SEND_RESERVEDPREFIX_BYTES];
                                CodedOutputStream output = CodedOutputStream.newInstance(result, WebSocketTransport.SEND_RESERVEDPREFIX_BYTES, size);
                                response.writeTo(output);
                                output.checkNoSpaceLeft();
                                
                                webSocketTransport.send(sessionToken, PROTOCOL.PING, result);
                                
                        }
                        catch (InvalidProtocolBufferException ex)
                        {
                                log.log(Level.SEVERE, "Protobuf Exception while parsing a message as a server", ex);
                        }
                        catch (IOException ex)
                        {
                                log.log(Level.SEVERE, "IOException while parsing a message as a server", ex);
                        }
                        catch (NoSuitableConnection ex)
                        {
                                log.log(Level.SEVERE, "NoSuitableConnection while parsing a message as a server", ex);
                        }
                }
                else if (protocol == PROTOCOL.GAME)
                {
                        GameProtocolConnection game;

                        synchronized(sessionToken)
                        {
                                game = gameAttachment.get(sessionToken);
                                if (game == null)
                                {
                                        return;
                                }
                        }
                        
                        game.parseMessage(message, receivedAt);
                }
        }

        @Override
        @ThreadSafe
        public void wstMessage(boolean server, SessionToken sessionToken, PROTOCOL protocol, int protocolVersion, String message, long receivedAt)
        {
                assert server;
                
                if (protocol == PROTOCOL.GAME)
                {
                }
        }

        @Override
        public void wstNewConnection(boolean server, SessionToken sessionToken, PROTOCOL protocol)
        {
                assert server;
                
                if (protocol == PROTOCOL.GAME)
                {
                        GameProtocolConnection game;

                        synchronized(sessionToken)
                        {
                                game = gameAttachment.get(sessionToken);
                                if (game == null)
                                {
                                        return;
                                }
                        }
                        
                        game.connectionAdded();
                }
        }

        @Override
        public void wstDropConnection(boolean server, SessionToken sessionToken, PROTOCOL protocol, WS_CLOSE_STATUS code, String reason)
        {
                assert server;
                
                if (protocol == PROTOCOL.GAME)
                {
                        GameProtocolConnection game;

                        synchronized(sessionToken)
                        {
                                game = gameAttachment.get(sessionToken);
                                if (game == null)
                                {
                                        return;
                                }
                        }
                        
                        game.connectionDropped(code, reason);
                }
        }

        @Override
        public void wstClientEstablishFailure(SessionToken sessionToken, PROTOCOL protocol, WS_CLOSE_STATUS code, String reason)
        {
                throw new Error("This method should not be used for servers");
        }
}
