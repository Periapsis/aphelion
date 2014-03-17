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

package aphelion.shared.physics;

import aphelion.shared.gameconfig.GCInteger;
import static aphelion.shared.physics.PhysicsMap.TILE_PIXELS;
import aphelion.shared.physics.entities.MapEntity;
import aphelion.shared.physics.valueobjects.EntityGrid;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsPointHistoryDetailed;
import aphelion.shared.swissarmyknife.ComparableIntegerDivision;
import aphelion.shared.swissarmyknife.LoopFilter;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import static aphelion.shared.swissarmyknife.SwissArmyKnife.abs;
import static aphelion.shared.swissarmyknife.SwissArmyKnife.clip;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;



/**
 * A helper class to perform (cross machine) deterministic collisions.
 * @author Joris
 */
public final class Collision
{
        private static final Logger log = Logger.getLogger("aphelion.shared.physics");
        
        private PhysicsMap map;
        
        private int radius;
        
        /** The position for the previous tick. */
        private final PhysicsPoint prevPos = new PhysicsPoint();
        
        /** The position for the previous tick. */
        private final PhysicsPoint newPos = new PhysicsPoint();
        private final PhysicsPoint vel = new PhysicsPoint(0, 0);
        private PhysicsPointHistoryDetailed posHistoryDetails;
        
        private EntityGrid collideGrid;
        private LoopFilter<MapEntity, Long> collideFilter = LoopFilter.NO_FILTER;
        
        private int bounceFriction;
        private int otherAxisFriction;
        
        private int bounces; // how many times did the object bounce of a tile?
        private int bounces_left;
        private boolean exhaustedBounces;
        
        private final ArrayList<HitData> hitEntities = new ArrayList<>(128);
        private final PhysicsPoint hitTile = new PhysicsPoint();
        
        
        public static final class HitData
        {
                /** What did we hit?. */
                public MapEntity entity;
                
                /** At what location did the hit occur?. */
                public final PhysicsPoint location = new PhysicsPoint();

                public HitData(MapEntity entity, PhysicsPoint location)
                {
                        this.entity = entity;
                        this.location.set(location);
                }
        }
        
        public static enum TILE_SIDE
        {
                NONE,
                TOP,
                RIGHT,
                BOTTOM,
                LEFT;
        }
        
        public Collision()
        {
                reset();
        }
        
        public void reset()
        {
                this.map = null;
                this.radius = 0;
                this.prevPos.unset();
                this.newPos.unset();
                this.vel.unset();
                this.posHistoryDetails = null;
                this.collideGrid = null;
                this.collideFilter = LoopFilter.NO_FILTER;
                this.bounceFriction = GCInteger.RATIO;
                this.otherAxisFriction = GCInteger.RATIO;
                this.bounces_left = -1;
        }

        public void getPreviousPosition(@Nonnull PhysicsPoint pos)
        {
                pos.set(this.prevPos);
        }
        
        public void getNewPosition(@Nonnull PhysicsPoint pos)
        {
                pos.set(this.newPos);
        }

        public void getVelocity(@Nonnull PhysicsPoint vel)
        {
                vel.set(this.vel);
        }

        public void setMap(@Nullable PhysicsMap map)
        {
                this.map = map;
        }

        public void setRadius(int radius)
        {
                this.radius = radius;
        }

        public void setPreviousPosition(@Nonnull PhysicsPoint pos)
        {
                this.prevPos.set(pos);
        }
        
        public void setNewPosition(@Nonnull PhysicsPoint pos)
        {
                this.newPos.set(pos);
        }

        public void setVelocity(@Nonnull PhysicsPoint vel)
        {
                this.vel.set(vel);
        }

        /** If set, the location of multiple collisions within the same tick will be recorded into "posHistoryDetails".
         * @param posHistoryDetails 
         */
        public void setPosHistoryDetails(PhysicsPointHistoryDetailed posHistoryDetails)
        {
                this.posHistoryDetails = posHistoryDetails;
        }

        public void setCollideGrid(@Nullable EntityGrid collideGrid)
        {
                this.collideGrid = collideGrid;
        }

        public void setCollideFilter(@Nonnull LoopFilter<MapEntity, Long> collideFilter)
        {
                this.collideFilter = collideFilter;
        }

        public void setBounceFriction(int bounceFriction)
        {
                this.bounceFriction = bounceFriction;
        }

        public void setOtherAxisFriction(int otherAxisFriction)
        {
                this.otherAxisFriction = otherAxisFriction;
        }

        /** Number of bounces on a tile the simulated object has left. Use -1 for infinite.
         * if this value is 0 and hits a tile, the object has collided 
         * @param bounces_left
         */
        public void setBouncesLeft(int bounces_left)
        {
                this.bounces_left = bounces_left;
        }

        /** Which entities did we hit during the previous tick()?.
         * @return 
         */
        public @Nonnull Iterator<HitData> getHitEntities()
        {
                return hitEntities.iterator();
        }

        /** If the entity has exhausted all its bounces in the previous tick(), 
         * this is the final tile it collided on.
         * @param tilePos The return value
         */
        public void getHitTile(PhysicsPoint tilePos)
        {
                tilePos.set(this.hitTile);
        }

        /** How many times has this entity bounced of a tile in the previous tick()?.
         * @return  
         */
        public int getBounces()
        {
                return bounces;
        }

        /** Did tick() stop because the entity made its final bounce?.
         * @return 
         */
        public boolean hasExhaustedBounces()
        {
                return exhaustedBounces;
        }
        
        
        /** Update the given position using the given velocity and attempt collision with the map.
         * Use setPreviousPosition() and setVelocity() to set the position to begin the simulation with.
         * The result will end up in getNewPosition() and getVelocity().
         * Use setPosHistoryDetails() if you would like to store the positions of any intermediate bounces.
         * @param tick For which tick is this collision?
         */
        public void tickMap(long tick)
        {
                this.hitTile.unset();
                this.bounces = 0;
                this.exhaustedBounces = false;
                
                if (!prevPos.set)
                {
                        throw new IllegalStateException();
                }
                
                if (!vel.set)
                {
                        throw new IllegalStateException();
                }
                
                if (radius < 0)
                {
                        throw new IllegalStateException();
                }
                
                // How much velocity still needs to be added?
                // This is used to properly calculate multiple tile bounces within a single tick
                // Each iteration of the loop below will take care of one bounce, until there is no more
                // velocity left.
                // This value is always >= 0
                final PhysicsPoint remaining = new PhysicsPoint();
                
                final PhysicsPoint previousPosHistoryDetail = new PhysicsPoint(prevPos);
                
                remaining.set(vel);
                remaining.abs();
                
                newPos.set(prevPos);
                
                while (true)
                {
                        final PhysicsPoint pos = new PhysicsPoint(newPos);
                        newPos.x += vel.x >= 0 ? remaining.x : -remaining.x ;
                        newPos.y += vel.y >= 0 ? remaining.y : -remaining.y ;                        
                        
                        
                        // If map is set, we are doing tile collision
                        // No need to match tiles if we are stationairy
                        if (this.map != null && !pos.equals(newPos))
                        {
                                final PhysicsPoint intersect = new PhysicsPoint();
                                boolean tileCollided = tileCollisionRay(pos, newPos, radius, intersect);
                                // at this point tileCollisionRay has set a value for newPos if there was a hit
                                // (its value is the earliest collision point in a straight line (adjusted for radius)).
                                // If not, the newPos is not modified.
                                // Use this line to try further collision
                                
                                if (tileCollided)
                                {
                                        assert intersect.set;
                                        newPos.set(intersect);
                                        ++bounces;
                                        
                                        if (!hasBouncesLeft())
                                        {
                                                // This entity can no longer bounce
                                                this.hitTile.set(pos);
                                                this.hitTile.divide(TILE_PIXELS);
                                                this.exhaustedBounces = true;
                                                return;
                                        }
                                }
                        }
                        
                        
                        
                        
                        
                        // No final collision, there are more steps to make
                        final PhysicsPoint prevRemaining = new PhysicsPoint(remaining);
                        remaining.x -= abs(newPos.x - pos.x);
                        remaining.y -= abs(newPos.y - pos.y);
                        
                        remaining.clip(null, prevRemaining);

                        if (remaining.equals(prevRemaining))
                        {
                                break;
                        }
                        
                        if (remaining.x < 0 || remaining.y < 0)
                        {
                                break;
                        }
                        
                        if (remaining.isZero())
                        {
                                // done
                                break;
                        }
                        
                        // Remaining has a value > 0, so there are more steps to make
                        
                        if (posHistoryDetails != null)
                        {
                                // do we need to add a detail? (have we not moved in a straight line during this iteration?)
                                // note that the very first step and the very last step are not a detail!
                                // the very first step is the end result of the previous tick, 
                                // the very last step is the end result of the current tick.
                                
                                // if previousPosHistoryDetail == prevPos there is no detail to add yet
                                if (!previousPosHistoryDetail.equals(this.prevPos)) 
                                {
                                        // have we moved in a straight line?
                                        if (findYOnLine(previousPosHistoryDetail, pos, newPos.x) != newPos.y)
                                        {
                                                previousPosHistoryDetail.set(pos);
                                                posHistoryDetails.appendDetail(tick, pos);
                                        }
                                }
                        }
                }
                
                // At this point, no "final" collision was made

                if (map != null)
                {
                        //enforce map limits to be safe
                        newPos.x = clip(
                                newPos.x, 
                                map.physicsGetMapLimitMinimum() * TILE_PIXELS + radius, 
                                map.physicsGetMapLimitMaximum() * TILE_PIXELS - radius);

                        newPos.y = clip(
                                newPos.y, 
                                map.physicsGetMapLimitMinimum() * TILE_PIXELS + radius, 
                                map.physicsGetMapLimitMaximum() * TILE_PIXELS - radius);
                }
                
                newPos.enforceOverflowLimit();
                vel.enforceOverflowLimit();
        }
        
        
        /** Attempt collision with other entities using the positions between tick-1 and tick.
         * This should usually be called using the results of tickMap().
         * Use setPreviousPosition() to set the position of the previous tick (tick-1). 
         * Use setNewPosition() to set the position of the current tick (tick+0).
         * Use setPosHistoryDetails() (optionally) to set the intermediate positions (tick-1 =&gt; tick).
         * You can retrieve the results using getHitEntities().
         * @param tick
         */
        public void tickEntityCollision(long tick)
        {
                this.hitEntities.clear();
                
                if (collideGrid == null)
                {
                        return;
                }
                
                if (this.posHistoryDetails != null)
                {
                        this.posHistoryDetails.seekDetail(tick);
                }
                
                final PhysicsPoint prev = new PhysicsPoint();
                final PhysicsPoint next = new PhysicsPoint();
                
                prev.set(this.prevPos);
                
                while (true)
                {
                        boolean lastIteration = false;
                        if (this.posHistoryDetails != null && this.posHistoryDetails.hasNextDetail())
                        {
                                this.posHistoryDetails.nextDetail(next);
                        }
                        else
                        {
                                // ran out of details..
                                // the final line to match is the last detail ->  the final position for this tick.
                                next.set(newPos);

                                lastIteration = true;
                        }
                        
                        // NOTE: This algorithm assumes ticks are quantum.
                        // Aka there is no tick 8.2, only tick 8 and 9. The whole movevement of the 
                        // colliding entities (including multiple bounces) within a single tick is 
                        // considered for collision.
                        // This may cause a ship to hit a projectile even though their paths really 
                        // should not have crossed when they both have a high velocity.
                        
                        
                        // First, find collision candidates quickly using a grid (2d array).
                        final PhysicsPoint diff = new PhysicsPoint();
                        diff.set(next);
                        diff.sub(prev);
                        diff.abs();
                        diff.add(radius);
                        
                        final PhysicsPoint low = new PhysicsPoint(prev);
                        low.sub(radius);
                        low.divideFloor(collideGrid.CELL_SIZE);

                        final PhysicsPoint high = new PhysicsPoint(prev);
                        high.add(radius);
                        high.divideCeil(collideGrid.CELL_SIZE);
                        
                        // Note: the collidGrid is based on non-smoothed positions.
                        // This does not matter at the moment because collisions are performed
                        // only with projectiles (which do not have smoothed positions).
                        // In case we ARE colliding with smoothed positions, a secondary grid is needed.
                        // Or perhaps simply use a wider area to look for collision candidates
                        
                        Iterator<MapEntity> it = collideGrid.iterator(low, high);

                        while (it.hasNext())
                        {
                                MapEntity collider = it.next();
                                
                                if (collideFilter.loopFilter(collider, tick))
                                {
                                        continue;
                                }

                                final PhysicsPoint hitLocation = new PhysicsPoint();
                                if (entityCollision(tick, prev, next, collider, hitLocation))
                                {
                                        this.hitEntities.add(new HitData(collider, hitLocation));
                                }
                        }
                        
                        
                        // Prepare for the next position detail step...
                        prev.set(next);

                        if (lastIteration)
                        {
                                break;
                        }
                }
   
        }
        
        private boolean hasBouncesLeft()
        {
                // call me after incrementing bounces
                return this.bounces_left < 0 || bounces <= this.bounces_left;
        }
        
        private boolean tileCollisionRay(PhysicsPoint rayFrom, PhysicsPoint rayTo, int radius, PhysicsPoint intersect)
        {
                // bresenham line algorithm
                // http://tech-algorithm.com/articles/drawing-line-using-bresenham-algorithm/
                // http://stackoverflow.com/questions/15295195/can-i-easily-skip-pixels-in-bresenhams-line-algorithm
                
                int x = rayTo.x;
                int y = rayTo.y;
                
                int w = rayFrom.x - x;
                int h = rayFrom.y - y;
                int dx1 = 0, dy1 = 0, dx2 = 0, dy2 = 0;
                
                if (w < 0)
                {
                        dx1 = -1;
                }
                else if (w > 0)
                {
                        dx1 = 1;
                }
                
                if (h < 0)
                {
                        dy1 = -1;
                }
                else if (h > 0)
                {
                        dy1 = 1;
                }
                
                if (w < 0)
                {
                        dx2 = -1;
                }
                else if (w > 0)
                {
                        dx2 = 1;
                }
                
                int biggest = Math.abs(w);
                int smallest = Math.abs(h);
                
                if (!(biggest > smallest))
                {
                        int tmp = biggest;
                        biggest = smallest;
                        smallest = tmp;
                        
                        if (h < 0)
                        {
                                dy2 = -1;
                        }
                        else if (h > 0)
                        {
                                dy2 = 1;
                        }
                        
                        dx2 = 0;
                }
                
                int numerator = biggest / 2;
                
                final PhysicsPoint tilePos = new PhysicsPoint(0, 0);
                
                for (int i = 0; i <= biggest; i += TILE_PIXELS)
                {
                        tilePos.x = x / TILE_PIXELS;
                        tilePos.y = y / TILE_PIXELS;
                        
                        if (map.physicsIsSolid(tilePos.x, tilePos.y) 
                                && tileCollision(rayFrom, rayTo, tilePos.x, tilePos.y, intersect))
                        {
                                assert intersect.set;
                                
                                return true;
                        }
                        
                        if (radius > 0)
                        {
                                // todo: efficiency?
                                int radiusTiles = (radius / TILE_PIXELS) + 1;
                                
                                for (int tileX = tilePos.x - radiusTiles; tileX <= tilePos.x + radiusTiles; ++tileX)
                                {
                                        for (int tileY = tilePos.y - radiusTiles; tileY <= tilePos.y + radiusTiles; ++tileY)
                                        {
                                                if (map.physicsIsSolid(tileX, tileY) 
                                                        && tileCollision(rayFrom, rayTo, tileX, tileY, intersect))
                                                {
                                                        return true;
                                                }
                                        }
                                }
                        }
                        
                        if (biggest == 0)
                        {
                                break;
                        }
                        
                        int k_old = numerator;
                        
                        numerator = (numerator + smallest * TILE_PIXELS) % biggest;
                        
                        if (!(numerator < biggest))
                        {
                                numerator -= biggest;
                        }
                        
                        x += ((k_old + smallest * TILE_PIXELS) / biggest) * dx1;
                        y += ((k_old + smallest * TILE_PIXELS) / biggest) * dy1;
                        
                        x += (TILE_PIXELS - ((k_old + smallest * TILE_PIXELS) / biggest)) * dx2;
                        y += (TILE_PIXELS - ((k_old + smallest * TILE_PIXELS) / biggest)) * dy2;
                }
                
                return false;
        }
        
        private boolean tileCollision(PhysicsPoint rayFrom, PhysicsPoint rayTo, int tileX, int tileY, PhysicsPoint intersect)
        {
                final PhysicsPoint tilePixelsTL = new PhysicsPoint();
                final PhysicsPoint tilePixelsBR = new PhysicsPoint();
                final TileSideDist[] sides = new TileSideDist[4];
                final TileSideDist top = new TileSideDist(TILE_SIDE.TOP);
                final TileSideDist right = new TileSideDist(TILE_SIDE.RIGHT);
                final TileSideDist bottom = new TileSideDist(TILE_SIDE.BOTTOM);
                final TileSideDist left = new TileSideDist(TILE_SIDE.LEFT);
                
                tilePixelsTL.set(tileX, tileY);
                tilePixelsTL.multiply(TILE_PIXELS);
                tilePixelsBR.set(tilePixelsTL);
                tilePixelsBR.add(TILE_PIXELS);
                
                boolean matchTopSide    = !map.physicsIsSolid(tileX    , tileY - 1);
                boolean matchRightSide  = !map.physicsIsSolid(tileX + 1, tileY    );
                boolean matchBottomSide = !map.physicsIsSolid(tileX    , tileY + 1);
                boolean matchLeftSide   = !map.physicsIsSolid(tileX - 1, tileY    );
                
                /*
                ______                             -----------
               (_   __) .-""""-.   PEW            |           |
                 ) (___/        '.   PEW          |           |
                (   ___          :  o o o o o o o x o o o o o o o o
                _) (__ \        .'                |           |
               (______) '-....-'                  |           |
                                                   -----------

                          match left line segment first, 
                          then match the right line segment.
                          Left AND right line segments are matched 
                          because a ship / bullet may be inside a 
                          field of tiles, or because of a tile that 
                          is semi permeable.
                */
                
                sortTileSidesByDist(
                        sides,
                        tilePixelsTL, rayFrom, 
                        matchTopSide, matchRightSide, matchBottomSide, matchLeftSide,
                        top, right, bottom, left);
                
                for (int i = 0; i < 4; ++i)
                {
                        TileSideDist side = sides[i];
                        if (side == null)
                        {
                                break;
                        }
                        
                        boolean collide;
                        
                        if (side.side == TILE_SIDE.LEFT)
                        {
                                collide = tileCollisionLineSegment(
                                        rayFrom, rayTo,
                                        tilePixelsTL.x, tilePixelsTL.y, 
                                        tilePixelsTL.x, tilePixelsTL.y + TILE_PIXELS,
                                        TILE_SIDE.LEFT,
                                        intersect);
                        }
                        else if (side.side == TILE_SIDE.RIGHT)
                        {
                                collide = tileCollisionLineSegment(
                                        rayFrom, rayTo,
                                        tilePixelsTL.x + TILE_PIXELS, tilePixelsTL.y, 
                                        tilePixelsTL.x + TILE_PIXELS, tilePixelsTL.y + TILE_PIXELS,
                                        TILE_SIDE.RIGHT,
                                        intersect);
                        }
                        else if (side.side == TILE_SIDE.TOP)
                        {
                                collide = tileCollisionLineSegment(
                                        rayFrom, rayTo,
                                        tilePixelsTL.x, tilePixelsTL.y, 
                                        tilePixelsTL.x + TILE_PIXELS, tilePixelsTL.y,
                                        TILE_SIDE.TOP,
                                        intersect);
                        }
                        else if (side.side == TILE_SIDE.BOTTOM)
                        {
                                collide = tileCollisionLineSegment(
                                        rayFrom, rayTo,
                                        tilePixelsTL.x, tilePixelsTL.y + TILE_PIXELS, 
                                        tilePixelsTL.x + TILE_PIXELS, tilePixelsTL.y + TILE_PIXELS,
                                        TILE_SIDE.BOTTOM,
                                        intersect
                                );
                        }
                        else
                        {
                                assert false;
                                continue;
                        }
                        
                        if (collide)
                        {
                                assert intersect.set;
                                return true;
                        }
                }
                
                return false;
        }
        
        
        /** Try a single entity on entity collision.
         * 
         * @param tick Current tick
         * @param posFrom The previous position of the entity that this Collision belongs to (not argument en)
         * @param posTo The next position of the entity that this Collision belongs to (not argument en)
         * @param en The entity to collide with
         * @param hitLocation The hit location is set here (nearest intersection)
         * @return 
         */
        private boolean entityCollision(long tick, PhysicsPoint posFrom, PhysicsPoint posTo, MapEntity en, PhysicsPoint hitLocation)
        {
                hitLocation.unset();
                if (en.radius.get() != 0)
                {
                        throw new IllegalStateException("Collision between two entities, both with radius is not implemented yet.");
                }
                
                final PhysicsPoint entityFrom = new PhysicsPoint();
                final PhysicsPoint entityTo = new PhysicsPoint();
                
                if (!en.getHistoricPosition(entityFrom, tick - 1, false) ||
                    !en.getHistoricPosition(entityTo, tick, false))
                {
                        return false;
                }
                
                if (lineSegmentIntersectsConvexPolygon(
                        entityFrom.x, entityFrom.y, entityTo.x, entityTo.y, // The line segment (0 radius)
                        buildPositionDeltaPolygon(posFrom, posTo, this.radius),
                        hitLocation, null))
                {
                        if (!hitLocation.set)
                        {
                                hitLocation.set(entityFrom);
                                hitLocation.set = true;
                        }

                        return true;
                }
                
                return false;
        }
        
        private static List<PhysicsPoint> buildPositionDeltaPolygon(PhysicsPoint from, PhysicsPoint to, int radius)
        {
                final PhysicsPoint v1 = new PhysicsPoint();
                final PhysicsPoint v2 = new PhysicsPoint();
                final PhysicsPoint v3 = new PhysicsPoint();
                final PhysicsPoint v4 = new PhysicsPoint();
                final PhysicsPoint v5 = new PhysicsPoint();
                final PhysicsPoint v6 = new PhysicsPoint();
                final ArrayList<PhysicsPoint> vertices = new ArrayList<>(6);
                
                assert from.set;
                assert to.set;
                
                // Create a convex polygon which represents 
                // the movement the entity rectangle went through.

                // it is possible to do this in less lines;
                // howeve, in this form you can understand 
                // wth is going on more easily.

                if (to.x >= from.x && to.y >= from.y)
                {
                        // moving to the bottom right

                        //   p2       p3
                        //     -------
                        //    |  FROM  \
                        //    |   o     \
                        //    |          \
                        // p1 \           \
                        //     \           \
                        //      \           \
                        //       \           \
                        //        \     TO   | p4
                        //         \     o   |
                        //          \        |
                        //        p6 --------  p5

                        v1.set(from);
                        v1.x -= radius;
                        v1.y += radius;

                        v2.set(from);
                        v2.x -= radius;
                        v2.y -= radius;

                        v3.set(from);
                        v3.x += radius;
                        v3.y -= radius;

                        v4.set(to);
                        v4.x += radius;
                        v4.y -= radius;

                        v5.set(to);
                        v5.x += radius;
                        v5.y += radius;

                        v6.set(to);
                        v6.x -= radius;
                        v6.y += radius;
                }
                else if (to.x >= from.x && to.y <= from.y)
                {
                        // moving to the top right

                        v1.set(from);
                        v1.x += radius;
                        v1.y += radius;

                        v2.set(from);
                        v2.x -= radius;
                        v2.y += radius;

                        v3.set(from);
                        v3.x -= radius;
                        v3.y -= radius;

                        v4.set(to);
                        v4.x -= radius;
                        v4.y -= radius;

                        v5.set(to);
                        v5.x += radius;
                        v5.y -= radius;

                        v6.set(to);
                        v6.x += radius;
                        v6.y += radius;
                }
                else if (to.x <= from.x && to.y >= from.y)
                {
                        // moving to the bottom left
                        v1.set(from);
                        v1.x -= radius;
                        v1.y -= radius;

                        v2.set(from);
                        v2.x += radius;
                        v2.y -= radius;

                        v3.set(from);
                        v3.x += radius;
                        v3.y += radius;

                        v4.set(to);
                        v4.x += radius;
                        v4.y += radius;

                        v5.set(to);
                        v5.x -= radius;
                        v5.y += radius;

                        v6.set(to);
                        v6.x -= radius;
                        v6.y -= radius;
                }
                else if (to.x <= from.x && to.y <= from.y)
                {
                        // moving to the top left
                        v1.set(from);
                        v1.x += radius;
                        v1.y -= radius;

                        v2.set(from);
                        v2.x += radius;
                        v2.y += radius;

                        v3.set(from);
                        v3.x -= radius;
                        v3.y += radius;

                        v4.set(to);
                        v4.x -= radius;
                        v4.y += radius;

                        v5.set(to);
                        v5.x -= radius;
                        v5.y -= radius;

                        v6.set(to);
                        v6.x += radius;
                        v6.y -= radius;
                }
                else
                {
                        assert false;
                }

                vertices.clear();
                vertices.add(v1);
                vertices.add(v2);
                vertices.add(v3);
                vertices.add(v4);
                vertices.add(v5);
                vertices.add(v6);
                assert vertices.size() > 3;
                return vertices; // clockwise
        }
        
        private boolean tileCollisionLineSegment(
                PhysicsPoint rayFrom, PhysicsPoint rayTo,
                int x1, int y1, int x2, int y2, 
                TILE_SIDE tileSide,
                PhysicsPoint intersection)
        {
                intersection.unset();
                
                if (this.radius == 0)
                {
                        // fast case for 0 radius
                        
                        if (!lineSegmentsIntersect(
                                rayFrom.x, rayFrom.y, rayTo.x, rayTo.y, 
                                x1, y1, x2, y2, 
                                false))
                        {
                                return false;
                        }
                }
                else
                {
                        if (!lineSegmentIntersectsConvexPolygon(
                                x1, y1, x2, y2,
                                buildPositionDeltaPolygon(rayFrom, rayTo, radius),
                                null, null))
                        {
                                return false;
                        }
                        
                }
                
                // yay we hit something!
                if (tileSide == TILE_SIDE.LEFT || tileSide == TILE_SIDE.RIGHT)
                {
                        int newX;
                        if (rayFrom.x <= x1)
                        {
                                newX = x1 - radius - 1;
                        }
                        else
                        {
                                newX = x1 + radius + 1;
                        }
                        
                        intersection.set = true;
                        intersection.y = findYOnLine(rayFrom, rayTo, newX);
                        intersection.x = newX;

                        if (hasBouncesLeft())
                        {
                                // this is not the result of the last bounce
                                vel.x = (int) ((long) vel.x * -bounceFriction / GCInteger.RATIO);
                                vel.y = (int) ((long) vel.y * otherAxisFriction / GCInteger.RATIO);
                        }
                        
                }
                else if (tileSide == TILE_SIDE.TOP || tileSide == TILE_SIDE.BOTTOM)
                {
                        int newY;
                        if (rayFrom.y <= y1)
                        {
                                newY = y1 - radius - 1;
                        }
                        else
                        {
                                newY = y1 + radius + 1;
                        }
                        
                        intersection.set = true;
                        intersection.x = findXOnLine(rayFrom, rayTo, newY);
                        intersection.y = newY;
                        
                        if (hasBouncesLeft())
                        {
                                // this is not the result of the last bounce
                                vel.x = (int) ((long) vel.x * otherAxisFriction / GCInteger.RATIO);
                                vel.y = (int) ((long) vel.y * -bounceFriction / GCInteger.RATIO);
                        }

                }
                else
                {
                        assert false;
                }
                
                assert intersection.set;
                
                return true;
        }
        
        private static class TileSideDist implements Comparable<TileSideDist>
        {
                final TILE_SIDE side;
                long distance;

                TileSideDist(TILE_SIDE side)
                {
                        this.side = side;
                }

                @Override
                public int compareTo(TileSideDist o)
                {
                        return Long.compare(distance, o.distance);
                }
        }
        
        private static void sortTileSidesByDist(
                TileSideDist[] ret,
                PhysicsPoint tilePixels, PhysicsPoint point, 
                boolean matchTopSide, boolean matchRightSide, boolean matchBottomSide, boolean matchLeftSide,
                TileSideDist top, TileSideDist right, TileSideDist bottom, TileSideDist left
                )
        {
                assert ret.length == 4;
                int i = 0;
                final PhysicsPoint sideCenter = new PhysicsPoint();
                
                if (matchTopSide)
                {
                        sideCenter.set(tilePixels);
                        sideCenter.addX(TILE_PIXELS / 2);
                        ret[i++] = top;
                        top.distance = sideCenter.distanceSquared(point);
                        assert top.side == TILE_SIDE.TOP;
                }
                
                if (matchRightSide)
                {
                        sideCenter.set(tilePixels);
                        sideCenter.addX(TILE_PIXELS / 2);
                        sideCenter.addX(TILE_PIXELS / 2);
                        sideCenter.addY(TILE_PIXELS / 2);
                        ret[i++] = right;
                        right.distance = sideCenter.distanceSquared(point);
                        assert right.side == TILE_SIDE.RIGHT;
                }
                
                if (matchBottomSide)
                {
                        sideCenter.set(tilePixels);
                        sideCenter.addX(TILE_PIXELS / 2);
                        sideCenter.addY(TILE_PIXELS / 2);
                        sideCenter.addY(TILE_PIXELS / 2);
                        ret[i++] = bottom;
                        bottom.distance = sideCenter.distanceSquared(point);
                        assert bottom.side == TILE_SIDE.BOTTOM;
                }
                
                if (matchLeftSide)
                {
                        sideCenter.set(tilePixels);
                        sideCenter.addY(TILE_PIXELS / 2);
                        ret[i++] = left; 
                        left.distance = sideCenter.distanceSquared(point);
                        assert left.side == TILE_SIDE.LEFT;
                }
                
                Arrays.sort(ret, 0, i);
                
                while (i < ret.length)
                {
                        ret[i++] = null;
                }
        }
        
        public static boolean lineSegmentsIntersect(
                PhysicsPoint start1, PhysicsPoint end1, 
                PhysicsPoint start2, PhysicsPoint end2, 
                boolean checkCollinear)
        {
                return lineSegmentsIntersect(
                        start1.x, start1.y, end1.x, end1.y,
                        start2.x, start2.y, end2.x, end2.y,
                        checkCollinear
                );
        }
        
        public static boolean lineSegmentsIntersect(
                int x1, int y1, int x2, int y2, 
                int x3, int y3, int x4, int y4,
                boolean checkCollinear)
        {
                // http://www.java-gaming.org/index.php?topic=22590.0
                
                
                // no overflows aslong as the values are under 2^30
                
                // Return false if either of the lines have zero length
                if (x1 == x2 && y1 == y2
                        || x3 == x4 && y3 == y4)
                {
                        return false;
                }
                
                // Fastest method, based on Franklin Antonio's "Faster Line Segment Intersection" 
                // topic "in Graphics Gems III" book (http://www.graphicsgems.org/)
                long ax = x2 - (long) x1;
                long ay = y2 - (long) y1;
                long bx = x3 - (long) x4;
                long by = y3 - (long) y4;
                long cx = x1 - (long) x3;
                long cy = y1 - (long) y3;

                long alphaNumerator = by * cx - bx * cy;
                long commonDenominator = ay * bx - ax * by;
                
                if (commonDenominator > 0)
                {
                        if (alphaNumerator < 0 || alphaNumerator > commonDenominator)
                        {
                                return false;
                        }
                }
                else if (commonDenominator < 0)
                {
                        if (alphaNumerator > 0 || alphaNumerator < commonDenominator)
                        {
                                return false;
                        }
                }
                
                long betaNumerator = ax * cy - ay * cx;
                if (commonDenominator > 0)
                {
                        if (betaNumerator < 0 || betaNumerator > commonDenominator)
                        {
                                return false;
                        }
                }
                else if (commonDenominator < 0)
                {
                        if (betaNumerator > 0 || betaNumerator < commonDenominator)
                        {
                                return false;
                        }
                }
                
                if (commonDenominator == 0)
                {
                        if (!checkCollinear)
                        {
                                return false;
                        }
                        
                        // This code wasn't in Franklin Antonio's method. It was added by Keith Woodward.
                        // The lines are parallel.
                        // Check if they're collinear.
                        
                        // see http://mathworld.wolfram.com/Collinear.html
                        
                        // If p3 is collinear with p1 and p2 then p4 will also be collinear, since p1-p2 is parallel with p3-p4
                        if (x1 * (y2 - y3) == 0 && 
                            x2 * (y3 - y1) == 0 && 
                            x3 * (y1 - y2) == 0)
                        {
                                // The lines are collinear. Now check if they overlap.
                                if (x1 >= x3 && x1 <= x4 || x1 <= x3 && x1 >= x4
                                        || x2 >= x3 && x2 <= x4 || x2 <= x3 && x2 >= x4
                                        || x3 >= x1 && x3 <= x2 || x3 <= x1 && x3 >= x2)
                                {
                                        if (y1 >= y3 && y1 <= y4 || y1 <= y3 && y1 >= y4
                                                || y2 >= y3 && y2 <= y4 || y2 <= y3 && y2 >= y4
                                                || y3 >= y1 && y3 <= y2 || y3 <= y1 && y3 >= y2)
                                        {
                                                return true;
                                        }
                                }
                        }
                        return false;
                }
                return true;
        }
        
        /** Determine if a line segment intersects a convex polygon.
         * if startX/Y is equal to endX/Y this method will match a point inside a polygon 
         * (although a more efficient method exists for this case).
         * 
         * @param startX The start of the line segment
         * @param startY The start of the line segment
         * @param endX The end of the line segment
         * @param endY The end of the line segment
         * @param vertices The coordinates of the all the points on the convex vector in clock wise order.
         * @param nearIntersection If not null, the intersection point nearest to the start of the line is set here.
         *        if the intersection is outside the gameplay area, the point will be unset.
         * @param farIntersection If not null, the intersection point furthest from the start of the line is set here.
         * @return true if the line intersects
         */
        public static boolean lineSegmentIntersectsConvexPolygon(
                int startX, int startY,
                int endX, int endY,
                List<PhysicsPoint> vertices,
                PhysicsPoint nearIntersection, PhysicsPoint farIntersection)
        {
                // http://elancev.name/oliver/segment%20-%20polygon%20intersection.htm
                // the vertices should be listed in clockwise order!
                assert vertices.size() >= 3;
                
                final PhysicsPoint lineStart = new PhysicsPoint();
                final PhysicsPoint lineEnd = new PhysicsPoint();
                
                final ComparableIntegerDivision tnear = new ComparableIntegerDivision();
                final ComparableIntegerDivision tfar = new ComparableIntegerDivision();
                final ComparableIntegerDivision tclip = new ComparableIntegerDivision();
                final PhysicsPoint xDir = new PhysicsPoint();
                final PhysicsPoint e = new PhysicsPoint();
                final PhysicsPoint en = new PhysicsPoint();
                final PhysicsPoint d = new PhysicsPoint();
                
                lineStart.set(startX, startY);
                lineEnd.set(endX, endY);
                
                int near_index = -1;
                int far_index = -1;
                
                // near intersection point is closest to the origin of the linesegment
                tnear.div = 0;
                tnear.mod = 0;
                
                tfar.div = 1;
                tfar.mod = 0;
                
                xDir.set(lineEnd);
                xDir.sub(lineStart);
                
                if (nearIntersection != null)
                {
                        nearIntersection.unset();
                }
                
                if (farIntersection != null)
                {
                        farIntersection.unset();
                }
                
		for (int j = vertices.size() - 1, i = 0;
                     i < vertices.size();
                     j = i, i++
                        )
                {
			PhysicsPoint e0 = vertices.get(j);
			PhysicsPoint e1 = vertices.get(i);
                        e.set(e1);
                        e.sub(e0);
                        en.set(e.y, -e.x);
                        d.set(e0);
                        d.sub(lineStart);
                        
                        long denom = d.dotProduct(en);
                        long numer = xDir.dotProduct(en);
                        
                        if (numer == 0) // ray parallel to plane
                        {
                                if (denom < 0) 
                                {
                                        return false;
                                }
                        }
                        else
                        {
                                tclip.calculate(denom, numer);
                                
                                if (numer < 0) // near intersection
                                {
                                        if (tclip.compareTo(tfar) > 0)
                                        {
                                                return false;
                                        }
                                        
                                        if (tclip.compareTo(tnear) > 0)
                                        {
                                                tnear.set(tclip);
                                                near_index = i;
                                        }
                                }
                                else // far intersection
                                {
                                        if (tclip.compareTo(tnear) < 0)
                                        {
                                                return false;
                                        }
                                        
                                        if (tclip.compareTo(tfar) < 0)
                                        {
                                                tfar.set(tclip);
                                                far_index = i;
                                        }
                                }
                        }
		}
                
                if (nearIntersection != null)
                {
                        if (near_index >= 0)
                        {
                                int j = near_index - 1;
                                if (j < 0)
                                {
                                        assert j == -1;
                                        j = vertices.size() - 1;
                                }

                                PhysicsPoint e0 = vertices.get(j);
                                PhysicsPoint e1 = vertices.get(near_index);
                                
                                findLineLineIntersection(nearIntersection, 
                                        lineStart, lineEnd,
                                        e0, e1
                                        );
                        }
                        else
                        {
                                // point is inside
                                nearIntersection.set(lineStart);
                        }
                }
                
                if (farIntersection != null)
                {
                        if (far_index >= 0)
                        {
                                int j = far_index - 1;
                                if (j < 0)
                                {
                                        assert j == -1;
                                        j = vertices.size() - 1;
                                }

                                PhysicsPoint e0 = vertices.get(j);
                                PhysicsPoint e1 = vertices.get(far_index);

                                findLineLineIntersection(farIntersection, 
                                        lineStart, lineEnd,
                                        e0, e1
                                        );
                        }
                        else
                        {
                                farIntersection.set(lineEnd);
                        }
                }
		
		return true;
        }
     
        public static int findXOnLine(PhysicsPoint a, PhysicsPoint b, int y)
        {
                //y = y1 + (y2 - y1) / (x2 - x1) * (x - x1), 
                long d = ((long) b.y - a.y);
                if (d == 0)
                {
                        return a.x;
                }
                return (int) (a.x + ((long) b.x - a.x) * ((long) y - a.y) / d);
        }
        
        public static int findYOnLine(PhysicsPoint a, PhysicsPoint b, int x)
        {
                //y = y1 + (y2 - y1) / (x2 - x1) * (x - x1), 
                long d = ((long) b.x - a.x);
                if (d == 0)
                {
                        return a.y;
                }
                return (int) (a.y + ((long) b.y - a.y) * ((long) x - a.x) / d);
        }
        
        /** Find the intersection point between 2 infinite lines.
         * @param result
         * @param line1Start
         * @param line1End
         * @param line2Start
         * @param line2End
         * @return true if an intersection is found; false if the lines 
         *         are parallel or if the intersection point is outside
         *         the gameplay area.
         * 
         */
        public static boolean findLineLineIntersection(
                PhysicsPoint result, 
                PhysicsPoint line1Start, PhysicsPoint line1End,
                PhysicsPoint line2Start, PhysicsPoint line2End
                )
        {
                // http://www.java-gaming.org/index.php?topic=22590.0
                
                result.unset();
                
                long line1Product = line1Start.crossProduct(line1End);
                long line2Product = line2Start.crossProduct(line2End);
                
                final PhysicsPoint line1Sub = new PhysicsPoint();
                final PhysicsPoint line2Sub = new PhysicsPoint();
                
                line1Sub.set(line1Start);
                line1Sub.sub(line1End);
                
                line2Sub.set(line2Start);
                line2Sub.sub(line2End);
                
                long linesProduct = line1Sub.crossProduct(line2Sub);

                if (linesProduct == 0)
                {
                        // The lines are parallel and there's either no solution or 
                        // multiple solutions if the lines overlap
                        return false;
                }
                
                // a * b / c == a / c * b == b / c * a
                // (a * b - c * d) / e == (a * b / e) - (c * d / e) == (a / e * b) - (d / e * c)

                long divmulA = divmul(line1Product, linesProduct, line2Sub.x);
                long divmulB = divmul(line2Product, linesProduct, line1Sub.x);
                
                if (divmulA == Long.MIN_VALUE || divmulB == Long.MIN_VALUE)
                {
                        return false;
                }
                
                long x = SwissArmyKnife.safeSubClipped(divmulA, divmulB);
                
                
                divmulA = divmul(line1Product, linesProduct, line2Sub.y);
                divmulB = divmul(line2Product, linesProduct, line1Sub.y);
                
                if (divmulA == Long.MIN_VALUE || divmulB == Long.MIN_VALUE)
                {
                        return false;
                }
                
                
                long y = SwissArmyKnife.safeSubClipped(divmulA, divmulB);
                
                // is the intersection outside of our possible range of gameplay?
                if (x < -PhysicsEnvironment.MAX_POSITION || x > PhysicsEnvironment.MAX_POSITION)
                {
                        return false;
                }
                
                if (y < -PhysicsEnvironment.MAX_POSITION || y > PhysicsEnvironment.MAX_POSITION)
                {
                        return false;
                }
                
                result.x = (int) x;
                result.y = (int) y;
                result.set = true;
                return true;
        }
        
        // calculate a * b / c
        // returns Long.MIN_VALUE when an overflow would have occurred.
        // if this occurs the solution is outside the range of integers, 
        // which is never a solution we would want
        private static long divmul(long a, long b, int c)
        {
                // a / b * c == int(a / b) * c   +   (a % b) * c / b
                // large / large * small
                
                if ( (b == 0) || ( (a == Long.MIN_VALUE || c == Long.MIN_VALUE) && (b == -1) ) )
                {
                        return Long.MIN_VALUE; // error
                }
                
                long aDivB = a / b;
                long aRemB = a - (b * aDivB);
                
                if (!SwissArmyKnife.isMultiplySafe(aDivB, c) || !SwissArmyKnife.isMultiplySafe(aRemB, c))
                {
                        return Long.MIN_VALUE; // error
                }
                
                return aDivB * c   +   aRemB * c / b;
        }
        
        /** Is the specified point inside the given convex polygon?
         * @param px
         * @param py
         * @param vertices Vertices sorted clockwise or counter clockwise; at least 3
         * @deprecated currently not in use, convert to integer before use
         */
        private static strictfp boolean pointInsideConvexPolygon(int px, int py, PhysicsPoint ... vertices)
        {
                assert vertices.length >= 3;
                
                double prevDot = 0;
		for (int i = 0; i < vertices.length; i++)
                {
			PhysicsPoint x = vertices[i];
			PhysicsPoint y = vertices[(i + 1) % vertices.length];
                        
                        double dot = ((double)px - x.x)*((double)y.y - x.y) - ((double)y.x - x.x) * ((double)py - x.y);
                        
                        if (dot >= 0 && prevDot < 0 || dot <= 0 && prevDot > 0)
                        {
                                return false;
                        }
                        
                        prevDot = dot;
		}
		
		return true;
        }
        
        /** @deprecated currently not in use, convert to integer before use */
        private static boolean pointInsideConvexPolygon(PhysicsPoint point, PhysicsPoint ... vertices)
        {
                return pointInsideConvexPolygon(point.x, point.y, vertices);
        }
}
