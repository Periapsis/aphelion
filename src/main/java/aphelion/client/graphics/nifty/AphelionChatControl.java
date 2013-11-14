/* Copyright (c) 2013, nifty-gui / Joris van der Wel
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package aphelion.client.graphics.nifty;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.NiftyEvent;
import de.lessvoid.nifty.controls.*;
import de.lessvoid.nifty.controls.chatcontrol.ChatEntryModelClass;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.input.NiftyInputEvent;
import de.lessvoid.nifty.render.NiftyImage;
import de.lessvoid.nifty.screen.KeyInputHandler;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.xml.xpp3.Attributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is the main controller for the chat control.
 *
 * @author Mark
 * @author Joris
 */
public class AphelionChatControl extends AbstractController implements Chat, KeyInputHandler
{
        private static final String CHAT_BOX = "#chatBox";
        private static final String PLAYER_LIST = "#playerList";
        private static final String CHAT_TEXT_INPUT = "#chat-text-input";
        private static final Logger log = Logger.getLogger(AphelionChatControl.class.getName());
        private TextField textControl;
        private final PlayerComparator playerComparator = new PlayerComparator();
        private Nifty nifty;
        private final List<ChatEntryModelClass> playerBuffer = new ArrayList<>();
        private final List<ChatEntryModelClass> linesBuffer = new ArrayList<>();

        /**
         * Default constructor.
         */
        public AphelionChatControl()
        {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final void bind(final Nifty niftyParam, final Screen screenParam, final Element newElement,
                               final Properties properties, final Attributes controlDefinitionAttributes)
        {
                super.bind(newElement);
                log.fine("binding chat control");
                nifty = niftyParam;

                // this buffer is needed because in some cases the entry is added to either list before the emelent is bound.
                final ListBox<ChatEntryModelClass> playerList = getListBox(PLAYER_LIST);
                while (!playerBuffer.isEmpty())
                {
                        ChatEntryModelClass player = playerBuffer.remove(0);
                        log.log(Level.FINE, "adding player {0}", (playerList.itemCount() + 1));
                        playerList.addItem(player);
                        playerList.sortAllItems(playerComparator);
                        playerList.showItem(player);
                }
                final ListBox<ChatEntryModelClass> chatBox = getListBox(CHAT_BOX);
                while (!linesBuffer.isEmpty())
                {
                        ChatEntryModelClass line = linesBuffer.remove(0);
                        log.log(Level.FINE, "adding message {0}", (chatBox.itemCount() + 1));
                        chatBox.addItem(line);
                        chatBox.showItemByIndex(chatBox.itemCount() - 1);
                }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onFocus(final boolean arg0)
        {
                textControl.setFocus();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final void onStartScreen()
        {
                log.fine("starting chat screen");
                textControl = getElement().findNiftyControl(CHAT_TEXT_INPUT, TextField.class);
                textControl.getElement().addInputHandler(this);
        }

        /**
         * {@inheritDoc
         */
        @Override
        public final void receivedChatLine(final String text, final NiftyImage icon)
        {
                receivedChatLine(text, icon, null);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void receivedChatLine(String text, NiftyImage icon, String style)
        {
                if (linesBuffer.isEmpty())
                {
                        try
                        {
                                final ListBox<ChatEntryModelClass> chatBox = getListBox(CHAT_BOX);
                                log.log(Level.FINE, "adding message {0}", (chatBox.itemCount() + 1));
                                final ChatEntryModelClass item = new ChatEntryModelClass(text, icon, style);
                                chatBox.addItem(item);
                                chatBox.showItemByIndex(chatBox.itemCount() - 1);
                        }
                        catch (NullPointerException npe)
                        {
                                linesBuffer.add(new ChatEntryModelClass(text, icon, style));
                        }
                }
                else
                {
                        linesBuffer.add(new ChatEntryModelClass(text, icon, style));
                }
        }

        /**
         * {@inheritDoc
         */
        @Override
        public final void addPlayer(final String playerName, final NiftyImage playerIcon)
        {
                addPlayer(playerName, playerIcon, null);
        }

        /**
         * {@inheritDoc
         */
        @Override
        public void addPlayer(String playerName, NiftyImage playerIcon, String style)
        {
                if (playerBuffer.isEmpty())
                {
                        try
                        {
                                final ListBox<ChatEntryModelClass> playerList = getListBox(PLAYER_LIST);
                                log.log(Level.FINE, "adding player {0}", (playerList.itemCount() + 1));
                                final ChatEntryModelClass item = new ChatEntryModelClass(playerName, playerIcon, style);
                                playerList.addItem(item);
                                playerList.sortAllItems(playerComparator);
                                playerList.showItem(item);
                        }
                        catch (NullPointerException npe)
                        {
                                playerBuffer.add(new ChatEntryModelClass(playerName, playerIcon, style));
                        }
                }
                else
                {
                        playerBuffer.add(new ChatEntryModelClass(playerName, playerIcon, style));
                }
        }

        /**
         * {@inheritDoc
         */
        @Override
        public final void removePlayer(final String playerName)
        {
                final ListBox<ChatEntryModelClass> playerList = getListBox(PLAYER_LIST);
                log.log(Level.FINE, "removing player {0}", playerName);
                final ChatEntryModelClass item = new ChatEntryModelClass(playerName, null);
                playerList.removeItem(item);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<ChatEntryModelClass> getPlayers()
        {
                final ListBox<ChatEntryModelClass> playerList = getListBox(PLAYER_LIST);
                return playerList.getItems();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<ChatEntryModelClass> getLines()
        {
                final ListBox<ChatEntryModelClass> chatBox = getListBox(CHAT_BOX);
                return chatBox.getItems();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void update()
        {
                getListBox(PLAYER_LIST).refresh();
        }

        /**
         * This method is called when the player either presses the send button or
         * the Return key.
         */
        public final void sendText()
        {
                final String text = textControl.getText();
                log.log(Level.INFO, "sending text {0}", text);
                nifty.publishEvent(getId(), new AphelionChatTextSendEvent(this, text));
                textControl.setText("");
        }

        @SuppressWarnings("unchecked")
        private ListBox<ChatEntryModelClass> getListBox(final String name)
        {

                return (ListBox<ChatEntryModelClass>) getElement().findNiftyControl(name, ListBox.class);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean keyEvent(final NiftyInputEvent inputEvent)
        {
                if (inputEvent == null)
                {
                        return false;
                }
                
                log.log(Level.FINE, "event received: {0}", inputEvent);
                
                if (inputEvent == NiftyInputEvent.SubmitText)
                {
                        sendText();
                        return true;
                }
                else if (inputEvent == NiftyInputEvent.MoveCursorRight)
                {
                }
                return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean inputEvent(final NiftyInputEvent inputEvent)
        {
                return keyEvent(inputEvent);
        }

        /**
         * Class used to sort the list of players by name.
         *
         * @author Mark
         * @version 0.2
         */
        private class PlayerComparator implements Comparator<ChatEntryModelClass>
        {

                /**
                 * Default constructor.
                 */
                PlayerComparator()
                {
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public int compare(final ChatEntryModelClass player1, final ChatEntryModelClass player2)
                {
                        String left = player1.getLabel();
                        String right = player2.getLabel();
                        return left.compareToIgnoreCase(right);
                }
        }

        public static class AphelionChatTextSendEvent implements NiftyEvent<Void>
        {
                private final AphelionChatControl chatControl;
                private final String text;

                public AphelionChatTextSendEvent(final AphelionChatControl chatControl, final String textParam)
                {
                        this.chatControl = chatControl;
                        this.text = textParam;
                }

                public AphelionChatControl getChatControl()
                {
                        return chatControl;
                }

                public String getText()
                {
                        return text;
                }

        }

}
