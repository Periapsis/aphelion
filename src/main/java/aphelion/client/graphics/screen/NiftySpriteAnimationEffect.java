package aphelion.client.graphics.screen;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.Size;
import de.lessvoid.nifty.effects.EffectImpl;
import de.lessvoid.nifty.effects.EffectProperties;
import de.lessvoid.nifty.effects.Falloff;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.layout.Box;
import de.lessvoid.nifty.render.NiftyImage;
import de.lessvoid.nifty.render.NiftyRenderEngine;
import de.lessvoid.nifty.render.image.CompoundImageMode;
import de.lessvoid.nifty.render.image.ImageModeFactory;
import de.lessvoid.nifty.render.image.ImageModeHelper;
import de.lessvoid.nifty.render.image.areaprovider.AreaProvider;
import de.lessvoid.nifty.render.image.renderstrategy.RenderStrategy;
import de.lessvoid.nifty.spi.render.RenderImage;
import de.lessvoid.nifty.tools.Alpha;
import de.lessvoid.nifty.tools.SizeValue;
import de.lessvoid.nifty.spi.time.TimeProvider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class NiftySpriteAnimationEffect implements EffectImpl
{
        private static final Logger log = Logger.getLogger(NiftySpriteAnimationEffect.class.getName());
        
        private static final Method getRenderStrategy;
        static
        {
                try
                {
                        getRenderStrategy = ImageModeFactory.class.getDeclaredMethod("getRenderStrategy", String.class);
                        getRenderStrategy.setAccessible(true);
                }
                catch (NoSuchMethodException ex)
                {
                        throw new Error(ex);
                }
        }
        
        private NiftyImage image;
        
        // From parameters:
        private int spriteW;
        private int spriteH;
        private int[] frameLength;
        private boolean reverse;
        private Alpha alpha;
        private SizeValue inset;
        private SizeValue width;
        private SizeValue height;
        private boolean center;
        private boolean hideIfNotEnoughSpace;
        private boolean activeBeforeStartDelay; // this will render the effect even when using a startDelay value so that it will already render before the startDelay
        
        private int spriteCount;
        private int spriteCountPerLine;
        private int index;
        private long lastUpdate;
        private long nextChange = 0;
        private boolean firstUpdate;
        
        private final AreaProvider areaProvider = new AreaProvider()
        {
                @Override
                public Box getSourceArea(RenderImage renderImage)
                {
                        int imageWidth = renderImage.getWidth();
                        int imageHeight = renderImage.getHeight();
                        
                        int spriteX = index % spriteCountPerLine;
                        int spriteY = index / spriteCountPerLine;

                        int imageX = spriteX * spriteW;
                        int imageY = spriteY * spriteH;

                        if (((imageX + spriteW) > imageWidth) || ((imageY + spriteH) > imageHeight))
                        {
                                log.warning("Sprite's area exceeds image's bounds.");
                        }

                        return new Box(imageX, imageY, spriteW, spriteH);
                }

                @Override
                public Size getNativeSize(NiftyImage image)
                {
                        return new Size(spriteW, spriteH);
                }

                @Override
                public void setParameters(String parameters)
                {
                        assert false;
                }
        };

        @Override
        public void activate(Nifty nifty, Element element, EffectProperties parameter)
        {
                image = nifty.getRenderEngine().createImage(nifty.getCurrentScreen(), parameter.getProperty("filename"), false);
                
                try
                {
                        String[] args = parameter.getProperty("sprite").split(",", 2);
                        spriteW = Integer.parseInt(args[0]);
                        spriteH = Integer.parseInt(args[1]);
                        
                        if (spriteW <= 0 || spriteH <= 0)
                        {
                                throw new IllegalArgumentException("Sprite width and height should be positive");
                        }
                }
                catch(NullPointerException | IndexOutOfBoundsException | NumberFormatException ex)
                {
                        throw new IllegalArgumentException("Missing or incorrect sprite parameter. Expected [w,h] but was ["+ parameter.getProperty("sprite")+"]", ex);
                }
                
                spriteCountPerLine = image.getWidth() / spriteW;
                spriteCount = spriteCountPerLine * (image.getHeight() / spriteH);
                
                RenderStrategy renderStrategy;
                try
                {
                        renderStrategy = (RenderStrategy) getRenderStrategy.invoke(ImageModeFactory.getSharedInstance(), ImageModeHelper.getRenderStrategyProperty(parameter));
                }
                catch (InvocationTargetException | IllegalAccessException ex)
                {
                        throw new AssertionError(ex);
                }
                image.setImageMode(new CompoundImageMode(areaProvider, renderStrategy));
                
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
                
                nextChange = frameLength[0];
                
                reverse = Boolean.valueOf(parameter.getProperty("reverse", "false"));
                alpha = new Alpha(parameter.getProperty("alpha", "#f"));
                inset = new SizeValue(parameter.getProperty("inset", "0px"));
                width = new SizeValue(parameter.getProperty("width", element.getWidth() + "px"));
                height = new SizeValue(parameter.getProperty("height", element.getHeight() + "px"));
                center = Boolean.valueOf(parameter.getProperty("center", "false"));
                hideIfNotEnoughSpace = Boolean.valueOf(parameter.getProperty("hideIfNotEnoughSpace", "false"));
                activeBeforeStartDelay = Boolean.valueOf(parameter.getProperty("activeBeforeStartDelay", "false"));
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
                if (!activeBeforeStartDelay && effectTime <= 0.0)
                {
                        return;
                }
                
                updateIndex(element.getNifty().getTimeProvider());

                int insetOffset = inset.getValueAsInt(element.getWidth());
                int imageX = element.getX() + insetOffset;
                int imageY = element.getY() + insetOffset;
                int imageWidth = width.getValueAsInt(element.getWidth()) - insetOffset * 2;
                int imageHeight = height.getValueAsInt(element.getHeight()) - insetOffset * 2;
                if (hideIfNotEnoughSpace)
                {
                        if (imageWidth > element.getWidth() || imageHeight > element.getHeight())
                        {
                                return;
                        }
                }
                r.saveState(null);
                if (falloff != null)
                {
                        r.setColorAlpha(alpha.mutiply(falloff.getFalloffValue()).getAlpha());
                }
                else
                {
                        if (!r.isColorAlphaChanged())
                        {
                                r.setColorAlpha(alpha.getAlpha());
                        }
                }
                if (center)
                {
                        r.renderImage(image, element.getX() + (element.getWidth() - imageWidth) / 2,
                                      element.getY() + (element.getHeight() - imageHeight) / 2, imageWidth, imageHeight);
                }
                else
                {
                        r.renderImage(image, imageX, imageY, imageWidth, imageHeight);
                }
                r.restoreState();
        }

        @Override
        public void deactivate()
        {
                image.dispose();
        }
        
        
        public static void registerEffect(Nifty nifty)
        {
                nifty.registerEffect("sprite-animation", NiftySpriteAnimationEffect.class.getName());
        }
}
