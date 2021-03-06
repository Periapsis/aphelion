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


package aphelion.shared.physics.events;


import aphelion.shared.physics.SimpleEnvironment;
import aphelion.shared.physics.State;
import aphelion.shared.physics.entities.Actor;
import aphelion.shared.physics.entities.ActorKey;
import aphelion.shared.physics.events.pub.ActorDiedPublic;
import aphelion.shared.physics.events.pub.EventPublic;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * @author Joris
 */
public class ActorDied extends Event implements ActorDiedPublic
{
        private static final Logger log = Logger.getLogger("aphelion.shared.physics");
        private final History[] history;
        
        public ActorDied(SimpleEnvironment env, Key key)
        {
                super(env, key);
                history = new History[env.econfig.TRAILING_STATES];
                
                for (int a = 0; a < env.econfig.TRAILING_STATES; ++a)
                {
                        history[a] = new History();
                }
        }

        @Override
        public Event cloneWithoutHistory(SimpleEnvironment env)
        {
                return new ActorDied(env, (Key) this.key);
        }
        
        public void execute(long tick, State state, Actor died, Event cause, Actor killer)
        {
                super.execute(tick, state);
                
                assert died != null;
                assert state == died.state;
                assert died.dead.get(tick) == 1 : "The actor should have been marked as dead before calling this event";
                
                if (SwissArmyKnife.assertEnabled)
                {
                        for (int s = 0; s < history.length; ++s)
                        {
                                if (history[s].cause != null)
                                {
                                        // Events are 1:1 on PhysicsEnvironment, not states
                                        // This event should also be spawned by the same cause in every state
                                        assert history[s].cause == cause;
                                }
                        }
                }
                
                History hist = history[state.id];
                hist.set = true;
                hist.tick = tick;
                hist.died = died;
                hist.cause = cause;
                hist.killer = killer;
                
                // nothing interesting to do yet, at the moment this event is only used for external notification
        }
        
        @Override
        public boolean isConsistent(State older, State newer)
        {
                // This event is called by the result of other events.
                // Those events already check consistency.
                return true;
        }
        
        @Override
        public void resetExecutionHistory(State state, State resetTo, Event resetToEvent)
        {
                History histFrom = ((ActorDied) resetToEvent).history[resetTo.id];
                History histTo = history[state.id];
                
                histTo.set(histFrom, state);
        }
        
        @Override
        public void resetToEmpty(State state)
        {
                History histTo = history[state.id];
                histTo.set(new History(), state);
        }

        @Override
        public long getOccurredAt(int stateid)
        {
                History hist = history[stateid];
                if (!hist.set)
                {
                        return 0; // use hasOccurred first
                }
                return hist.tick;
        }
        
        @Override
        public boolean hasOccurred(int stateid)
        {
                 History hist = history[stateid];
                 return hist.set;
        }

        @Override
        public EventPublic getCause(int stateid)
        {
                History hist = history[stateid];
                return hist.cause;
        }

        @Override
        public int getDied(int stateid)
        {
                History hist = history[stateid];
                return hist.died == null ? 0 : hist.died.pid;
        }

        @Override
        public int getKiller(int stateid)
        {
                History hist = history[stateid];
                return hist.killer == null ? 0 : hist.killer.pid;
        }
        
        private static class History
        {
                boolean set = false;
                long tick;
                Actor died;
                Event cause;
                Actor killer;
                
                public void set(History other, State myState)
                {
                        set = other.set;
                        tick = other.tick;
                        cause = other.cause == null ? null : other.cause.findInOtherEnv(myState.env);
                        
                        died = other.died == null ? null : other.died.findInOtherState(myState);
                        killer = other.killer == null ? null : other.killer.findInOtherState(myState);
                }
        }
        
        public static final class Key implements EventKey
        {
                private final ProjectileExplosion.Key causedBy;
                private final ActorKey died;

                public Key(ProjectileExplosion.Key causedBy, ActorKey died)
                {
                        if (causedBy == null || died == null)
                        {
                                throw new IllegalArgumentException();
                        }
                        this.causedBy = causedBy;
                        this.died = died;
                }

                @Override
                public int hashCode()
                {
                        int hash = 7;
                        hash = 67 * hash + Objects.hashCode(this.causedBy);
                        hash = 67 * hash + Objects.hashCode(this.died);
                        return hash;
                }

                @Override
                public boolean equals(Object obj)
                {
                        if (obj == null)
                        {
                                return false;
                        }
                        if (!(obj instanceof Key))
                        {
                                return false;
                        }
                        final Key other = (Key) obj;
                        if (!Objects.equals(this.causedBy, other.causedBy))
                        {
                                return false;
                        }
                        if (!Objects.equals(this.died, other.died))
                        {
                                return false;
                        }
                        return true;
                }
                
                
        }
}
