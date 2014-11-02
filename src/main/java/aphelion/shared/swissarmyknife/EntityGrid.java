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

package aphelion.shared.swissarmyknife;

import aphelion.shared.physics.valueobjects.PhysicsPoint;

import static aphelion.shared.swissarmyknife.SwissArmyKnife.clip;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;



/**
 * Fast way to look up entities by their position.
 * (At the expensive of memory).
 * @author Joris
 * @param <T> 
 */
public class EntityGrid<T extends EntityGridEntity>
{
        private final LinkedListHead<T>[][] grid;

        /** The size of a single cell in the entity grid.
         * Must be a factor of 2!
         */
        public final int CELL_SIZE;
        
        /** The size of the entire grid (per axis).
         * 
         */
        public final int GRID_SIZE;

        /**
         * Are actions being queued? (until disableQueue() is called)
         */
        private boolean queue_enabled = false;

        /**
         * The queue containing actions to execute upon disableQueue()
         * Grows as needed and stays that way to prevent allocation memory over and over
         */
        private final ArrayList<QueueEntry<T>> queue = new ArrayList<>();

        /** The index of the last entry with actual data.
         */
        private int queue_currentIndex = -1;

        
        /** 
         * @param cellSize The size of a single cell in the entity grid.
         * Must be a factor of 2!
         * @param cells  The size of the entire grid (per axis).
         */
        public EntityGrid(int cellSize, int cells)
        {
                this.CELL_SIZE = cellSize;
                this.GRID_SIZE = cells;
                
                this.grid = new LinkedListHead[GRID_SIZE][GRID_SIZE];
                
                for (int x = 0; x < GRID_SIZE; ++x)
                {
                        for (int y = 0; y < GRID_SIZE; ++y)
                        {
                                grid[x][y] = new LinkedListHead<>();
                        }
                }
        }

        private QueueEntry<T> getEmptyQueueEntry()
        {
                int index = ++queue_currentIndex;
                QueueEntry<T> ret;

                if (index >= queue.size())
                {
                        ret = new QueueEntry<>();
                        queue.add(ret);
                        return ret;
                }

                return queue.get(index);
        }
        
        /** Remove an entity from the grid.
         * 
         * @param entity
         */
        public void removeEntity(@Nonnull T entity)
        {
                LinkedListEntry link;
                QueueEntry<T> entry;

                if (this.queue_enabled)
                {
                        entry = this.getEmptyQueueEntry();
                        entry.removeAction = true;
                        entry.entity = entity;
                        return;
                }

                link = entity.getEntityGridEntry(this);
                link.remove();
        }
        
        /** Update an entity to its new location on the grid. 
         * Or remove it from the grid entirely if the entity no longer has a valid location.
         * 
         * @param entity
         * @param x
         * @param y
         */
        public void updateLocation(@Nonnull T entity, int x, int y)
        {
                LinkedListEntry<T> link;
                QueueEntry<T> entry;

                if (this.queue_enabled)
                {
                        entry = this.getEmptyQueueEntry();
                        entry.removeAction = false;
                        entry.x = x;
                        entry.y = y;
                        entry.entity = entity;
                        return;
                }

                link = entity.getEntityGridEntry(this);

                link.remove();
                
                // Because ENTITY_GRID_CELL_SIZE is a multiple of 2,
                // This should optimize to a simple bitshift
                // If support for negative values were to be added, floor() should probably be used.
                // Currently negative values > -CELL_SIZE are stored in cell 0, this is okay.
                int cell_x = x / CELL_SIZE;
                int cell_y = y / CELL_SIZE;

                try
                {
                        LinkedListHead<T> cell = grid[cell_x][cell_y];
                        cell.append(link);
                }
                catch (IndexOutOfBoundsException ex)
                {
                        // do nothing, the entity will not be part of the collision
                        // This means the projectile is outside of the map
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
        public void updateLocation(@Nonnull T entity, @Nullable PhysicsPoint pos)
        {
                if (pos == null || !pos.set)
                {
                        removeEntity(entity);
                }
                else
                {
                        updateLocation(entity, pos.x, pos.y);
                }
        }
        
        /** Iterate over all the entities in the given square. This iterator is not safe against modifications
         * (however it will not crash)
         * @param low
         * @param high
         * @return 
         */
        public Iterator<T> iterator(final PhysicsPoint low, final PhysicsPoint high)
        {
                return new Iterator<T>()
                {
                        final PhysicsPoint start = new PhysicsPoint();
                        final PhysicsPoint end = new PhysicsPoint();
                        int x;
                        int y;
                        LinkedListEntry<T> next;
                        
                        {
                                start.set = true;
                                start.x = clip(low.x / CELL_SIZE, 0, GRID_SIZE-1);
                                start.y = clip(low.y / CELL_SIZE, 0, GRID_SIZE-1);
                                end.set = true;
                                end.x   = clip(high.x / CELL_SIZE, 0, GRID_SIZE-1);
                                end.y   = clip(high.y / CELL_SIZE, 0, GRID_SIZE-1);
                                
                                x = start.x;
                                y = start.y;
                                
                                findNext();
                        }
                        
                        void findNext()
                        {                                
                                if (next != null)
                                {
                                        next = next.next;
                                }
                                
                                while (next == null && y <= end.y)
                                {
                                        next = grid[x][y].first;
                                        ++x;
                                        
                                        if (x > end.x)
                                        {
                                                x = start.x;
                                                ++y;
                                        }
                                }
                        }
                        
                        @Override
                        public boolean hasNext()
                        {
                                return next != null;
                        }

                        @Override
                        public T next()
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
        
        /** Iterate over all the entities within the given radius (square). This iterator is not safe against modifications
         * (however it will not crash) unless you call enableQueue() first.
         * @param center
         * @param radius
         * @return 
         */
        public Iterator<T> iterator(final PhysicsPoint center, int radius)
        {
                final PhysicsPoint low = new PhysicsPoint(center);
                final PhysicsPoint high = new PhysicsPoint(center);
                low.sub(radius);
                high.add(radius);
                return iterator(low, high);
        }

        /**
         * Enable queuing of any change to the grid. Enable this during iteration to prevent modifications messing up
         * this iteration.
         * Note: if multiple iterators of the same entity grid are needed at the same time (with different views), a
         * different implementation will be needed
         * @throws IllegalStateException The queue is already enabled
         */
        public void enableQueue()
        {
                if (this.queue_enabled)
                {
                        throw new IllegalStateException("EntityGrid queue is already enabled");
                }

                this.queue_enabled = true;
        }

        /**
         * Disable the queue enabled by enableQueue() and apply the changes in the queue.
         * @throws IllegalStateException The queue is already disabled
         */
        public void disableQueue()
        {
                if (!this.queue_enabled)
                {
                        throw new IllegalStateException("EntityGrid queue is already disabled");
                }

                this.queue_enabled = false;

                for (int i = 0; i <= this.queue_currentIndex; ++i)
                {
                        QueueEntry<T> entry = this.queue.get(i);
                        if (entry.removeAction)
                        {
                                this.removeEntity(entry.entity);
                        }
                        else
                        {
                                this.updateLocation(entry.entity, entry.x, entry.y);
                        }
                        entry.reset();
                }

                // clear the queue without removing the objects
                this.queue_currentIndex = -1;
        }

        private static class QueueEntry<T>
        {
                boolean removeAction;
                int x;
                int y;
                T entity;

                public void reset()
                {
                        removeAction = false;
                        x = 0;
                        y = 0;
                        entity = null;
                }
        }
}
