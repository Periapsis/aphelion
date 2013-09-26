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
 
package aphelion.shared.map.tile.classic;

import aphelion.client.graphics.Graph;
import aphelion.shared.resource.ResourceDB;
import aphelion.client.graphics.screen.Camera;
import aphelion.shared.map.MapClassic;
import aphelion.shared.map.tile.TileType;
import aphelion.shared.swissarmyknife.Point;


/**
 *
 * @author Joris
 */
public class TileClassic extends TileType
{
        private boolean solid;
        float tileSetLTX;
        float tileSetLTY;
        float tileSetBRX;
        float tileSetBRY;

        public TileClassic(ResourceDB db, short tileID)
        {
                super(db, tileID);
                
                solid = (tileID >= 170 && tileID <= 190) 
                        || (tileID >= 162 && tileID <= 169); // doors are fly through for now
                solid = ! solid;
                
                tileSetLTX = (tileID-1) % 19;
                tileSetLTY = (tileID-1) / 19; // integer division
                tileSetLTX *= 16;
                tileSetLTY *= 16;
                tileSetBRX = tileSetLTX+16;
                tileSetBRY = tileSetLTY+16;
                
                if (tileID >= 176 && tileID <= 190)
                {
                        this.layer = TILE_LAYER.PLAIN_OVER_SHIP;
                }
                else if (tileID >= 162 && tileID <= 169) 
                {
                        this.layer = TILE_LAYER.PLAIN_OVER_SHIP;
                }
                else
                {
                        this.layer = TILE_LAYER.PLAIN;
                }
        }

        @Override
        public void render(Camera camera, int tileX, int tileY, MapClassic map)
        {
                Point screenPos = new Point();
                
                if (tileID <= 0 || tileID > 190)
                {
                        return;
                }
                
                camera.mapToScreenPosition(tileX * 16, tileY * 16, screenPos);
                Graph.g.drawImage(
                        map.getTileset(), 
                        screenPos.x, screenPos.y,
                        screenPos.x + 16f * camera.zoom, screenPos.y + 16f * camera.zoom,
                        tileSetLTX, tileSetLTY, 
                        tileSetBRX, tileSetBRY);
                
                /*if (solid)
                 {
                 camera.graphics.setColor(Color.red);
                 camera.graphics.drawRect(screenPos.x, screenPos.y, 16, 16);
                 }*/
        }

        @Override
        public boolean physicsIsSolid()
        {
                return solid;
        }
}
