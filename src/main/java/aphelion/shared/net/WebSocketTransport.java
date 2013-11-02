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

import aphelion.shared.event.LoopEvent;
import aphelion.shared.swissarmyknife.MySecureRandom;
import aphelion.shared.swissarmyknife.ThreadSafe;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.WebSocketListener;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;

/**
 * Manages all the websockets you need for your protocol.
 * Each client-server combination is identified by a SessionToken.
 * Multiple websockets and protocols may share the same SessionToken.
 * Multiple websockets for the same protocol will use Inverse Multiplexing 
 * to send their data to lessen the effects of packetloss.
 * Multiple protocols may share the same SessionToken, this might be useful if 
 * you want chat on a different socket than the game protocol.
 * 
 * TODO:
 *      max websocket message size
 *      timeout sockets
 * @author Joris
 */

public class WebSocketTransport implements HttpWebSocketServerListener, LoopEvent
{
        private static final Logger log = Logger.getLogger("aphelion.net");
        /**
         * When calling send(byte[]) make sure there are this many
         * unused bytes at the beginning of the byte array.
         */
        public static final int SEND_RESERVEDPREFIX_BYTES = 1;
        private final LinkedList<WebSocket> serverUnitializedWebsockets = new LinkedList<>();
        private final LinkedList<MyWebSocketClient> unitializedClients = new LinkedList<>();
        private final HashMap<SessionToken, LinkedList<WebSocket>> websockets = new HashMap<>();
        private final WebSocketTransportListener listener;
        private final ConcurrentLinkedQueue<CloseDTO> closeQueue = new ConcurrentLinkedQueue<>();
        private final LinkedList<WeakReference<Thread>> clientThreads = new LinkedList<>();
        
        // todo use the timeout value of WebSocketClient when this is implemented in the lib
        private static final long CLIENT_CONNECT_TIMEOUT = 10_000_000_000L; 
        private static final long SERVER_INIT_TIMEOUT = 10_000_000_000L;

        public WebSocketTransport(WebSocketTransportListener listener)
        {
                this.listener = listener;
        }

        @Override
        /** Do a single loop to update internal state.
         * NOTE: when using any of the close() functions make sure loop() is called at least once before
         * forgetting about this object.
         */
        public void loop(long systemNanoTime, long unused)
        {
                // do not use unused (this method is not just called by TickedEventLoop)
                synchronized (this.serverUnitializedWebsockets)
                {
                        Iterator<WebSocket> it = this.serverUnitializedWebsockets.iterator();
                        while (it.hasNext())
                        {
                                WebSocket ws = it.next();
                                WebSocketData wsd = WebSocketData.get(ws);

                                if (systemNanoTime - wsd.openedAt > SERVER_INIT_TIMEOUT)
                                {
                                        log.log(Level.WARNING, "Dropped unitialized websocket as a server ({0}) due to timeout ({1} nanoseconds). ", new Object[] { ws.getRemoteSocketAddress(), systemNanoTime - wsd.openedAt });
                                        close(ws, WS_CLOSE_STATUS.INIT_TIMEOUT);
                                        it.remove();
                                }
                        }
                }

                synchronized (this.unitializedClients)
                {
                        Iterator<MyWebSocketClient> it = this.unitializedClients.iterator();
                        while (it.hasNext())
                        {
                                MyWebSocketClient client = it.next();

                                if (systemNanoTime - client.openedAt > CLIENT_CONNECT_TIMEOUT)
                                {
                                        log.log(Level.WARNING, "Dropped unitialized websocket as a client due to timeout ({0} nanoseconds).", systemNanoTime - client.openedAt);
                                        
                                        // this will fail if the thread spawned by WebSocketClient; 
                                        // has not had a chance to run yet. 
                                        close(client.getConnection(), WS_CLOSE_STATUS.INIT_TIMEOUT);
                                        
                                        it.remove();
                                }
                        }
                }
                
                CloseDTO closeDto;
                while ( (closeDto = closeQueue.poll()) != null)
                {
                        closeDto.ws.close(closeDto.code.id, closeDto.message); // may generate an event immediately, which this class listens to
                }
        }

        /** Establish a new connection with a server.
         * If there already is a connection with the same session token and protocol.
         * Data will be sent using alternating sockets to reduce the impact of packetloss.
         *
         * @param uri
         * @param session if null, the server will generate a new session.
         * The server will also generate a new one if this token is invalid.
         * @param protocol
         * @param protocolVersion
         */
        @ThreadSafe
        public void establishClientConnection(URI uri, SessionToken session, PROTOCOL protocol, int protocolVersion)
        {
                MyWebSocketClient client;
                
                log.log(Level.INFO, "Attempting to establish connection as a client to {0}", uri);

                client = new MyWebSocketClient(uri);

                client.requestedSession = session;
                client.requestedProtocol = protocol;
                client.requestedProtocolVersion = protocolVersion;
                
                // Same as client.connect()
                if (client.thread != null)
                {
                        client.thread.interrupt();
                }
                client.thread = new Thread(client); // this thread should auto close if the socket gets closed
                client.thread.setName("WebSocketClient-" + client.thread.getId());
                client.thread.start();

                client.openedAt = System.nanoTime();
                synchronized (WebSocketTransport.this.unitializedClients)
                {
                        WebSocketTransport.this.unitializedClients.add(client);
                }
                
                synchronized (WebSocketTransport.this.clientThreads)
                {
                        WebSocketTransport.this.clientThreads.add(new WeakReference<>(client.thread));
                }
        }
        
        /** Return the amount of sockets for a particular session and protocol.
         *
         * @param session
         * @param protocol Use NONE to match any protocol
         * @return
         */
        @ThreadSafe
        public int getSocketCount(SessionToken session, PROTOCOL protocol)
        {
                synchronized (this.websockets)
                {
                        LinkedList<WebSocket> sockets = this.websockets.get(session);
                        if (sockets == null)
                        {
                                return 0;
                        }
                        
                        int count = 0;
                        for (WebSocket ws : sockets)
                        {
                                WebSocketData wsd = WebSocketData.get(ws);
                                if (protocol == PROTOCOL.NONE || wsd.protocol == protocol)
                                {
                                        count++;
                                }
                        }
                        
                        return count;
                }
        }
        
        /** Send some bytes to the other party.
         *
         * @param session The session that this message is a part of. This is used to determine the correct socket.
         * @param protocol The protocol that is sending the message. This is used to determine the correct socket.
         * @param bytes A byte array, the first SEND_RESERVEDPREFIX_BYTES bytes are set by this method and should be unused.
         * @throws NoSuitableConnection There are no connections for this protocol
         */
        
        @ThreadSafe
        public void send(SessionToken session, PROTOCOL protocol, byte[] bytes) throws NoSuitableConnection
        {
                // Note: the websocket library will immediately encode the payload into websocket frames
                // avoid calling this from the main thread

                WebSocket ws;

                synchronized (this.websockets)
                {
                        ws = findOptimalSocket(session, protocol);
                        WebSocketData wsd = WebSocketData.get(ws);

                        if (ws == null)
                        {
                                throw new NoSuitableConnection();
                        }

                        wsd.unacked += 1;
                        
                        // Send the number of messages we received previously to the client
                        if (wsd.ackreply > 254)
                        {
                                // Reserve the value 255 for future use
                                bytes[0] = (byte) 254;
                                wsd.ackreply -= 254;
                        }
                        else
                        {
                                bytes[0] = (byte) wsd.ackreply;
                                wsd.ackreply = 0;
                        }
                        
                        assert wsd.ackreply >= 0;
                }

                ws.send(bytes);
        }
        
        /** Send a unicode message to the other party.
         * 
         * Packetloss optimization (using an acknowledgment count) is not supported with this send method.
         * 
         * If multiple sockets are used (which is not very useful with unicode send ), sockets are 
         * picked as follows:
         * If mixing binary send() and unicode send() on the same protocol (which should be avoided), 
         * The results of the binary send() determine the sockets that are used with the unicode send(), 
         * the unicode send() itself does not directly infleunce the sockets that are used.
         * 
         * If only unicode send() is used on the same protocol, all the acknowledgment counters stay at 0.
         * Alternating sockets will simply be used in this case.
         *
         * @param session The session that this message is a part of. This is used to determine the correct socket.
         * @param protocol The protocol that is sending the message. This is used to determine the correct socket.
         * @param message A unicode string
         * @throws NoSuitableConnection There are no connections for this protocol
         */
        @ThreadSafe
        public void send(SessionToken session, PROTOCOL protocol, String message) throws NoSuitableConnection
        {
                WebSocket ws = findOptimalSocket(session, protocol);

                if (ws == null)
                {
                        throw new NoSuitableConnection();
                }
                
                ws.send(message);
        }
        
        private static class ListenerDTO
        {
                final boolean server;
                final SessionToken session;
                final PROTOCOL protocol;

                ListenerDTO(boolean server, SessionToken session, PROTOCOL protocol)
                {
                        this.server = server;
                        this.session = session;
                        this.protocol = protocol;
                }
                
                @Override
                public int hashCode()
                {
                        int hash = 3;
                        hash = 13 * hash + (this.server ? 1 : 0);
                        hash = 13 * hash + (this.session != null ? this.session.hashCode() : 0);
                        hash = 13 * hash + (this.protocol != null ? this.protocol.hashCode() : 0);
                        return hash;
                }

                @Override
                public boolean equals(Object obj)
                {
                        if (obj == null)
                        {
                                return false;
                        }
                        if (!(obj instanceof ListenerDTO))
                        {
                                return false;
                        }
                        final ListenerDTO other = (ListenerDTO) obj;
                        if (this.server != other.server)
                        {
                                return false;
                        }
                        if (this.session != other.session && (this.session == null || !this.session.equals(other.session)))
                        {
                                return false;
                        }
                        if (this.protocol != other.protocol)
                        {
                                return false;
                        }
                        return true;
                }
                
                
        }
        
        @ThreadSafe
        public void closeAll(WS_CLOSE_STATUS code, String reason)
        {
                HashSet<ListenerDTO> dropEvents = new HashSet<>();
                
                // .close() fires onClose events
                synchronized(this.websockets)
                {
                        for (LinkedList<WebSocket> list : this.websockets.values())
                        {
                                for (WebSocket ws : list)
                                {
                                        WebSocketData wsd = WebSocketData.get(ws);
                                        listener.wstDropConnection(ws instanceof ServerWebSocketImpl, wsd.session, wsd.protocol, code, reason);
                                        
                                        // defer so that the hashset can ensure this event is only fired once
                                        dropEvents.add(new ListenerDTO(ws instanceof ServerWebSocketImpl, wsd.session, wsd.protocol));
                                        
                                        // a socket may not have a SessionToken if it is not in the websockets list
                                        // and all sockets in the websockets have a SessionToken
                                        wsd.session = null; // settings this to null prevents from the drop event 
                                        // being fired in the websocket close event
                                        close(ws, code, reason);
                                }
                        }
                        
                        this.websockets.clear();
                }
                
                synchronized(this.unitializedClients)
                {
                        for (MyWebSocketClient client : this.unitializedClients)
                        {
                                WebSocket ws = client.getMyConnection();
                                if (ws != null)
                                {
                                        close(ws, code, reason);
                                }
                        }
                        
                        this.unitializedClients.clear();
                }
                
                synchronized(this.serverUnitializedWebsockets)
                {
                        for (WebSocket ws : this.serverUnitializedWebsockets)
                        {
                                close(ws, code, reason); 
                        }
                        
                        this.serverUnitializedWebsockets.clear();
                }
                
                for (ListenerDTO dto : dropEvents)
                {
                        listener.wstDropProtocol(dto.server, dto.session, dto.protocol);
                }
                
            
        }
        
        @ThreadSafe
        public void closeAll(WS_CLOSE_STATUS code)
        {
                closeAll(code, "");
        }
        
        @SuppressWarnings("deprecation")
        public void stopClients() // only call from main
        {
                log.log(Level.INFO, "Stopping all clients...");
                closeAll(WS_CLOSE_STATUS.NORMAL);
                
                long nanoTime = System.nanoTime();
                
                loop(nanoTime, nanoTime); // make sure closeQueue is emptied
                
                synchronized(this.clientThreads)
                {
                        for (WeakReference<Thread> threadRef : this.clientThreads)
                        {
                                Thread thread = threadRef.get();
                                if (thread != null)
                                {
                                        try
                                        {
                                                thread.join(1000);
                                                thread.interrupt();
                                                thread.join(1000);
                                                if (thread.isAlive())
                                                {
                                                        log.log(Level.SEVERE, "Websocket Thread is not stopping. Forcing the thread to stop...");
                                                        thread.stop();
                                                }
                                        }
                                        catch (InterruptedException ex)
                                        {
                                                Thread.currentThread().interrupt();
                                                return;
                                        }
                                }
                        }
                }
        }
        
        @ThreadSafe
        public void closeSession(SessionToken session, WS_CLOSE_STATUS code, String message)
        {
                closeSession(session, PROTOCOL.NONE, code, message);
        }
        
        @ThreadSafe
        public void closeSession(SessionToken session, WS_CLOSE_STATUS code)
        {
                closeSession(session, PROTOCOL.NONE, code, "");
        }
        
        @ThreadSafe
        public void closeSession(SessionToken session, PROTOCOL protocol, WS_CLOSE_STATUS code)
        {
                closeSession(session, protocol, code, "");
        }
        
        @ThreadSafe
        public void closeSession(SessionToken session, PROTOCOL protocol, WS_CLOSE_STATUS code, String reason)
        {
                HashSet<ListenerDTO> dropEvents = new HashSet<>();
                
                synchronized(this.websockets)
                {
                        LinkedList<WebSocket> list = this.websockets.remove(session);
                        if (list != null)
                        {
                                for (WebSocket ws : list)
                                {
                                        WebSocketData wsd = WebSocketData.get(ws);
                                        if (protocol == PROTOCOL.NONE || wsd.protocol == protocol)
                                        {
                                                listener.wstDropConnection(ws instanceof ServerWebSocketImpl, wsd.session, wsd.protocol, code, reason);
                                                dropEvents.add(new ListenerDTO(ws instanceof ServerWebSocketImpl, wsd.session, wsd.protocol));
                                        
                                                // a socket may not have a SessionToken if it is not in the websockets list
                                                // and all sockets in the websockets have a SessionToken
                                                wsd.session = null; // settings this to null prevents from the drop event being fired
                                                close(ws, code, reason); // may generate an event immediately
                                        }
                                }
                        }
                }
                
                for (ListenerDTO dto : dropEvents)
                {
                        listener.wstDropProtocol(dto.server, dto.session, dto.protocol);
                }
        }
        
        private static class CloseDTO
        {
                final WebSocket ws;
                final WS_CLOSE_STATUS code;
                final String message;

                CloseDTO(WebSocket ws, WS_CLOSE_STATUS code, String message)
                {
                        this.ws = ws;
                        this.code = code;
                        this.message = message;
                }
                
        }
        private void close(WebSocket ws, WS_CLOSE_STATUS code, String message)
        {
                if (ws == null) { return; }
                closeQueue.add(new CloseDTO(ws, code, message));
        }
        
        private void close(WebSocket ws, WS_CLOSE_STATUS code)
        {
                close(ws, code, "");
        }
        
        private WeakReference<WebSocket> findOptimalSocketPrevious;

        /** Find the socket with the least amount of messages that have not been acknowledged.
         */
        @ThreadSafe
        private WebSocket findOptimalSocket(SessionToken session, PROTOCOL protocol)
        {
                synchronized (this.websockets)
                {
                        WebSocket previous = findOptimalSocketPrevious == null ? null : findOptimalSocketPrevious.get();

                        LinkedList<WebSocket> sockets = this.websockets.get(session);
                        if (sockets == null)
                        {
                                return null;
                        }

                        Iterator<WebSocket> it = sockets.iterator();
                        boolean foundPrevious = false;

                        if (previous != null)
                        {
                                // This ensures the previously returned connection is always the last one to be checked.
                                // Suppose the "unacked" value remains the same for every connection, this method will then 
                                // simply return the next connection on the list. ( 123 123 123 123 ...).
                                // This ensures that if there are more connections than we need (at the moment), the extra connections
                                // will still be used. So that they do not timeout and their congestion window will increase.

                                // Find the location of the previous returned connection
                                while (it.hasNext())
                                {
                                        WebSocket conn = it.next();
                                        if (conn == previous)
                                        {
                                                foundPrevious = true;
                                                break;
                                        }
                                }

                                if (!it.hasNext())
                                {
                                        // Previous is either the last value, or does not exist anymore
                                        it = sockets.iterator(); // start at the beginning
                                }
                        }

                        int bestValue = 0;
                        WebSocket bestConn = null;

                        while (it.hasNext()) // start with the element after the previous one
                        {
                                WebSocket ws = it.next();
                                WebSocketData wsd = WebSocketData.get(ws);

                                if (!it.hasNext() && foundPrevious)
                                {
                                        // this is the last list item, but we have to start 
                                        // over because we started somewhere in the middle of the list.
                                        it = sockets.iterator();
                                }

                                if (!wsd.protocol.equals(protocol))
                                {
                                        continue;
                                }

                                if (bestConn == null)
                                {
                                        // start off with the first connection we find
                                        bestConn = ws;
                                        bestValue = wsd.unacked;
                                }
                                else
                                {
                                        if (wsd.unacked < bestValue)
                                        {
                                                // This connection has less unacknowledged messages than the previous one
                                                bestConn = ws;
                                                bestValue = wsd.unacked;
                                        }
                                }

                                if (ws == previous) // we are where we started off
                                {
                                        break;
                                }
                        }

                        if (bestConn != null)
                        {
                                findOptimalSocketPrevious = new WeakReference<>(bestConn);
                        }

                        return bestConn;
                }
        }

        @Override
        @ThreadSafe
        public boolean wssConnect(SelectionKey key)
        {
                return true;
        }

        @Override
        @ThreadSafe
        public void wssOpen(WebSocket ws, ClientHandshake handshake)
        {
                log.log(Level.INFO, "New websocket connection from {0}", ws.getRemoteSocketAddress());
                // can not do anything with it yet, the client must send an initialization packet first
                WebSocketData wsd = WebSocketData.get(ws);

                synchronized (this.serverUnitializedWebsockets)
                {
                        wsd.openedAt = System.nanoTime();
                        this.serverUnitializedWebsockets.add(ws);
                }
        }

        @Override
        @ThreadSafe
        public void wssClose(WebSocket conn, int code, String reason, boolean remote)
        {
                log.log(Level.INFO, "Closed websocket connection from {0}", conn.getRemoteSocketAddress());
                WebSocketData connd = WebSocketData.get(conn);
                
                synchronized (this.serverUnitializedWebsockets)
                {
                        this.serverUnitializedWebsockets.remove(conn);
                }
                
                synchronized (this.websockets)
                {
                        if (connd.session != null)
                        {
                                assert connd.protocol != null && connd.protocol != PROTOCOL.NONE;

                                LinkedList<WebSocket> websocketList = this.websockets.get(connd.session);
                                boolean removed = websocketList.remove(conn);
                                assert removed;

                                boolean droppedAllForProtocol = true;
                                for (WebSocket ws : websocketList)
                                {
                                        WebSocketData wsd = WebSocketData.get(ws);
                                        if (wsd.protocol.equals(connd.protocol))
                                        {
                                                droppedAllForProtocol = false;
                                                break;
                                        }
                                }
                                
                                WS_CLOSE_STATUS status = WS_CLOSE_STATUS.fromId(code);
                                if (status == null)
                                {
                                        status = WS_CLOSE_STATUS.UNKNOWN;
                                        log.log(Level.WARNING, "Unknown close code {0}", code);
                                }

                                listener.wstDropConnection(true, connd.session, connd.protocol, status, reason);
                                if (droppedAllForProtocol)
                                {
                                        listener.wstDropProtocol(true, connd.session, connd.protocol);
                                }

                                connd.session = null;
                        }
                }
                
        }

        @Override
        @ThreadSafe
        public void wssMessage(WebSocket ws, String message)
        {
                long now = System.nanoTime();
                WebSocketData wsd = WebSocketData.get(ws);
                
                if (wsd.session == null)
                {
                        log.log(Level.WARNING, "WebSocket UTF-8 payload ignored because the client has not sent a handshake yet from {0}", ws.getRemoteSocketAddress());
                }
                else
                {
                        SessionToken session;
                        PROTOCOL protocol;
                        int protocolVersion;
                                
                        synchronized(this.websockets)
                        {
                                session = wsd.session;
                                protocol = wsd.protocol;
                                protocolVersion = wsd.protocolVersion;
                        }
                        
                        assert protocol != null && protocol != PROTOCOL.NONE;
                        
                        listener.wstMessage(true, session, protocol, protocolVersion, message, now);
                }
        }

        @Override
        @ThreadSafe
        public void wssMessage(WebSocket conn, ByteBuffer message)
        {
                long now = System.nanoTime();
                WebSocketData connd = WebSocketData.get(conn);
                
                if (connd.session == null)
                {
                        boolean newProtocol;
                        SessionToken session;
                        PROTOCOL protocol;
                        
                        synchronized (this.websockets)
                        {
                                /*// After establishment, the client must send the following packet:
                                 struct C2SInitPacket { // Big Endian, 39 bytes
                                 i16 aphelion_protocol;
                                 i32 protocol_version;
                                 i8 has_session_token;
                                 u8[32] session_token;
                                 }*/

                                if (message.remaining() != 39)
                                {
                                        close(conn, WS_CLOSE_STATUS.MALFORMED_INIT_PACKET, "Malformed handshake");
                                        log.log(Level.WARNING, "Malformed handshake from {0}", conn.getRemoteSocketAddress());
                                        return;
                                }

                                short aphelion_protocol = message.getShort(); // 2 bytes
                                int protocol_version = message.getInt(); // 4 bytes
                                boolean has_session_token = message.get() != 0; // 1 byte
                                byte[] session_token = new byte[32];
                                message.get(session_token); // 32 bytes

                                protocol = PROTOCOL.fromId(aphelion_protocol);

                                if (protocol == null || protocol == PROTOCOL.NONE)
                                {
                                        close(conn, WS_CLOSE_STATUS.INVALID_PROTOCOL, "Invalid protocol id");
                                        return;
                                }

                                if (!listener.wstIsValidProtocol(protocol))
                                {
                                        close(conn, WS_CLOSE_STATUS.INVALID_PROTOCOL, "Rejected protocol id");
                                        return;
                                }

                                int versionCompare = listener.wstIsValidProtocolVersion(protocol, protocol_version);
                                if (versionCompare != 0)
                                {
                                        if (versionCompare < 0)
                                        {
                                                close(conn, WS_CLOSE_STATUS.INVALID_PROTOCOL_VERSION_OLD, "Invalid protocol version. Try updating your client.");
                                        }
                                        else
                                        {
                                                close(conn, WS_CLOSE_STATUS.INVALID_PROTOCOL_VERSION_NEW, "Invalid protocol version. The server needs to be updated (or downgrade your client).");
                                        }
                                        return;
                                }

                                boolean newClient = false;
                                


                                session = null;
                                LinkedList<WebSocket> websocketList = null;

                                if (has_session_token)
                                {
                                        session = new SessionToken(session_token);
                                        websocketList = this.websockets.get(session);
                                        
                                        // make sure the session token object is unique
                                        for (SessionToken existing_token : this.websockets.keySet())
                                        {
                                                if (existing_token.equals(session))
                                                {
                                                        session = existing_token;
                                                }
                                        }
                                        
                                        if (websocketList == null || websocketList.isEmpty())
                                        {
                                                this.websockets.remove(session);
                                                websocketList = null;
                                                log.log(Level.WARNING, "Invalid session token from {0}", conn.getRemoteSocketAddress());
                                        }
                                }

                                // invalid session token, or not specified (generate a new one)
                                if (websocketList == null)
                                {
                                        newClient = true;

                                        websocketList = new LinkedList<>();

                                        session = SessionToken.generate();

                                        do
                                        {
                                                session = SessionToken.generate();
                                        } while (this.websockets.containsKey(session));


                                        this.websockets.put(session, websocketList);
                                }

                                connd.session = session;
                                connd.protocol = protocol;
                                connd.protocolVersion = protocol_version;
                                connd.initialized = true;

                                newProtocol = true;
                                for (WebSocket ws : websocketList)
                                {
                                        WebSocketData wsd = WebSocketData.get(ws);
                                        if (wsd.protocol.equals(protocol))
                                        {
                                                newProtocol = false;
                                                break;
                                        }
                                }

                                websocketList.add(conn);


                                if (newClient)
                                {
                                        // add more entropy

                                        MySecureRandom.addSeed(conn.getRemoteSocketAddress());
                                        MySecureRandom.addTimeSeed();
                                }

                                /*// The server replies with:
                        
                                 struct S2CInitPacket { // Big Endian, 32 bytes
                                 u8[32] your_session_token;
                                 }
                                 // Or the server may reply with a WebSocket close frame (with an error description):
                                 4000 = invalid protocol
                                 4001 = invalid protocol version
                                 4002 = malformed C2SInitPacket (invalid length)
                                 */

                                // send handshake reply
                                assert connd.session != null;
                                byte[] token = new byte[32];
                                connd.session.get(token);
                                conn.send(token);

                                
                                websocketList = this.websockets.get(connd.session);
                                log.log(Level.INFO, "There are now {0} connections for the session {1}", new Object[] { websocketList.size(), connd.session });
                        }

                        synchronized (this.serverUnitializedWebsockets)
                        {
                                this.serverUnitializedWebsockets.remove(conn);
                        }

                        if (newProtocol)
                        {
                                listener.wstNewProtocol(true, session, protocol);
                        }
                        listener.wstNewConnection(true, session, protocol);
                }
                else
                {
                        SessionToken session;
                        PROTOCOL protocol;
                        int protocolVersion;
                        
                        int ack = message.get(); // moves the position of the buffer
                                
                        synchronized(this.websockets)
                        {
                                session = connd.session;
                                protocol = connd.protocol;
                                protocolVersion = connd.protocolVersion;
                                
                                connd.unacked -= ack;
                                if (connd.unacked < 0)
                                {
                                        log.log(Level.WARNING, "conn.unacked is < 0");
                                }
                        }
                        
                        assert protocol != null && protocol != PROTOCOL.NONE;
                        
                        listener.wstMessage(true, session, protocol, protocolVersion, message, now);
                }
        }

        @Override
        @ThreadSafe
        public void wssError(WebSocket conn, Exception ex)
        {
                try
                {
                        log.log(Level.WARNING, "WebSocket error from " + conn.getRemoteSocketAddress(), ex);
                }
                catch (Throwable e)
                {
                        log.log(Level.WARNING, "WebSocket error", ex);
                }
        }

        private class MyWebSocketClient extends WebSocketClient
        {
                public long openedAt;
                SessionToken requestedSession;
                PROTOCOL requestedProtocol;
                int requestedProtocolVersion;
                Thread thread;

                MyWebSocketClient(URI serverURI)
                {
                        super(serverURI, new Draft_17());
                }
                

                @Override
                @ThreadSafe
                public void onOpen(ServerHandshake handshakedata)
                {
                        /*// After establishment, the client must send the following packet:
                         struct C2SInitPacket { // Big Endian, 39 bytes
                         i16 aphelion_protocol;
                         i32 protocol_version;
                         i8 has_session_token;
                         u8[32] session_token;
                         }*/

                        ByteBuffer data = ByteBuffer.allocate(39);

                        synchronized (WebSocketTransport.this.unitializedClients)
                        {
                                data.putShort(requestedProtocol.id); // 2 bytes
                                data.putInt(requestedProtocolVersion); // 4 bytes
                                data.put((byte) (requestedSession == null ? 0 : 1)); // 1 byte
                                if (requestedSession != null)
                                {
                                        byte[] token = new byte[32];
                                        requestedSession.get(token);
                                        data.put(token);
                                }
                                else
                                {
                                        data.putLong(0); // 8 bytes
                                        data.putLong(0);
                                        data.putLong(0);
                                        data.putLong(0);
                                }
                        }

                        this.send(data.array());
                }

                @Override
                @ThreadSafe
                public void onMessage(String message)
                {
                        long now = System.nanoTime();
                        WebSocket ws = this.getMyConnection();
                        WebSocketData wsd = WebSocketData.get(ws);

                        if (wsd.session == null)
                        {
                                log.log(Level.WARNING, "WebSocket UTF-8 payload ignored because the handshake has not yet been completed. {0}", ws.getRemoteSocketAddress());
                        }
                        else
                        {
                                SessionToken session;
                                PROTOCOL protocol;
                                int protocolVersion;

                                synchronized(WebSocketTransport.this.websockets)
                                {
                                        session = wsd.session;
                                        protocol = wsd.protocol;
                                        protocolVersion = wsd.protocolVersion;
                                }

                                assert protocol != null && protocol != PROTOCOL.NONE;
                                
                                listener.wstMessage(false, session, protocol, protocolVersion, message, now);
                        }
                }

                @Override
                public void onMessage(ByteBuffer bytes)
                {
                        long now = System.nanoTime();
                        WebSocket conn = this.getMyConnection();
                        WebSocketData connd = WebSocketData.get(conn);

                        if (connd.session == null)
                        {
                                // handshake reply
                                if (bytes.remaining() != 32)
                                {
                                        WebSocketTransport.this.close(conn, WS_CLOSE_STATUS.MALFORMED_INIT_PACKET, "Malformed handshake");
                                        log.log(Level.WARNING, "Malformed handshake reply from {0}", conn.getRemoteSocketAddress());
                                        return;
                                }

                                byte[] session_token = new byte[32];
                                bytes.get(session_token, 0, 32);

                                boolean newProtocol = true;
                                synchronized (WebSocketTransport.this.websockets)
                                {
                                        connd.protocol = this.requestedProtocol;
                                        connd.protocolVersion = this.requestedProtocolVersion;
                                        connd.session = new SessionToken(session_token);
                                        connd.initialized = true;

                                        // make sure the session token object is unique
                                        for (SessionToken existing_token : WebSocketTransport.this.websockets.keySet())
                                        {
                                                if (existing_token.equals(connd.session))
                                                {
                                                        connd.session = existing_token;
                                                }
                                        }

                                
                                        LinkedList<WebSocket> websocketList;

                                        websocketList = WebSocketTransport.this.websockets.get(connd.session);
                                        if (websocketList == null)
                                        {
                                                websocketList = new LinkedList<>();
                                                WebSocketTransport.this.websockets.put(connd.session, websocketList);
                                        }

                                        for (WebSocket ws : websocketList)
                                        {
                                                WebSocketData wsd = WebSocketData.get(ws);
                                                if (wsd.protocol.equals(connd.protocol))
                                                {
                                                        newProtocol = false;
                                                        break;
                                                }
                                        }

                                        websocketList.add(conn);
                                        
                                        websocketList = WebSocketTransport.this.websockets.get(connd.session);
                                        log.log(Level.INFO, "There are now {0} connections for the session {1}", new Object[] { websocketList.size(), connd.session });
                                }

                                if (newProtocol)
                                {
                                        WebSocketTransport.this.listener.wstNewProtocol(false, connd.session, connd.protocol);
                                }
                                
                                WebSocketTransport.this.listener.wstNewConnection(false, connd.session, connd.protocol);
                                

                                synchronized (WebSocketTransport.this.unitializedClients)
                                {
                                        WebSocketTransport.this.unitializedClients.remove(this);
                                }
                        }
                        else
                        {
                                SessionToken session;
                                PROTOCOL protocol;
                                int protocolVersion;

                                synchronized(WebSocketTransport.this.websockets)
                                {
                                        session = connd.session;
                                        protocol = connd.protocol;
                                        protocolVersion = connd.protocolVersion;
                                        
                                        int ack = bytes.get(); // moves the position of the buffer
                                        connd.unacked -= ack;
                                        if (connd.unacked < 0)
                                        {
                                                log.log(Level.WARNING, "conn.unacked is < 0");
                                        }
                                }
                                
                                assert protocol != null && protocol != PROTOCOL.NONE;

                                listener.wstMessage(false, session, protocol, protocolVersion, bytes, now);
                        }
                }

                @Override
                @ThreadSafe
                public void onClose(int code, String reason, boolean remote)
                {
                        WebSocket conn = this.getMyConnection();
                        WebSocketData connd = WebSocketData.get(conn);

                        log.log(Level.INFO, "Closed websocket connection as a client from {0}", conn.getRemoteSocketAddress());

                        WS_CLOSE_STATUS status = WS_CLOSE_STATUS.fromId(code);
                        if (status == null)
                        {
                                status = WS_CLOSE_STATUS.UNKNOWN;
                                log.log(Level.WARNING, "Unknown close code {0}", code);
                        }
                        
                        synchronized (WebSocketTransport.this.unitializedClients)
                        {
                                WebSocketTransport.this.unitializedClients.remove(this);
                        }
                        
                        SessionToken session;
                        boolean initialized;
                        PROTOCOL protocol;
                        boolean fireDropListener = false;
                        
                        synchronized (WebSocketTransport.this.websockets)
                        {
                                session = connd.session;
                                initialized = connd.initialized;
                                protocol = connd.protocol;
                                
                                if (session != null)
                                {
                                        assert connd.protocol != null && connd.protocol != PROTOCOL.NONE;

                                        LinkedList<WebSocket> websocketList = WebSocketTransport.this.websockets.get(session);
                                        assert websocketList != null;
                                        boolean removed = websocketList.remove(conn);
                                        assert removed;

                                        fireDropListener = true;
                                        for (WebSocket ws : websocketList)
                                        {
                                                WebSocketData wsd = WebSocketData.get(ws);
                                                if (wsd.protocol.equals(protocol))
                                                {
                                                        fireDropListener = false;
                                                        break;
                                                }
                                        }
                                        
                                        if (websocketList.isEmpty())
                                        {
                                                WebSocketTransport.this.websockets.remove(session);
                                        }
                                        
                                        
                                        
                                        connd.session = null;
                                        listener.wstDropConnection(false, session, protocol, status, reason);
                                }
                        }
                        
                        
                        if (fireDropListener)
                        {
                                listener.wstDropProtocol(false, session, protocol);
                        }
                        
                        if (!initialized)
                        {
                                listener.wstClientEstablishFailure(this.requestedSession, this.requestedProtocol, status, reason);
                        }
                }

                @Override
                @ThreadSafe
                public void onError(Exception ex)
                {
                        WebSocket conn;
                        conn = getMyConnection();
                        if (conn == null)
                        {
                                log.log(Level.WARNING, "WebSocket client error (no connection)", ex);
                        }
                        else
                        {
                                log.log(Level.WARNING, "WebSocket client error from " + conn.getRemoteSocketAddress(), ex);
                        }
                }

                @ThreadSafe
                public WebSocket getMyConnection()
                {
                        return (WebSocket) this.getConnection();
                }

                /*@Override
                public String getResourceDescriptor()
                {
                        WebSocket w = this.getConnection();
                        if (w == null)
                        {
                                return null;
                        }
                        else
                        {
                                return w.getResourceDescriptor();
                        }
                }*/
        }
        
        @SuppressWarnings("serial")
        public static class NoSuitableConnection extends Exception
        {
        }
        
        public static final class ServerWebSocketImpl extends WebSocketImpl
        {
                public ServerWebSocketImpl(WebSocketListener listener)
                {
                        // Draft_17 corresponds to Sec-WebSocket-Version: 13 which is RFC 6455
                        super(listener, Arrays.asList(new Draft[]{new Draft_17()}));
                }
        }
}
