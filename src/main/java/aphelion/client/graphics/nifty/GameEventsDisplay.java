/* Copyright (c) 2013, Joris van der Wel
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

import aphelion.shared.event.LoopEvent;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.controls.Controller;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.elements.render.TextRenderer;
import de.lessvoid.nifty.input.NiftyInputEvent;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.xml.xpp3.Attributes;
import java.util.ArrayList;
import java.util.Properties;

/**
 *
 * @author Joris
 */
public class GameEventsDisplay implements Controller, LoopEvent
{
        private ArrayList<Line> lines;

        private long expireLineAfter_nanos = 5_000_000_000L;
        
        private class Line
        {
                final TextRenderer renderer;
                boolean active;
                long placedAt_nanos;

                Line(TextRenderer renderer)
                {
                        this.renderer = renderer;
                }
        }
        
        @Override
        public void bind(Nifty nifty, Screen screen, Element element, Properties parameter,
                         Attributes controlDefinitionAttributes)
        {
                Element[] lineElements = SwissArmyKnife.findNiftyElementsByIdPrefix(element, "#line");
                this.lines = new ArrayList<>(lineElements.length);
                
                for (Element line : lineElements)
                {
                        this.lines.add(new Line(line.getRenderer(TextRenderer.class)));
                }
                
                // in milliseconds
                expireLineAfter_nanos = controlDefinitionAttributes.getAsInteger("expire-line-after", 5_000) * 1_000_000L;
        }

        @Override
        public void init(Properties parameter, Attributes controlDefinitionAttributes)
        {
        }

        @Override
        public void onStartScreen()
        {
        }

        @Override
        public void onFocus(boolean getFocus)
        {
        }

        @Override
        public void loop(long systemNanoTime, long sourceNanoTime)
        {
                for (Line line : lines)
                {
                        if (line.active && systemNanoTime - line.placedAt_nanos >= expireLineAfter_nanos)
                        {
                                line.active = false;
                                line.renderer.setText("");
                        }
                }
        }
        
        @Override
        public boolean inputEvent(NiftyInputEvent inputEvent)
        {
                return false;
        }
        
        public void addLine(String text)
        {
                if (lines.isEmpty())
                {
                        return;
                }
                
                Line line = null;
                long oldest_nanos = 0;
                
                for (Line candidate : lines)
                {
                        if (candidate.active)
                        {
                                if (line == null || candidate.placedAt_nanos < oldest_nanos)
                                {
                                        line = candidate;
                                        oldest_nanos = candidate.placedAt_nanos;
                                }
                        }
                        else
                        {
                                line = candidate;
                                break;
                        }
                }
                
                assert line != null;
                
                line.active = true;
                line.placedAt_nanos = System.nanoTime();
                line.renderer.setText(text);
        }
}
