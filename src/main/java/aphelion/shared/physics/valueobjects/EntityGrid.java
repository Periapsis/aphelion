/*
 * Aphelion
 * Copyright (c) 2014  Joris van der Wel
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
 * different from the original version
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
 * If you modify this library, you may extend this exception to your version
 * of the library, but you are not obliged to do so. If you do not wish to do
 * so, delete this exception statement from your version.
 */

package aphelion.shared.physics.valueobjects;

import aphelion.shared.map.MapClassic;
import aphelion.shared.physics.entities.MapEntity;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import aphelion.shared.swissarmyknife.LinkedListHead;
import aphelion.shared.swissarmyknife.LoopFilter;
import java.util.Iterator;
import java.util.NoSuchElementException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;



/**
 * Fast way to look up entities by their position.
 * (At the expensive of memory).
 * @author Joris
 */
public class EntityGrid
{
        private final LinkedListHead<MapEntity>[][] grid;

        /** The size of a single cell in the entity grid.
         * Must be a factor of 2!
         */
        public final int CELL_SIZE;
        
        /** The size of the entire grid (per axis).
         * 
         */
        public final int GRID_SIZE;
        
        
        public EntityGrid(int cellSize)
        {
                this.CELL_SIZE = cellSize;
                
                // Todo: map size is fixed at 1024x1024, what about bigger maps or a chunked map format?
                GRID_SIZE = 1024 * MapClassic.TILE_PIXELS / CELL_SIZE;
                if (1024 * MapClassic.TILE_PIXELS % CELL_SIZE != 0)
                {
                        throw new IllegalArgumentException();
                }
                
                this.grid = new LinkedListHead[GRID_SIZE][GRID_SIZE];
                
                for (int x = 0; x < GRID_SIZE; ++x)
                {
                        for (int y = 0; y < GRID_SIZE; ++y)
                        {
                                grid[x][y] = new LinkedListHead<>();
                        }
                }
        }
        
        /** Update an entity to its new location on the grid. 
         * Or remove it from the grid entirely if the entity no longer has a valid location.
         * 
         * @param entity
         * @param pos If set, calculate the new grid position (using a fast bit shift), 
         * if not set remove the entity from the grid (or if the entity is outside of the map borders).
         * @return True if the entity is now present in the grid.
         */
        public boolean updateLocation(@Nonnull MapEntity entity, @Nullable PhysicsPoint pos)
        {
                entity.entityGridEntry.remove();
                        
                if (pos != null && pos.set)
                {
                        // Because ENTITY_GRID_CELL_SIZE is a multiple of 2,
                        // This should optimize to a simple bitshift
                        int cell_x = pos.x / CELL_SIZE;
                        int cell_y = pos.y / CELL_SIZE;

                        try
                        {
                                LinkedListHead<MapEntity> cell = grid[cell_x][cell_y];
                                cell.append(entity.entityGridEntry);
                                return true;
                        }
                        catch (IndexOutOfBoundsException ex)
                        {
                                // do nothing, the entity will not be part of the collison
                                // This means the projectile is outside of the map
                        }
                }
                
                return false;
        }
        
        /** Iterate over all the entities in the given square.
         * @param low
         * @param high
         * @return 
         */
        public Iterator<MapEntity> iterator(final PhysicsPoint low, final PhysicsPoint high)
        {
                return new Iterator<MapEntity>()
                {
                        int x = low.x;
                        int y = low.y;
                        LinkedListEntry<MapEntity> next;
                        
                        {
                                if (x < 0) { x = 0; }
                                if (y < 0) { y = 0; }
                                findNext();
                        }
                        
                        void findNext()
                        {
                                if (next != null)
                                {
                                        next = next.next;
                                }
                                
                                while (next == null)
                                {
                                        next = grid[x][y].first;
                                        ++x;
                                        
                                        if (x > high.x || x > GRID_SIZE)
                                        {
                                                x = low.x;
                                                ++y;
                                                
                                                if (y > high.y || y > GRID_SIZE)
                                                {
                                                        next = null;
                                                        return;
                                                }
                                        }
                                }
                        }
                        
                        @Override
                        public boolean hasNext()
                        {
                                return next != null;
                        }

                        @Override
                        public MapEntity next()
                        {
                                if (!hasNext())
                                {
                                        throw new NoSuchElementException();
                                }
                                
                                try
                                {
                                        return next.data;
                                }
                                finally
                                {
                                        findNext();
                                }
                        }

                        @Override
                        public void remove()
                        {
                                throw new UnsupportedOperationException();
                        }
                };
        }
        
        /** Iterate over all the entities within the given radius (square).
         * @param center
         * @param radius
         * @return 
         */
        public Iterator<MapEntity> iterator(final PhysicsPoint center, int radius)
        {
                final PhysicsPoint low = new PhysicsPoint(center);
                final PhysicsPoint high = new PhysicsPoint(center);
                low.sub(radius);
                high.add(radius);
                return iterator(low, high);
        }
}
