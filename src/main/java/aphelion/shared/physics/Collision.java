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

package aphelion.shared.physics;


import aphelion.shared.gameconfig.GCInteger;
import aphelion.shared.physics.entities.MapEntity;
import static aphelion.shared.swissarmyknife.SwissArmyKnife.abs;
import static aphelion.shared.swissarmyknife.SwissArmyKnife.clip;
import static aphelion.shared.physics.PhysicsMap.TILE_PIXELS;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsPointHistoryDetailed;
import aphelion.shared.swissarmyknife.ComparableIntegerDivision;
import aphelion.shared.swissarmyknife.LoopFilter;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/** A helper class to perform collisions with the use of floating points.
 * @author Joris
 */
public class Collision
{
        private static final Logger log = Logger.getLogger("aphelion.shared.physics");

        public Collision()
        {
        }
        
        public static enum TILE_SIDE
        {
                NONE,
                TOP,
                RIGHT,
                BOTTOM,
                LEFT;
        }
        
        // arguments
        private long tick;
        private PhysicsPoint oldPos;
        private PhysicsPoint vel;
        private PhysicsMap map;
        private final PhysicsPoint newPos = new PhysicsPoint();
        private int radius;
        private int bounceFriction;
        private int otherAxisFriction;
        private int bounces_left;
        private int activate_bounces_left;
        
        // return values
        public MapEntity hitEntity;
        public final PhysicsPoint hitTile = new PhysicsPoint();
        public int bounces; // how many times did the object bounce of a tile?

        
        /**
         * @param tick 
         * @param posHistoryDetails If not null, every bounce or any other change in direction is added here as a history detail.
         * @param oldPos The position to update
         * @param vel The velocity to update
         * @param map The map to use for collision
         * @param tileCollide 
         * @param collidesWith All the entities that this entity may collide with
         * @param collidesWithFilter A filter to apply on collidesWith. ARG is the current tick
         * @param radius The radius in pixels * 1024 (not diameter) of the object. The radius may be 0 if it has no
         * diameter, like a bullet.
         * @param bounceFriction The velocity should decrease by this factor when bouncing (1024 = no slow down, 0 = stop)
         * @param otherAxisFriction The velocity of the axis that did not bounce should decrease by this factor when 
         *                     bouncing (1024 = no slow down, 0 = stop)
         * @param bounces_left Number of bounces on a tile the simulates object has left. Use -1 for infinite.
         *        if this value is 0 and hits a tile, the object has collided
         * @param activate_bounces_left Number of bounces on a tile this object has left before it may collide with 
         *        "collidesWith"
         * @return True if a final collision for this object was made (projectile hitting an actor, hitting a tile).
         * use collision.hitEntity and hitTile to find out what was hit.
         * the argument oldPos will be set at the point of collision
         */
        public boolean deadReckonTick(
                long tick,
                PhysicsPointHistoryDetailed posHistoryDetails,
                PhysicsPoint oldPos, PhysicsPoint vel,
                PhysicsMap map, boolean tileCollide,
                List<MapEntity> collidesWith, LoopFilter<MapEntity, Long> collidesWithFilter,
                int radius, int bounceFriction, int otherAxisFriction, 
                int bounces_left, int activate_bounces_left)
        {
                this.tick = tick;
                this.oldPos = oldPos;
                this.vel = vel;
                this.map = map;
                this.radius = radius;
                this.bounceFriction = bounceFriction;
                this.otherAxisFriction = otherAxisFriction;
                this.newPos.unset();
                this.bounces_left = bounces_left;
                this.activate_bounces_left = activate_bounces_left;
                
                if (!oldPos.set)
                {
                        throw new IllegalArgumentException();
                }
                
                if (!vel.set)
                {
                        throw new IllegalArgumentException();
                }
                
                hitEntity = null;
                hitTile.unset();
                bounces = 0;
                
                final PhysicsPoint previousPosHistoryDetail = new PhysicsPoint();
                final PhysicsPoint remaining = new PhysicsPoint();
                final PhysicsPoint prevRemaining = new PhysicsPoint();
                
                if (posHistoryDetails != null)
                {
                        posHistoryDetails.clearDetails(tick);
                }
                
                remaining.set(vel);
                remaining.abs();
                assert radius >= 0;
                
                previousPosHistoryDetail.set(oldPos);
                
                while(true)
                {
                        newPos.set(oldPos);
                        newPos.set = true;
                        
                        newPos.x += vel.x >= 0 ? remaining.x : -remaining.x ;
                        newPos.y += vel.y >= 0 ? remaining.y : -remaining.y ;
                        
                        boolean tileCollided = false;
                        if (tileCollide && !oldPos.equals(newPos))  // no need to match tiles if we are stationairy
                        {
                                tileCollided = tileCollisionRay(oldPos, newPos, radius);
                                // todo stuff with radius
                        }
                        // at this point tile collision has set a new value for newPos if there was a hit
                        // in a straight line. 
                        // If not, the newPos is not modified.
                        // Use this line to try actor collision
                        
                        
                        // projectile -> actor collision
                        
                        // NOTE: This algorithm assumes time between ticks does not exist.
                        // Aka there is no tick 8.2, only tick 8 and 9. The whole movevement of the 
                        // colliding entities within a single tick is considered for collision.
                        // This may cause a ship to hit a projectile even though their paths really 
                        // should not have crossed when they are both have a high velocity.

                        
                        for (int a = 0; 
                                collidesWith != null && hasActivated() && a < collidesWith.size(); 
                                ++a)
                        {
                                MapEntity en = collidesWith.get(a);
                                if (collidesWithFilter.loopFilter(en, tick))
                                {
                                        continue;
                                }
                                
                                if (mayCollide(en) && entityCollision(en))
                                {
                                        oldPos.set(newPos);
                
                                        //enforce map limits to be safe
                                        oldPos.x = clip(oldPos.x, radius, 1024 * TILE_PIXELS - radius);
                                        oldPos.y = clip(oldPos.y, radius, 1024 * TILE_PIXELS - radius);

                                        oldPos.enforceOverflowLimit();
                                        vel.enforceOverflowLimit();
                                        this.hitEntity = en;
                                        return true;
                                }
                        }
                        
                        // did not collide with an actor
                        if (tileCollided && !hasBouncesLeft())
                        {
                                //we can no longer bounce
                                this.hitTile.set(newPos);
                                this.hitTile.divide(TILE_PIXELS);
                                return true;
                        }
                        
                        
                        prevRemaining.set(remaining);
                        remaining.x -= abs(newPos.x - oldPos.x);
                        remaining.y -= abs(newPos.y - oldPos.y);

                        if (remaining.x > prevRemaining.x)
                        {
                                remaining.x = prevRemaining.x;
                        }

                        if (remaining.y > prevRemaining.y)
                        {
                                remaining.y = prevRemaining.y;
                        }

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
                        
                        
                        // At this point there are more steps to make
                        
                        if (posHistoryDetails != null)
                        {
                                // do we need to add a detail? (have we not moved in a straight line during this iteration?)
                                // note tha the very first step and the very last step are not a detail!
                                // the very first step is the end result of the previous tick, 
                                // the very last step is the end result of the current tick.
                                
                                // if previousPosHistoryDetail == oldPos there is no detail to add yet
                                if (!previousPosHistoryDetail.equals(oldPos)) 
                                {
                                        // have we moved in a straight line?
                                        if (findYOnLine(previousPosHistoryDetail, oldPos, newPos.x) != newPos.y)
                                        {
                                                previousPosHistoryDetail.set(oldPos);
                                                posHistoryDetails.appendDetail(tick, oldPos);
                                        }
                                }
                        }
                        
                        
                        oldPos.set(newPos);
                }
                
                oldPos.set(newPos);
                
                //enforce map limits to be safe
                oldPos.x = clip(
                        oldPos.x, 
                        map.physicsGetMapLimitMinimum() * TILE_PIXELS + radius, 
                        map.physicsGetMapLimitMaximum() * TILE_PIXELS - radius);
                
                oldPos.y = clip(
                        oldPos.y, 
                        map.physicsGetMapLimitMinimum() * TILE_PIXELS + radius, 
                        map.physicsGetMapLimitMaximum() * TILE_PIXELS - radius);
                
                oldPos.enforceOverflowLimit();
                vel.enforceOverflowLimit();
                return false;
        }
        
        private boolean hasBouncesLeft()
        {
                // call me after incrementing bounces
                return this.bounces_left < 0 || bounces <= this.bounces_left;
        }
        
        private boolean hasActivated()
        {
                // call me after incrementing bounces
                return this.activate_bounces_left <= 0 || bounces > this.activate_bounces_left;
        }
        
        /** A quick check to see if an entity may collide with a point. 
         * Returning true does not mean it will collide (often it does not)
         */
        private boolean mayCollide(MapEntity en)
        {
                final PhysicsPoint prev = new PhysicsPoint();
                final PhysicsPoint next = new PhysicsPoint();
                
                PhysicsPointHistoryDetailed posHistory;
                
                if (en.useSmoothForCollision(tick))
                {
                        if (!en.getHistoricSmoothPosition(prev, tick - 1, false))
                        {
                                return false;
                        }
                        
                        posHistory = null;
                }
                else
                {
                        if (!en.getHistoricPosition(prev, tick - 1, false))
                        {
                                return false;
                        }
                        
                        posHistory = en.getAndSeekHistoryDetail(tick, true);
                }

                int posDiffX = Math.abs(newPos.x - oldPos.x);
                int posDiffY = Math.abs(newPos.y - oldPos.y);
                
                int enRadius = en.radius.get();
                
                while (true)
                {
                        boolean lastIteration = false;
                        if (posHistory != null && posHistory.hasNextDetail())
                        {
                                posHistory.nextDetail(next);
                        }
                        else
                        {
                                // run out of details
                                // the final line to match is the last detail + 
                                // the final position for this tick.
                                if (en.useSmoothForCollision(tick))
                                {
                                        if (!en.getHistoricSmoothPosition(next, tick, false))
                                        {
                                                break;
                                        }
                                }
                                else
                                {
                                        if (!en.getHistoricPosition(next, tick, false))
                                        {
                                                break;
                                        }
                                }
                                
                                lastIteration = true;
                        }
                        
                        int diffX = Math.abs(next.x - prev.x);
                        int diffY = Math.abs(next.y - prev.y);
                        
                        if ((oldPos.x + this.radius + posDiffX >= prev.x - diffX - enRadius) &&
                            (oldPos.y + this.radius + posDiffY >= prev.y - diffY - enRadius) && 
                            (oldPos.x - this.radius - posDiffX <= prev.x + diffX + enRadius) &&
                            (oldPos.y - this.radius - posDiffY <= prev.y + diffY + enRadius))
                        {
                                return true;
                        }
                        
                        prev.set(next);
                        if (lastIteration)
                        {
                                break;
                        }
                }
                
                return false;
        }
        
        private boolean tileCollisionRay(PhysicsPoint from, PhysicsPoint to, int radius)
        {
                // bresenham line algorithm
                // http://tech-algorithm.com/articles/drawing-line-using-bresenham-algorithm/
                // http://stackoverflow.com/questions/15295195/can-i-easily-skip-pixels-in-bresenhams-line-algorithm
                
                int x = to.x;
                int y = to.y;
                
                // todo test from == to
                
                int w = from.x - x;
                int h = from.y - y;
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
                
                int biggest = Math.abs(w); // biggest
                int smallest = Math.abs(h); // smallest
                
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
                                && tileCollision(tilePos.x, tilePos.y))
                        {
                                return true;
                        }
                        
                        if (radius > 0)
                        {
                                // todo more efficient
                                int radiusTiles = (radius / TILE_PIXELS) + 1;
                                
                                for (int tileX = tilePos.x - radiusTiles; tileX <= tilePos.x + radiusTiles; ++tileX)
                                {
                                        for (int tileY = tilePos.y - radiusTiles; tileY <= tilePos.y + radiusTiles; ++tileY)
                                        {
                                                if (map.physicsIsSolid(tileX, tileY) 
                                                        && tileCollision(tileX, tileY))
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
        
        private boolean tileCollision(int tileX, int tileY)
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
                        tilePixelsTL, oldPos, 
                        matchTopSide, matchRightSide, matchBottomSide, matchLeftSide,
                        top, right, bottom, left);
                
                for (int i = 0; i < 4; ++i)
                {
                        TileSideDist side = sides[i];
                        if (side == null)
                        {
                                break;
                        }
                        
                        if (side.side == TILE_SIDE.LEFT)
                        {
                                if (tileCollisionLineSegment(
                                        tilePixelsTL.x, tilePixelsTL.y, 
                                        tilePixelsTL.x, tilePixelsTL.y + TILE_PIXELS,
                                        TILE_SIDE.LEFT
                                )) return true;
                        }
                        else if (side.side == TILE_SIDE.RIGHT)
                        {
                                if (tileCollisionLineSegment(
                                        tilePixelsTL.x + TILE_PIXELS, tilePixelsTL.y, 
                                        tilePixelsTL.x + TILE_PIXELS, tilePixelsTL.y + TILE_PIXELS,
                                        TILE_SIDE.RIGHT
                                 )) return true;
                        }
                        else if (side.side == TILE_SIDE.TOP)
                        {
                                if (tileCollisionLineSegment(
                                        tilePixelsTL.x, tilePixelsTL.y, tilePixelsTL.x + TILE_PIXELS, tilePixelsTL.y,
                                        TILE_SIDE.TOP
                                )) return true;
                        }
                        else if (side.side == TILE_SIDE.BOTTOM)
                        {
                                if (tileCollisionLineSegment(
                                        tilePixelsTL.x, tilePixelsTL.y + TILE_PIXELS, 
                                        tilePixelsTL.x + TILE_PIXELS, tilePixelsTL.y + TILE_PIXELS,
                                        TILE_SIDE.BOTTOM
                                )) return true;
                        }
                        else
                        {
                                assert false;
                        }
                }
                
                return false;
        }
        
        private boolean entityCollision(MapEntity en)
        {
                // note: "en" may be stationairy
                if (this.radius != 0)
                {
                        throw new IllegalArgumentException("Collision between two objects, both with radius is not implemented yet.");
                }
                
                final PhysicsPoint prev = new PhysicsPoint();
                final PhysicsPoint next = new PhysicsPoint();
                final PhysicsPoint nearIntersection = new PhysicsPoint();
                
                prev.unset();
                next.unset();
                
                PhysicsPointHistoryDetailed posHistory;
                
                if (en.useSmoothForCollision(tick))
                {
                        if (!en.getHistoricSmoothPosition(prev, tick - 1, false))
                        {
                                return false;
                        }
                        
                        // Smooth position does not have details
                        posHistory = null;
                }
                else
                {
                        if (!en.getHistoricPosition(prev, tick - 1, false))
                        {
                                return false;
                        }
                        
                        posHistory = en.getAndSeekHistoryDetail(tick, false);
                }
                
                while (true)
                {
                        boolean lastIteration = false;
                        if (posHistory != null && posHistory.hasNextDetail())
                        {
                                posHistory.nextDetail(next);
                        }
                        else
                        {
                                // run out of details
                                // the final line to match is the last detail + 
                                // the final position for this tick.
                                if (en.useSmoothForCollision(tick))
                                {
                                        if (!en.getHistoricSmoothPosition(next, tick, false))
                                        {
                                                break;
                                        }
                                }
                                else
                                {
                                        if (!en.getHistoricPosition(next, tick, false))
                                        {
                                                break;
                                        }
                                }

                                lastIteration = true;
                        }
                        
                        if (lineSegmentIntersectsConvexPolygon(
                                oldPos.x, oldPos.y, newPos.x, newPos.y, // the line
                                buildPositionDeltaPolygon(prev, next, en.radius.get()),
                                nearIntersection, null))
                        {
                                if (nearIntersection.set)
                                {
                                        newPos.set(nearIntersection);
                                }
                                else
                                {
                                        newPos.set(oldPos);
                                        newPos.set = true;
                                }
                                
                                return true;
                        }
                        

                        prev.set(next);

                        if (lastIteration)
                        {
                                break;
                        }
                }
                
                return false;
        }
        
        private List<PhysicsPoint> buildPositionDeltaPolygon(PhysicsPoint from, PhysicsPoint to, int radius)
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
        
        
       
        private boolean tileCollisionLineSegment(int x1, int y1, int x2, int y2, TILE_SIDE tileSide)
        {
                if (this.radius == 0)
                {
                        // fast case for 0 radius
                        
                        if (!lineSegmentsIntersect(
                                oldPos.x, oldPos.y, newPos.x, newPos.y, 
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
                                buildPositionDeltaPolygon(oldPos, newPos, radius),
                                null, null))
                        {
                                return false;
                        }
                        
                }
                
                ++bounces;
                
                // yay we hit something!
                if (tileSide == TILE_SIDE.LEFT || tileSide == TILE_SIDE.RIGHT)
                {
                        int newX;
                        if (oldPos.x <= x1)
                        {
                                newX = x1 - radius - 1;
                        }
                        else
                        {
                                newX = x1 + radius + 1;
                        }
                        
                        newPos.y = findYOnLine(oldPos, newPos, newX);
                        newPos.x = newX;

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
                        if (oldPos.y <= y1)
                        {
                                newY = y1 - radius - 1;
                        }
                        else
                        {
                                newY = y1 + radius + 1;
                        }
                        
                        newPos.x = findXOnLine(oldPos, newPos, newY);
                        newPos.y = newY;
                        
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
        
        
        
        private void sortTileSidesByDist(
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
        
        private boolean lineSegmentsIntersect(
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
                
                // Fastest method, based on Franklin Antonio's "Faster Line Segment Intersection" topic "in Graphics Gems III" book (http://www.graphicsgems.org/)
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
        public boolean lineSegmentIntersectsConvexPolygon(
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
     
        private int findXOnLine(PhysicsPoint a, PhysicsPoint b, int y)
        {
                //y = y1 + (y2 - y1) / (x2 - x1) * (x - x1), 
                long d = ((long) b.y - a.y);
                if (d == 0)
                {
                        return a.x;
                }
                return (int) (a.x + ((long) b.x - a.x) * ((long) y - a.y) / d);
        }
        
        private int findYOnLine(PhysicsPoint a, PhysicsPoint b, int x)
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
         * @return true if an intersection is found; false if the lines 
         *         are parallel or if the intersection point is outside
         *         the gameplay area.
         * 
         */
        private boolean findLineLineIntersection(
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
        // returns Long.MIN_VALUE when an overflow would have occured.
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
        private strictfp boolean pointInsideConvexPolygon(int px, int py, PhysicsPoint ... vertices)
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
        private boolean pointInsideConvexPolygon(PhysicsPoint point, PhysicsPoint ... vertices)
        {
                return pointInsideConvexPolygon(point.x, point.y, vertices);
        }
        
        
        
        /*private static void bresenham_line(int x, int y, int x2, int y2, final int step)
        {
                // bresenham line algorithm
                // http://tech-algorithm.com/articles/drawing-line-using-bresenham-algorithm/
                // http://stackoverflow.com/questions/15295195/can-i-easily-skip-pixels-in-bresenhams-line-algorithm
                
                int w = x2 - x;
                int h = y2 - y;
                int dx1 = 0, dy1 = 0, dx2 = 0, dy2 = 0;
                
                assert step > 0;
                
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
                
                int biggest = Math.abs(w); // biggest
                int smallest = Math.abs(h); // smallest
                
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
                
                int numerator = biggest >> 1;
                
                for (int i = 0; i <= biggest; i += step)
                {
                        Graph.g.drawLine(x, y, x, y);
                        
                        if (biggest == 0)
                        {
                                break;
                        }
                        
                        int k_old = numerator;
                        
                        numerator = (numerator + smallest * step) % biggest;
                        
                        if (!(numerator < biggest))
                        {
                                numerator -= biggest;
                        }
                        
                        x += ((k_old + smallest * step) / biggest) * dx1 + (step - ((k_old + smallest * step) / biggest)) * dx2;
                        y += ((k_old + smallest * step) / biggest) * dy1 + (step - ((k_old + smallest * step) / biggest)) * dy2;
                }
        }
        
        public static void main(String[] args)  throws Exception
        {
                Display.setTitle("Aphelion");
                Display.setFullscreen(false);
                Display.setVSyncEnabled(false);
                Display.setInitialBackground(0f, 0f, 0f);
                Display.setDisplayMode(new DisplayMode(1024, 768));
                Display.create();
                
                while(true)
                {
                        Display.update();
                        
                        if (Display.isCloseRequested())
                        {
                                break;
                        }
                        GL11.glViewport(0, 0, 1024, 768);

                        GL11.glMatrixMode(GL11.GL_PROJECTION);
                        GL11.glLoadIdentity();
                        GL11.glOrtho(0, 1024, 768, 0, -1, 1);

                        GL11.glMatrixMode(GL11.GL_TEXTURE);
                        GL11.glLoadIdentity();

                        GL11.glMatrixMode(GL11.GL_MODELVIEW);
                        GL11.glLoadIdentity();
                        
                        Graph.g.setColor(Color.red);
                        bresenham_line(100, 100, 400, 130, 1);
                        
                        Graph.g.setColor(Color.green);
                        bresenham_line(100, 100, 400, 130, 4);
                        
                        
                        Graph.g.setColor(Color.red);
                        bresenham_line(100, 90, 100, 90, 1);
                        
                        
                        
                        
                        Display.sync(60);
                }
                
                Display.destroy();
        }*/
}
