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

package aphelion.client.net;

import aphelion.shared.event.LoopEvent;
import aphelion.shared.event.Workable;
import aphelion.shared.net.PROTOCOL;
import aphelion.shared.net.protocols.GameProtocolConnection;
import aphelion.shared.net.protocols.GameListener;
import aphelion.shared.net.SessionToken;
import aphelion.shared.net.WS_CLOSE_STATUS;
import aphelion.shared.net.WebSocketTransport;
import aphelion.shared.net.WebSocketTransportListener;
import aphelion.shared.swissarmyknife.ThreadSafe;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Attempts to establish a single game session with the server using multiple sockets.
 * Use connect() to the start the session.
 * The listener that is passed to the constructor of this class is called from the main thread.
 * In normal conditions, gameNewClient is only called once. In failure conditions gameNewClient 
 * may be called multiple times with different instances of GameProtocol, if this happens the 
 * server considers it as a new session (in other words, you now have multiple players connected).
 * The client-side simulation will have to start over.
 * 
 * TODO: what happens when 1 socket is lost?
 * @author Joris
 */
public class SingleGameConnection implements LoopEvent, WebSocketTransportListener
{
        private static final Logger log = Logger.getLogger("aphelion.net");
        
        private final static int CLIENT_PROTO_VERSION = 1; // use 0 to disable checking
        
        private URI uri;
        private Workable workable;
        private WebSocketTransport webSocketTransport;
        private GameListener gameClientListener;
        private GameProtocolConnection game;
        private final int desired_sockets; //TODO: set me to 5

        public SingleGameConnection(URI uri, Workable workable, GameListener gameClientListener)
        {
                this.uri = uri;
                this.workable = workable;
                this.gameClientListener = gameClientListener;
                
                this.webSocketTransport = new WebSocketTransport(this);
                
                this.desired_sockets = 5;
        }
        
        public SingleGameConnection(URI uri, Workable workable, GameListener gameClientListener, int desired_sockets)
        {
                this.uri = uri;
                this.workable = workable;
                this.gameClientListener = gameClientListener;
                
                this.webSocketTransport = new WebSocketTransport(this);
                
                this.desired_sockets = desired_sockets;
        }
        
        
        @Override
        public void loop(long systemNanoTime, long sourceNanoTime)
        {
                webSocketTransport.loop(systemNanoTime, sourceNanoTime);
        }
        
        public void connect()
        {
                if (this.game != null)
                {
                        webSocketTransport.closeAll(WS_CLOSE_STATUS.NORMAL);
                        this.game = null;
                }
                
                this.webSocketTransport.establishClientConnection(uri, null, PROTOCOL.GAME, CLIENT_PROTO_VERSION);
        }
        
        public void close(WS_CLOSE_STATUS code)
        {
                webSocketTransport.closeAll(code);
        }
        
        public void close(WS_CLOSE_STATUS code, String message)
        {
                webSocketTransport.closeAll(code, message);
        }
        
        public void stop()
        {
                webSocketTransport.stopClients();
        }

        @Override
        @ThreadSafe
        public boolean wstIsValidProtocol(PROTOCOL protocol)
        {
                throw new Error("This method should not be used for clients");
        }

        @Override
        @ThreadSafe
        public int wstIsValidProtocolVersion(PROTOCOL protocol, int protocolVersion)
        {
                throw new Error("This method should not be used for clients");
        }

        
        @Override
        public void wstClientEstablishFailure(SessionToken sessionToken, PROTOCOL protocol, WS_CLOSE_STATUS code, String reason)
        {
                if (protocol == PROTOCOL.GAME)
                {
                        synchronized(this)
                        {
                                assert this.game == null;
                                
                                workable.runOnMain(new GameProtocolConnection.CallGameClientListener(this.gameClientListener, null, 0, code, reason));
                        }
                        
                        log.log(Level.WARNING, "Error establish game connection to the server {0}: {1}", new Object[]{code, reason});
                }
                else
                {
                        log.log(Level.WARNING, "???");
                }
        }
        
        @Override
        @ThreadSafe
        public void wstNewProtocol(boolean server, SessionToken sessionToken, PROTOCOL protocol)
        {
                synchronized(this)
                {
                        if (this.game != null)
                        {
                                // only allow one game session
                                log.log(Level.WARNING, "Received a second session token from the server. The old one has been removed");
                                
                                this.webSocketTransport.closeSession(game.session, WS_CLOSE_STATUS.CLOSING_PREVIOUS_SESSION, null);
                        }
                        this.game = new GameProtocolConnection(workable, webSocketTransport, sessionToken, false, gameClientListener);
                        this.game.created();
                        
                        int sockets = this.webSocketTransport.getSocketCount(sessionToken, protocol);
                        while (sockets < desired_sockets)
                        {
                                this.webSocketTransport.establishClientConnection(uri, sessionToken, PROTOCOL.GAME, CLIENT_PROTO_VERSION);
                                ++sockets;
                        }
                        
                }
        }

        
        @Override
        @ThreadSafe
        public void wstDropProtocol(boolean server, SessionToken sessionToken, PROTOCOL protocol)
        {
                synchronized(this)
                {
                        if (this.game == null)
                        {
                                log.log(Level.WARNING, "Received a wstDropProtocol in an incorrect state");
                                return;
                        }
                        
                        if (this.game.session.equals(sessionToken))
                        {
                                this.game.removed();
                                this.game = null;
                        }
                        else
                        {
                                log.log(Level.WARNING, "Received a wstDropProtocol about an unknown session token or one that has recently been removed");
                        }
                }
        }

        @Override
        @ThreadSafe
        public void wstMessage(boolean server, SessionToken sessionToken, PROTOCOL protocol, int protocolVersion, ByteBuffer message, long receivedAt)
        {
                GameProtocolConnection game;
                synchronized(this)
                {
                        game = this.game;
                        if (game == null)
                        {
                                log.log(Level.WARNING, "Received a binary wstMessage event in an incorrect state");
                                return;
                        }
                        
                        if (!game.session.equals(sessionToken))
                        {
                                log.log(Level.WARNING, "Received a wstMessage about an unknown session token or one that has recently been removed");
                                return;
                                
                        }
                }
                
                // this is an expensive call, make sure we do not have a lock
                game.parseMessage(message, receivedAt);
        }

        @Override
        @ThreadSafe
        public void wstMessage(boolean server, SessionToken sessionToken, PROTOCOL protocol, int protocolVersion, String message, long receivedAt)
        {
                log.log(Level.WARNING, "Received an unicode wstMessage event however unicode should not be used in this protocol");
        }

        @Override
        public void wstNewConnection(boolean server, SessionToken sessionToken, PROTOCOL protocol)
        {
                synchronized(this)
                {
                        if (this.game == null)
                        {
                                log.log(Level.WARNING, "Received a wstNewConnection in an incorrect state");
                                return;
                        }
                        
                        this.game.connectionAdded();
                }
        }

        @Override
        public void wstDropConnection(boolean server, SessionToken sessionToken, PROTOCOL protocol, WS_CLOSE_STATUS code, String reason)
        {
                synchronized(this)
                {
                        if (this.game == null)
                        {
                                log.log(Level.WARNING, "Received a wstDropConnection in an incorrect state");
                                return;
                        }
                        
                        log.log(Level.WARNING, "Client game connection dropped {0}: {1}", new Object[]{code, reason});
                        this.game.connectionDropped(code, reason);
                }
        }

        

}
