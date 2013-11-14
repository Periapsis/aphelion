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
import de.lessvoid.nifty.tools.Color;

/**
 *
 * @author Joris
 */
public class BackgroundColorAnimated implements EffectImpl
{
        private final Color currentColor = new Color("#000f");
        private final Color tempColor = new Color("#000f");
        private Color startColor;
        private Color endColor;

        @Override
        public void activate(final Nifty nifty, final Element element, final EffectProperties parameter)
        {
                startColor = new Color(parameter.getProperty("startColor", "#0000"));
                endColor = new Color(parameter.getProperty("endColor", "#ffff"));
        }

        @Override
        public void execute(
                final Element element,
                final float normalizedTime,
                final Falloff falloff,
                final NiftyRenderEngine r)
        {
                currentColor.linear(startColor, endColor, normalizedTime);
                if (falloff == null)
                {
                        setColor(element, currentColor);
                }
                else
                {
                        tempColor.mutiply(currentColor, falloff.getFalloffValue());
                        setColor(element, tempColor);
                }
        }

        private void setColor(final Element element, final Color color)
        {
                element.getRenderer(PanelRenderer.class).setBackgroundColor(color);
        }

        @Override
        public void deactivate()
        {
        }
        
        public static void registerEffect(Nifty nifty)
        {
                nifty.registerEffect("backgroundColorAnimated", BackgroundColorAnimated.class.getName());
        }
}
