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

package aphelion.client.graphics.screen;


import aphelion.client.graphics.Graph;
import aphelion.shared.resource.ResourceDB;
import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.builder.ControlDefinitionBuilder;
import de.lessvoid.nifty.builder.PanelBuilder;
import de.lessvoid.nifty.controls.Controller;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.elements.render.ElementRenderer;
import de.lessvoid.nifty.elements.render.PanelRenderer;
import de.lessvoid.nifty.input.NiftyInputEvent;
import de.lessvoid.nifty.render.NiftyRenderEngine;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.xml.xpp3.Attributes;
import java.util.Properties;
import java.util.WeakHashMap;
import org.newdawn.slick.Color;
import org.newdawn.slick.opengl.TextureImpl;

/**
 *
 * @author Joris
 */
public class CameraNiftyController implements Controller
{ 
        private Element element;
        private Nifty nifty;
        private Camera camera;
        private CameraForNifty handler;
        private boolean stars;
        private boolean clip;
        private boolean zoomFill;
        
        @Override
        public void bind(Nifty nifty, Screen screen, Element element, Properties parameter,
                         Attributes controlDefinitionAttributes)
        {
                assert this.element == null;
                this.element = element;
                this.nifty = nifty;
                
                ElementRenderer[] renders = element.getElementRenderer();
                for (int i = 0; i < renders.length; ++i)
                {
                        if (renders[i] instanceof PanelRenderer)
                        {
                                renders[i] = new CameraRenderer();
                        }
                }
        }

        @Override
        public void init(Properties parameter, Attributes controlDefinitionAttributes)
        {
                synchronized(handlers)
                {
                        handler = handlers.get(nifty);
                        assert handler != null;
                }
                
                camera = new Camera(handler.getResourceDB());
                camera.nifty = nifty;
                
                String zoom = controlDefinitionAttributes.get("zoom");
                zoomFill = "fill".equals(zoom);
                if (!zoomFill && zoom != null)
                {
                        try
                        {
                                float fZoom = Float.parseFloat(zoom);
                                camera.setZoom(fZoom <= 0 ? 1 : fZoom);
                        }
                        catch(NumberFormatException ex){}
                }
                
                clip = controlDefinitionAttributes.getAsBoolean("clip", true);
                stars = controlDefinitionAttributes.getAsBoolean("stars", false);
                
                String fontPlayer = controlDefinitionAttributes.get("font-player");
                if (fontPlayer != null)
                {
                        camera.playerFont = nifty.createFont(fontPlayer);
                }
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
        
        private class CameraRenderer implements ElementRenderer
        {
                @Override
                public void render(Element element, NiftyRenderEngine r)
                {
                        assert element == CameraNiftyController.this.element;
                        
                        camera.setDimension(element.getWidth(), element.getHeight());
                        camera.setScreenPosition(element.getX(), element.getY());
                        
                        if (zoomFill)
                        {
                                camera.setZoom(camera.dimension.y / 1024f / 16f);
                        }
                        
                        TextureImpl.unbind();
                        Graph.g.setColor(Color.white);
                        
                        if (clip)
                        {
                                camera.setGraphicsClip();
                        }
                        
                        handler.renderCamera(camera, stars);
                        
                        if (clip)
                        {
                                Graph.g.clearClip();
                        }
                        
                        TextureImpl.unbind();
                        Graph.g.setColor(Color.white);
                        r.setColor(de.lessvoid.nifty.tools.Color.WHITE);
                }       
        }
        
        
        private static final WeakHashMap<Nifty, CameraForNifty> handlers = new WeakHashMap<>(1);
        public static void registerControl(Nifty nifty, CameraForNifty handler)
        {
                new ControlDefinitionBuilder("aphelion-camera") {{
                        controller(CameraNiftyController.class.getName());
                        
                        panel(new PanelBuilder() {{
                                width(controlParameter("width"));
                                height(controlParameter("height"));
                                x(controlParameter("width"));
                                y(controlParameter("width"));
                                margin(controlParameter("margin"));
                                backgroundColor("#ff0000aa"); // test
                                
                                try{  align( Align.valueOf(controlParameter( "align"))); }catch(IllegalArgumentException ex) {}
                                try{ valign(VAlign.valueOf(controlParameter("valign"))); }catch(IllegalArgumentException ex) {}
                        }});
                }}.registerControlDefintion(nifty);
                
                synchronized(handlers)
                {
                        handlers.put(nifty, handler);
                }
        }
        
        public static interface CameraForNifty
        {
                ResourceDB getResourceDB();
                void renderCamera(Camera camera, boolean renderStars);
        }
}
