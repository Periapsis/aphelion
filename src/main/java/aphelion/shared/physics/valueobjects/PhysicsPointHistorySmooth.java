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

package aphelion.shared.physics.valueobjects;

import aphelion.shared.gameconfig.GCInteger;

/**
 * Used to provide dead reckon convergence / smoothing.
 * Note that the results of this object might not be deterministic!
 * Also, the output depends on the order that the input is given (this is how dead 
 * reckon convergence works).
 * It should not used be used for things that need strong consistency across clients,
 * or the results should be checked by later states for consistency, (eventual 
 * consistency).
 * 
 * @author Joris
 */
public class PhysicsPointHistorySmooth 
{
        private final PhysicsPointHistory positionHist;
        private final PhysicsPointHistory velocityHist;
        private final int HISTORY_LENGTH;
        
        /** The "baseline" is used so that multiple changes in the position do not cause multiple 
         * steps in the convergence
         */
        private final PhysicsPointHistory baseline;
        private final PhysicsPointHistory smooth;
        private SMOOTHING_ALGORITHM algorithm = SMOOTHING_ALGORITHM.NONE;
        private int lookAheadTicks = 20;
        private int stepRatio = 5000;
        private long smoothLimitDistanceSq = 10_000 * 10_000;
        
        public PhysicsPointHistorySmooth(long initial_tick, PhysicsPointHistory positionHist, PhysicsPointHistory velocityHist)
        {
                HISTORY_LENGTH = positionHist.HISTORY_LENGTH;
                assert HISTORY_LENGTH == velocityHist.HISTORY_LENGTH;
                
                // use the same index for ticks as positionHist for easy implementation
                
                this.positionHist = positionHist;
                this.velocityHist = velocityHist;
                positionHist.setListener(posListener);
                velocityHist.setListener(posListener);
                
                baseline = new PhysicsPointHistory(initial_tick, HISTORY_LENGTH);
                smooth = new PhysicsPointHistory(initial_tick, HISTORY_LENGTH);
        }

        public void setAlgorithm(SMOOTHING_ALGORITHM algorithm)
        {
                this.algorithm = algorithm;
        }

        public void setLookAheadTicks(int lookAheadTicks)
        {
                this.lookAheadTicks = lookAheadTicks;
        }
        
        /**
         * @param permille A ratio between 0 and 1048576
         */
        public void setStepRatio(int permille)
        {
                this.stepRatio = permille;
        }

        public void setSmoothLimitDistance(int smoothLimitDistance)
        {
                this.smoothLimitDistanceSq = (long) smoothLimitDistance * (long) smoothLimitDistance;
        }
        
        /** This method should be called before each iteration of the state.
         * It ensures that setting the actor position multiple times within the same state iteration,
         * has the exact same result as setting it only once.
         */
        public void updateBaseLine()
        {
                baseline.set(smooth);
        }
        
        public void getSmooth(PhysicsPoint ret, long tick)
        {
                smooth.get(ret, tick);
        }
        
        public int getX(long tick)
        {
                return smooth.getX(tick);
        }

        public int getY(long tick)
        {
                return smooth.getY(tick);
        }
        
        private void calculate(long tick)
        {
                PhysicsPoint base = new PhysicsPoint();
                PhysicsPoint desired = new PhysicsPoint();
                PhysicsPoint velocity = new PhysicsPoint();
                
                baseline.get(base, tick - 1);
                positionHist.get(desired, tick);
                velocityHist.get(velocity, tick);
                
                smooth.setHistory(tick, desired);
                
                if (base.set && base.distanceSquared(desired) <= smoothLimitDistanceSq)
                {
                        if (algorithm == SMOOTHING_ALGORITHM.NONE)
                        {
                        }
                        else if (algorithm == SMOOTHING_ALGORITHM.LINEAR && lookAheadTicks > 0)
                        {
                                PhysicsPoint smoothed = new PhysicsPoint();
                                smoothed.set(velocity);
                                smoothed.multiply(lookAheadTicks);
                                smoothed.add(desired);

                                smoothed.sub(base);
                                smoothed.applyRatio(stepRatio, GCInteger.RATIO_PRECISE);
                                smoothed.add(base);
                                
                                if (base.distanceSquared(desired) <= base.distanceSquared(smoothed) )
                                {
                                        // do not bother with smoothed if the real position is closer!
                                        smoothed.set(desired);
                                }
                                
                                smooth.setHistory(tick, smoothed);
                        }
                }
        }

        private final PhysicsPointHistory.UpdateListener posListener = new PhysicsPointHistory.UpdateListener()
        {
                @Override
                public void updated(long tick, int index)
                {
                        calculate(tick);
                }

                @Override
                public void updatedAll()
                {
                        for (long t = positionHist.getLowestTick(); t <= positionHist.getHighestTick(); ++t)
                        {
                                calculate(t);
                        }
                }
        };
        
        
        public static enum SMOOTHING_ALGORITHM
        {
                NONE,
                LINEAR;
                // quadratic, cubic spline, etc
        }
}
