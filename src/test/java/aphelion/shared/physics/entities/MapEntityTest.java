/*
 * Aphelion
 * Copyright (c) 2015  Joris van der Wel
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

package aphelion.shared.physics.entities;

import aphelion.shared.physics.*;
import aphelion.shared.physics.valueobjects.PhysicsPositionVector;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * @author Joris
 */
public class MapEntityTest
{
        private EnvironmentConf econf;
        private SimpleEnvironment env;
        private State oldState;
        private State state;
        private MapEntity[] crossStateList;
        private MapEntity oldEn;
        private MapEntity en;

        @Before
        public void setUp() throws NoSuchFieldException, IllegalAccessException
        {
                econf = new EnvironmentConf(false, true, true);
                env = new SimpleEnvironment(econf, new MapEmpty(), false);
                Field field = env.getClass().getDeclaredField("trailingStates");
                field.setAccessible(true);
                oldState = ( (State[]) field.get(env) )[2];
                state = ( (State[]) field.get(env) )[1];

                crossStateList = new MapEntity[econf.TRAILING_STATES];

                en = new MapEntity(
                        state,
                        crossStateList,
                        90, // created at tick
                        10 // history length
                ) {
                        @Override
                        public void performDeadReckoning(PhysicsMap map, long tick_now, long reckon_ticks, boolean applyForceEmitters)
                        {
                                // noop
                        }
                };

                en.pos.pos.set(12, 34);
                en.pos.vel.set(5, 6);

                oldEn = new MapEntity(
                        oldState,
                        crossStateList,
                        90, // created at tick
                        10 // history length
                ) {
                        @Override
                        public void performDeadReckoning(PhysicsMap map, long tick_now, long reckon_ticks, boolean applyForceEmitters)
                        {
                                // noop
                        }
                };

                oldEn.pos.set(en.pos);

                tickTo(state, 100);
        }

        private void tickTo(State target, int tick)
        {
                while (target.tick_now < tick)
                {
                        oldEn.updatedPosition(oldState.tick_now);
                        en.updatedPosition(state.tick_now);
                        env.tick();
                }
        }

        @Test
        public void testSoftRemove()
        {
                assert crossStateList[state.id] == en;

                assertFalse(en.isRemovedSet());
                assertFalse(en.isRemoved(1));
                assertFalse(en.isRemoved(100));
                assertFalse(en.isRemoved(1000));

                en.softRemove(105);

                assert crossStateList[state.id] == en;
                assertTrue(en.isRemovedSet());
                assertFalse(en.isRemoved(1));
                assertFalse(en.isRemoved(104));
                assertTrue(en.isRemoved(105));
                assertTrue(en.isRemoved(106));
                assertTrue(en.isRemoved(1000));

                en.softRemove(104);

                assert crossStateList[state.id] == en;
                assertTrue(en.isRemovedSet());
                assertFalse(en.isRemoved(1));
                assertFalse(en.isRemoved(103));
                assertTrue(en.isRemoved(104));
                assertTrue(en.isRemoved(105));
                assertTrue(en.isRemoved(1000));
        }

        @Test
        public void testHardRemove()
        {
                assert crossStateList[state.id] == en;
                en.markDirtyPositionPath(95);
                assert en.dirtyPositionPathLink_state.head != null;

                assertFalse(en.isRemovedSet());
                assertFalse(en.isRemoved(1));
                assertFalse(en.isRemoved(100));
                assertFalse(en.isRemoved(1000));

                en.hardRemove(105);

                assert crossStateList[state.id] == null;
                assert en.dirtyPositionPathLink_state.head == null;
                assertTrue(en.isRemovedSet());
                assertFalse(en.isRemoved(1));
                assertFalse(en.isRemoved(104));
                assertTrue(en.isRemoved(105));
                assertTrue(en.isRemoved(106));
                assertTrue(en.isRemoved(1000));

                en.hardRemove(104);

                assert crossStateList[state.id] == null;
                assert en.dirtyPositionPathLink_state.head == null;
                assertTrue(en.isRemovedSet());
                assertFalse(en.isRemoved(1));
                assertFalse(en.isRemoved(103));
                assertTrue(en.isRemoved(104));
                assertTrue(en.isRemoved(105));
                assertTrue(en.isRemoved(1000));
        }

        @Test
        public void testIsNonExistent()
        {
                assertTrue(en.isNonExistent(1));
                assertTrue(en.isNonExistent(89)); // entity created at tick 90
                assertFalse(en.isNonExistent(100));
                assertFalse(en.isNonExistent(1000));

                en.softRemove(105);

                assertFalse(en.isNonExistent()); // state tick_now (100)
                assertTrue(en.isNonExistent(1));
                assertTrue(en.isNonExistent(89));
                assertFalse(en.isNonExistent(100));
                assertFalse(en.isNonExistent(104));
                assertTrue(en.isNonExistent(105));
                assertTrue(en.isNonExistent(1000));

                en.softRemove(100);
                assertTrue(en.isNonExistent()); // state tick_now (100)
        }

        @Test
        public void testMarkDirtyPositionPath_beforeCreation()
        {
                assert en.dirtyPositionPathLink_state.head == null;

                // entity is created at tick 90
                en.markDirtyPositionPath(85); // before its creation tick
                assertFalse(en.dirtyPositionPathTracker.isDirty(89));
                assertTrue(en.dirtyPositionPathTracker.isDirty(90));
                assertTrue(en.dirtyPositionPathTracker.isDirty(1000));

                assert en.dirtyPositionPathLink_state.head == state.dirtyPositionPathList;
        }

        @Test
        public void testMarkDirtyPositionPath_afterCreation()
        {
                assert en.dirtyPositionPathLink_state.head == null;

                // entity is created at tick 90
                en.markDirtyPositionPath(95); // after its creation tick
                assertFalse(en.dirtyPositionPathTracker.isDirty(94));
                assertTrue(en.dirtyPositionPathTracker.isDirty(95));
                assertTrue(en.dirtyPositionPathTracker.isDirty(1000));

                assert en.dirtyPositionPathLink_state.head == state.dirtyPositionPathList;
        }

        @Test
        public void testGetEntityAtOnlyThis()
        {
                // last argument false means do not look at other states
                assert null == en.getEntityAt(89, false, false);
                assert en == en.getEntityAt(90, false, false);
                en.softRemove(105);
                assert en == en.getEntityAt(104, false, false);
                assert null == en.getEntityAt(105, false, false);
                assert en == en.getEntityAt(105, true, false); // ignore soft delete
        }

        @Test
        public void testGetEntityAt()
        {
                tickTo(oldState, 100);
                // 132

                assert null == en.getEntityAt(89, false, true);
                assert oldEn == en.getEntityAt(90, false, true);
                assert oldEn == en.getEntityAt(99, false, true);
                assert oldEn == en.getEntityAt(100, false, true);
                assert en == en.getEntityAt(101, false, true);

                assert null == oldEn.getEntityAt(89, false, true);
                assert oldEn == oldEn.getEntityAt(90, false, true);
                assert oldEn == oldEn.getEntityAt(99, false, true);
                assert oldEn == oldEn.getEntityAt(100, false, true);
                assert en == oldEn.getEntityAt(101, false, true);


                tickTo(oldState, 100 + econf.HIGHEST_DELAY); // too far back for the createdAt to interfere
                assert null == en.getEntityAt(100, false, true);
                assert null == en.getEntityAt(oldState.tick_now + 1000, false, true);
        }

        @Test
        public void testGetPosition()
        {
                final PhysicsPositionVector pos = new PhysicsPositionVector();
                en.getPosition(pos);

                assertEquals(12, pos.pos.x);
                assertEquals(34, pos.pos.y);
                assertEquals(5, pos.vel.x);
                assertEquals(6, pos.vel.y);
        }
}
