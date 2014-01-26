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

package aphelion.client;


import aphelion.client.graphics.nifty.GameEventsDisplay;
import aphelion.client.net.NetworkedGame;
import aphelion.client.net.SingleGameConnection;
import aphelion.shared.net.game.ActorListener;
import aphelion.shared.net.game.GameProtocolConnection;
import aphelion.shared.net.game.GameS2CListener;
import aphelion.shared.net.game.NetworkedActor;
import aphelion.shared.net.protobuf.GameS2C;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 *
 * @author Joris
 */
public class GameEvents implements ActorListener, GameS2CListener
{
        private static final Logger log = Logger.getLogger("aphelion.client");
        private final NetworkedGame netGame;
        private final List<GameEventsDisplay> displays;

        public GameEvents(@Nonnull NetworkedGame netGame, @Nonnull List<GameEventsDisplay> displays)
        {
                this.netGame = netGame;
                this.displays = displays;
        }
        
        public void subscribeListeners(@Nonnull SingleGameConnection connection)
        {
                connection.addListener(this);
                netGame.addActorListener(this, false);
        }

        @Override
        public void gameS2CMessage(@Nonnull GameProtocolConnection game, @Nonnull GameS2C.S2C s2c, long receivedAt)
        {
                for (GameS2C.ActorDied msg : s2c.getActorDiedList())
                {
                        NetworkedActor died = netGame.getActor(msg.getDied());
                        
                        if (died == null)
                        {
                                log.log(Level.WARNING, "Received ActorDied with unknown pid {0} for died", msg.getDied());
                                continue;
                        }
                        
                        
                        NetworkedActor killer = null;
                        if (msg.hasKiller())
                        {
                                killer = netGame.getActor(msg.getKiller());
                                if (killer == null)
                                {
                                        log.log(Level.WARNING, "Received ActorDied with unknown pid {0} for killer", msg.getKiller());
                                }
                        }
                        
                        String color;
                        if (died.local || (killer != null && killer.local))
                        {
                                color = "\\#ffbd29#";
                        }
                        else
                        {
                                color = "\\#6bde5a#";
                        }
                        
                        String line;
                        if (killer == null)
                        {
                                line = died.name + " died";
                        }
                        else
                        { 
                                line = died.name + " killed by: " + killer.name;
                        }
                        
                        log.log(Level.INFO, "{0}", line);
                        addLine(color + line);
                }
        }

        private void addLine(@Nonnull String line)
        {
                for (GameEventsDisplay display : displays)
                {
                        display.addLine(line);
                }
        }
        
        @Override
        public void newActor(@Nonnull NetworkedActor actor)
        {
                addLine("\\#c6c6f7#" + actor.name + " entered arena");
        }

        @Override
        public void actorModified(@Nonnull NetworkedActor actor)
        {
        }

        @Override
        public void removedActor(@Nonnull NetworkedActor actor)
        {
                addLine("\\#c6c6f7#" + actor.name + " left arena");
        }
}
