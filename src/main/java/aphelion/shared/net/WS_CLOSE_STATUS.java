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

/**
 *
 * @author Joris
 */
public enum WS_CLOSE_STATUS
{
        FLASHPOLICY(-3),
        BUGGYCLOSE(-2),
        /** We are a client and the connection never succesfully established. */
        NEVERCONNECTED(-1),
	
        UNKNOWN (0),
        /**
	 * indicates a normal closure, meaning whatever purpose the
	 * connection was established for has been fulfilled.
	 */
	NORMAL (1000),
	/**
	 * 1001 indicates that an endpoint is "going away", such as a server
	 * going down, or a browser having navigated away from a page.
	 */
	GOING_AWAY (1001),
	/**
	 * 1002 indicates that an endpoint is terminating the connection due
	 * to a protocol error.
	 */
	PROTOCOL_ERROR(1002),
	/**
	 * 1003 indicates that an endpoint is terminating the connection
	 * because it has received a type of data it cannot accept (e.g. an
	 * endpoint that understands only text data MAY send this if it
	 * receives a binary message).
	 */
	REFUSE (1003),
	/*1004: Reserved. The specific meaning might be defined in the future.*/
	/**
	 * 1005 is a reserved value and MUST NOT be set as a status code in a
	 * Close control frame by an endpoint. It is designated for use in
	 * applications expecting a status code to indicate that no status
	 * code was actually present.
	 */
	NOCODE(1005),
	/**
	 * 1006 is a reserved value and MUST NOT be set as a status code in a
	 * Close control frame by an endpoint. It is designated for use in
	 * applications expecting a status code to indicate that the
	 * connection was closed abnormally, e.g. without sending or
	 * receiving a Close control frame.
	 */
	ABNORMAL_CLOSE(1006),
	/**
	 * 1007 indicates that an endpoint is terminating the connection
	 * because it has received data within a message that was not
	 * consistent with the type of the message (e.g., non-UTF-8 [RFC3629]
	 * data within a text message).
	 */
	NO_UTF8 (1007),
	/**
	 * 1008 indicates that an endpoint is terminating the connection
	 * because it has received a message that violates its policy. This
	 * is a generic status code that can be returned when there is no
	 * other more suitable status code (e.g. 1003 or 1009), or if there
	 * is a need to hide specific details about the policy.
	 */
	POLICY_VALIDATION (1008),
	/**
	 * 1009 indicates that an endpoint is terminating the connection
	 * because it has received a message which is too big for it to
	 * process.
	 */
	TOOBIG (1009),
	/**
	 * 1010 indicates that an endpoint (client) is terminating the
	 * connection because it has expected the server to negotiate one or
	 * more extension, but the server didn't return them in the response
	 * message of the WebSocket handshake. The list of extensions which
	 * are needed SHOULD appear in the /reason/ part of the Close frame.
	 * Note that this status code is not used by the server, because it
	 * can fail the WebSocket handshake instead.
	 */
	EXTENSION (1010),
	/**
	 * 1011 indicates that a server is terminating the connection because
	 * it encountered an unexpected condition that prevented it from
	 * fulfilling the request.
	 **/
	UNEXPECTED_CONDITION (1011),
	/**
	 * 1015 is a reserved value and MUST NOT be set as a status code in a
	 * Close control frame by an endpoint. It is designated for use in
	 * applications expecting a status code to indicate that the
	 * connection was closed due to a failure to perform a TLS handshake
	 * (e.g., the server certificate can't be verified).
	 **/
	TLS_ERROR (1015),
        
        // WebSocket: The range of status codes from 4000-4999 is designated for Private Use.
        INVALID_PROTOCOL                (4000),
        INVALID_PROTOCOL_VERSION_OLD    (4001), // requested protocol version is too old
        INVALID_PROTOCOL_VERSION_NEW    (4002),
        MALFORMED_INIT_PACKET           (4003),
        
        /** The client waited too long to send its initialization packet, or the server waited too long with replying. */
        INIT_TIMEOUT                    (4004),
        
        /** The server unexpectedly created a new session for the client. The client is now closing the old ones  */
        CLOSING_PREVIOUS_SESSION        (4005),
        
        /** Encountered a malformed protocol packet (generic). */
        MALFORMED_PACKET (4006);
        
        // Also see org.java_websocket.framing.CloseFrame
        
        public final int id;

        private WS_CLOSE_STATUS(int id)
        {
                this.id = id;
        }
        
        public static WS_CLOSE_STATUS fromId(int id)
        {
                for (WS_CLOSE_STATUS s : WS_CLOSE_STATUS.values())
                {
                        if (s.id == id)
                        {
                                return s;
                        }
                }
                
                return null;
        }
}
