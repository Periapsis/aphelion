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
package aphelion.client.graphics.nifty.chat;

import de.lessvoid.nifty.controls.ListBox;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.elements.render.ImageRenderer;
import de.lessvoid.nifty.elements.render.TextRenderer;

/**
 * Handles a line in the chat controller. This can be either a chat line or an
 * entry in the list of players.
 *
 * @author Mark
 */
public class ChatPlayerViewConverter implements ListBox.ListBoxViewConverter<PlayerEntry>
{
        private static final String CHAT_LINE_ICON = "#chat-line-icon";
        private static final String CHAT_LINE_TEXT = "#chat-line-text";

        /**
         * Default constructor.
         */
        public ChatPlayerViewConverter()
        {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final void display(final Element listBoxItem, final PlayerEntry item)
        {
                final Element text = listBoxItem.findElementByName(CHAT_LINE_TEXT);
                final TextRenderer textRenderer = text.getRenderer(TextRenderer.class);
                final Element icon = listBoxItem.findElementByName(CHAT_LINE_ICON);
                final ImageRenderer iconRenderer = icon.getRenderer(ImageRenderer.class);
                if (item != null)
                {
                        textRenderer.setText(item.toString());
                        iconRenderer.setImage(item.getIcon());
                        if (item.getStyle() != null && !item.getStyle().equals(""))
                        {
                                text.setStyle(item.getStyle());
                        }
                        else
                        {
                                text.setStyle("default");
                        }
                }
                else
                {
                        textRenderer.setText("");
                        iconRenderer.setImage(null);
                }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final int getWidth(final Element listBoxItem, final PlayerEntry item)
        {
                final Element text = listBoxItem.findElementByName(CHAT_LINE_TEXT);
                final TextRenderer textRenderer = text.getRenderer(TextRenderer.class);
                return ((textRenderer.getFont() == null) ? 0 : textRenderer.getFont().getWidth(item.getLabel()))
                       + ((item.getIcon() == null) ? 0 : item.getIcon().getWidth());
        }
}
