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
import de.lessvoid.nifty.effects.EffectImpl;
import de.lessvoid.nifty.effects.EffectProperties;
import de.lessvoid.nifty.effects.Falloff;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.elements.render.PanelRenderer;
import de.lessvoid.nifty.render.NiftyRenderEngine;
import de.lessvoid.nifty.spi.time.TimeProvider;
import de.lessvoid.nifty.tools.Color;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class BackgroundColorSpriteEffect implements EffectImpl
{
        private static final Logger log = Logger.getLogger(BackgroundColorSpriteEffect.class.getName());
        
        private Color tempColor = new Color("#000f");

        // From parameters:
        private int[] frameLength;
        private Color[] colors;
        private boolean reverse;
        
        private int spriteCount;
        private int index;
        private long lastUpdate;
        private long nextChange = 0;
        private boolean firstUpdate;
        
        @Override
        public void activate(Nifty nifty, Element element, EffectProperties parameter)
        {
                try
                {
                        if (parameter.containsKey("frameLength"))
                        {
                                String[] times = parameter.getProperty("frameLength").split(",");
                                frameLength = new int[times.length];
                                if (times.length > 0)
                                {
                                        for (int i = 0; i < times.length; ++i)
                                        {
                                                frameLength[i] = Integer.parseInt(times[i]);
                                        }
                                }
                                else
                                {
                                        frameLength = new int[] { 100 };
                                }
                        }
                        else
                        {
                                frameLength = new int[] { 100 };
                        }
                }
                catch (NumberFormatException ex)
                {
                        throw new IllegalArgumentException("Invalid frameLength: " + parameter.getProperty("frameLength"), ex);
                }
                
                if (parameter.containsKey("colors"))
                {
                        String[] times = parameter.getProperty("colors").split(",");
                        colors = new Color[times.length];
                        if (times.length > 0)
                        {
                                for (int i = 0; i < times.length; ++i)
                                {
                                        colors[i] = new Color(times[i]);
                                }
                        }
                        else
                        {
                                colors = new Color[] { Color.WHITE };
                        }
                }
                else
                {
                        colors = new Color[] { Color.WHITE };
                }
                spriteCount = colors.length;
                
                reverse = Boolean.valueOf(parameter.getProperty("reverse", "false"));
                
        }
        
        private void updateIndex(TimeProvider time)
        {
                long now = time.getMsTime();
                long delta;
                if (firstUpdate)
                {
                        delta = 0;
                        firstUpdate = false;
                        index = reverse ? spriteCount - 1 : 0;
                }
                else
                {
                        delta = now - lastUpdate;
                }
                lastUpdate = now;
                nextChange -= delta;
                
                while (nextChange < 0)
                {
                        index = (index + (reverse ? -1 : 1));
                        if (index < 0)
                        {
                                index += spriteCount;
                        }
                        index %= spriteCount;
                        
                        int duration = index < frameLength.length ? frameLength[index] : frameLength[frameLength.length-1];
                        nextChange += duration;
                }
        }

        @Override
        public void execute(Element element, float effectTime, Falloff falloff, NiftyRenderEngine r)
        {
                updateIndex(element.getNifty().getTimeProvider());
                Color currentColor = colors[index];
                
                if (falloff == null)
                {
                        element.getRenderer(PanelRenderer.class).setBackgroundColor(currentColor);
                }
                else
                {
                        tempColor.mutiply(currentColor, falloff.getFalloffValue());
                        element.getRenderer(PanelRenderer.class).setBackgroundColor(tempColor);
                }       
        }

        @Override
        public void deactivate()
        {
        }
        
        public static void registerEffect(Nifty nifty)
        {
                nifty.registerEffect("backgroundColorSpriteEffect", BackgroundColorSpriteEffect.class.getName());
        }
}
