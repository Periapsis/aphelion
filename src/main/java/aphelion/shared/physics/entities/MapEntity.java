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

package aphelion.shared.physics.entities;

import aphelion.shared.gameconfig.GCInteger;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.PhysicsMap;
import aphelion.shared.physics.State;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsPointHistoryDetailed;
import aphelion.shared.physics.valueobjects.PhysicsPositionVector;

/**
 *
 * @author Joris
 */
public abstract class MapEntity
{
        public long createdAt_tick;
        protected boolean removed = false;
        /** valid if removed = true */
        public long removedAt_tick;
        
        /** Set if this entity was removed during State.resetTo(). 
         * It is very likely this entity will be reused if this attribute is set.
         * This attribute is used for assertions and should not be modified in MapEntity.resetTo
         */
        public boolean removedDuringReset = false;
        
        public final State state;
        public final MapEntity[] crossStateList;
        
        public final int HISTORY_LENGTH;
        public final PhysicsPositionVector pos = new PhysicsPositionVector();
        public final PhysicsPointHistoryDetailed posHistory;
        
        /** The radius in pixels * 1024 (not diameter) of the object. The radius may be 0 if it has no
         * diameter, like a bullet.
         */
        public GCInteger radius;

        public long debug_id;
        public long debug_id_with_reset;
        MapEntity(State state, MapEntity[] crossStateList, long createdAt_tick, int historyLength)
        {
                this.debug_id = ++state.env.debug_entities;
                this.debug_id_with_reset = debug_id;
                this.state = state;
                this.crossStateList = crossStateList;
                crossStateList[state.id] = this;
                this.createdAt_tick = createdAt_tick;
                this.HISTORY_LENGTH = historyLength;
                this.posHistory = new PhysicsPointHistoryDetailed(createdAt_tick, HISTORY_LENGTH);
                assert posHistory.HISTORY_LENGTH == HISTORY_LENGTH;
        }
        
        public void softRemove(long tick)
        {
                if (removed)
                {
                        if (removedAt_tick > tick)
                        {
                                removedAt_tick = tick;
                        }
                }
                else
                {
                        removedAt_tick = tick;
                }
                
                removed = true;
        }
        
        public boolean isRemoved()
        {
                return this.removed;
        }
        
        public boolean isRemoved(long tick)
        {
                if (this.removed)
                {
                        return this.removedAt_tick <= tick;
                }
                
                return false;
        }
        
        public MapEntity getOlderEntity(long tick, boolean ignoreSoftDelete, boolean lookAtOtherStates)
        {
                MapEntity en;
                if (lookAtOtherStates)
                {
                        int ticks_ago = (int) (state.tick_now - tick);

                        int states_ago = ticks_ago / state.econfig.TRAILING_STATE_DELAY;

                        int state_id = state.id + states_ago;
                        if (state_id < 0 || state_id >= crossStateList.length)
                        {
                                return null;
                        }
                        
                        en = crossStateList[state_id];
                }
                else
                {
                        en = this;
                }
                
                if (en == null)
                {
                        return null;
                }
                
                if (en.createdAt_tick > tick)
                {
                        return null;
                }
                
                if (!ignoreSoftDelete)
                {
                        if (en.removed && tick > en.removedAt_tick)
                        {
                                return null;
                        }
                }
                
                return en;
        }
        
        /** Get the historic position of this entity.
         * @param pos The position is filled in this object
         * @param tick 
         * @param lookAtOtherStates If set, also look at entities in older states. 
         *        If the history of this actor overlaps with the older one, the position 
         *        from the older one is used.
         * @return true if a position was found (the entity existed at this point in time)
         */
        public boolean getHistoricPosition(PhysicsPoint pos, long tick, boolean lookAtOtherStates)
        {
                pos.set = false;
                
                MapEntity en = getOlderEntity(tick, false, lookAtOtherStates);

                if (en == null)
                {
                        return false; // deleted or too far in the past
                }
                
                pos.set = true;
                pos.x = en.posHistory.getX(tick);
                pos.y = en.posHistory.getY(tick);
                return true;
        }
        
        /** Add (or update) the current position to the history
         *
         * @param tick The tick the current position (this.pos) belongs to
         */
        public void updatePositionHistory(long tick)
        {
                posHistory.setHistory(tick, pos.pos);       
        }
        
        public PhysicsPointHistoryDetailed getAndSeekHistoryDetail(long tick, boolean lookAtOtherStates)
        {
                MapEntity en = getOlderEntity(tick, false, lookAtOtherStates);

                if (en == null)
                {
                        return null; // deleted or too far in the past
                }
                
                en.posHistory.seekDetail(tick);
                return en.posHistory;
        }
        
        public void resetTo(State myState, MapEntity other)
        {
                assert other.crossStateList == this.crossStateList;
                
                if (other.crossStateList[myState.id] != null)
                {
                        assert other.crossStateList[myState.id] == this;
                }
                
                if (this.crossStateList[other.state.id] != null)
                {
                        assert this.crossStateList[other.state.id] == other;
                }
                
                this.debug_id_with_reset = other.debug_id_with_reset;
                createdAt_tick = other.createdAt_tick;
                this.removed = other.removed;
                removedAt_tick = other.removedAt_tick;
                
                pos.set(other.pos);
                posHistory.set(other.posHistory);
        }
}
