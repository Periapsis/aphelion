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

import aphelion.shared.gameconfig.enums.GCFunction2D;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import static aphelion.shared.swissarmyknife.SwissArmyKnife.max;
import static aphelion.shared.swissarmyknife.SwissArmyKnife.abs;
import static aphelion.shared.physics.PhysicsMap.TILE_PIXELS;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public final class PhysicsMath
{
        private PhysicsMath()
        {
        }
        
        /**
         * Converts a rotation point and a length to a point.
         *
         * @param point The x,y point to add the result to
         * @param rot A rotation value between 0 and ROTATION_POINTS
         * @param length The length of the vector
         */
        public static void rotationToPoint(PhysicsPoint point, int rot, int length)
        { 
                point.addX((int) ((long)  length * PhysicsTrig.sin(rot) / PhysicsTrig.MAX_VALUE));
                point.addY((int) ((long) -length * PhysicsTrig.cos(rot) / PhysicsTrig.MAX_VALUE));
        }

        /**
         * Snap the rotation by rounding to the nearest multiple of rotationPoints
         * @param rot Rotation value
         * @param rotationPoints <= 1 disables snapping (as if this function was never called.
         * @return Snapped rotation value
         */
        public static int snapRotation(int rot, int rotationPoints)
        {
                int rem;
                int snap;
                
                if (rotationPoints <= 1)
                {
                        return rot;
                }
                
                snap = EnvironmentConf.ROTATION_POINTS / rotationPoints;
                rem = rot % snap;
                if (rem == 0)
                {
                        return rot;
                }

                if (rem < snap / 2)
                {
                        return rot - rem;
                }

                return (rot + (snap - rem)) % EnvironmentConf.ROTATION_POINTS;
        }

        public static boolean rectanglesCollide(int xA1, int yA1, int xA2, int yA2, int xB1, int yB1, int xB2, int yB2)
        {
                if (xA1 > xB2 || xA2 < xB1)
                {
                        return false;
                }
                if (yA1 > yB2 || yA2 < yB1)
                {
                        return false;
                }
                return true;
        }

        /**
         * Calculate the amount of force applied between an emitter and a receiving point.
         * @param ret Set if the arguments are valid, not set otherwise.
         * @param applyTo
         * @param forcePoint
         * @param range
         * @param strength
         * @return true if "ret" is not 0,0
         */
        public static boolean force(
                PhysicsPoint ret,
                PhysicsPoint applyTo,
                PhysicsPoint forcePoint,
                int range,
                int strength,
                GCFunction2D function)
        {
                return force(ret, applyTo, forcePoint, range, (long) range * (long) range, strength, function);
        }

        /**
         * Calculate the amount of force applied between an emitter and a receiving point.
         * @param ret Set if the arguments are valid, not set otherwise.
         * @param applyTo
         * @param forcePoint
         * @param range
         * @param rangeSq Must be exactly equal to (long) range * range
         * @param strength
         * @return true if "ret" is not 0,0
         */
        public static boolean force(
                PhysicsPoint ret, 
                PhysicsPoint applyTo, 
                PhysicsPoint forcePoint, 
                int range,
                long rangeSq,
                int strength,
                GCFunction2D function)
        {
                ret.unset();
                if (!applyTo.set ||
                    range < 1  ||
                    strength == 0)
                {
                        return false;
                }

                ret.set = true;

                long distSq = forcePoint.distanceSquared(applyTo);
                if (distSq >= rangeSq)
                {
                        // too far away (quick)
                        return false;
                }
                
                long dist = forcePoint.distance(applyTo, distSq);
                if (dist >= range)
                {
                        // too far away
                        return false;
                }
                
                if (dist == 0)
                {
                        // apply south
                        ret.x = 0;
                        ret.y = strength;
                        return true;
                }

                long force;

                if (function == GCFunction2D.LINEAR)
                {
                        force = range - dist; // fits integer
                        force = force * strength / range;
                }
                else // if (function == GCFunction2D.ABSOLUTE)
                {
                        force = strength;
                }
                
                ret.x = (int) ((applyTo.x - forcePoint.x) * force / dist);
                ret.y = (int) ((applyTo.y - forcePoint.y) * force / dist);
                return !ret.isZero();
        }
}
