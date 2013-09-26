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

import aphelion.shared.swissarmyknife.ThreadSafe;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;


public interface HttpWebSocketServerListener
{
        /**
         * Returns whether a new connection shall be accepted or not.<br> Therefore method is well suited to implement
         * some kind of connection limitation.<br>
         *
         * @param key 
         * @return true if the connection should be accepted
         * @see
         * {@link #onOpen(WebSocket, ClientHandshake)}, {@link #onWebsocketHandshakeReceivedAsServer(WebSocket, Draft, ClientHandshake)}
	 *
         */
        @ThreadSafe
        public boolean wssConnect(SelectionKey key);
        
        /**
         * Called after an opening handshake has been performed and the given websocket is ready to be written on.
         * @param conn
         * @param handshake  
         */
        @ThreadSafe
        public void wssOpen(MyWebSocketImpl conn, ClientHandshake handshake);
        
        /**
         * Called after the websocket connection has been closed.
         *
         * @param conn 
         * @param code The codes can be looked up here: {@link CloseFrame}
         * @param reason Additional information string
         * @param remote Returns whether or not the closing of the connection was initiated by the remote host.
	 *
         */
        @ThreadSafe
        public void wssClose(MyWebSocketImpl conn, int code, String reason, boolean remote);
        
         /**
         * Callback for string messages received from the remote host
         *
         * @param conn 
         * @param message 
         * @see #onMessage(WebSocket, ByteBuffer)
	 *
         */
        @ThreadSafe
        public void wssMessage(MyWebSocketImpl conn, String message);
        
        /**
         * Callback for binary messages received from the remote host
         *
         * @param conn 
         * @param message 
         * @see #onMessage(WebSocket, String)
	 *
         */
        @ThreadSafe
        public void wssMessage(MyWebSocketImpl conn, ByteBuffer message);

        /**
         * Called when errors occurs. If an error causes the websocket connection to fail
         * {@link #onClose(WebSocket, int, String, boolean)} will be called additionally.<br> This method will be called
         * primarily because of IO or protocol errors.<br> If the given exception is an RuntimeException that probably
         * means that you encountered a bug.<br>
         * 
         * @param conn Can be null if there error does not belong to one specific websocket. For example if the servers
         * port could not be bound.
	 * @param ex 
         */
        @ThreadSafe
        public void wssError(MyWebSocketImpl conn, Exception ex);
}
