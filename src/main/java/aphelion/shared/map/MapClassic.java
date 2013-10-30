/*
 * Aphelion
 * Copyright (c) 2013  Joris van der Wel & Joshua Edwards
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
 
package aphelion.shared.map;

import aphelion.shared.resource.ResourceDB;
import aphelion.shared.event.WorkerTask;
import aphelion.shared.event.promise.PromiseException;
import aphelion.shared.map.tile.classic.TileClassicFactory;
import aphelion.shared.map.tile.TileFactory;
import aphelion.shared.map.tile.TileType;
import aphelion.shared.physics.PhysicsMap;
import aphelion.shared.swissarmyknife.Colori;
import aphelion.shared.swissarmyknife.SWASlickImageBuffer;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.newdawn.slick.Color;
import org.newdawn.slick.Image;
import org.newdawn.slick.geom.Vector2f;

public class MapClassic implements PhysicsMap
{
        private static final Logger log = Logger.getLogger("aphelion.config");
        
        private ResourceDB resourceDB;
        
        private TileType[][] tiles; // 8 * 1024 * 1024 + 16 * 1024 bytes
        private TileType mapEdge;
        
        private SWASlickImageBuffer tilesetBuffer;
        private Image tileSet;
        
        private SWASlickImageBuffer radarBuffer;
        private Image radar;
        
        private TileFactory tileFactory;
        
        private long levelSize = 1;
        
        private ArrayList<Color> tilesetColors = new ArrayList<Color>();
        private ArrayList<Color> tileColors = new ArrayList<Color>();

        public MapClassic(ResourceDB resourceDB, boolean loadGraphics)
        {
                this.resourceDB = resourceDB;
                tiles = new TileType[1024][1024];
                tileFactory = new TileClassicFactory(resourceDB, loadGraphics);
                
                mapEdge = tileFactory.getTile((short) 20);
                assert mapEdge.physicsIsSolid();
        }
        
        public void read(byte[] mapFile, boolean tileset) throws IOException
        {
                this.levelSize = mapFile.length;
                
                if (tileset)
                {
                        // dimensions of the tileset should be 304, 160
                        parseTileSet(ImageIO.read(new ByteArrayInputStream(mapFile)));
                }
                
                ByteBuffer buf = ByteBuffer.wrap(mapFile);
                buf.order(ByteOrder.LITTLE_ENDIAN);

                // skip first 2 bytes
                if (buf.get() != 0x42 || buf.get() != 0x4D) // BM
                {
                        throw new IOException("Invalid map file");
                }
                
                // After that we get a 4 byte value giving us the offset to the actual tile data.
                // This way we ignore ASSS' elvl stuff.
                int tileDataOffset = buf.getInt();
                log.log(Level.INFO, "Tile data offset = {0}", tileDataOffset);
                
                buf.position(tileDataOffset);

                TileType tileFiller = tileFactory.getTile((short) -1);
                
                while (buf.remaining() >= 4)
                {
                        int i = buf.getInt();
                        
                        short tile = (short) (i >> 24 & 0x00ff);
                        int y = (i >> 12) & 0x03FF;
                        int x = i & 0x03FF;

                        if (x > -1 && y > -1)
                        {
                                TileType tileType;
                                tileType = tileFactory.getTile(tile);

                                assert tileType != null;
                                tiles[x][y] = tileType;

                                int size = tileType.getSize();
                                
                                if (size > 1 && tileType.physicsIsSolid())
                                {
                                        for (int xFiller = x; xFiller < x + size; ++xFiller)
                                        {
                                                for (int yFiller = y; yFiller < y + size; ++yFiller)
                                                {
                                                        if (xFiller == x && yFiller == y)
                                                        {
                                                                continue;
                                                        }

                                                        tiles[xFiller][yFiller] = tileFiller;
                                                }
                                        }
                                }
                        }
                }
        }
        
        public Image getTileset()
        {
                if (tileSet == null)
                {
                        tileSet = tilesetBuffer.getImage(Image.FILTER_NEAREST);
                }
                
                return this.tileSet;
        }
        
        public Image getRadarImage()
        {
                if (this.radar == null)
                {
                        if (this.radarBuffer == null)
                        {
                                this.radar = resourceDB.getTextureLoader().getTexture("gui.loading.map").getCachedImage();
                        }
                        else
                        {
                                this.radar = this.radarBuffer.getImage();
                        }
                }
                
                return this.radar;
        }

        public static long chunkXY2Int(int x, int y)
        {
                long l = x;
                long l1 = y;
                return l & 0xffffffffL | (l1 & 0xffffffffL) << 32;
        }

        @Override
        public boolean physicsIsSolid(int tileX, int tileY)
        {
                TileType tile;

                if (tileX < 0 || tileY < 0
                        || tileX > 1023 || tileY > 1023)
                {
                        return true;
                }


                tile = getTile(tileX, tileY);
                if (tile == null)
                {
                        return false;
                }

                return tile.physicsIsSolid();
        }

        public TileType getTile(int tileX, int tileY)
        {
                if (tileX < 0 || tileY < 0 || tileX > 1023 || tileY > 1023)
                {
                        return null;
                }
                return tiles[tileX][tileY];
        }

        private void parseTileSet(java.awt.image.BufferedImage src)
        {
                tilesetBuffer = new SWASlickImageBuffer(src.getWidth(), src.getHeight());
                for (int y = 0; y < src.getHeight(); y++)
                {
                        for (int x = 0; x < src.getWidth(); x++)
                        {
                                int pixelCol = src.getRGB(x, y);
                                int a = (pixelCol >>> 24) & 0xff;
                                int r = (pixelCol >>> 16) & 0xff;
                                int g = (pixelCol >>> 8) & 0xff;
                                int b = pixelCol & 0xff;
                                if (r == 0 && g == 0 && b == 0)
                                {
                                        a = 0;
                                }
                                tilesetBuffer.setRGBA(x, y, r, g, b, a);
                        }
                }
                
                for (int y = 0; y < 10; y++)
                {
                        for (int x = 0; x < 19; x++)
                        {
                                ArrayList<Float> colorR = new ArrayList<Float>();
                                ArrayList<Float> colorG = new ArrayList<Float>();
                                ArrayList<Float> colorB = new ArrayList<Float>();

                                for (int y2 = 0; y2 < 16; y2++)
                                {
                                        for (int x2 = 0; x2 < 16; x2++)
                                        {
                                                Color color;

                                                int getX = x * 16 + x2;
                                                int getY = y * 16 + y2;
                                                
                                                float r = tilesetBuffer.getRed  (getX, getY) / 255f;
                                                float g = tilesetBuffer.getGreen(getX, getY) / 255f;
                                                float b = tilesetBuffer.getBlue (getX, getY) / 255f;

                                                if (r == 0 && g == 0 && b == 0)
                                                {
                                                        continue;
                                                }
                                                colorR.add(r);
                                                colorG.add(g);
                                                colorB.add(b);
                                        }
                                }
                                Float averageR = 0F;
                                int rCount = 0;
                                Float averageG = 0F;
                                int gCount = 0;
                                Float averageB = 0F;
                                int bCount = 0;
                                for (Float floatObject : colorR)
                                {
                                        averageR += floatObject;
                                        rCount++;
                                }
                                for (Float floatObject : colorG)
                                {
                                        averageG += floatObject;
                                        gCount++;
                                }
                                for (Float floatObject : colorB)
                                {
                                        averageB += floatObject;
                                        bCount++;
                                }
                                averageR /= rCount;
                                averageG /= gCount;
                                averageB /= bCount;
                                Color compositeColor = new Color(averageR, averageG, averageB);
                                tilesetColors.add(compositeColor);
                        }
                }
        }
        
        public void renderRadar(boolean renderLight)
        {
                radarBuffer = new SWASlickImageBuffer(1024, 1024);
                Random random = SwissArmyKnife.random;
                
                Colori black = new Colori(0, 0, 0);
                Colori background = new Colori(0, 0, 0);  // TODO configurable
                Colori colorSafe = new Colori(0x18, 0x52, 0x18);  // TODO configurable
                Colori colorGoal = new Colori(0xFF, 0x39, 0x08);  // TODO configurable
                Colori colorWall = new Colori(0x5a, 0x5A, 0x5A);

                Colori colorWormholeInner = new Colori(0, 0, 0);
                Colori colorWormholeOuter = new Colori(0, 0, 0);
                
                for (int y = 0; y < 1024; y++)
                {
                        for (int x = 0; x < 1024; x++)
                        {
                                TileType tile = tiles[x][y];
                                short tileID = tile == null ? 0 : tile.tileID;
                                
                                if (tileID < 1 || (tileID >= 242 && tileID <= 255))
                                {
                                        int randomInt = random.nextInt(4) - 2;
                                        
                                        // it has no color yet
                                        if (radarBuffer.getAlpha(x, y) == 0)
                                        {
                                                // random background noise
                                                radarBuffer.setRGBA(
                                                        x, y,
                                                        SwissArmyKnife.clip(background.r + randomInt, 0, 255),
                                                        SwissArmyKnife.clip(background.g + randomInt, 0, 255),
                                                        SwissArmyKnife.clip(background.b + randomInt, 0, 255),
                                                        background.a);
                                        }
                                        
                                }
                                else
                                {
                                        if (tileID == 171) // Safe Tile
                                        {
                                                colorSafe.setRGBA(radarBuffer, x, y);
                                        }
                                        else if (tileID == 172) // Goal Tile
                                        {
                                                colorGoal.setRGBA(radarBuffer, x, y);
                                        }
                                        else if (tileID == 217)
                                        {
                                                colorWall.setRGBA(radarBuffer, x + 0, y + 0);
                                                colorWall.setRGBA(radarBuffer, x + 0, y + 1);
                                                colorWall.setRGBA(radarBuffer, x + 1, y + 0);
                                                colorWall.setRGBA(radarBuffer, x + 1, y + 1);
                                        }
                                        else if (tileID == 219)
                                        {
                                                for (int y2 = 0; y2 < 5; y2++)
                                                {
                                                        for (int x2 = 0; x2 < 5; x2++)
                                                        {
                                                                colorWall.setRGBA(radarBuffer, x + x2, y + y2);
                                                        }
                                                }
                                        }
                                        else if (tileID == 220)
                                        {
                                                colorWormholeOuter.setRGBA(radarBuffer, x, y);
                                                colorWormholeOuter.setRGBA(radarBuffer, x + 0, y + 0);
                                                colorWormholeOuter.setRGBA(radarBuffer, x + 1, y + 0);
                                                colorWormholeOuter.setRGBA(radarBuffer, x + 2, y + 0);
                                                colorWormholeOuter.setRGBA(radarBuffer, x + 3, y + 0);
                                                colorWormholeOuter.setRGBA(radarBuffer, x + 4, y + 0);

                                                colorWormholeOuter.setRGBA(radarBuffer, x + 0, y + 1);
                                                colorWormholeInner.setRGBA(radarBuffer, x + 1, y + 1);
                                                colorWormholeInner.setRGBA(radarBuffer, x + 2, y + 1);
                                                colorWormholeInner.setRGBA(radarBuffer, x + 3, y + 1);
                                                colorWormholeOuter.setRGBA(radarBuffer, x + 4, y + 1);

                                                colorWormholeOuter.setRGBA(radarBuffer, x + 0, y + 2);
                                                colorWormholeInner.setRGBA(radarBuffer, x + 1, y + 2);
                                                black.setRGBA(radarBuffer, x + 2, y + 2);
                                                colorWormholeInner.setRGBA(radarBuffer, x + 3, y + 2);
                                                colorWormholeOuter.setRGBA(radarBuffer, x + 4, y + 2);

                                                colorWormholeOuter.setRGBA(radarBuffer, x + 0, y + 3);
                                                colorWormholeInner.setRGBA(radarBuffer, x + 1, y + 3);
                                                colorWormholeInner.setRGBA(radarBuffer, x + 2, y + 3);
                                                colorWormholeInner.setRGBA(radarBuffer, x + 3, y + 3);
                                                colorWormholeOuter.setRGBA(radarBuffer, x + 4, y + 3);

                                                colorWormholeOuter.setRGBA(radarBuffer, x + 0, y + 4);
                                                colorWormholeOuter.setRGBA(radarBuffer, x + 1, y + 4);
                                                colorWormholeOuter.setRGBA(radarBuffer, x + 2, y + 4);
                                                colorWormholeOuter.setRGBA(radarBuffer, x + 3, y + 4);
                                                colorWormholeOuter.setRGBA(radarBuffer, x + 4, y + 4);
                                        }
                                        else if (tileID <= 190)
                                        {
                                                radarBuffer.setRGBA(
                                                        x,
                                                        y,
                                                        (int) (tilesetColors.get(tileID - 1).r * 255),
                                                        (int) (tilesetColors.get(tileID - 1).g * 255),
                                                        (int) (tilesetColors.get(tileID - 1).b * 255),
                                                        255);
                                        }
                                        else
                                        {
                                                colorWall.setRGBA(radarBuffer, x, y);
                                        }

                                }
                        }
                }

                if (renderLight)
                {
                        for (int y = 0; y < 1024; y++)
                        {
                                for (int x = 0; x < 1024; x++)
                                {
                                        TileType tile = tiles[x][y];
                                        short tileID = tile == null ? 0 : tile.tileID;
                                
                                        if (tileID == 171)
                                        {
                                                lightUpColorFromEmitters(radarBuffer, x, y, 4, true, 0);
                                        }
                                        else if (tileID == 172)
                                        {
                                                lightUpColorFromEmitters(radarBuffer, x, y, 4, true, 0);
                                        }
                                        else if (tileID == 217)
                                        {
                                                for (int y2 = 0; y2 < 2; y2++)
                                                {
                                                        for (int x2 = 0; x2 < 2; x2++)
                                                        {
                                                                lightUpColorFromEmitters(radarBuffer, x + x2, y + y2, 3, false, 0);
                                                        }
                                                }
                                        }
                                        else if (tileID == 220)
                                        {
                                                for (int y2 = 0; y2 < 6; y2++)
                                                {
                                                        for (int x2 = 0; x2 < 6; x2++)
                                                        {
                                                                lightUpColorFromEmitters(radarBuffer, x + x2, y + y2, 3, false, 0);
                                                        }
                                                }
                                        }
                                        else if (tileID != 0)
                                        {
                                                lightUpColorFromEmitters(radarBuffer, x, y, 3, false, 2);
                                        }
                                }
                        }
                }
        }
        
        private void lightUpColorFromEmitters(SWASlickImageBuffer stencilWalls, int pointX, int pointY, int light_radius, boolean addOrSub, int offset)
        {
                Color[][] lightColors = new Color[light_radius * 2][light_radius * 2];
                Vector2f vectorSource = new Vector2f(pointX, pointY);
                Colori color = new Colori(0, 0, 0, 255);

                for (int y = -light_radius; y < light_radius; y++)
                {
                        for (int x = -light_radius; x < light_radius; x++)
                        {
                                if (pointX + x < 0 || pointX + x > 1023 || pointY + y < 0 || pointY + y > 1023)
                                {
                                        continue;
                                }
                                lightColors[x + light_radius][y + light_radius] = new Color(0, 0, 0);
                                if (x == 0 && y == 0)
                                {
                                        continue;
                                }
                                TileType tile = tiles[pointX + x][pointY + y];
                                short tileID = tile == null ? 0 : tile.tileID;
                                
                                if (tileID == 172)
                                {
                                        continue;
                                }
                                color.r = (short) stencilWalls.getRed(x, y);
                                color.g = (short) stencilWalls.getGreen(x, y);
                                color.b = (short) stencilWalls.getBlue(x, y);
                                color.a = (short) stencilWalls.getAlpha(x, y);

                                float x2 = x;
                                if (x2 == 0)
                                {
                                        x2 = 1F;
                                }
                                float y2 = y;
                                if (y2 == 0)
                                {
                                        y2 = 1F;
                                }
                                Vector2f vectorResult = new Vector2f(x2, y2);
                                if (addOrSub)
                                {
                                        lightColors[x + light_radius][y + light_radius].r += ((vectorSource.x - vectorResult.x) / vectorSource.distance(vectorResult)) * .001;
                                        lightColors[x + light_radius][y + light_radius].r += ((vectorSource.y - vectorResult.y) / vectorSource.distance(vectorResult)) * .001;
                                        lightColors[x + light_radius][y + light_radius].g += ((vectorSource.x - vectorResult.x) / vectorSource.distance(vectorResult)) * .001;
                                        lightColors[x + light_radius][y + light_radius].g += ((vectorSource.y - vectorResult.y) / vectorSource.distance(vectorResult)) * .001;
                                        lightColors[x + light_radius][y + light_radius].b += ((vectorSource.x - vectorResult.x) / vectorSource.distance(vectorResult)) * .001;
                                        lightColors[x + light_radius][y + light_radius].b += ((vectorSource.y - vectorResult.y) / vectorSource.distance(vectorResult)) * .001;
                                }
                                else
                                {
                                        lightColors[x + light_radius][y + light_radius].r -= ((vectorSource.x - vectorResult.x) / vectorSource.distance(vectorResult)) * .001;
                                        lightColors[x + light_radius][y + light_radius].r -= ((vectorSource.y - vectorResult.y) / vectorSource.distance(vectorResult)) * .001;
                                        lightColors[x + light_radius][y + light_radius].g -= ((vectorSource.x - vectorResult.x) / vectorSource.distance(vectorResult)) * .001;
                                        lightColors[x + light_radius][y + light_radius].g -= ((vectorSource.y - vectorResult.y) / vectorSource.distance(vectorResult)) * .001;
                                        lightColors[x + light_radius][y + light_radius].b -= ((vectorSource.x - vectorResult.x) / vectorSource.distance(vectorResult)) * .001;
                                        lightColors[x + light_radius][y + light_radius].b -= ((vectorSource.y - vectorResult.y) / vectorSource.distance(vectorResult)) * .001;
                                }
                        }
                }
                for (int y = -light_radius; y < light_radius; y++)
                {
                        for (int x = -light_radius; x < light_radius; x++)
                        {
                                try
                                {
                                        if (pointX + (x + offset) < 0 || pointX + (x + offset) > 1023 || pointY + (y + offset) < 0 || pointY + (y + offset) > 1023)
                                        {
                                                continue;
                                        }

                                        stencilWalls.setRGBA(
                                                x, y,
                                                SwissArmyKnife.clip(stencilWalls.getRed(pointX + (x + offset), pointY + (y + offset)) + (int) (lightColors[x + light_radius][y + light_radius].r * 255), 0, 255),
                                                SwissArmyKnife.clip(stencilWalls.getGreen(pointX + (x + offset), pointY + (y + offset)) + (int) (lightColors[x + light_radius][y + light_radius].g * 255), 0, 255),
                                                SwissArmyKnife.clip(stencilWalls.getBlue(pointX + (x + offset), pointY + (y + offset)) + (int) (lightColors[x + light_radius][y + light_radius].b * 255), 0, 255),
                                                stencilWalls.getAlpha(x, y));


                                }
                                catch (NullPointerException e)
                                {
                                        continue;
                                }
                        }
                }

        }

        /**
         * @return the mapEdge
         */
        public TileType getMapEdge()
        {
                return mapEdge;
        }

        /**
         * @return the levelSize
         */
        public long getLevelSize()
        {
                return levelSize;
        }

        @Override
        public int physicsGetMapLimitMinimum()
        {
                return 0;
        }

        @Override
        public int physicsGetMapLimitMaximum()
        {
                return 1024;
        }
        
        /** Read and parse a lvl file. The parameter given should be the resource key.
         */
        public static class LoadMapTask extends WorkerTask<String, MapClassic>
        {
                private final ResourceDB db;
                private final boolean graphics;

                public LoadMapTask(ResourceDB db, boolean graphics)
                {
                        this.db = db;
                        this.graphics = graphics;
                }
                
                @Override
                public MapClassic work(String argument) throws PromiseException
                {
                        MapClassic map = new MapClassic(db, graphics);
                        try
                        {
                                byte[] bytes = db.getBytesSync(argument);
                                if (bytes == null)
                                {
                                        throw new PromiseException("Resource does not exist (map)");
                                }
                                map.read(bytes, graphics);
                                if (graphics)
                                {
                                        map.renderRadar(false);
                                }
                        }
                        catch (IOException ex)
                        {
                                throw new PromiseException(ex);
                        }
                        return map;
                }
        }
}
