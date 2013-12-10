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


import aphelion.client.graphics.nifty.chat.AphelionChatControl;
import aphelion.client.graphics.nifty.chat.AphelionChatControl.AphelionChatTextSendEvent;
import aphelion.client.net.NetworkedGame;
import aphelion.client.net.OperationDroppedListener;
import aphelion.shared.event.TickedEventLoop;
import aphelion.shared.net.COMMAND_SOURCE;
import aphelion.shared.net.game.ActorListener;
import aphelion.shared.net.protobuf.GameC2S;
import aphelion.shared.net.protobuf.GameC2S.C2S;
import aphelion.shared.net.protobuf.GameS2C;
import aphelion.shared.net.game.GameProtocolConnection;
import aphelion.shared.net.game.GameS2CListener;
import aphelion.shared.net.game.NetworkedActor;
import de.lessvoid.nifty.screen.Screen;
import java.util.Arrays;
import java.util.List;
import org.bushe.swing.event.EventTopicSubscriber;

/**
 *
 * @author Joris
 */
public class LocalChat implements EventTopicSubscriber<AphelionChatTextSendEvent>, GameS2CListener, ActorListener, OperationDroppedListener
{
        private final TickedEventLoop loop;
        private final NetworkedGame netGame;
        private final List<AphelionChatControl> chatLocals;
        private final long WARNING_DROPPED_OPERATION_INTERVAL = 30_000_000_000L; // 30s
        private long lastDroppedWarning_nanos;

        public LocalChat(TickedEventLoop loop, NetworkedGame netGame, List<AphelionChatControl> chatLocals)
        {
                if (netGame == null)
                {
                        throw new IllegalArgumentException();
                }
                
                this.loop = loop;
                this.netGame = netGame;
                this.chatLocals = chatLocals;
        }
        
        public void subscribeListeners(Screen screen)
        {
                netGame.addActorListener(this, true);
                netGame.getGameConn().addListener(this);
                netGame.addOperationDroppedLIstener(this);
                for (AphelionChatControl control : chatLocals)
                {
                        control.getElement().getNifty().subscribe(screen, control.getElement().getId(), AphelionChatTextSendEvent.class, this);
                }
        }
        
        public void addLine(String sender, String line)
        {
                for (AphelionChatControl control : chatLocals)
                {
                        if (sender == null)
                        {
                                control.receivedChatLine(line, null);
                        }
                        else
                        {
                                control.receivedChatLine(sender + "> " + line, null);
                        }
                }
        }
        
        @Override
        public void onEvent(String topic, AphelionChatTextSendEvent data)
        {
                String text = data.getText();
                if (text == null || text.isEmpty())
                {
                        return;
                }
                
                if (text.charAt(0) == '?' || text.charAt(0) == '*')
                {
                        String[] arg = text.substring(1).split("\\s+");
                        if (arg.length > 0 && !arg[0].isEmpty())
                        {
                                netGame.sendCommand(COMMAND_SOURCE.USER_MANUAL, arg[0], Arrays.copyOfRange(arg, 1, arg.length));
                                for (AphelionChatControl control : chatLocals)
                                {
                                        control.receivedChatLine(text, null);
                                }
                                return;
                        }
                }

                C2S.Builder builder = C2S.newBuilder();
                GameC2S.SendLocalChat.Builder chat = builder.addSendLocalChatBuilder();
                chat.setMessage(text);
                netGame.getGameConn().send(builder);
        }

        @Override
        public void gameS2CMessage(GameProtocolConnection game, GameS2C.S2C s2c, long receivedAt)
        {
                for (GameS2C.LocalChatMessage message : s2c.getLocalChatMessageList())
                {
                        addLine(message.hasSender() ? message.getSender() : null, message.getMessage());
                }
        }

        @Override
        public void newActor(NetworkedActor actor)
        {
                for (AphelionChatControl control : chatLocals)
                {
                        control.addPlayer(actor.pid, actor.name, null);
                }
        }

        @Override
        public void actorModified(NetworkedActor actor)
        {
                for (AphelionChatControl control : chatLocals)
                {
                        control.renamePlayer(actor.pid, actor.name);
                }
        }

        @Override
        public void removedActor(NetworkedActor actor)
        {
                for (AphelionChatControl control : chatLocals)
                {
                        control.removePlayer(actor.pid);
                }
        }

        @Override
        public void operationDropped(long tick, int pid, Object messsage)
        {
                long now = loop.getLoopSystemNanoTime();
                
                if (lastDroppedWarning_nanos == 0 || now - lastDroppedWarning_nanos >= WARNING_DROPPED_OPERATION_INTERVAL)
                {
                        lastDroppedWarning_nanos = now;
                        addLine(null, "\\#de3108#You are experiencing very high lag, please check your network connection.");
                }
        }
}
