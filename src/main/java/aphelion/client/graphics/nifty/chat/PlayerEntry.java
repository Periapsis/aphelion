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

import de.lessvoid.nifty.render.NiftyImage;

/**
 * Handles a line in the chat controller. This can be either a chat line or an
 * entry in the list of players.
 *
 * @author Mark
 */
public final class PlayerEntry
{
        private final int pid;
        private ChatEntry chatEntry;

        public PlayerEntry(int pid, final String labelParam, final NiftyImage iconParam)
        {
                this.pid = pid;
                this.chatEntry = new ChatEntry(labelParam, iconParam);
        }

        public PlayerEntry(int pid, final String labelParam, final NiftyImage iconParam, String style)
        {
                this.pid = pid;
                this.chatEntry = new ChatEntry(labelParam, iconParam, style);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString()
        {
                return chatEntry.getLabel();
        }

        /**
         * Return the supplied label. This can be either a chat line or a player name.
         *
         * @return The supplied label.
         */
        public String getLabel()
        {
                return chatEntry.getLabel();
        }

        /**
         * Set a new label to replace the current one.
         *
         * @param label The new label.
         */
        public void setLabel(String label)
        {
                chatEntry.setLabel(label);
        }

        /**
         * Return the supplied icon.
         *
         * @return The supplied icon.
         */
        public NiftyImage getIcon()
        {
                return chatEntry.getIcon();
        }

        /**
         * Supply a new icon which replaces the current one.
         *
         * @param icon The icon.
         */
        public void setIcon(NiftyImage icon)
        {
                chatEntry.setIcon(icon);
        }

        /**
         * Returns the style of the current entry.
         *
         * @return The style.
         */
        public String getStyle()
        {
                return this.chatEntry.getStyle();
        }

        /**
         * Supply a new style which replaces the current one, null reverts to the
         * default style from the XML.
         *
         * @param style The new style.
         */
        public void setStyle(String style)
        {
                this.chatEntry.setStyle(style);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(final Object obj)
        {
                if (obj == null || !(obj instanceof PlayerEntry))
                {
                        return false;
                }
        
                return this.pid == ((PlayerEntry) obj).pid;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode()
        {
                return pid;
        }

        static class ChatEntry
        {
                private String label;
                private NiftyImage icon;
                private String style;

                public ChatEntry(String label, NiftyImage icon)
                {
                        setLabel(label);
                        setIcon(icon);
                }

                public ChatEntry(String label, NiftyImage icon, String style)
                {
                        setLabel(label);
                        setIcon(icon);
                        setStyle(style);
                }

                public NiftyImage getIcon()
                {
                        return icon;
                }

                public void setIcon(NiftyImage icon)
                {
                        this.icon = icon;
                }

                public String getLabel()
                {
                        return label;
                }

                public void setLabel(String label)
                {
                        this.label = label;
                }

                public String getStyle()
                {
                        return style;
                }

                public void setStyle(String style)
                {
                        this.style = style;
                }

        }

}
