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

import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.entities.ActorPublicImpl;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.entities.Actor;
import aphelion.shared.physics.entities.ActorIterator;
import aphelion.shared.physics.operations.ActorWeaponFire;
import aphelion.shared.physics.operations.ActorRemove;
import aphelion.shared.physics.operations.ActorNew;
import aphelion.shared.physics.operations.WeaponSync;
import aphelion.shared.physics.operations.ActorMove;
import aphelion.shared.physics.operations.Operation;
import aphelion.shared.physics.operations.ActorWarp;
import aphelion.shared.event.TickEvent;
import aphelion.shared.gameconfig.ConfigSelection;
import aphelion.shared.gameconfig.GCBoolean;
import aphelion.shared.gameconfig.GCBooleanList;
import aphelion.shared.gameconfig.GCImage;
import aphelion.shared.gameconfig.GCInteger;
import aphelion.shared.gameconfig.GCIntegerList;
import aphelion.shared.gameconfig.GCString;
import aphelion.shared.gameconfig.GCStringList;
import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.physics.events.Event;
import aphelion.shared.physics.events.pub.EventPublic;
import aphelion.shared.physics.operations.ActorModification;
import aphelion.shared.physics.operations.ActorSync;
import aphelion.shared.physics.operations.LoadConfig;
import aphelion.shared.physics.operations.pub.OperationPublic;
import aphelion.shared.physics.operations.UnloadConfig;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.physics.valueobjects.PhysicsWarp;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import aphelion.shared.swissarmyknife.LinkedListHead;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Physics Engine based on TSS.
 *
 * All position values are in pixels * 1024. The Top Left corner of the screen is considered 0,0. All time values are in
 * ticks (10ms by default) Actor ids (pid) are unique, and should be for a while (if a player leaves, do not immediately
 * reuse his pid). 
 * 
 * All position values (pos, velocity) must be between -(2^30-1) and 2^30-1;
 *
 * Trailing State Synchronisation: 
 * 
 * + Operations arrive by network / or are generated at the client. 
 * 
 * + An operation is
 *   placed on the pending list for each trailing state (PhysicsState).
 *
 * + Late operations: These are executed immediately. (operation.tick lte state.tick_now).
 *
 * + Early operations: These are placed on a todo list (operation.tick gt state.tick_now).
 *
 * + As an operation executes, it stores the changes it made to the game state. This is stored for each trailing state.
 *
 * + At some point (there are multiple viable strategies), each trailing state looks at the changes in game state that
 *   an operation produced. These changes are compared with changed recorded at the directly previous state.
 *
 * + Very late operations: If an operation is very late (so late that it does not get added to at least 1 state todo
 *   list, at least 2 on the server). It is ignored. This means lost weapons, etc.
 *
 * + Very early operations: If an operation is very early (based on a setting). It is ignored, this is to prevent
 *   cheating.
 *
 * + Any operation that is older than the last trailing state is removed (HISTORY_DURATION)
 *
 * + Movement operations consist of the ActorWarp(position,velocity,rotation,time) and the
 *   ActorMove(up,down,left,right,time) operations. ActorWarp may only be generated by the server. And is sent upon spawn,
 *   special items and periodically to prevent synchronization mistakes. ActorMove is generated by the client and consists
 *   of what keys the player pressed for that tick. ActorMove is sent to all clients. Multiple moves on the same tick are
 *   ignored.
 *
 *
 * + Weapon fire consists of the WeaponFire(position,velocity,weaponid,time) operation. In states where the operation is
 *   late, it is executed using the position and velocity values. in these states, there is no check for cheating. And the
 *   "current" position of the projectile is made up to date using dead reckoning. In states where the operation is early,
 *   position and velocity are ignored. The position and direction of weapon fire are determined by the movement commands
 *   the player has sent. Things like weapon delay and energy are also checked to prevent cheating.
 *
 * Operations vs Events:
 * + An operation is reexecuted in a timewarp (added back to the todo list). An event is never reexecuted 
 *   unless the original cause occurs again.
 * + Both operations and events have a single instance for the entire environment. Operations are placed in a 
 *   todo or history list for each state. Events only in a history list.
 * + Both execute consistency checks.
 * + Operations always have an external cause (user hits a key, something recieved over the network).
 *   Events always have an internal cause (previous operation triggered a weapon fire, the event is the 
 *   projectile hitting someone).
 *
 * @author Joris
 */
public class PhysicsEnvironment implements TickEvent
{
        private static final Logger log = Logger.getLogger("aphelion.shared.physics");
        private OutputStreamWriter debugLog;
        
        /** The maximum amount of trailing states for both client and server. */
        public static final int MAX_TRAILING_STATES = 8;
        /** Each state trails this much time (tick) behind the next state. 
         * 16 is a power of 2 which gives a small speed benefit */
        public static final int TRAILING_STATE_DELAY = 16;
        
        /** The total amount of ticks history is kept for. */
        public static final int TOTAL_HISTORY = MAX_TRAILING_STATES * TRAILING_STATE_DELAY;
        
        public final boolean server; // true = server; false = client
        public final int MAX_OPERATION_AGE; // do not accept operations that are older than this many ticks
        public final int TRAILING_STATES;
        public final int TIMEWARP_EVERY_TICKS = 10; // If two timewarps need to be executed in rapid succession, wait this many ticks
        
        public static final int ROTATION_POINTS = (2*2*2*2*2*2) * (3*3*3) * (5*5) * 7 * 11 * 13 * 17; // Highly Composite Number (it has 1344 divisors)
        public static final int ROTATION_1_2TH = ROTATION_POINTS / 2;
        public static final int ROTATION_1_4TH = ROTATION_POINTS / 4;
        public static final int ROTATION_3_4TH = ROTATION_1_2TH + ROTATION_1_4TH;
        public static final int MAX_POSITION = 1073741823; // 2^30-1

        
        long tick_now = 0;
        long ticked_at;
        private long remote_tick_offset = 0;
        private long lastTimewarp_tick = 0;
        private PhysicsMap map;
        State[] trailingStates; // delay = index * TRAILING_STATE_DURATION
        final LinkedListHead<Event> eventHistory = new LinkedListHead<>(); // ordered by the order of appending
        private LinkedListEntry<Event> eventHistory_lastSeen = null; // last seen by nextEvent()
        
        
        private int timewarps = 0; // used for debugging
        public long debug_entities = 0;

        public PhysicsEnvironment(boolean server, PhysicsMap map)
        {
                int a;

                if (server)
                {
                        TRAILING_STATES = 4;
                        // C2S operations will have to arrive within  640ms 
                }
                else
                {
                        TRAILING_STATES = 8;
                        // S2C and C2S2C operations will have to arrive within 1280ms
                        // The MAX_OPERATION_AGE for clients should be atleast 2x as large as the maximum age of the server.
                        // So that it will be unlikely that a C2S2C operation that was accepted by the server, to be rejected by clients.
                }
                
                // The last state does not accept new (non critical) operations
                MAX_OPERATION_AGE = (TRAILING_STATES-1) * TRAILING_STATE_DELAY;

                this.server = server;
                this.map = map;

                trailingStates = new State[TRAILING_STATES];

                for (a = 0; a < TRAILING_STATES; a++)
                {
                        trailingStates[a] = new State(
                                this, 
                                a, 
                                a * TRAILING_STATE_DELAY, 
                                a < TRAILING_STATES - 2, // do not allow hints in the last 2 trailing states
                                a == TRAILING_STATES - 1  // force late config execution in the last trailing state
                                );
                        
                        trailingStates[a].tick_now = this.tick_now - (a * TRAILING_STATE_DELAY);
                }
        }
        
        /** Set the tick offset to the server.
         * Ticks are used to seed hash functions which are used to randomize things.
         * This tick value must be consistent with the server. Use this method to set 
         * the offset to the server. This value should remain 0 for the server instance.
         * @param offset 
         */
        public void setTickOffsetToServer(long offset)
        {
                remote_tick_offset = offset;
        }
        
        public long localTickToServer(long localTick)
        {
                return localTick + this.remote_tick_offset;
        }

        /** The number of times tick() has been called.
         * Tick values are sent over the network in order to synchronize events.
         *
         * @return
         */
        public long getTick()
        {
                return tick_now;
        }

        /** The System.nanoTime() at which the current tick has started.
         *
         * @return nano time
         */
        public long getTickedAt()
        {
                return ticked_at;
        }
        
        /** The number of times tick() has been called in a particular state
         * Tick values are sent over the network in order to synchronize events.
         *
         * @param stateid A state id, 0 for the most current state. TRAILING_STATES-1 for the oldest. 
         * @return
         */
        public long getTick(int stateid)
        {
                if (stateid < 0 || stateid >= TRAILING_STATES)
                {
                        throw new IllegalArgumentException("Invalid state");
                }
                
                return trailingStates[stateid].tick_now;
        }

        @Override
        public void tick(long eventloop_tick) // interface
        {
                tick();
        }
        public static int debug_current_state = -1;

        public void tick() // make sure this is called in such a way ticks are synchronised between peers
        {
                int a;
                long tick;

                ++tick_now;
                ticked_at = System.nanoTime();

                for (a = 0; a < TRAILING_STATES; a++)
                {
                        debug_current_state = a;
                        tick = this.tick_now - (a * TRAILING_STATE_DELAY);
                        this.trailingStates[a].tick(tick);
                }

                
                consistencyCheck();
                removeOldHistory();
        }

        
        public boolean consistencyCheck()
        {
                for (int s = TRAILING_STATES - 1; s > 0; --s)
                {
                        State older = this.trailingStates[s];
                        State newer = this.trailingStates[s - 1];
                        
                        if (!older.needTimewarpToThisState)
                        {
                                older.needTimewarpToThisState = !areStatesConsistent(older, newer);
                        }
                        
                        if (older.needTimewarpToThisState)
                        {
                                if (older.isLast // Always timewarp to the last state (because state data is going to be discarded)
                                   || this.lastTimewarp_tick == 0 
                                   || this.tick_now - this.lastTimewarp_tick >= this.TIMEWARP_EVERY_TICKS)
                                        
                                {
                                        // state is not consistent with the older state
                                        // fix all higher states

                                        timewarp(s);

                                        return false;
                                }
                        }
                }
                
                return true;
        }
        
        
        /** Reset all states to the given state and re-simulate.
         * @param stateid The state to reset lower (more recent) states to
         */
        public void timewarp(int stateid)
        {
                lastTimewarp_tick = this.tick_now;
                long start = System.nanoTime();
                
                timewarps++;
                
                for (int s = stateid; s > 0; --s)
                {
                        State older = this.trailingStates[s];
                        State newer = this.trailingStates[s - 1];
                        
                        older.needTimewarpToThisState = false;

                        long wasTick = newer.tick_now;
                        
                        newer.resetTo(older);

                        long tick = newer.tick_now + 1;
                        while (tick <= this.tick_now - newer.delay)
                        {
                                newer.tick(tick);
                                tick++;
                        }
                        
                        assert newer.tick_now == wasTick;
                }
                
                long end = System.nanoTime();
                
                log.log(Level.WARNING, "Time Warp {0}: to state {1} in {2}ms. Tick {3} to {4}.", new Object[] {
                        server ? "(server)" : "(client)",
                        stateid,
                        (end - start) / 1_000_000.0,
                        this.trailingStates[0].tick_now,
                        this.trailingStates[stateid].tick_now,
                });
        }
        
        public int getTimewarpCount()
        {
                return timewarps;
        }

        private void removeOldHistory() // (or recycle)
        {
                LinkedListEntry<Operation> linkOp, linkOpNext;
                LinkedListEntry<Event> linkEv, linkEvNext;
                Operation op;
                State oldestState;

                // If an operation has been executed in the oldest state,
                // it can be removed.
                
                oldestState = this.trailingStates[TRAILING_STATES - 1];
                
                linkOp = oldestState.history.first;
                while (linkOp != null)
                {
                        linkOpNext = linkOp.next;
                        op = linkOp.data;
                        
                        if (op.tick < oldestState.tick_now - 1) // -1 to be sure
                        {
                                // remove the operation everywhere
                                for (int s = 0; s < TRAILING_STATES; ++s)
                                {
                                        op.link[s].remove();
                                }
                        }
                        else
                        {
                                // do not remove operations which were just added to the history in the oldest state
                                // (op.tick == oldestState.tick_now)
                                // remove it next tick
                                break;
                        }

                        linkOp = linkOpNext;
                }
                
                linkEv = this.eventHistory.first;
                while (linkEv != null)
                {
                        linkEvNext = linkEv.next;
                        if (linkEv.data.isOld(oldestState.tick_now))
                        {
                                if (eventHistory_lastSeen == linkEv)
                                {
                                        eventHistory_lastSeen = linkEv.previous;
                                }
                                
                                linkEv.remove();
                                linkEv.data.added = false;
                                
                                
                        }
                        
                        linkEv = linkEvNext;
                }
        }

        /**
         * Full consistency check for a state
         */
        private boolean areStatesConsistent(State older, State newer)
        {
                assert newer.id == older.id - 1;
                
                // Go through the actors of the newer state
                // There is no need to verify if older state has actors we do not have;
                // The operation history check will take care of this.
                PhysicsShipPosition newerPosition = new PhysicsShipPosition();
                PhysicsShipPosition olderPosition = new PhysicsShipPosition();
                
                for (int i = 0; i < newer.actorsList.size(); ++i)
                {
                        Actor actorNewer = newer.actorsList.get(i);
                        
                        if (actorNewer.createdAt_tick <= older.tick_now)
                        {
                                // this works because histories overlap
                                actorNewer.getHistoricPosition(newerPosition, older.tick_now, false);
                                actorNewer.getHistoricPosition(olderPosition, older.tick_now, true);
                                
                                // note if this method is not called every tick, all the tick since
                                // the last call of areStatesConsistent() will have to be checked, 
                                // instead of only the current

                                if (!olderPosition.equals(newerPosition))
                                {
                                        log.log(Level.WARNING, "{0}: Inconsistency in position/velocity/rotation, actor {1}", new Object[]{
                                                server ? "Server" : "Client",
                                                actorNewer.pid
                                        });
                                        return false;
                                }
                                
                                if (actorNewer.getHistoricEnergy(older.tick_now, false) != 
                                    actorNewer.getHistoricEnergy(older.tick_now, true))
                                {
                                        log.log(Level.WARNING, "{0}: Inconsistency in energy, actor {1}", new Object[]{
                                                server ? "Server" : "Client",
                                                actorNewer.pid
                                        });
                                        return false;
                                }
                        }
                        
                        
                        
                        
                }
                
                // Go through the history of the newer state
                for (LinkedListEntry<Operation> linkOp = newer.history.first; linkOp != null; linkOp = linkOp.next)
                {
                        assert linkOp.head == newer.history;
                        
                        if (!linkOp.data.isConsistent(older, newer))
                        {
                                log.log(Level.WARNING, "{0}: Inconsistency in operation {1}", new Object[]{
                                        server ? "Server" : "Client",
                                        linkOp.data.getClass().getName()
                                });
                                return false;
                        }
                }
                
                // go through the events of the newer state
                for (LinkedListEntry<Event> linkEv = eventHistory.first; linkEv != null; linkEv = linkEv.next)
                {
                        if (!linkEv.data.isConsistent(older, newer))
                        {
                                log.log(Level.WARNING, "{0}: Inconsistency in event {1}", new Object[]{
                                        server ? "Server" : "Client",
                                        linkEv.data.getClass().getName()
                                });
                                return false;
                        }
                }

                return true;
        }
        
        /** .
         * 
         * @return false if the operation was too old and has been ignored
         */
        private boolean addOperation(Operation operation)
        {
                int a;

                if (operation.ignorable && operation.tick < this.tick_now - MAX_OPERATION_AGE)
                {
                        log.log(Level.WARNING, "Operation about actor {1} at {2} dropped, too old ({0}). now = {3}", new Object[] {
                                server ? "server" : "client",
                                operation.getPid(),
                                operation.getTick(),
                                this.tick_now
                        });
                        return false;
                }

                for (a = 0; a < TRAILING_STATES; a++)
                {
                        trailingStates[a].addOperation(operation);
                }
                
                return true;
        }

        /**
         * Look up an actor by pid. The returned value is a wrapper (PhysicsActor) around the real actor class
         * (PhysicsActorPrivate). This wrapper is unaffected by things such as temporary removal of the actor.
         * @param pid
         * @return  
         */
        public ActorPublic getActor(int pid)
        {
                return getActor(pid, false);
        }
        
        public ActorPublic getActor(int pid, boolean nofail)
        {
                return getActor(pid, 0, nofail);
        }

        /**
         * Look up an actor by pid. The returned value is a wrapper (PhysicsActor) around the real actor class
         * (PhysicsActorPrivate). This wrapper is unaffected by things such as temporary removal of the actor.
         *
         * @param pid 
         * @param stateid A state id, 0 for the most current state. TRAILING_STATES-1 for the oldest.
         * @param nofail if set, an actor wrapper is always returned, even if the actor does not exist at the moment.
         * This lets you get a wrapper before the actual actor creation operation has been executed (which may take a
         * while, depending on the timestamp).
         * @return  
         */
        public ActorPublic getActor(int pid, int stateid, boolean nofail)
        {
                Actor actor;
                State state;
        
                if (stateid < 0 || stateid >= TRAILING_STATES)
                {
                        throw new IllegalArgumentException("Invalid state");
                }
                
                state = trailingStates[stateid];
                
                actor = state.actors.get(pid);
                if (actor == null)
                {
                        if (nofail)
                        {
                                return new ActorPublicImpl(pid, state);
                        }
                        else
                        {
                                return null;
                        }
                }

                return actor.publicWrapper;
        }
        
        /** Loop over all the known actors in a given state using an iterator.
         * It is OK to reference PhysicsActorPublic for long periods of time.
         * @param stateid A state id, 0 for the most current state. TRAILING_STATES-1 for the oldest.
         * @return A read only iterator. Only use this iterator on the main thread, do not store the iterator.
         */
        public Iterator<ActorPublic> actorIterator(int stateid)
        {
                State state;
        
                if (stateid < 0 || stateid >= TRAILING_STATES)
                {
                        throw new IllegalArgumentException("Invalid state");
                }
                
                state = trailingStates[stateid];
                
                return new ActorIterator(state.actors.iterator(), state);
        }
        
        /** Loop over all the known actors in a given state using an iterator.
         * It is OK to reference PhysicsActorPublic for long periods of time.
         * @param stateid A state id, 0 for the most current state. TRAILING_STATES-1 for the oldest.
         * @return A read only iterator. Only use this iterator on the main thread, do not store the iterator.
         */
        public Iterable<ActorPublic> actorIterable(int stateid)
        {
                if (stateid < 0 || stateid >= TRAILING_STATES)
                {
                        throw new IllegalArgumentException("Invalid state");
                }
                
                final State state = trailingStates[stateid];
                
                return new Iterable<ActorPublic>()
                {

                        @Override
                        public Iterator<ActorPublic> iterator()
                        {
                                return new ActorIterator(state.actors.iterator(), state);
                        }
                };
        }
        
        /** Return the actor count, including those who have been soft deleted.
         * This is equal to the number of actors iterated by actorIterator()
         * @param stateid
         * @return  
         */
        public int getActorCount(int stateid)
        {
                State state;
        
                if (stateid < 0 || stateid >= TRAILING_STATES)
                {
                        throw new IllegalArgumentException("Invalid state");
                }
                
                state = trailingStates[stateid];
                
                assert state.actors.size() == state.actorsList.size();
                return state.actors.size();
        }
        
        @SuppressWarnings("unchecked")
        public Iterator<ProjectilePublic> projectileIterator(int stateid)
        {
                State state;
        
                if (stateid < 0 || stateid >= TRAILING_STATES)
                {
                        throw new IllegalArgumentException("Invalid state");
                }
                
                state = trailingStates[stateid];
                
                return (Iterator<ProjectilePublic>) (Object) state.projectiles.iteratorReadOnly();
        }
        
        @SuppressWarnings("unchecked")
        public Iterable<ProjectilePublic> projectileIterable(int stateid)
        {
                if (stateid < 0 || stateid >= TRAILING_STATES)
                {
                        throw new IllegalArgumentException("Invalid state");
                }
                
                final State state = trailingStates[stateid];
                
                return new Iterable<ProjectilePublic>()
                {
                        @Override
                        public Iterator<ProjectilePublic> iterator()
                        {
                                return (Iterator<ProjectilePublic>) (Object) state.projectiles.iteratorReadOnly();
                        }
                };
        }
        
        /** Return the projectile count, including those who have been soft deleted.
         * This is equal to the number of projectiles iterated by projectileIterator()
         * This method is O(n) time and primarily intended for test cases.
         * @param stateid
         * @return  
         */
        public int calculateProjectileCount(int stateid)
        {
                if (stateid < 0 || stateid >= TRAILING_STATES)
                {
                        throw new IllegalArgumentException("Invalid state");
                }
                
                final State state = trailingStates[stateid];
                
                return state.projectiles.calculateSize();
        }
        
        /** Loop over all the operations in the todo list of a state.
         * @param stateid A state id, 0 for the most current state. TRAILING_STATES-1 for the oldest.
         * @return A read only iterator. Only use this iterator on the main thread, do not 
         * store the iterator or any of its values after iterating.
         */
        @SuppressWarnings("unchecked")
        public Iterator<OperationPublic> todoListIterator(int stateid)
        {
                State state;
        
                if (stateid < 0 || stateid >= TRAILING_STATES)
                {
                        throw new IllegalArgumentException("Invalid state");
                }
                
                state = trailingStates[stateid];
                
                return (Iterator<OperationPublic>) (Object) state.todo.iteratorReadOnly();
        }
        
        public ConfigSelection newConfigSelection(int stateid)
        {
                if (stateid < 0 || stateid >= TRAILING_STATES)
                {
                        throw new IllegalArgumentException("Invalid state");
                }
                
                return trailingStates[stateid].config.newSelection();
        }
        
        public void loadConfig(long tick, String fileIdentifier, List yamlDocuments)
        {
                LoadConfig op = new LoadConfig();
                op.tick = tick;
                op.fileIdentifier = fileIdentifier;
                op.yamlDocuments = yamlDocuments;
                
                boolean ret = addOperation(op); // no fail
                assert ret;
                
                
        }
        
        public void unloadConfig(long tick, String fileIdentifier)
        {
                UnloadConfig op = new UnloadConfig();
                op.tick = tick;
                op.fileIdentifier = fileIdentifier;
                
                boolean ret = addOperation(op); // no fail
                assert ret;
        }
        
        public void actorNew(long tick, int pid, String name, long seed, String ship)
        {
                ActorNew op = new ActorNew();
                op.tick = tick;
                op.pid = pid;
                op.name = name;
                op.seed = seed;
                op.ship = ship;

                boolean ret = addOperation(op);
                assert ret;
        }
        
        public void actorSync(GameOperation.ActorSync sync, long sync_tick_offset)
        {
                assert !this.server;
                
                ActorSync op = new ActorSync();
                op.tick = sync.getTick() - sync_tick_offset;
                op.pid = sync.getPid();
                op.sync = sync;
                op.sync_tick_offset = sync_tick_offset;

                boolean ret = addOperation(op);
                assert ret; // atleast the oldest state must accept the sync
        }
        
        public void actorModification(long tick, int pid, String ship)
        {
                ActorModification op = new ActorModification();
                op.tick = tick;
                op.pid = pid;
                op.ship = ship;

                boolean ret = addOperation(op);
                assert ret;
        }

        public void actorRemove(long tick, int pid)
        {
                ActorRemove op = new ActorRemove();
                op.tick = tick;
                op.pid = pid;

                boolean ret = addOperation(op);
                assert ret;
        }

         /** .
         * 
         * @return false if the operation was too old and has been ignored
         */
        public boolean actorWarp(long tick, int pid, boolean hint, int x, int y, int x_vel, int y_vel, int rotation)
        {
                ActorWarp op = new ActorWarp();
                op.tick = tick;
                op.pid = pid;
                op.hint = hint;
                op.warp = new PhysicsWarp(x, y, x_vel, y_vel, rotation);

                return addOperation(op);
        }

        /** .
         * 
         * @return false if the operation was too old and has been ignored
         */
        public boolean actorWarp(
                long tick, int pid, boolean hint, int x, int y, int x_vel, int y_vel, int rotation, boolean has_x, boolean has_y, boolean has_x_vel, boolean has_y_vel, boolean has_rotation)
        {
                ActorWarp op = new ActorWarp();
                op.tick = tick;
                op.pid = pid;
                op.hint = hint;
                op.warp = new PhysicsWarp(
                        has_x ? x : null, 
                        has_y ? y : null, 
                        has_x_vel ? x_vel : null, 
                        has_y_vel ? y_vel : null, 
                        has_rotation ? rotation : null);

                return addOperation(op);
        }
        
        /** .
         * 
         * @return false if the operation was too old and has been ignored
         */
        public boolean actorMove(long tick, int pid, PhysicsMovement move)
        {
                ActorMove op = new ActorMove();
                op.tick = tick;
                op.pid = pid;
                op.move = move;

                return addOperation(op);
        }
        
        public boolean actorWeapon(
                long tick, int pid, WEAPON_SLOT weapon_slot,
                boolean hint_set, 
                int hint_x, int hint_y, 
                int hint_x_vel, int hint_y_vel, 
                int hint_snapped_rotation)
        {
                ActorWeaponFire op = new ActorWeaponFire();
                op.tick = tick;
                op.pid = pid;
                op.weapon_slot = weapon_slot;
                if (hint_set)
                {
                        op.hint_set = hint_set;
                        op.hint_x = hint_x;
                        op.hint_y = hint_y;
                        op.hint_x_vel = hint_x_vel;
                        op.hint_y_vel = hint_y_vel;
                        op.hint_snapped_rot = hint_snapped_rotation;
                }
                
                return addOperation(op);
        }
        
        public boolean weaponSync(long tick, 
                int owner_pid, 
                String weaponKey, 
                GameOperation.WeaponSync.Projectile[] projectiles, 
                long projectiles_tick_offset)
        {
                assert !this.server;
                // do not modify "projectiles" after calling this method
                
                WeaponSync op = new WeaponSync();
                op.tick = tick;
                op.pid = owner_pid;
                op.weaponKey = weaponKey;
                op.projectiles = projectiles;
                op.projectiles_tick_offset = projectiles_tick_offset;
                return addOperation(op);
        }
        
        

        /**
         * @return the map
         */
        public PhysicsMap getMap()
        {
                return map;
        }
        
        /** Register the occurrence of an event. 
         * This should only be called by physics.
         * @param event 
         */
        public void addEvent(Event event)
        {
                if (event.added) { return; }
                event.added = true;
                eventHistory.append(event.link);
        }

        /** Return the next event that has occured. 
         * This event will never be returned again.
         * If this method is not called regurarly you may miss events because events
         * are discarded after haven been executed in every state. (see Event.isOld())
         * @return 
         */
        public EventPublic nextEvent()
        {
                if (this.eventHistory_lastSeen == null)
                {
                        eventHistory_lastSeen = this.eventHistory.first;
                }
                else
                {
                        if (eventHistory_lastSeen.next == null)
                        {
                                return null;
                        }
                        else
                        {
                                eventHistory_lastSeen = eventHistory_lastSeen.next;
                        }
                }
                
                if (eventHistory_lastSeen == null)
                {
                        return null;
                }
                
                return (EventPublic) eventHistory_lastSeen.data;
        }
        
        /** Iterate over all events that have not yet been discarded.
         * An event may be discarded if it has been executed in every state.
         * (see Event.isOld())
         * @return 
         */
        public Iterator<EventPublic> eventIterator()
        {
                return (Iterator<EventPublic>) (Object) this.eventHistory.iteratorReadOnly();
        }
        
        /** Iterate over all events that have not yet been discarded.
         * An event may be discarded if it has been executed in every state.
         * (see Event.isOld())
         * @return 
         */
        public Iterable<EventPublic> eventIterable()
        {
                return new Iterable<EventPublic>()
                {
                        @Override
                        public Iterator<EventPublic> iterator()
                        {
                                return (Iterator<EventPublic>) (Object) eventHistory.iteratorReadOnly();
                        }
                };
        }
        
        public GCInteger getGlobalConfigInteger(int stateid, String name)
        {
                return trailingStates[stateid].globalConfig.getInteger(name);
        }
        public GCString getGlobalConfigString(int stateid, String name)
        {
                return trailingStates[stateid].globalConfig.getString(name);
        }
        public GCBoolean getGlobalConfigBoolean(int stateid, String name)
        {
                return trailingStates[stateid].globalConfig.getBoolean(name);
        }
        public GCIntegerList getGlobalConfigIntegerList(int stateid, String name)
        {
                return trailingStates[stateid].globalConfig.getIntegerList(name);
        }
        public GCStringList getGlobalConfigStringList(int stateid, String name)
        {
                return trailingStates[stateid].globalConfig.getStringList(name);
        }
        public GCBooleanList getGlobalConfigBooleanList(int stateid, String name)
        {
                return trailingStates[stateid].globalConfig.getBooleanList(name);
        }
        
        public GCImage getGlobalConfigImage(int stateid, String name, ResourceDB db)
        {
                return trailingStates[stateid].globalConfig.getImage(name, db);
        }
}
