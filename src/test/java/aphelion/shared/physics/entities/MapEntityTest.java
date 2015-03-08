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
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsPointHistory;
import aphelion.shared.physics.valueobjects.PhysicsPointHistoryDetailed;
import aphelion.shared.physics.valueobjects.PhysicsPositionVector;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Iterator;

import static org.junit.Assert.*;

/**
 * @author Joris
 */
public class MapEntityTest
{
        private static final Field trailingStatesField;
        static
        {
                try
                {
                        trailingStatesField = SimpleEnvironment.class.getDeclaredField("trailingStates");
                        trailingStatesField.setAccessible(true);
                }
                catch (NoSuchFieldException e)
                {
                        throw new Error(e);
                }
        }

        private EnvironmentConf econf;
        private SimpleEnvironment env;
        private State oldState;
        private State state;
        private MapEntity[] crossStateList;
        private MapEntity oldEn;
        private MapEntity en;

        private static class MyMapEntity extends MapEntity
        {
                public MyMapEntity(State state, MapEntity[] crossStateList, long createdAt_tick, int historyLength)
                {
                        super(state, crossStateList, createdAt_tick, historyLength);
                }

                @Override
                public void performDeadReckoning(PhysicsMap map, long tick_now, long reckon_ticks, boolean applyForceEmitters)
                {
                        // noop
                }
        }

        @Before
        public void setUp() throws IllegalAccessException
        {
                econf = new EnvironmentConf(false, true, true);
                env = new SimpleEnvironment(econf, new MapEmpty(), false);
                oldState = ( (State[]) trailingStatesField.get(env) )[2];
                state = ( (State[]) trailingStatesField.get(env) )[1];

                crossStateList = new MapEntity[econf.TRAILING_STATES];

                en = new MyMapEntity(state, crossStateList, /* created at tick */ 90, /* history length*/ 10);
                en.pos.pos.set(1000, 2000);
                en.pos.vel.set(3, 7);
                en.updatedPosition(state.tick_now);

                oldEn = new MyMapEntity(oldState, crossStateList, 90, 10);
                oldEn.pos.pos.set(5000, 7000);
                oldEn.pos.vel.set(4, 9);
                oldEn.updatedPosition(oldState.tick_now);

                // (the entities are not registered in the state in this test)

                tickTo(state, 100);
        }

        private void tickTo(State target, int tick)
        {
                while (target.tick_now < tick)
                {
                        env.tick();
                        oldEn.pos.pos.add(oldEn.pos.vel);
                        en.pos.pos.add(en.pos.vel);

                        oldEn.updatedPosition(oldState.tick_now);
                        en.updatedPosition(state.tick_now);
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

                pos.pos.assertEquals(1396, 2924);
                pos.vel.assertEquals(3, 7);
        }

        @Test
        public void testGetHistoricPositionOnlyThis()
        {
                final PhysicsPositionVector pos = new PhysicsPositionVector();

                // into the future
                assertFalse(en.getHistoricPosition(pos, 101, false));
                assert !pos.pos.set;
                assert !pos.vel.set;

                assertTrue(en.getHistoricPosition(pos, 100, false));
                pos.pos.assertEquals(1396, 2924);
                pos.vel.assertEquals(3, 7);

                assertTrue(en.getHistoricPosition(pos, 99, false));
                pos.pos.assertEquals(1393, 2917);
                pos.vel.assertEquals(3, 7);

                assertTrue(en.getHistoricPosition(pos, 91, false));
                pos.pos.assertEquals(1369, 2861);
                pos.vel.assertEquals(3, 7);

                //further back than the history length we set (10)
                assertFalse(en.getHistoricPosition(pos, 90, false));
                assert !pos.pos.set;
                assert !pos.vel.set;
        }

        @Test
        public void testGetHistoricPosition()
        {
                final PhysicsPositionVector pos = new PhysicsPositionVector();

                tickTo(oldState, 100);

                // into the future
                assertFalse(en.getHistoricPosition(pos, state.tick_now + 1, true));
                assert !pos.pos.set;
                assert !pos.vel.set;

                assertTrue(en.getHistoricPosition(pos, state.tick_now, true));
                pos.pos.assertEquals(1492, 3148);
                pos.vel.assertEquals(3, 7);

                assertTrue(en.getHistoricPosition(pos, state.tick_now - 1, true));
                pos.pos.assertEquals(1489, 3141);
                pos.vel.assertEquals(3, 7);

                // the older entity has a different position on purpose so that we can test this
                assertTrue(en.getHistoricPosition(pos, oldState.tick_now, true));
                pos.pos.assertEquals(5656, 8476);
                pos.vel.assertEquals(4, 9);


        }

        @Test
        public void testGetHistoricSmoothPosition()
        {
                // Not implemented in MapEntities by default
                final PhysicsPoint pos = new PhysicsPoint();
                assertFalse(en.getHistoricSmoothPosition(pos, state.tick_now, false));
                assert !pos.set;
        }

        @Test
        public void testUpdatedPositionFutureTick()
        {
                assertTrue(en.dirtyPositionPathTracker.isDirty(101));

                en.pos.pos.set(500000, 666666);
                en.pos.vel.set(-1, -2);
                en.updatedPosition(101);

                assertEquals(500000, en.posHistory.getX(101));
                assertEquals(666666, en.posHistory.getY(101));
                assertEquals(-1, en.velHistory.getX(101));
                assertEquals(-2, en.velHistory.getY(101));
                assertFalse(en.dirtyPositionPathTracker.isDirty(101));

                // 101 is not the current tick so not in the entity grid:
                Iterator<MapEntity> it = state.entityGrid.iterator(new PhysicsPoint(500000, 666666), 1);
                assertFalse(it.hasNext());
        }

        @Test
        public void testUpdatedPositionNow()
        {
                en.pos.pos.set(500000, 666666);
                en.pos.vel.set(-1, -2);
                en.updatedPosition(100);

                assertEquals(500000, en.posHistory.getX(100));
                assertEquals(666666, en.posHistory.getY(100));
                assertEquals(-1, en.velHistory.getX(100));
                assertEquals(-2, en.velHistory.getY(100));

                // 100 is the current tick so we should be in the grid
                Iterator<MapEntity> it = state.entityGrid.iterator(new PhysicsPoint(500000, 666666), 1);
                assertTrue(it.hasNext());
                assertEquals(it.next(), en);
        }

        @Test
        public void testUpdatedPositionNowRemoved()
        {
                en.softRemove(100);
                en.pos.pos.set(500000, 666666);
                en.pos.vel.set(-1, -2);
                en.updatedPosition(100);

                assertEquals(500000, en.posHistory.getX(100));
                assertEquals(666666, en.posHistory.getY(100));
                assertEquals(-1, en.velHistory.getX(100));
                assertEquals(-2, en.velHistory.getY(100));

                // 100 is the current tick so we should be in the grid
                // if removed we should not be in the entity grid
                Iterator<MapEntity> it = state.entityGrid.iterator(new PhysicsPoint(500000, 666666), 1);
                assertFalse(it.hasNext());
        }

        @Test
        public void testGetAndSeekHistoryDetail()
        {
                final PhysicsPoint pos = new PhysicsPoint();

                en.posHistory.appendDetail(98, 1100, 2005);

                PhysicsPointHistoryDetailed history = en.getAndSeekHistoryDetail(98, false);
                assertTrue(history.hasNextDetail());

                history.nextDetail(pos);
                pos.assertEquals(1100, 2005);

                assertFalse(history.hasNextDetail());

                // history length was set at "10"
                assertNull(en.getAndSeekHistoryDetail(89, false));
        }

        @Test
        public void testReset()
        {
                tickTo(oldState, 100);

                assertEquals(101, oldEn.dirtyPositionPathTracker.getFirstDirtyTick());
                oldEn.createdAt_tick = 89;
                oldEn.softRemove(110);
                oldEn.pos.pos.set(1234, 5678);
                oldEn.pos.vel.set(910, 1112);
                oldEn.updatedPosition(oldState.tick_now);
                oldEn.markDirtyPositionPath(95);
                assertEquals(100, oldEn.posHistory.getHighestTick());
                assertEquals(95, oldEn.dirtyPositionPathTracker.getFirstDirtyTick());

                en.resetTo(oldEn);

                assertEquals(95, en.dirtyPositionPathTracker.getFirstDirtyTick());
                assertEquals(89, en.createdAt_tick);
                assertEquals(110, en.removedAt_tick);
                assertTrue(en.isRemovedSet());
                en.pos.pos.assertEquals(1234, 5678);
                en.pos.vel.assertEquals(910, 1112);
                assertEquals(100, en.posHistory.getHighestTick());
                assertEquals(1234, en.posHistory.getX(100));
                assertEquals(5678, en.posHistory.getY(100));
                assertEquals(100, en.velHistory.getHighestTick());
                assertEquals(910, en.velHistory.getX(100));
                assertEquals(1112, en.velHistory.getY(100));
                assertTrue(en.dirtyPositionPathTracker.isDirty(95));
                assertFalse(en.dirtyPositionPathTracker.isDirty(94));
        }

        @Test
        public void testResetForeign() throws IllegalAccessException
        {
                // test a foreign reset with a different history length

                // First environment //
                econf = new EnvironmentConf(false, true, true);
                env = new SimpleEnvironment(econf, new MapEmpty(), false);
                oldState = ( (State[]) trailingStatesField.get(env) )[1];
                state = ( (State[]) trailingStatesField.get(env) )[0];

                crossStateList = new MapEntity[econf.TRAILING_STATES];

                // overlapping histories (by 5 ticks)
                en = new MyMapEntity(state, crossStateList, 2, econf.TRAILING_STATE_DELAY + 5);
                en.pos.pos.set(1000, 2000);
                en.pos.vel.set(3, 7);
                en.updatedPosition(state.tick_now);

                oldEn = new MyMapEntity(oldState, crossStateList, 0, econf.TRAILING_STATE_DELAY + 5);
                oldEn.pos.pos.set(5000, 7000);
                oldEn.pos.vel.set(4, 9);
                oldEn.updatedPosition(oldState.tick_now);


                // The foreign environment //
                EnvironmentConf econfForeign = new EnvironmentConf(false, true, true);
                SimpleEnvironment envForeign = new SimpleEnvironment(econfForeign, new MapEmpty(), false);
                State foreignState = ( (State[]) trailingStatesField.get(envForeign) )[0];

                MapEntity entityForeign = new MyMapEntity(foreignState, new MapEntity[1], 90, econf.TRAILING_STATE_DELAY * 2);
                entityForeign.pos.pos.set(500000, 400000);
                entityForeign.pos.vel.set(150, 100);
                entityForeign.updatedPosition(foreignState.tick_now);
                assertNotEquals(en.HISTORY_LENGTH, entityForeign.HISTORY_LENGTH);


                while (state.tick_now < 100)
                {
                        env.tick();
                        envForeign.tick();
                        assert env.getTick() == envForeign.getTick();

                        oldEn.pos.pos.add(oldEn.pos.vel);
                        en.pos.pos.add(en.pos.vel);
                        entityForeign.pos.pos.add(entityForeign.pos.vel);

                        oldEn.updatedPosition(oldState.tick_now);
                        en.updatedPosition(state.tick_now);
                        entityForeign.updatedPosition(foreignState.tick_now);
                }

                // Now at tick 100
                assertEquals(101, en.dirtyPositionPathTracker.getFirstDirtyTick());
                en.createdAt_tick = 89;
                en.softRemove(110);
                en.pos.pos.set(1234, 5678);
                en.pos.vel.set(910, 1112);
                en.updatedPosition(state.tick_now);
                en.markDirtyPositionPath(95);
                assertEquals(100, en.posHistory.getHighestTick());
                assertEquals(95, en.dirtyPositionPathTracker.getFirstDirtyTick());

                entityForeign.resetTo(en);

                assertEquals(95, entityForeign.dirtyPositionPathTracker.getFirstDirtyTick());
                assertEquals(89, entityForeign.createdAt_tick);
                assertEquals(110, entityForeign.removedAt_tick);
                assertTrue(entityForeign.isRemovedSet());
                entityForeign.pos.pos.assertEquals(1234, 5678);
                entityForeign.pos.vel.assertEquals(910, 1112);
                assertEquals(100, entityForeign.posHistory.getHighestTick());
                assertEquals(1234, entityForeign.posHistory.getX(100));
                assertEquals(5678, entityForeign.posHistory.getY(100));
                assertEquals(100, entityForeign.velHistory.getHighestTick());
                assertEquals(910, entityForeign.velHistory.getX(100));
                assertEquals(1112, entityForeign.velHistory.getY(100));
                assertTrue(entityForeign.dirtyPositionPathTracker.isDirty(95));
                assertFalse(entityForeign.dirtyPositionPathTracker.isDirty(94));

                assertHistoryTickEqual(en.posHistory, entityForeign.posHistory, 99);
                assertHistoryTickEqual(en.posHistory, entityForeign.posHistory, 100 - econf.TRAILING_STATE_DELAY);
                // This is where state 0 and 1 of "env" overlap by 5 ticks; state 0 has priority:
                assertHistoryTickEqual(en.posHistory, entityForeign.posHistory, 100 - econf.TRAILING_STATE_DELAY - 4);
                assertHistoryTickEqual(oldEn.posHistory, entityForeign.posHistory, 100 - econf.TRAILING_STATE_DELAY - 5);
                assertHistoryTickEqual(oldEn.posHistory, entityForeign.posHistory, 100 - econf.TRAILING_STATE_DELAY * 2 + 1);

                assertHistoryTickEqual(en.velHistory, entityForeign.velHistory, 99);
                assertHistoryTickEqual(en.velHistory, entityForeign.velHistory, 100 - econf.TRAILING_STATE_DELAY);
                // This is where state 0 and 1 of "env" overlap by 5 ticks; state 0 has priority:
                assertHistoryTickEqual(en.velHistory, entityForeign.velHistory, 100 - econf.TRAILING_STATE_DELAY - 4);
                assertHistoryTickEqual(oldEn.velHistory, entityForeign.velHistory, 100 - econf.TRAILING_STATE_DELAY - 5);
                assertHistoryTickEqual(oldEn.velHistory, entityForeign.velHistory, 100 - econf.TRAILING_STATE_DELAY * 2 + 1);
        }

        private static void assertHistoryTickEqual(PhysicsPointHistory expected, PhysicsPointHistory actual, long tick)
        {
                assertEquals(expected.getX(tick), actual.getX(tick));
                assertEquals(expected.getY(tick), actual.getY(tick));
        }
}
