/*
 * Aphelion
 * Copyright (c) 2014  Joris van der Wel
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
 * different from the original version
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
 * If you modify this library, you may extend this exception to your version
 * of the library, but you are not obliged to do so. If you do not wish to do
 * so, delete this exception statement from your version.
 */

package aphelion.shared.physics;

import aphelion.shared.event.LoopEvent;
import aphelion.shared.event.TickEvent;
import aphelion.shared.event.TickedEventLoop;
import aphelion.shared.gameconfig.*;
import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.events.pub.EventPublic;
import aphelion.shared.physics.operations.*;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.physics.valueobjects.PhysicsWarp;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.ThreadSafe;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;



/**
 * This class runs states in two threads in order to make sure one thread never blocks for long.
 * The first thread tick()s and creates this object will only run a single 
 * state (state 0) that does not perform timewarps on its own.
 * A second thread is spawned by this object that runs multiple states and performs timewarps.
 * After having completed such a timewarp the state 0 of thread 2 is copied into the state 0 of 
 * thread 1.
 * All getter will act upon the first thread. This means game play will continue smoothly, 
 * even during timewarps.
 * @author Joris
 */
public class DualRunnerEnvironment implements TickEvent, LoopEvent, PhysicsEnvironment
{
        private static final Logger log = Logger.getLogger("aphelion.shared.physics");
        private final TickedEventLoop mainLoop;
        private long lastLoopSync;
        private final MyThread thread;
        final SimpleEnvironment environment;
        private final SimpleEnvironment[] envs;
        
        private boolean firstTick = true;
        private final ReentrantLock syncEnvsLock;
        private final AtomicLong needsStateReset = new AtomicLong();
        private long tryStateReset_lock_timeout = 0;
        private static final long TRYSTATERESET_LOCKTIMEOUT_START = 2_000_000L;
        private static final long TRYSTATERESET_LOCKTIMEOUT_PERTICK =  20_000L; // 10ms lock after 5 seconds of trying
        private volatile long env_tickCount = 0;
        private long nextOpSeq = 1;
        private long stateResets = 0;
        
        public final EnvironmentConf econfig_single;
        public final EnvironmentConf econfig_thread;

        public DualRunnerEnvironment(TickedEventLoop loop, PhysicsMap map)
        {
                this.mainLoop = loop;
                environment = new SimpleEnvironment(new EnvironmentConf(true, 1234), map, false);
                thread = new MyThread(new SimpleEnvironment(new EnvironmentConf(false), map, true));
                envs =  new SimpleEnvironment[] {environment, thread.environment};
                
                econfig_single = environment.econfig;
                econfig_thread = thread.environment.econfig;
                
                 // ^true = allow threads to call add operation methods
                syncEnvsLock = new ReentrantLock();
        }

        @ThreadSafe
        @Override
        public EnvironmentConf getConfig()
        {
                return econfig_thread;
        }

        @Override
        public void tick(long tick)
        {
                this.tick();
        }
        
        @Override
        public void loop(long systemNanoTime, long sourceNanoTime)
        {
                if (!firstTick)
                {
                        tryStateReset();

                        if (lastLoopSync == 0)
                        {
                                lastLoopSync = systemNanoTime;
                        }

                        if (systemNanoTime - lastLoopSync > 250_000_000L) // 250ms
                        {
                                try
                                {
                                        if (syncEnvsLock.tryLock(2, TimeUnit.MILLISECONDS))
                                        {
                                                try
                                                {
                                                        doLoopSync();
                                                }
                                                finally
                                                {
                                                        syncEnvsLock.unlock();
                                                }
                                        }
                                }
                                catch (InterruptedException ex)
                                {
                                        Thread.currentThread().interrupt();
                                        return;
                                }
                        }
                }
        }
        
        private void tryStateReset()
        {
                final long stateReset = this.needsStateReset.get();
                
                if (stateReset > 0)
                {
                        try
                        {
                                // try for 2 milliseconds by default
                                // if we do not acquire the lock, start to increase the timeout every tick (see tick())
                                long timeout = tryStateReset_lock_timeout == 0 
                                               ? TRYSTATERESET_LOCKTIMEOUT_START 
                                               : tryStateReset_lock_timeout;
                                
                                if (syncEnvsLock.tryLock(timeout, TimeUnit.NANOSECONDS))
                                {
                                        try
                                        {
                                                tryStateReset_lock_timeout = 0;
                                                resetState();
                                                this.needsStateReset.addAndGet(-stateReset);
                                                doLoopSync();
                                        }
                                        finally
                                        {
                                                syncEnvsLock.unlock();
                                        }
                                }
                                else
                                {
                                        tryStateReset_lock_timeout = TRYSTATERESET_LOCKTIMEOUT_START;
                                }
                        }
                        catch (InterruptedException ex)
                        {
                                Thread.currentThread().interrupt();
                                return;
                        }
                }
        }
        
        private void doLoopSync() // call with lock
        {
                // mainLoop might get synced because of clock sync
                // so also synchronize the loop in the thread.
                // add half a tick length so that the threads can be scheduled better
                if (thread.loop != null)
                {
                        thread.loop.synchronize(mainLoop.currentNano() + EnvironmentConf.TICK_LENGTH * 1_000_000L / 2, mainLoop.currentTick());
                }
                lastLoopSync = System.nanoTime();
        }
        
        
        @Override
        public void tick()
        {
                if (firstTick)
                {
                        firstTick = false;
                        log.log(Level.INFO, "Starting dual runner thread...");
                        
                        thread.createLoop(mainLoop);
                        thread.syncLoop(mainLoop);
                        thread.start();
                }
                
                if (tryStateReset_lock_timeout > 0)
                {
                        tryStateReset_lock_timeout += TRYSTATERESET_LOCKTIMEOUT_PERTICK;
                }
                
                environment.tick();
                env_tickCount = environment.getTick();
                //System.out.printf("%d: %4d => %4d (M)\n", Objects.hashCode(mainLoop), (mainLoop.currentNano()/1_000_000L), env_tickCount);
        }
        
        public void done()
        {
                log.log(Level.INFO, "Stopping dual runner environment...");
                thread.done();
        }
        
        /** Wait until the internal thread has caught up with the loop on the main thread.
         * This is used for test cases
         * 
         * @return The tick the internal thread is now at. 
         *         This is never greater than the tick of the main thread (it should now be equal).
         * @throws java.lang.InterruptedException
         */
        long waitForThreadToCatchup() throws InterruptedException
        {
                while(true)
                {
                        syncEnvsLock.lock();
                        try
                        {
                                if (thread.environment.getTick() >= this.environment.getTick())
                                {
                                        return thread.environment.getTick();
                                }
                        }
                        finally
                        {
                                syncEnvsLock.unlock();
                        }
                        
                        Thread.sleep(1);
                }
        }
        
        /** Wait until all pending operations have been polled.
         * This is used for test cases
         * 
         * @param minCount Wait until this many operations have been polled
         * @return The total amount of operations polled so far
         * @throws java.lang.InterruptedException
         */
        long waitForThreadParsedOperations(long minCount) throws InterruptedException
        {
                while(true)
                {
                        syncEnvsLock.lock();
                        try
                        {
                                long count = thread.environment.polledAddOperationsCount.get();
                                
                                if (count >= minCount)
                                {
                                        return count;
                                }
                        }
                        finally
                        {
                                syncEnvsLock.unlock();
                        }
                        
                        Thread.sleep(1);
                }
        }
        
        long tryResetStateNow() throws InterruptedException
        {
                syncEnvsLock.lock();
                try
                {
                        tryStateReset();
                        return stateResets;
                }
                finally
                {
                        syncEnvsLock.unlock();
                }
        }
        
        private void resetState() // (call with lock held)
        {
                long tickBeforeReset = environment.getTick();

                ++stateResets;
                
                long start = System.nanoTime();
                environment.trailingStates[0].timewarp(thread.environment.trailingStates[0]);
                long end = System.nanoTime();
                
                log.log(Level.WARNING, "Completed dual runner reset in {0}ms", new Object[]{
                        (end - start) / 1_000_000.0,
                });
                
                if (environment.getTick() != tickBeforeReset)
                {
                        throw new AssertionError("timewarp() should tick until we are current again");
                }
        }

        @Override
        public void skipForward(long tick)
        {
                if (thread.isAlive())
                {
                        throw new IllegalStateException("This method may only be used before tick()ing");
                }
                
                environment.skipForward(tick);
                thread.environment.skipForward(tick);
        }

        @Override
        public long getTick()
        {
                return environment.getTick();
        }

        @Override
        public long getTickedAt()
        {
                return environment.getTickedAt();
        }

        @Override
        public ActorPublic getActor(int pid)
        {
                return environment.getActor(pid);
        }

        @Override
        public ActorPublic getActor(int pid, boolean nofail)
        {
                return environment.getActor(pid, nofail);
        }

        @Override
        public Iterator<ActorPublic> actorIterator()
        {
                return environment.actorIterator();
        }

        @Override
        public Iterable<ActorPublic> actorIterable()
        {
                return environment.actorIterable();
        }

        @Override
        public int getActorCount()
        {
                return environment.getActorCount();
        }

        @Override
        public Iterator<ProjectilePublic> projectileIterator()
        {
                return environment.projectileIterator();
        }

        @Override
        public Iterable<ProjectilePublic> projectileIterable()
        {
                return environment.projectileIterable();
        }

        @Override
        public int calculateProjectileCount()
        {
                return environment.calculateProjectileCount();
        }

        @Override
        public ConfigSelection newConfigSelection()
        {
                return environment.newConfigSelection();
        }
        
        @Override
        public PhysicsMap getMap()
        {
                return environment.getMap();
        }

        @Override
        public Iterator<EventPublic> eventIterator()
        {
                return environment.eventIterator();
        }

        @Override
        public Iterable<EventPublic> eventIterable()
        {
                return environment.eventIterable();
        }

        @Override
        public GCInteger getGlobalConfigInteger(String name)
        {
                return environment.getGlobalConfigInteger(name);
        }

        @Override
        public GCString getGlobalConfigString(String name)
        {
                return environment.getGlobalConfigString(name);
        }

        @Override
        public GCBoolean getGlobalConfigBoolean(String name)
        {
                return environment.getGlobalConfigBoolean(name);
        }

        @Override
        public GCIntegerList getGlobalConfigIntegerList(String name)
        {
                return environment.getGlobalConfigIntegerList(name);
        }

        @Override
        public GCStringList getGlobalConfigStringList(String name)
        {
                return environment.getGlobalConfigStringList(name);
        }

        @Override
        public GCBooleanList getGlobalConfigBooleanList(String name)
        {
                return environment.getGlobalConfigBooleanList(name);
        }

        @Override
        public GCImage getGlobalConfigImage(String name, ResourceDB db)
        {
                return environment.getGlobalConfigImage(name, db);
        }
        
        public long getTimewarpCount()
        {
                return thread.timewarpCountLastSeen;
        }
        
        public long getResetCount()
        {
                return this.stateResets;
        }
        
        private OperationKey getNextOperationKey()
        {
                return new OperationKey(nextOpSeq++);
        }
        
        @Override
        @ThreadSafe
        public void loadConfig(long tick, String fileIdentifier, List yamlDocuments)
        {
                OperationKey key = getNextOperationKey();
                for (SimpleEnvironment env : envs)
                {
                        LoadConfig op = new LoadConfig(env, key);
                        op.tick = tick;
                        op.fileIdentifier = fileIdentifier;
                        op.yamlDocuments = yamlDocuments;

                        boolean ret = env.addOperation(op); // no fail
                        assert ret;
                }
        }
        
        @Override
        @ThreadSafe
        public void unloadConfig(long tick, String fileIdentifier)
        {
                OperationKey key = getNextOperationKey();
                for (SimpleEnvironment env : envs)
                {
                        UnloadConfig op = new UnloadConfig(env, key);
                        op.tick = tick;
                        op.fileIdentifier = fileIdentifier;

                        boolean ret = env.addOperation(op); // no fail
                        assert ret;
                }
        }
        
        @Override
        @ThreadSafe
        public void actorNew(long tick, int pid, long seed, String ship)
        {
                OperationKey key = getNextOperationKey();
                for (SimpleEnvironment env : envs)
                {
                        ActorNew op = new ActorNew(env, key);
                        op.tick = tick;
                        op.pid = pid;
                        op.seed = seed;
                        op.ship = ship;

                        boolean ret = env.addOperation(op);
                        assert ret;
                }
        }
        
        @Override
        @ThreadSafe
        public void actorSync(GameOperation.ActorSync sync)
        {
                OperationKey key = getNextOperationKey();
                for (SimpleEnvironment env : envs)
                {
                        assert !env.econfig.server;

                        ActorSync op = new ActorSync(env, key);
                        op.tick = sync.getTick();
                        op.pid = sync.getPid();
                        op.sync = sync;

                        boolean ret = env.addOperation(op);
                        assert ret; // atleast the oldest state must accept the sync
                }
        }
        
        @Override
        @ThreadSafe
        public void actorModification(long tick, int pid, String ship)
        {
                OperationKey key = getNextOperationKey();
                for (SimpleEnvironment env : envs)
                {
                        ActorModification op = new ActorModification(env, key);
                        op.tick = tick;
                        op.pid = pid;
                        op.ship = ship;

                        boolean ret = env.addOperation(op);
                        assert ret;
                }
        }

        @Override
        @ThreadSafe
        public void actorRemove(long tick, int pid)
        {
                OperationKey key = getNextOperationKey();
                for (SimpleEnvironment env : envs)
                {
                        ActorRemove op = new ActorRemove(env, key);
                        op.tick = tick;
                        op.pid = pid;

                        boolean ret = env.addOperation(op);
                        assert ret;
                }
        }

        @Override
        @ThreadSafe
        public boolean actorWarp(long tick, int pid, boolean hint, int x, int y, int x_vel, int y_vel, int rotation)
        {
                OperationKey key = getNextOperationKey();
                boolean okay = true;
                
                for (SimpleEnvironment env : envs)
                {
                        ActorWarp op = new ActorWarp(env, key);
                        op.tick = tick;
                        op.pid = pid;
                        op.hint = hint;
                        op.warp = new PhysicsWarp(x, y, x_vel, y_vel, rotation);

                        okay = env.addOperation(op) && okay;
                }
                
                return okay;
        }

        @Override
        @ThreadSafe
        public boolean actorWarp(
                long tick, int pid, boolean hint, int x, int y, int x_vel, int y_vel, int rotation, boolean has_x, boolean has_y, boolean has_x_vel, boolean has_y_vel, boolean has_rotation)
        {
                OperationKey key = getNextOperationKey();
                boolean okay = true;
                
                for (SimpleEnvironment env : envs)
                {
                        ActorWarp op = new ActorWarp(env, key);
                        op.tick = tick;
                        op.pid = pid;
                        op.hint = hint;
                        op.warp = new PhysicsWarp(
                                has_x ? x : null, 
                                has_y ? y : null, 
                                has_x_vel ? x_vel : null, 
                                has_y_vel ? y_vel : null, 
                                has_rotation ? rotation : null);

                        okay = env.addOperation(op) && okay;
                }
                
                return okay;
        }
        
        @Override
        @ThreadSafe
        public boolean actorMove(long tick, int pid, PhysicsMovement move)
        {
                OperationKey key = getNextOperationKey();
                boolean okay = true;
                
                for (SimpleEnvironment env : envs)
                {
                        ActorMove op = new ActorMove(env, key);
                        op.tick = tick;
                        op.pid = pid;
                        op.move = move;

                        if (move == null)
                        {
                                throw new IllegalArgumentException();
                        }

                        okay = env.addOperation(op) && okay;
                }
                
                return okay;
        }
        
        @Override
        @ThreadSafe
        public boolean actorWeapon(
                long tick, int pid, WEAPON_SLOT weapon_slot,
                boolean hint_set, 
                int hint_x, int hint_y, 
                int hint_x_vel, int hint_y_vel, 
                int hint_snapped_rotation)
        {
                OperationKey key = getNextOperationKey();
                boolean okay = true;
                
                for (SimpleEnvironment env : envs)
                {
                        ActorWeaponFire op = new ActorWeaponFire(env, key);
                        op.tick = tick;
                        op.pid = pid;
                        op.weapon_slot = weapon_slot;

                        if (weapon_slot == null)
                        {
                                throw new IllegalArgumentException();
                        }

                        if (hint_set)
                        {
                                op.hint_set = hint_set;
                                op.hint_x = hint_x;
                                op.hint_y = hint_y;
                                op.hint_x_vel = hint_x_vel;
                                op.hint_y_vel = hint_y_vel;
                                op.hint_snapped_rot = hint_snapped_rotation;
                        }

                        okay = env.addOperation(op) && okay;
                }
                
                return okay;
        }
        
        @Override
        @ThreadSafe
        public boolean actorWeapon(long tick, int pid, WEAPON_SLOT weapon_slot)
        {
                OperationKey key = getNextOperationKey();
                boolean okay = true;
                
                for (SimpleEnvironment env : envs)
                {
                        ActorWeaponFire op = new ActorWeaponFire(env, key);
                        op.tick = tick;
                        op.pid = pid;
                        op.weapon_slot = weapon_slot;

                        if (weapon_slot == null)
                        {
                                throw new IllegalArgumentException();
                        }

                        okay = env.addOperation(op) && okay;
                }
                
                return okay;
        }
        
        @Override
        @ThreadSafe
        public boolean weaponSync(long tick, 
                int owner_pid, 
                String weaponKey, 
                GameOperation.WeaponSync.Projectile[] projectiles,
                long syncKey)
        {
                OperationKey key = getNextOperationKey();
                boolean okay = true;
                
                for (SimpleEnvironment env : envs)
                {
                        assert !env.econfig.server;
                        // do not modify "projectiles" after calling this method

                        WeaponSync op = new WeaponSync(env, key);
                        op.tick = tick;
                        op.pid = owner_pid;
                        op.weaponKey = weaponKey;
                        op.syncProjectiles = projectiles;
                        op.syncKey = syncKey;
                        okay = env.addOperation(op) && okay;
                }
                
                return okay;
        }
        
        private final class MyThread extends Thread implements TickEvent, LoopEvent
        {
                TickedEventLoop loop;
                final SimpleEnvironment environment;
                volatile long timewarpCountLastSeen = 0;

                MyThread(SimpleEnvironment env)
                {
                        this.environment = env;
                        setDaemon(true);
                }
                
                /** Set the loop and sync with it properly.
                 * Do not set this unless you are actually going to start ticking.
                 */
                void createLoop(TickedEventLoop syncWith)
                {
                        if (this.isAlive())
                        {
                                throw new IllegalStateException();
                        }
                        
                        loop = new TickedEventLoop(syncWith, 0);
                        loop.addTickEvent(this);
                        loop.addLoopEvent(this);
                }
                
                @ThreadSafe
                void syncLoop(TickedEventLoop syncWith)
                {
                        syncEnvsLock.lock();
                        try
                        {
                                long syncNano = syncWith.currentNano();
                                // This thread is spawned during the first physics tick.
                                // This means we are 1 tick behind! So substract a full tick lenght.
                                // But running at exactly the same time is not optimal for performance,
                                // we are probably sleeping between ticks, so only substract half a tick.
                                syncNano -= TimeUnit.MILLISECONDS.toNanos(EnvironmentConf.TICK_LENGTH) / 2;
                                this.loop.synchronize(syncNano, syncWith.currentTick());
                        }
                        finally
                        {
                                syncEnvsLock.unlock();
                        }
                }
                
                @Override
                public void run()
                {
                        log.log(Level.INFO, "Started dual runner thread with {0} states", new Object[] {
                                this.environment.getConfig().TRAILING_STATES
                        });
                        setName("DualRunnerEnvironment-"+this.getId());
                        loop.run();
                }
                
                @ThreadSafe
                public void done()
                {
                        if (!this.isAlive())
                        {
                                return;
                        }
                        
                        loop.interrupt();
                        this.interrupt();
                        try
                        {
                                this.join();
                        }
                        catch (InterruptedException ex)
                        {
                                Thread.currentThread().interrupt();
                        }
                }

                @Override
                public void loop(long systemNanoTime, long sourceNanoTime)
                {
                        if (environment.hasThreadedAddOperation())
                        {
                                syncEnvsLock.lock();
                                try
                                {
                                        environment.pollThreadedAddOperation();
                                }
                                finally
                                {
                                        syncEnvsLock.unlock();
                                }
                        }
                }
                
                @Override
                public void tick(long tick)
                {
                        syncEnvsLock.lock();
                        boolean hasLock = true;
                        
                        try
                        {
                                while (environment.getTick() >= DualRunnerEnvironment.this.env_tickCount)
                                {
                                        // We are ticking ahead of the environment in the main thread.
                                        // This is not okay because when the environment in the main thread
                                        // resets to this one, it will be a tick ahead of what it was previously!
                                        // The reverse situation is okay because we can catch up by calling tick() 
                                        // an extra time.

                                        environment.pollThreadedAddOperation();
                                        
                                        hasLock = false;
                                        syncEnvsLock.unlock();
                                        
                                        try
                                        {
                                                Thread.sleep(1);
                                        }
                                        catch (InterruptedException ex)
                                        {
                                                Thread.currentThread().interrupt();
                                                return;
                                        }
                                        
                                        syncEnvsLock.lock();
                                        hasLock = true;
                                }
                                
                                environment.tick();
                                //System.out.printf("%d: %4d => %4d (T)\n", Objects.hashCode(this.loop), (loop.currentNano()/1_000_000L), environment.getTick());
                                if (timewarpCountLastSeen != environment.getTimewarpCount())
                                {
                                        timewarpCountLastSeen = environment.getTimewarpCount();
                                        DualRunnerEnvironment.this.needsStateReset.addAndGet(1);
                                        
                                        log.log(Level.WARNING, "Timewarp just occured: needsStateReset");
                                }
                        }
                        finally
                        {
                                if (hasLock)
                                {
                                        syncEnvsLock.unlock();
                                }
                        }
                }
        }
}
