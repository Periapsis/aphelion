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
import aphelion.client.graphics.world.MapEntity;
import aphelion.shared.map.MapClassic;
import aphelion.shared.map.tile.TileType;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.Point;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import org.lwjgl.opengl.Display;
import org.newdawn.slick.Color;
import org.newdawn.slick.Image;

/**
 *
 * @author Joris
 */
public final class Camera
{
        public final ResourceDB resourceDB;
        
        // All dimension values are without zoom
        public final Point dimension = new Point();
        public int dimensionX; // integer version of the above
        public int dimensionY;
        public final Point dimensionHalf = new Point();
        public final Point dimensionHalfTiles = new Point();

        //Perspective frequency the camera is viewing
        private short cameraFrequency = 0;
        
        // Positions on the map:
        public final Point pos = new Point(0,0); // center of the camera
        public int posX;
        public int posY;
        public final Point leftTop = new Point(); // including zoom
        public final Point rightBottom = new Point(); // including zoom
        public final Point tilePos = new Point(0,0);
        
        // Positions on the screen:
        public  final Point screenPos = new Point(0,0);
        public int screenPosX;
        public int screenPosY;

        /**
         * If the zoom is lower than this value, the camera is rendered as if it was a radar.
         * This means one bitmap image for the entire map is used. 
         * And ships should be rendered without images
         */
        public static final float RADAR_RENDERING = 1 / 8f;
        public float zoom = 1; // higher is zooming in. 
        public float zoom_inverse = 1; // 1/zoom

        public boolean radarRendering;
        
        public Camera(ResourceDB resourceDB)
        {
                this.resourceDB = resourceDB;
                setDimension(Display.getWidth(), Display.getHeight());
        }
        
        private void updateCornerValues()
        {
                this.leftTop.set(
                        pos.x - (this.dimensionHalf.x * zoom_inverse),
                        pos.y - (this.dimensionHalf.y * zoom_inverse));
                
                this.rightBottom.set(
                        pos.x + (this.dimensionHalf.x * zoom_inverse), 
                        pos.y + (this.dimensionHalf.y * zoom_inverse));
        }
        
        public void setPosition(float x, float y)
        {
                if (pos.x == x && pos.y == y)
                {
                        return;
                }
                
                pos.set(x, y);
                tilePos.set(pos);
                tilePos.divide(16);
                tilePos.floor();
                posX = (int) pos.x;
                posY = (int) pos.y;
                updateCornerValues();
        }
        
        public void setPosition(Point pos)
        {
                setPosition(pos.x, pos.y);
        }
        
        public void clipPosition(float xMin, float yMin, float xMax, float yMax)
        {
                setPosition(
                        SwissArmyKnife.clip(
                        pos.x, 
                        SwissArmyKnife.floor(xMin + dimensionHalf.x * zoom_inverse),
                        SwissArmyKnife.ceil(xMax - dimensionHalf.x * zoom_inverse)),
                        
                        
                        SwissArmyKnife.clip(
                        pos.y, 
                        SwissArmyKnife.floor(yMin + dimensionHalf.y * zoom_inverse),
                        SwissArmyKnife.ceil(yMax - dimensionHalf.y * zoom_inverse))
                );
        }
        
        public void setScreenPosition(float x, float y)
        {
                screenPos.set(x, y);
                screenPosX = (int) screenPos.x;
                screenPosY = (int) screenPos.y;
        }
        
        public void setScreenPosition(Point screenPos)
        {
                setScreenPosition(screenPos.x, screenPos.y);
        }
        
        
        public void setDimension(float width, float height)
        {
                if (dimension.x == width && dimension.y == height)
                {
                        return;
                }
                
                width = SwissArmyKnife.ceil(width);
                height = SwissArmyKnife.ceil(height);
                
                // make sure we can draw pricesely at the center without point values
                if (width % 2 == 1)
                {
                        ++width;
                }
                
                if (height % 2 == 1)
                {
                        ++height;
                }
                
                dimension.set(width, height);
                dimensionX = (int) dimension.x;
                dimensionY = (int) dimension.y;
                dimensionHalf.set(dimension);
                dimensionHalf.divide(2);
                dimensionHalfTiles.set(dimensionHalf);
                dimensionHalfTiles.divide(16);
                updateCornerValues();
        }
        
        public void setDimension(Point dimension)
        {
                setDimension(dimension.x, dimension.y);
        }
        
        public void setZoom(float zoom)
        {
                assert zoom > 0;
                this.zoom = zoom;
                this.zoom_inverse = 1 / zoom;
                updateCornerValues();
        }
        
        
        public void mapToScreenPosition(Point objectPos, Point result)
        {
                mapToScreenPosition(objectPos.x, objectPos.y, result);
        }
        
        public void mapToScreenPosition(float x, float y, Point result)
        {
                result.x = -(this.pos.x - x);
                result.y = -(this.pos.y - y);
                result.multiply(zoom);
                result.add(this.dimensionHalf);
                result.add(this.screenPos);
                
                // avoid drawing at point values. 
                // this messes with textures especially those with alpha channels!
                result.round(); 
        }
        
        public boolean isOnScreen(Point objectPos)
        {
                return isOnScreen(objectPos.x, objectPos.y);
        }
        
        public boolean isOnScreen(float x, float y)
        {
                if (x < this.leftTop.x || y < this.leftTop.y)
                {
                        return false;
                }
                
                if (x > this.rightBottom.x || y > this.rightBottom.y)
                {
                        return false;
                }
                                
                return true;
        }
        
        public boolean isOnScreen(float xLow, float yLow, float xHigh, float yHigh)
        {
                if (xHigh < this.leftTop.x || yHigh < this.leftTop.y)
                {
                        return false;
                }
                
                if (xLow > this.rightBottom.x || yLow > this.rightBottom.y)
                {
                        return false;
                }
                
                return true;
        }
        
        public boolean isOnScreen(Point low, Point high)
        {
                return isOnScreen(low.x, low.y, high.x, high.y);
        }
        
        private static Color background = new Color(0, 0, 0, 0.8f);
        private static Color edgeA = new Color(0x84, 0x84, 0x84);
        private static Color edgeB = new Color(0x6B, 0x63, 0x63);
        private static Color edgeC = new Color(0x52, 0x52, 0x52);
        
        public void renderCameraBox()
        {
                // Background
                Graph.g.setColor(background);
                Graph.g.fillRect(screenPos.x, screenPos.y, dimension.x, dimension.y);
                
                // Edges
                Graph.g.setColor(edgeA);
                cameraBoxRect(screenPos.x-1, screenPos.y-1, dimension.x+2, dimension.y+2, 0);
                
                Graph.g.setColor(edgeB);
                cameraBoxRect(screenPos.x-2, screenPos.y-2, dimension.x+4, dimension.y+4, 0);
                
                Graph.g.setColor(edgeC);
                cameraBoxRect(screenPos.x-3, screenPos.y-3, dimension.x+6, dimension.y+6, 2);
                
                /*Graph.g.setColor(Color.red);
                Graph.g.drawRect(screenPos.x + dimension.x / 2, 
                        screenPos.y + dimension.y / 2, 
                        1, 
                        1);*/
        }
        
        private void cameraBoxRect(float x1, float y1, float width, float height, float cornerCutOff)
        {
		Graph.g.drawLine(
                        x1 + cornerCutOff,
                        y1,
                        x1 + width - cornerCutOff,
                        y1);
                
		Graph.g.drawLine(
                        x1 + width,
                        y1 + cornerCutOff,
                        x1 + width,
                        y1 + height - cornerCutOff );
                
		Graph.g.drawLine(
                        x1 + width - cornerCutOff,
                        y1 + height,
                        x1 + cornerCutOff,
                        y1 + height);
                
		Graph.g.drawLine(
                        x1,
                        y1 + height - cornerCutOff,
                        x1,
                        y1  + cornerCutOff);
        }
        
        public void setGraphicsClip()
        {
                Graph.g.setClip((int)screenPos.x, (int)screenPos.y, (int)dimension.x, (int)dimension.y);
        }
        
        public void renderTiles(MapClassic map, TileType.TILE_LAYER layer)
        {
                int xStart, xEnd, yStart, yEnd;
                int x, y;
                TileType tile;
                this.radarRendering = zoom <= RADAR_RENDERING;
                
                if (radarRendering)
                {
                        if (layer != TileType.TILE_LAYER.PLAIN)
                        {
                                return;
                        }
                        
                        setGraphicsClip();

                        float imgX = leftTop.x / 16;
                        float imgY = leftTop.y / 16;
                        float imgX2 = rightBottom.x / 16;
                        float imgY2 = rightBottom.y / 16;
                        
                        Image img = map.getRadarImage();
                        if (img != null)
                        {
                                img.draw(
                                        screenPos.x, screenPos.y, 
                                        screenPos.x + dimension.x, screenPos.y + dimension.y,
                                        imgX,
                                        imgY,
                                        imgX2, 
                                        imgY2
                                        );
                        }
                        Graph.g.clearClip();
                        
                        return;
                }
                
                setGraphicsClip();
                
                Point tileRange = new Point(dimensionHalfTiles);
                tileRange.multiply(zoom_inverse);
                tileRange.add(zoom_inverse);
                
                xStart = (int) SwissArmyKnife.floor(tilePos.x - tileRange.x);
                xEnd = (int) SwissArmyKnife.ceil(tilePos.x + tileRange.x);

                yStart = (int) SwissArmyKnife.floor(tilePos.y - tileRange.y);
                yEnd = (int) SwissArmyKnife.ceil(tilePos.y + tileRange.y);
                
                TileType mapEdge = map.getMapEdge();
                
                for (x = xStart; x <= xEnd; ++x)
                {
                        for (y = yStart; y <= yEnd; ++y)
                        {
                                if (x == -1 || y == -1 || x == 1024 || y == 1024)
                                {
                                        if (layer == TileType.TILE_LAYER.PLAIN)
                                        {
                                                if (x >= -1 && y >= -1 && x <= 1024 && y <= 1024)
                                                {
                                                        mapEdge.render(this, x, y, map);
                                                }
                                        }
                                }
                                else
                                {
                                        tile = map.getTile(x, y);
                                        if (tile != null && tile.layer == layer)
                                        {
                                                tile.render(this, x, y, map);
                                        }
                                }
                        }
                }
                Graph.g.clearClip();
        }
        
        public <T extends MapEntity> void renderEntities(Iterable<T> entities)
        {
                this.radarRendering = zoom <= RADAR_RENDERING;
                
                setGraphicsClip();
                
                boolean needMore = true;
                
                int iteration = 0;
                while(needMore)
                {
                        needMore = false;
                        for (T en : entities)
                        {                                
                                if (en.isWithinCameraRange(leftTop, rightBottom))
                                {
                                        boolean r = en.render(this, iteration);
                                        needMore = needMore || r;
                                }
                                else
                                {
                                        en.noRender();
                                }
                        }
                        
                        ++iteration;
                }
                
                Graph.g.clearClip();
        }
        
        public void renderEntity(MapEntity en)
        {
                boolean needMore = true;
                
                int iteration = 0;
                while(needMore)
                {
                        needMore = false;

                        setGraphicsClip();
                        if (en.isWithinCameraRange(leftTop, rightBottom))
                        {
                                boolean r = en.render(this, iteration);
                                needMore = needMore || r;
                        }
                        else
                        {
                                en.noRender();
                        }
                        Graph.g.clearClip();       
                }
        }
        
        public short getFrequency()
        {
        	return this.cameraFrequency;
        }
        
        public void setFrequency(short freq)
        {
        	this.cameraFrequency = freq;
        }
}
