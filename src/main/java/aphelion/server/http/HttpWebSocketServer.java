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
package aphelion.server.http;

import aphelion.shared.net.HttpWebSocketServerListener;
import aphelion.shared.net.WebSocketTransport.ServerWebSocketImpl;
import aphelion.shared.swissarmyknife.ThreadSafe;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.java_websocket.*;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;

/**
 *
 * @author Joris
 */
class HttpWebSocketServer extends WebSocketAdapter implements Runnable
{
        private static final Logger log = Logger.getLogger("aphelion.server.http");
        public Thread thread = new Thread(this);
        private volatile boolean ready = false;
        private Selector selector;
        private ConcurrentLinkedQueue<NewChannel> newChannels = new ConcurrentLinkedQueue<>();
        private final HttpWebSocketServerListener listener;
        private final Set<WebSocket> connections = new HashSet<>();
        private ByteBuffer buffer;

        HttpWebSocketServer(HttpWebSocketServerListener listener)
        {
                this.listener = listener;
                if (this.listener == null)
                {
                        throw new IllegalArgumentException();
                }
        }

        @ThreadSafe
        public void startWaitReady()
        {
                thread.start();
                
                while (!ready)
                {
                        try
                        {
                                Thread.sleep(1);
                        }
                        catch (InterruptedException ex)
                        {
                                Thread.currentThread().interrupt();
                                return;
                        }
                }
        }

        @ThreadSafe
        public void stop()
        {
                thread.interrupt();
        }
        
        public void setDeamon(boolean on)
        {
                thread.setDaemon(on);
        }

        public Set<WebSocket> connections()
        {
                return this.connections;
        }

        private boolean registerNewChannel() throws IOException, InterruptedException
        {
                NewChannel newChannel = newChannels.poll();
                if (newChannel == null)
                {
                        return false; // done
                }

                log.log(Level.INFO, "Registering new websocket connection: {0}", newChannel.sChannel.getRemoteAddress());

                newChannel.sChannel.configureBlocking(false);

                

                
                WebSocketImpl conn = new ServerWebSocketImpl(this);
                
                newChannel.sChannel.socket().setTcpNoDelay(true);
                conn.key = newChannel.sChannel.register(selector, SelectionKey.OP_READ, conn);
                
                if (!onConnect(conn.key))
                {
                        conn.key.cancel();
                }
                else
                {
                        conn.channel = newChannel.sChannel;

                        ByteBuffer prependData = newChannel.prependData;
                        newChannel.prependData = null;
                        
                        conn.decode(prependData);
                }

                return true;
        }

        private boolean readable(SelectionKey key, WebSocketImpl conn) throws InterruptedException, IOException
        {
                if (SocketChannelIOHelper.read(buffer, conn, (ByteChannel) conn.channel))
                {
                        // Something has been read (up to WebSocket.RCVBUF)
                        // Perhaps there is more in the TCP receive buffer, 
                        // but other connections will get a chance first
                        
                        conn.decode(buffer);
                        return true; // true = something has been read

                }

                return false;
        }

        private boolean writable(SelectionKey key, WebSocketImpl conn) throws IOException
        {
                if (SocketChannelIOHelper.batch(conn, (ByteChannel) conn.channel))
                {
                        if (key.isValid())
                        {
                                key.interestOps(SelectionKey.OP_READ);
                        }
                        return true; // true = done writing
                }

                return false; // false = there is more to write, but give other connections a chance to write something
        }
        
        @Override
        public void run()
        {
                thread.setName("WebSocketServer-" + thread.getId());

                buffer = ByteBuffer.allocate(WebSocketImpl.RCVBUF);

                try
                {
                        try
                        {
                                selector = Selector.open();
                        }
                        catch (IOException ex)
                        {
                                log.log(Level.SEVERE, "Unable to open selector", ex);
                                return;
                        }
                        
                        ready = true;

                        while (!thread.isInterrupted())
                        {
                                SelectionKey key = null;

                                Iterator<SelectionKey> it;
                                try
                                {
                                        selector.select();

                                        while (registerNewChannel())
                                        {
                                        }

                                        it = selector.selectedKeys().iterator();

                                }
                                catch (InterruptedException ex)
                                {
                                        break;
                                }
                                catch (ClosedSelectorException ex)
                                {
                                        break;
                                }
                                catch (IOException ex)
                                {
                                        log.log(Level.SEVERE, "IOException in select()", ex);
                                        break;
                                }

                                while (it.hasNext())
                                {
                                        WebSocketImpl conn = null;
                                        key = it.next();
                                        
                                        if (!key.isValid())
                                        {
                                                continue;
                                        }
                                        
                                        try
                                        {
                                                if (key.isReadable())
                                                {
                                                        conn = (WebSocketImpl) key.attachment();
                                                        if (readable(key, conn))
                                                        {
                                                                it.remove();
                                                        }

                                                }

                                                if (key.isValid() && key.isWritable())
                                                {
                                                        conn = (WebSocketImpl) key.attachment();
                                                        if (writable(key, conn))
                                                        {
                                                                try
                                                                {
                                                                        it.remove();
                                                                }
                                                                catch (IllegalStateException ex)
                                                                {
                                                                        // already removed
                                                                }
                                                        }
                                                }

                                        }
                                        catch (ClosedSelectorException ex)
                                        {
                                                break;
                                        }
                                        catch (InterruptedException ex)
                                        {
                                                break;
                                        }
                                        catch (CancelledKeyException ex)
                                        {
                                                it.remove();
                                                
                                                // an other thread may cancel the key
                                        }
                                        catch (IOException ex)
                                        {
                                                log.log(Level.SEVERE, "IOException while parsing selector", ex);
                                                key.cancel();
                                                it.remove();
                                                
                                                handleIOException(conn, ex);
                                        }
                                        
                                }
                        }


                        for (WebSocket ws : connections)
                        {
                                ws.close(CloseFrame.NORMAL);
                        }
                }
                catch (RuntimeException ex)
                {
                        log.log(Level.SEVERE, null, ex);

                        onError(null, ex);
                }
        }

        private void handleIOException(WebSocket conn, IOException ex)
        {
                onWebsocketError(conn, ex); // conn may be null here
                
                try
                {
                        if (conn != null)
                        {
                                conn.close(CloseFrame.ABNORMAL_CLOSE);
                        }
                }
                catch(CancelledKeyException ex2)
                {
                        onWebsocketClose(conn, CloseFrame.ABNORMAL_CLOSE, null, true);
                }
        }

        @Override
        public final void onWebsocketMessage(WebSocket conn, String message)
        {
                onMessage(conn, message);
        }

        @Override
        public final void onWebsocketMessage(WebSocket conn, ByteBuffer blob)
        {
                onMessage(conn, blob);
        }

        @Override
        public final void onWebsocketOpen(WebSocket conn, Handshakedata handshake)
        {

                if (this.connections.add(conn))
                {
                        onOpen(conn, (ClientHandshake) handshake);
                }

        }

        @Override
        public final void onWebsocketClose(WebSocket conn, int code, String reason, boolean remote)
        {
                try
                {
                        selector.wakeup();
                }
                catch (IllegalStateException ex)
                {
                }

                if (this.connections.remove(conn))
                {
                        onClose(conn, code, reason, remote);
                }

        }

        /**
         * @param conn may be null if the error does not belong to a single connection
         */
        @Override
        public final void onWebsocketError(WebSocket conn, Exception ex)
        {
                onError(conn, ex);
        }

        @Override
        public final void onWriteDemand(WebSocket w)
        {
                WebSocketImpl conn = (WebSocketImpl) w;
                conn.key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                try
                {
                        selector.wakeup();
                }
                catch (IllegalStateException ex)
                {
                }
        }

        public boolean onConnect(SelectionKey key)
        {
                return listener.wssConnect(key);
        }

        public void onOpen(WebSocket conn, ClientHandshake handshake)
        {
                listener.wssOpen(conn, handshake);
        }

        public void onClose(WebSocket conn, int code, String reason, boolean remote)
        {
                listener.wssClose(conn, code, reason, remote);
        }

        public void onMessage(WebSocket conn, String message)
        {
                listener.wssMessage( conn, message);
        }

        public void onMessage(WebSocket conn, ByteBuffer message)
        {
                listener.wssMessage(conn, message);
        }

        public void onError(WebSocket conn, Exception ex)
        {
                listener.wssError(conn, ex);
        }

        @ThreadSafe
        void addNewChannel(SocketChannel sChannel, ByteBuffer prependData)
        {
                newChannels.add(new NewChannel(sChannel, prependData));
                try
                {
                        selector.wakeup();
                }
                catch (IllegalStateException | NullPointerException ex)
                {
                        // Thread has not started yet, or it just stopped
                        assert false;
                }
        }

        @Override
        public void onWebsocketClosing(WebSocket ws, int code, String reason, boolean remote)
        {
        }

        @Override
        public void onWebsocketCloseInitiated(WebSocket ws, int code, String reason)
        {
        }

        private Socket getSocket( WebSocket conn )
        {
		WebSocketImpl impl = (WebSocketImpl) conn;
		return ( (SocketChannel) impl.key.channel() ).socket();
	}
        
        @Override
        public InetSocketAddress getLocalSocketAddress(WebSocket conn)
        {
                return (InetSocketAddress) getSocket( conn ).getLocalSocketAddress();
        }

        @Override
        public InetSocketAddress getRemoteSocketAddress(WebSocket conn)
        {
                return (InetSocketAddress) getSocket( conn ).getRemoteSocketAddress();
        }

        private static final class NewChannel
        {
                SocketChannel sChannel;
                ByteBuffer prependData;

                NewChannel(SocketChannel sChannel, ByteBuffer prependData)
                {
                        this.sChannel = sChannel;
                        this.prependData = prependData;
                }
        }
}
