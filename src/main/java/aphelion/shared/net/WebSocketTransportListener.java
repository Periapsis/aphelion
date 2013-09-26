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

/**
 *
 * @author Joris
 */
public interface WebSocketTransportListener
{
        /** Are we handling this protocol ?.
         * Only used server side
         * @param protocol The protocol ID specified by the client
         * @return false if the connection should be rejected because of 
         *         an invalid protocol.
         */
        @ThreadSafe
        boolean wstIsValidProtocol(PROTOCOL protocol);
        
        /** Are we handling this combination of protocol and protocol version?.
         * This method is called right after isValidProtocol()
         * Only used server side
         * 
         * @param protocol The protocol ID specified by the client
         * @param protocolVersion The protocol version specified by the client. 
         *        0 if the client does not want any version checking.
         * @return 0 if the connection should be rejected because of 
         *         an invalid protocol version. -1 if the requested version is 
         *         too old. 1 if the requested version is too new.
         */
        @ThreadSafe
        int wstIsValidProtocolVersion(PROTOCOL protocol, int protocolVersion);
        
        /** A session or protocol is unable to initialize properly.
         * If this event occurs, wstNewProtocol has never been called for this session.
         * 
         * This most likely means the server is unreachable.
         * 
         * @param sessionToken The requested session token (or null if server=true and a 
         *        handshake was never received)
         * @param protocol  The requested protocol (or null if server=true and a handshake 
         *        was never received)
         * @param code WebSocket socket close code
         * @param reason  WebSocket socket close message
         */
        @ThreadSafe
        void wstClientEstablishFailure(SessionToken sessionToken, PROTOCOL protocol, WS_CLOSE_STATUS code, String reason);
        
        /** A new protocol has been successfully established.
         * If multiple sockets are used for the same session and protocol, this 
         * method is only called once. (multiple protocol versions do not 
         * trigger multiple calls).
         * 
         * If you are acting as a client and the session token suddenly changes, 
         * you now have 2 client sessions, this is probably an error.
         * Drop the old session or quit in failure.
         * 
         * @param server If true, the other party is a client that has connected to us
         * @param sessionToken Immutable and unique
         * @param protocol
         */
        @ThreadSafe
        void wstNewProtocol(boolean server, SessionToken sessionToken, PROTOCOL protocol);
        
        /** A protocol is no longer active, all its sockets are gone.
         * 
         * @param server If true, the other party is a client that has connected to us
         * @param sessionToken Immutable and unique
         * @param protocol
         */
        @ThreadSafe
        void wstDropProtocol(boolean server, SessionToken sessionToken, PROTOCOL protocol);
        
        /** Called when a connection has been added for the specified protocol.
         * At this point the connection is ready to be used to send or receive.
         * Fired AFTER wstNewProtocol
         *
         * @param server 
         * @param sessionToken Immutable and unique
         * @param protocol 
         */
        @ThreadSafe
        public void wstNewConnection(boolean server, SessionToken sessionToken, PROTOCOL protocol);
        
        /** Called when a connection has been dropped for the specified protocol.
         * Fired BEFORE wstDropProtocol
         * 
         * @param server 
         * @param sessionToken Immutable and unique
         * @param protocol 
         * @param code WebSocket socket close code
         * @param reason  WebSocket socket close message
         */
        @ThreadSafe
        public void wstDropConnection(boolean server, SessionToken sessionToken, PROTOCOL protocol, WS_CLOSE_STATUS code, String reason);
        
        /** Received a binary message
         * 
         * @param server If true, the other party is a client that has connected to us
         * @param sessionToken Immutable and unique
         * @param protocol 
         * @param protocolVersion 
         * @param message
         * @param receivedAt When was this message received? (System.nanoTime )
         */
        @ThreadSafe
        void wstMessage(boolean server, SessionToken sessionToken, PROTOCOL protocol, int protocolVersion, ByteBuffer message, long receivedAt);
        
        /** Received a unicode message
         * 
         * @param server If true, the other party is a client that has connected to us
         * @param sessionToken Immutable and unique
         * @param protocol 
         * @param protocolVersion 
         * @param message  
         * @param receivedAt When was this message received? (System.nanoTime )
         */
        @ThreadSafe
        void wstMessage(boolean server, SessionToken sessionToken, PROTOCOL protocol, int protocolVersion, String message, long receivedAt);
}
