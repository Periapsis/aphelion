package aphelion.client.graphics.screen;


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
public class NiftyBackgroundColorSpriteEffect implements EffectImpl
{
        private static final Logger log = Logger.getLogger(NiftyBackgroundColorSpriteEffect.class.getName());
        
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
                nifty.registerEffect("backgroundColorSpriteEffect", NiftyBackgroundColorSpriteEffect.class.getName());
        }
}
