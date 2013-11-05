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
import de.lessvoid.nifty.controls.Controller;
import de.lessvoid.nifty.effects.Effect;
import de.lessvoid.nifty.effects.EffectEventId;
import de.lessvoid.nifty.effects.EffectImpl;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.elements.render.TextRenderer;
import de.lessvoid.nifty.input.NiftyInputEvent;
import de.lessvoid.nifty.layout.align.HorizontalAlign;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.tools.SizeValue;
import de.lessvoid.xml.xpp3.Attributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class EnergyBar implements Controller
{
        private Element element;
        private Element progressBarElement;
        private Element progressTextElement;
        private float min;
        private float max;
        private boolean first = true;
        
        private static class AutoEffectKey implements Comparable<AutoEffectKey>
        {
                final float val;
                final String autoKey;

                AutoEffectKey(float val, String autoKey)
                {
                        this.val = val;
                        this.autoKey = autoKey;
                }

                @Override
                public int compareTo(AutoEffectKey o)
                {
                        return Float.compare(val, o.val);
                }
                
        }

        @Override
        public void bind(
                final Nifty nifty,
                final Screen screenParam,
                final Element element,
                final Properties parameter,
                final Attributes controlDefinitionAttributes)
        {
                if (this.element != null)
                {
                        throw new IllegalStateException();
                }
                
                this.element = element;
                progressBarElement = element.findElementByName("#progress");
                progressTextElement = element.findElementByName("#progress-text");
                
                if (progressBarElement != null)
                {
                        String align = controlDefinitionAttributes.get("barAlign");
                        if (align != null)
                        {
                                progressBarElement.setConstraintHorizontalAlign(HorizontalAlign.valueOf(align));
                        }
                }
                
                Float min = controlDefinitionAttributes.getAsFloat("min"); // exclusive
                Float max = controlDefinitionAttributes.getAsFloat("max"); // inclusive
                if (min == null) { min = -0.1f; }
                if (max == null) { max = 1f; }
                this.min = min;
                this.max = max;
        }

        @Override
        public void init(Properties parameter, Attributes controlDefinitionAttributes)
        {
                setProgress(1f);
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
        public boolean inputEvent(NiftyInputEvent inputEvent)
        {
                return false;
        }

        public void setProgress(final float progressValue)
        {
                float progress = progressValue;
                
                if (progress < 0.0f)
                {
                        progress = 0.0f;
                }
                else if (progress > 1.0f)
                {
                        progress = 1.0f;
                }
                
                if (progressValue > this.min && progressValue <= this.max)
                {
                        if (!element.isVisible() || first) { element.show(); }
                }
                else
                {
                        if (element.isVisible()) { element.hide(); }
                }
                
                if (progressBarElement != null)
                {
                        int pixelWidth = (int) (progressBarElement.getParent().getWidth() * progress);
                        progressBarElement.setConstraintWidth(new SizeValue(pixelWidth + "px"));
                        progressBarElement.getParent().layoutElements();
                }

                if (progressTextElement != null)
                {
                        String progressText = String.format("%3.0f%%", progress * 100);
                        progressTextElement.getRenderer(TextRenderer.class).setText(progressText);
                }
                
                first = false;
        }
}
