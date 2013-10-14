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

import aphelion.shared.gameconfig.ConfigSelection;
import aphelion.shared.gameconfig.GameConfig;
import aphelion.shared.physics.entities.Projectile;
import aphelion.shared.physics.entities.Actor;
import aphelion.shared.physics.events.Event;
import aphelion.shared.physics.events.pub.ProjectileExplosionPublic.EXPLODE_REASON;
import aphelion.shared.physics.operations.Operation;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import aphelion.shared.swissarmyknife.LinkedListHead;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class State
{
        private static final Logger log = Logger.getLogger("aphelion.shared.physics");
        private static final int ACTOR_INITIALCAPACITY = 64;
        public final int id;
        public final PhysicsEnvironment env;
        public final Collision collision = new Collision();
        
        /** how far (ticks) in the past is this state? */
        public final long delay; 
        
        /** Use operation hints in this state? (such as the x,y,x_vel,y_vel hint in the weapon fire packet ) */
        public final boolean allowHints;
        public final boolean isLast;
        public long tick_now;
        public boolean needTimewarpToThisState;
        
        public final TIntObjectHashMap<Actor> actors = new TIntObjectHashMap<>(ACTOR_INITIALCAPACITY); // actor id -> entity
        public final ArrayList<Actor> actorsList = new ArrayList<>(ACTOR_INITIALCAPACITY);
        public final LinkedList<Actor> actorsRemovedDuringReset = new LinkedList<>();
        
        public LinkedListHead<Projectile> projectiles = new LinkedListHead<>();
        public final LinkedListHead<Operation> history = new LinkedListHead<>(); // ordered by tick ascending
        public final LinkedListHead<Operation> todo = new LinkedListHead<>(); // ordered by tick ascending
        
        public final GameConfig config = new GameConfig();
        public final ConfigSelection globalConfig = config.newSelection();
        public long config_lastModification = 0; // edge case: 0 is used as a "not set" value here. however 0 is also a valid tick
        public HashSet<Integer> unknownActorRemove = new HashSet<>(); // Assumes PIDs are unique. ActorNew is never called twice with the same PID.
        
        State(PhysicsEnvironment env, int state_id, long delay, boolean allowHints, boolean isLast)
        {
                this.env = env;
                this.id = state_id;
                this.delay = delay;
                this.tick_now = -delay;
                this.allowHints = allowHints;
                this.isLast = isLast;
        }

        private void tickActors()
        {
                TIntObjectIterator<Actor> itActor = this.actors.iterator();
                while (itActor.hasNext())
                {
                        itActor.advance();

                        Actor actor = itActor.value();
                        actor.updatePositionHistory(tick_now);
                        if (actor.moveHistory.getMostRecentTick() < this.tick_now)
                        {
                                // make sure old moves can be cleaned up (and that getRelative always matches)
                                actor.moveHistory.setHistory(tick_now, null);
                        }
                        
                        if (actor.isRemoved())
                        {
                                // Can we really remove the actor now?
                                if (env.tick_now - actor.removedAt_tick > env.TRAILING_STATES * PhysicsEnvironment.TRAILING_STATE_DELAY)
                                {
                                        boolean activeSomewhere = false;
                                        for (int s = 0; s < actor.crossStateList.length; ++s)
                                        {
                                                if (actor.crossStateList[s] != null && !actor.crossStateList[s].isRemoved())
                                                {
                                                        activeSomewhere = true;
                                                        break;
                                                }
                                        }

                                        if (!activeSomewhere)
                                        {
                                                // really remove it now
                                                for (int s = 0; s < env.TRAILING_STATES; ++s)
                                                {
                                                        State state = env.trailingStates[s];

                                                        if (state == this)
                                                        {
                                                                itActor.remove();
                                                                boolean removed = state.actorsList.remove(actor);
                                                                assert removed;
                                                        }
                                                        else
                                                        {
                                                                Actor stateActor = state.actors.remove(actor.pid);
                                                                if (stateActor != null)
                                                                {
                                                                        boolean removed = state.actorsList.remove(stateActor);
                                                                        assert removed;
                                                                        state.actors.compact();
                                                                }
                                                        }

                                                        actor.crossStateList[s] = null;
                                                }
                                        }
                                }
                                
                                continue; // skip dead reckoning
                        }
                        
                        if (actor.isDead(tick_now))
                        {
                                if (this.tick_now >= actor.spawnAt_tick)
                                {
                                        assert this.tick_now == actor.spawnAt_tick;
                                        PhysicsPoint spawn = new PhysicsPoint();
                                        actor.dead.setAbsoluteValue(0, actor.spawnAt_tick, 0);
                                        
                                        if (actor.empUntil_tick != null && actor.empUntil_tick >= actor.spawnAt_tick)
                                        {
                                                actor.empUntil_tick = actor.spawnAt_tick - 1;
                                        }
                                        
                                        actor.findSpawnPoint(spawn, actor.spawnAt_tick);
                                        spawn.multiply(PhysicsMap.TILE_PIXELS);
                                        spawn.add(PhysicsMap.TILE_PIXELS / 2);
                                        
                                        actor.pos.pos.set(spawn);
                                        actor.pos.vel.set(0, 0);
                                        actor.rot.points = actor.randomRotation(actor.spawnAt_tick);
                                        actor.rot.snapped = PhysicsMath.snapRotation(actor.rot.points, actor.rotationPoints.get());
                                        
                                        actor.energy.addAbsoluteValue(Actor.ENERGY_SETTER.OTHER.id, this.tick_now, actor.getMaxEnergy());
                                        
                                        actor.updatePositionHistory(tick_now);
                                }
                                
                                continue; // skip dead reckoning
                        }
                        
                        actor.performDeadReckoning(env.getMap(), tick_now, 1);
                }
        }
        
        private void tickProjectiles()
        {
                LinkedListEntry<Projectile> linkProjectile_next;
                for (LinkedListEntry<Projectile> linkProjectile = this.projectiles.first; linkProjectile != null; linkProjectile = linkProjectile_next)
                {
                        Projectile projectile = linkProjectile.data;
                        linkProjectile_next = linkProjectile.next;

                        projectile.updatePositionHistory(tick_now);
                        
                        // do not bother if the projectile index is not 0
                        // in this case, it has already been checked (thanks to combined)
                        if (projectile.isRemoved())
                        {
                                // NOTE: this removal checking has been optimized to turn 
                                // "n * (n-1)" checks into "n" checks (in the case of coupled 
                                // projectiles). The downside is that it is a bit harder to 
                                // understand.
                                
                                // Can we really remove the projectile now?
                                // do not bother if the projectile index is not 0, 
                                // it has already been checked
                                if (projectile.projectile_index == 0
                                    && env.tick_now - projectile.removedAt_tick > env.TRAILING_STATES * PhysicsEnvironment.TRAILING_STATE_DELAY)
                                {
                                        boolean activeSomewhere = false;

                                        ACTIVE_CHECK: for (Projectile coupledProjectile : projectile.coupled)
                                        {
                                                for (int s = 0; s < coupledProjectile.crossStateList.length; ++s)
                                                {
                                                        if (coupledProjectile.crossStateList[s] != null 
                                                                && !coupledProjectile.crossStateList[s].isRemoved())
                                                        {
                                                                activeSomewhere = true;
                                                                break ACTIVE_CHECK;
                                                        }
                                                }
                                        }

                                        if (!activeSomewhere)
                                        {
                                                // really remove it now
                                                for (Projectile coupledProjectile : projectile.coupled)
                                                {
                                                        if (linkProjectile_next == coupledProjectile.projectileListLink_state)
                                                        {
                                                                // make sure we do not break the loop
                                                                linkProjectile_next = linkProjectile_next.next;
                                                        }
                                                        
                                                        for (int s = 0; s < coupledProjectile.crossStateList.length; ++s)
                                                        {
                                                                if (coupledProjectile.crossStateList[s] != null)
                                                                {
                                                                        Projectile stateProj = ((Projectile) coupledProjectile.crossStateList[s]);
                                                                        stateProj.hardRemove(tick_now);

                                                                        // NOTE: hardRemove modifies coupled, the list we are iterating over.
                                                                        // However the iterator of LinkedListEntry supports removing 
                                                                        // the current object we are iterating over (but only the current).
                                                                }
                                                        }
                                                }
                                        }
                                }
                                
                                
                                continue; // do not perform dead reckoning etc for removed projectiles
                        }
                        
                        // at this point the projectile is _not_ removed
                        
                        // keep the projectile stored everywhere until it has been removed in every state
                        // this way you can figure out when it was removed etc                                

                        if (tick_now >= projectile.expires_at_tick)
                        {
                                projectile.explodeWithoutHit(tick_now, EXPLODE_REASON.EXPIRATION);
                                projectile.softRemove(tick_now);
                                continue;
                        }

                        projectile.performDeadReckoning(env.getMap(), tick_now, 1);
                }
        }
        
        private void tickOperations(boolean lateSync)
        {
                LinkedListEntry<Operation> linkOp = this.todo.first;
                while (linkOp != null) // go over all todo's with op.tick <= tick_now
                {
                        assert linkOp.head == this.todo;
                        Operation op = linkOp.data;
                        if (op.tick > tick_now)
                        {
                                break; // the todo (and history) lists are ordered. So there is nothing to-do anymore
                        }

                        if (op.isLateSyncOperation() != lateSync)
                        {
                                linkOp = linkOp.next;
                                continue;
                        }
                        
                        LinkedListEntry<Operation> nextOp = linkOp.next;

                        long late = tick_now - op.tick;
                        if (!op.execute(this, late))
                        {
                                needTimewarpToThisState = true;
                                
                                log.log(Level.WARNING, "{0}: Inconsistency in queued operation {1}. (lateSync {2})", new Object[]{
                                        env.server ? "Server" : "Client",
                                        op.getClass().getName(),
                                        lateSync
                                });
                        }
                        operationToHistory(op); // done

                        
                        linkOp = nextOp;
                }
        }
        
        
        private void tickActorsLate()
        {
                TIntObjectIterator<Actor> itActor = this.actors.iterator();
                while (itActor.hasNext())
                {
                        itActor.advance();

                        Actor actor = itActor.value();
                        actor.tickEnergy();
                }
        }
        
        void tick(long tick_now)
        {
                assert this.tick_now == tick_now - 1;
                this.tick_now = tick_now;

                // 1. Dead reckoning
                // 2. Actor cleanup
                // 3. Respawning (executed before any operations so 
                //    that the server may override the spawn location by sending an
                //    ActorWarp at the same tick as the respawn)
                tickActors();
                
                
                tickProjectiles();
                tickOperations(false);
                tickActorsLate();
                config.tick(tick_now); // used for cleanup
                tickOperations(true); // sync operations
        }
        
        /** Lookup an actor that was removed as part of the current timewarp.
         * This list is cleared after the timewarp has been completed.
         * If an actor is recreated as part of a timewarp, a new actor object 
         * should not be created.
         * Otherwide references (external to physics) might break.
         * The method used here works properly because PIDs may not be reused.
         * @param pid
         * @return  
         */
        public Actor getActorRemovedDuringReset(int pid)
        {
                for (Actor removedActor : this.actorsRemovedDuringReset)
                {
                        if (removedActor.pid == pid)
                        {
                                return removedActor;       
                        }
                }
                
                return null;
        }
        
        public void timewarp(State older)
        {
                long wasTick = this.tick_now;

                this.resetTo(older);

                long tick = this.tick_now + 1;
                while (tick <= wasTick)
                {
                        this.tick(tick);
                        tick++;
                }

                this.actorsRemovedDuringReset.clear();

                assert this.tick_now == wasTick;
        }

        /**
         * Copy everything from the other state (Actors, Projectiles, config, etc)
         * (but not operations) to this one.
         */
        @SuppressWarnings("unchecked")
        private void resetTo(State older)
        {
                assert older.tick_now <= this.tick_now;
                this.tick_now = older.tick_now;

                if (config_lastModification != older.config_lastModification)
                {
                        // A config change is very rare, so do not waste time if it has not been modified.
                        config.resetTo(older.config);
                        config_lastModification = older.config_lastModification;
                        config.applyChanges();
                }
                
                // Reset actors
                {
                        TIntObjectIterator<Actor> actorIt = this.actors.iterator();
                        while (actorIt.hasNext())
                        {
                                actorIt.advance();

                                Actor actorMine = actorIt.value();
                                Actor actorOther = older.actors.get(actorIt.key());

                                // actor in my state does not exist in the other state, so remove it
                                if (actorOther == null)
                                {
                                        actorIt.remove();
                                        boolean removed = this.actorsList.remove(actorMine);
                                        assert removed;
                                        this.actorsRemovedDuringReset.add(actorMine);
                                        continue;
                                }

                                actorMine.resetTo(this, actorOther);
                        }
                }

                // reset actors that are in the other state, but not in mine
                if (this.actors.size() != older.actors.size())
                {
                        // NOTE: At the moment this branch never executes.
                        // The only way for this condition to occur is when
                        // ActorNew is able to execute in state 1, but not in state 0.
                        // Unlike ActorWeapon & projectiles there is no way for this
                        // to occur.
                        // Please make a test case for this branch if this condition
                        // ever becomes possible (for example when a weapon launches
                        // a fake player, and the firing of that weapon depends on energy
                        // or a fire delay).
                        
                        TIntObjectIterator<Actor> actorIt = older.actors.iterator();
                        while (actorIt.hasNext())
                        {
                                actorIt.advance();

                                Actor actorOther = actorIt.value();
                                Actor actorMine = this.actors.get(actorIt.key());

                                // actor that is in the other state, does not exist in mine
                                if (actorMine == null)
                                {
                                        actorMine = this.getActorRemovedDuringReset(actorOther.pid);
                
                                        if (actorMine == null)
                                        {
                                                actorMine = new Actor(this, actorOther.crossStateList, actorOther.pid, actorOther.createdAt_tick);
                                        }
                                        
                                        actorMine.resetTo(this, actorOther);
                                }
                        }
                }

                this.actors.compact();

                // Reset projectiles
                {
                        LinkedListHead<Projectile> oldProjectiles = this.projectiles;
                        this.projectiles = new LinkedListHead<>();

                        // loop over all the projectiles in the state we are resetting to
                        LinkedListEntry<Projectile> entry = older.projectiles.first;
                        while (entry != null)
                        {
                                Projectile projectileOther = entry.data;
                                Projectile projectileMine = (Projectile) projectileOther.crossStateList[this.id];
                                if (projectileMine == null)
                                {
                                        // pretend that the owner is null for now, resetTo() will resolve this
                                        projectileMine = new Projectile(this, projectileOther.crossStateList, null, projectileOther.createdAt_tick, null, projectileOther.projectile_index);
                                }
                                else
                                {
                                        // remove from the old list (oldProjectiles) so that 
                                        // the old list contains all projectiles that are not in "other" when we are done looping
                                        projectileMine.projectileListLink_state.remove();
                                }
                                
                                this.projectiles.append(projectileMine.projectileListLink_state);
                                projectileMine.resetTo(this, projectileOther);
                                
                                entry = entry.next;
                        }
                        
                        // these are all the projectiles that are not in the other state
                        entry = oldProjectiles.first;
                        while (entry != null)
                        {
                                LinkedListEntry<Projectile> next = entry.next;
                                entry.data.hardRemove(tick_now);
                                entry = next;
                        }
                        
                        
                        if (SwissArmyKnife.assertEnabled)
                        {
                                for (Projectile p : this.projectiles)
                                {
                                        p.coupled.assertCircularConsistency();
                                }
                        }
                }
                
                {
                        LinkedListEntry<Operation> linkStart = null;
                        LinkedListEntry<Operation> linkEnd = this.history.last;

                        // find the first operation that should now be in the todo
                        
                        for (LinkedListEntry<Operation> linkOp = this.history.first; 
                             linkOp != null;
                             linkOp = linkOp.next
                        )
                        {
                                if (linkOp.data.tick > older.tick_now)
                                {
                                        linkStart = linkOp;
                                        break;
                                }
                                else
                                {
                                        // reset the execution history for operations that are
                                        // in the history for both states
                                        linkOp.data.resetExecutionHistory(this, older);
                                }
                        }

                        if (linkStart != null)
                        {
                                this.todo.prependForeignRange(linkStart, linkEnd);
                                
                                
                                // reset the execution history for operations that are now
                                // in the todo list for the newer state
                                for (LinkedListEntry<Operation> linkOp = linkStart; 
                                        true;
                                        linkOp = linkOp.next
                                   )
                                {
                                        linkOp.data.resetExecutionHistory(this, older);
                                        
                                        if (linkOp == linkEnd)
                                        {
                                                break;
                                        }
                                }
                                
                        }

                        todo.assertConsistency();
                        history.assertConsistency();
                }
                
                // reset the event history
                for (LinkedListEntry<Event> linkEv = env.eventHistory.first, linkNext = null;
                     linkEv != null; 
                     linkEv = linkNext)
                {
                        linkNext = linkEv.next;
                        
                        Event event = linkEv.data;
                        assert event.link == linkEv;
                        event.resetExecutionHistory(this, older);
                        
                        boolean occuredSomewhere = false;
                        for (int s = 0; s < env.TRAILING_STATES; ++s)
                        {
                                if (event.hasOccured(s))
                                {
                                        occuredSomewhere = true;
                                        break;
                                }
                        }
                        
                        if (!occuredSomewhere)
                        {
                                linkEv.remove();
                                event.inEnvList = false;
                        }
                }
                
                unknownActorRemove = (HashSet<Integer>) older.unknownActorRemove.clone();
        }

        void addOperation(Operation op)
        {
                LinkedListEntry<Operation> opLink, link;

                opLink = op.link[this.id];
                assert opLink.head == null;
                assert opLink.previous == null;
                assert opLink.next == null;
                
                if (op.tick <= this.tick_now) // late operation
                {
                        if (!op.execute(this, this.tick_now - op.tick))
                        {
                                log.log(Level.WARNING, "{0}: Inconsistency in late operation {1}", new Object[]{
                                        env.server ? "Server" : "Client",
                                        op.getClass().getName()
                                });
                                needTimewarpToThisState = true;
                        }
                        operationToHistory(op);
                        return;
                }

                link = this.todo.last;

                while (link != null)
                {
                        // If 2 operations occur on the same time, 
                        // use the priority to determine which takes precedence 
                        // (lower priority is executed first)
                        
                        // if the priority is the same, the first to be 
                        // added takes precedence

                        
                        if (link.data.tick < op.tick || 
                            (link.data.tick == op.tick && link.data.comparePriority(op) <= 0)
                           )
                        {
                                link.append(opLink);
                                
                                todo.assertConsistency();
                                history.assertConsistency();
                                return;
                        }

                        link = link.previous;
                }
                
                // went through the entire todo list, todo list is either empty or this operation is the oldest
                this.todo.prepend(opLink);
                
                todo.assertConsistency();
                history.assertConsistency();
        }

        private void operationToHistory(Operation op)
        {
                LinkedListEntry<Operation> opLink, historyLink;

                opLink = op.link[this.id];

                historyLink = this.history.last;
                while (historyLink != null)
                {
                        if (historyLink.data.tick < op.tick || 
                            (historyLink.data.tick == op.tick && historyLink.data.comparePriority(op) <= 0)
                           )
                        {
                                historyLink.appendForeignRange(opLink, opLink);
                                
                                todo.assertConsistency();
                                history.assertConsistency();
                                return;
                        }
                        historyLink = historyLink.previous;
                        
                }

                history.prependForeignRange(opLink, opLink);
                
                todo.assertConsistency();
                history.assertConsistency();
        }
        
        @Override public String toString()
        {
                return this.getClass().getSimpleName() + ": " + this.id;
        }
        
        private boolean isPointFree(int tileX, int tileY, int freeRadius)
        {
                for (int x = tileX - freeRadius; x <= tileX + freeRadius; ++x)
                {
                        for (int y = tileY - freeRadius; y <= tileY + freeRadius; ++y)
                        {
                                if (env.getMap().physicsIsSolid(x, y))
                                {
                                        return false;
                                }
                        }
                }
                return true;
        }
        
        public void findRandomPointOnMap(
                PhysicsPoint result, 
                long tick, long seed,
                int tileX, int tileY, int radius, 
                int freeRadius)
        {
                int minX = SwissArmyKnife.clip(tileX - radius, 
                        env.getMap().physicsGetMapLimitMinimum(), 
                        env.getMap().physicsGetMapLimitMaximum());
                
                int maxX = SwissArmyKnife.clip(tileX + radius, 
                        env.getMap().physicsGetMapLimitMinimum(), 
                        env.getMap().physicsGetMapLimitMaximum());
                
                int diffX = maxX - minX + 1;
                
                int minY = SwissArmyKnife.clip(tileY - radius, 
                        env.getMap().physicsGetMapLimitMinimum(), 
                        env.getMap().physicsGetMapLimitMaximum());
                
                int maxY = SwissArmyKnife.clip(tileY + radius, 
                        env.getMap().physicsGetMapLimitMinimum(), 
                        env.getMap().physicsGetMapLimitMaximum());
                
                int diffY = maxY - minY + 1;
                
                int seedHigh = (int) (seed >> 32); // high bits
                int seedLow = (int) seed; // low bits
                int lowTick = (int) tick; // low bits
                
                int x, y;
                
                int i = 0;
                do
                {
                        x = (Math.abs(SwissArmyKnife.jenkinMix(seedHigh, lowTick, i)) % diffX) + minX;
                        y = (Math.abs(SwissArmyKnife.jenkinMix(seedLow, lowTick, i)) % diffY) + minY;
                        
                        i += 2;
                        if (i == 5000)
                        {
                                result.set(tileX, tileY);
                                return;
                        }
                }
                while (!isPointFree(x, y, freeRadius));
                
                result.set(x, y);
        }
}
