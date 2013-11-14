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
package aphelion.shared.net.game;

import aphelion.shared.net.WS_CLOSE_STATUS;
import aphelion.shared.net.protobuf.GameC2S;
import aphelion.shared.net.protobuf.GameS2C;

/** 
 * @author Joris
 */
public interface GameProtoListener extends GameC2SListener, GameS2CListener
{
        public void gameEstablishFailure(WS_CLOSE_STATUS code, String reason);
        
        /** Called (from the main thread) when a new client has started the game protocol.
         * This method is usually called upon the first socket connection for this client 
         * (after some protocol handshakes). New socket connections for the same client will 
         * not cause this method to be called again.
         * @param game
         */
        public void gameNewClient(GameProtocolConnection game);
        
        /** Called (from the main thread) when the client is no longer present.
         * This method is usually called after the last socket connection for this client is 
         * gone. This event may be delayed to allow the client to reconnect, but this 
         * is optional.
         * @param game
         */
        public void gameRemovedClient(GameProtocolConnection game);
        
        /** Called (from the main thread) when a connection has been added.
         * At this point the connection is ready to be used to send or receive.
         * Fired AFTER gameNewClient
         *
         * @param game
         */
        public void gameNewConnection(GameProtocolConnection game);
        
        /** Called (from the main thread) when a connection has been dropped .
         * Fired BEFORE gameRemovedClient
         * 
         * @param game
         * @param code See WS_CLOSE_STATUS 
         * @param reason An optional message to display to the user
         */
        public void gameDropConnection(GameProtocolConnection game, WS_CLOSE_STATUS code, String reason);
}
