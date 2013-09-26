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


import aphelion.shared.net.PROTOCOL;
import aphelion.shared.net.SessionToken;
import aphelion.shared.net.WS_CLOSE_STATUS;
import aphelion.shared.net.WebSocketTransport;
import aphelion.shared.net.WebSocketTransportListener;
import aphelion.shared.net.protobuf.Ping.PingRequest;
import aphelion.shared.net.protobuf.Ping.PingResponse;
import aphelion.shared.swissarmyknife.ThreadSafe;
import com.google.protobuf.CodedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.java_websocket.framing.CloseFrame;

/**
 *
 * @author Joris
 */
public class Ping extends Thread
{
        private static final Logger log = Logger.getLogger(Ping.class.getName());
        
        private final static int CLIENT_PROTO_VERSION = 1; // use 0 to disable checking
        private final static long PING_INTERVAL_NANO = 5_000_000_000L;
        private final HashMap<URI, ServerData> servers = new HashMap<>();
        private PingListener listener;
        private volatile boolean firstResult = true;
        
        // todo: atm this launches a thread per connection (see WebSocketTransport). Improve?
        private class ServerData implements WebSocketTransportListener
        {
                URI uri;
                WebSocketTransport webSocketTransport;
                SessionToken session = null;
                Long lastRequest_nanos = null;
                boolean attemptConnect = false;
                boolean disconnected = true;
                boolean stop;

                ServerData(URI uri)
                {
                        this.uri = uri;
                        webSocketTransport = new WebSocketTransport(this);
                }
                
                @ThreadSafe
                public synchronized void send()
                {
                        if (disconnected)
                        {
                                if (!attemptConnect)
                                {
                                        webSocketTransport.establishClientConnection(uri, session, PROTOCOL.PING, CLIENT_PROTO_VERSION);
                                        attemptConnect = true;
                                }
                                return;
                        }
                        
                        PingRequest.Builder builder = PingRequest.newBuilder();
                        lastRequest_nanos = System.nanoTime();
                        builder.setClientTime(lastRequest_nanos);
                        PingRequest request = builder.build();
                        int size = request.getSerializedSize();
                        byte[] result = new byte[size + WebSocketTransport.SEND_RESERVEDPREFIX_BYTES];

                        CodedOutputStream output = CodedOutputStream.newInstance(result, WebSocketTransport.SEND_RESERVEDPREFIX_BYTES, size);

                        try
                        {
                                request.writeTo(output);
                        }
                        catch (IOException ex)
                        {
                                throw new Error(ex);
                        }

                        // assert that there are no bytes left
                        output.checkNoSpaceLeft();
                        try
                        {
                                webSocketTransport.send(session, PROTOCOL.PING, result);
                        }
                        catch (WebSocketTransport.NoSuitableConnection ex)
                        {
                                log.log(Level.WARNING, null, ex); // should not happen
                        }
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
                @ThreadSafe
                public void wstClientEstablishFailure(SessionToken sessionToken, PROTOCOL protocol, WS_CLOSE_STATUS code, String reason)
                {
                        synchronized(this)
                        {
                                assert protocol == PROTOCOL.PING;
                                disconnected = true; // reconnect
                                attemptConnect = false;
                        }
                        
                        listener.pingResult(uri, -1, -1, -1);
                }

                @Override
                @ThreadSafe
                public synchronized void wstNewProtocol(boolean server, SessionToken sessionToken, PROTOCOL protocol)
                {
                        assert protocol == PROTOCOL.PING;
                        this.session = sessionToken;
                        disconnected = false;
                        attemptConnect = false;
                        
                        send(); // do the first ping immediately
                }

                @Override
                @ThreadSafe
                public void wstDropProtocol(boolean server, SessionToken sessionToken, PROTOCOL protocol)
                {
                        synchronized(this)
                        {
                                assert protocol == PROTOCOL.PING;
                                disconnected = true; // reconnect
                        }
                        
                        listener.pingResult(uri, -1, -1, -1);
                }

                @Override
                @ThreadSafe
                public void wstNewConnection(boolean server, SessionToken sessionToken, PROTOCOL protocol)
                {
                }

                @Override
                @ThreadSafe
                public void wstDropConnection(boolean server, SessionToken sessionToken, PROTOCOL protocol, WS_CLOSE_STATUS code, String reason)
                {
                }

                @Override
                @ThreadSafe
                public void wstMessage(boolean server, SessionToken sessionToken, PROTOCOL protocol, int protocolVersion, ByteBuffer message, long receivedAt)
                {
                        assert protocol == PROTOCOL.PING;
                        
                        long result_rttLatency;
                        int result_players;
                        int result_playing;
                        
                        synchronized(this)
                        {
                                try
                                {
                                        PingResponse pingResponse = PingResponse.parseFrom(new ByteArrayInputStream(message.array(), message.position(), message.remaining()));

                                        // race condition on this variable is okay, it would at most make us loose more than 1 result.
                                        // anyways, the very first result is inaccurate because of java lazy class loading.
                                        if (firstResult) 
                                        {
                                                firstResult = false;
                                                return;
                                        }

                                        if (pingResponse.getClientTime() >= lastRequest_nanos)
                                        {
                                                result_rttLatency = receivedAt - pingResponse.getClientTime();
                                                result_players = pingResponse.hasPlayers() ? pingResponse.getPlayers() : -1;
                                                result_playing = pingResponse.hasPlaying() ? pingResponse.getPlaying() : -1;
                                        }
                                        else
                                        {
                                                return;
                                        }
                                }
                                catch (IOException ex)
                                {
                                        log.log(Level.SEVERE, "Error parsing ping response", ex);
                                        return;
                                }
                        }
                        
                        listener.pingResult(uri, result_rttLatency, result_players, result_playing);
                }

                @Override
                @ThreadSafe
                public void wstMessage(boolean server, SessionToken sessionToken, PROTOCOL protocol, int protocolVersion, String message, long receivedAt)
                {
                }
        }

        public Ping(PingListener listener)
        {
                this.listener = listener;
        }
        
        /** Perform pings to an aphelion server at uri.
         * The pings to do not stop until stop() is called
         * @param uri 
         */
        @ThreadSafe
        public void startPing(URI uri)
        {
                synchronized(this)
                {
                        ServerData data = servers.get(uri);
                        if (data != null && !data.stop)
                        {
                                return; // already pinging it
                        }
                        
                        servers.put(uri, new ServerData(uri));
                }
        }
        
        /** Stop pinging a server.
         * @param uri 
         */
        @ThreadSafe
        public void stopPing(URI uri)
        {
                synchronized(this)
                {
                        ServerData data = servers.get(uri);
                        if (data == null)
                        {
                                return;
                        }
                        
                        data.webSocketTransport.closeAll(WS_CLOSE_STATUS.NORMAL);
                        data.stop = true;
                }
        }
        
        /** Stop pinging all servers
         */
        @ThreadSafe
        public void stopPings()
        {
                synchronized(this)
                {
                        for (ServerData data : servers.values())
                        {
                                data.stop = true;
                                data.webSocketTransport.closeAll(WS_CLOSE_STATUS.NORMAL);
                        }
                }
        }

        @Override
        public void run()
        {
                setName("Ping-" + this.getId());
                while (true)
                {
                        try
                        {
                                synchronized(this)
                                {
                                        Iterator<ServerData> it = servers.values().iterator();
                                        while(it.hasNext())
                                        {
                                                ServerData data = it.next();

                                                data.webSocketTransport.loop();
                                                // make sure that any value that had stopPing() called gets atleast 1 loop() call!

                                                if (data.stop)
                                                {
                                                        it.remove();
                                                }
                                        }
                                }

                                synchronized(this)
                                {
                                        long now = System.nanoTime();
                                        for (ServerData data : servers.values())
                                        {
                                                if (data.lastRequest_nanos == null || now - data.lastRequest_nanos >= PING_INTERVAL_NANO)
                                                {
                                                        data.lastRequest_nanos = now;
                                                        data.send();
                                                } 
                                        }
                                }
                        
                        
                                Thread.sleep(10);
                        }
                        catch (InterruptedException ex)
                        {
                                break;
                        }
                }
                
                synchronized(this)
                {
                        for (ServerData data : servers.values())
                        {
                                data.webSocketTransport.stopClients();
                        }
                }
        }
        
        /*private static int dbg_count = 0;
        private static Ping dbg_ping = null;
        public static void main(String[] args) throws Exception
        {
                dbg_ping = new Ping(new PingListener()
                {

                        @Override
                        public void pingResult(URI uri, long rttNanos, int players, int playing)
                        {
                                System.out.println(uri + " " + (rttNanos / 1_000_000.0) + " " + players + " " + playing);
                                ++dbg_count;
                                
                                if (dbg_count == 3)
                                {
                                        dbg_ping.interrupt();
                                }
                        }
                
                });
                
                dbg_ping.start();
                dbg_ping.startPing(new URI("ws://86.87.83.47:80/aphelion"));
        }*/
        
}
