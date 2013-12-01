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

import java.util.Arrays;

/**
 *
 * @author Joris
 */
public class PhysicsPointHistorySmooth 
{
        private final PhysicsPointHistory positionHist;
        private final PhysicsPointHistory velocityHist;
        private final int HISTORY_LENGTH;
        
        // The "baseline" is used so that multiple changes in the position do not cause multiple 
        // steps in the convergence
        private final int[] baseline_x;
        private final int[] baseline_y;
        private final int[] smooth_x;
        private final int[] smooth_y;
        private final boolean[] dirty;
        private SMOOTHING_ALGORITHM algorithm = SMOOTHING_ALGORITHM.NONE;
        private int lookAheadTicks = 20;
        private long smoothLimitDistanceSq = 10_000 * 10_000;
        
        public PhysicsPointHistorySmooth(PhysicsPointHistory positionHist, PhysicsPointHistory velocityHist)
        {
                HISTORY_LENGTH = positionHist.HISTORY_LENGTH;
                assert HISTORY_LENGTH == velocityHist.HISTORY_LENGTH;
                
                // use the same index for ticks as positionHist for easy implementation
                
                this.positionHist = positionHist;
                this.velocityHist = velocityHist;
                positionHist.setListener(posListener);
                velocityHist.setListener(velListener);
                
                
                baseline_x = new int[positionHist.HISTORY_LENGTH];
                baseline_y = new int[positionHist.HISTORY_LENGTH];
                smooth_x = new int[positionHist.HISTORY_LENGTH];
                smooth_y = new int[positionHist.HISTORY_LENGTH];
                dirty = new boolean[positionHist.HISTORY_LENGTH];
        }

        public void setAlgorithm(SMOOTHING_ALGORITHM algorithm)
        {
                this.algorithm = algorithm;
        }

        public void setLookAheadTicks(int lookAheadTicks)
        {
                this.lookAheadTicks = lookAheadTicks;
        }

        public void setSmoothLimitDistance(int smoothLimitDistance)
        {
                this.smoothLimitDistanceSq = (long) smoothLimitDistance * (long) smoothLimitDistance;
        }
        
        /** This method should be called before each iteration of the state.
         * It ensures that setting the position multiple times within the same state iteration,
         * has the exact same result as setting it only once.
         */
        public void setBaseLine()
        {
                for (int i = 0; i < HISTORY_LENGTH; ++i)
                {
                        baseline_x[i] = smooth_x[i];
                        baseline_y[i] = smooth_y[i];
                }
        }
        
        public void getSmooth(PhysicsPoint ret, long tick)
        {
                int index = positionHist.getIndex(tick);
                if (index < 0)
                {
                        ret.unset();
                        return;
                }
                
                if (dirty[index])
                {
                        calculate(tick, index);
                }
                
                ret.set = true;
                ret.x = smooth_x[index];
                ret.y = smooth_y[index];
        }
        
        public int getX(long tick)
        {
                int index = positionHist.getIndex(tick);
                if (index < 0)
                {
                        return 0;
                }
                
                if (dirty[index])
                {
                        calculate(tick, index);
                }
                
                return smooth_x[index];
        }

        public int getY(long tick)
        {
                int index = positionHist.getIndex(tick);
                if (index < 0)
                {
                        return 0;
                }
                
                if (dirty[index])
                {
                        calculate(tick, index);
                }
                
                return smooth_y[index];
        }
        
        private void calculate(long tick, int index)
        {
                PhysicsPoint base = new PhysicsPoint();
                PhysicsPoint desired = new PhysicsPoint();
                PhysicsPoint velocity = new PhysicsPoint();
                
                base.set(baseline_x[index], baseline_y[index]);
                positionHist.getByIndex(desired, index);
                velocityHist.get(velocity, tick);
                
                assert base.set;
                assert desired.set;
                
                
                smooth_x[index] = desired.x;
                smooth_y[index] = desired.y;
                
                if (base.distanceSquared(desired) <= smoothLimitDistanceSq)
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
                                smoothed.divide(lookAheadTicks);
                                smoothed.add(base);
                                
                                smooth_x[index] = smoothed.x;
                                smooth_y[index] = smoothed.y;
                        }
                }
                
                dirty[index] = false;
        }

        private final PhysicsPointHistory.UpdateListener posListener = new PhysicsPointHistory.UpdateListener()
        {
                @Override
                public void updated(long tick, int index)
                {
                        dirty[index] = true;
                }

                @Override
                public void updatedAll()
                {
                        Arrays.fill(dirty, true);
                }
        };
        
        private final PhysicsPointHistory.UpdateListener velListener = new PhysicsPointHistory.UpdateListener()
        {
                @Override
                public void updated(long tick, int index)
                {
                        index = positionHist.getIndex(tick);
                        if (index >= 0)
                        {
                                dirty[index] = true;
                        }
                }

                @Override
                public void updatedAll()
                {
                        Arrays.fill(dirty, true);
                }
        };

        
        
        
        public static enum SMOOTHING_ALGORITHM
        {
                NONE,
                LINEAR;
        }
}
