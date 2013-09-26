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
package aphelion.shared.map.tile;

import aphelion.shared.resource.ResourceDB;
import aphelion.shared.map.tile.classic.TileFiller;
import java.util.ArrayList;

/**
 *
 * @author Joris
 */
public abstract class TileFactory
{
        protected ResourceDB db;
        protected boolean loadGraphics;
        private TileType tileFiller;
        private ArrayList<TileType> tileTypes = new ArrayList<>(128);

        public TileFactory(ResourceDB db, boolean loadGraphics)
        {
                this.db = db;
                this.loadGraphics = loadGraphics;
                tileFiller = new TileFiller((short) -1);
        }
        

        public final TileType getTile(short tileID)
        {
                TileType tile;
                int size;

                if (tileID == -1)
                {
                        return tileFiller;
                }

                try
                {
                        tile = tileTypes.get(tileID);
                        if (tile != null)
                        {
                                return tile;
                        }
                }
                catch (IndexOutOfBoundsException ex)
                {
                }

                if (tileID >= tileTypes.size())
                {
                        size = tileTypes.size();
                        if (size < 1)
                        {
                                size = 1;
                        }

                        while (tileID >= size)
                        {
                                size *= 2;
                        }

                        tileTypes.ensureCapacity(size);
                        for (int a = size - tileTypes.size(); a >= 0; --a)
                        {
                                tileTypes.add(null);
                        }
                }

                tile = newTileType(tileID);
                tileTypes.set(tileID, tile);
                return tile;
        }

        protected abstract TileType newTileType(short tileID);
}
