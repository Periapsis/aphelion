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
package aphelion.shared.event;

import aphelion.shared.event.promise.AbstractPromise;
import aphelion.shared.event.promise.Promise;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import aphelion.shared.swissarmyknife.LinkedListHead;
import aphelion.shared.swissarmyknife.ThreadSafe;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A simple event loop that runs on a fixed interval (ticks). 
 * If a tick suddenly takes too much time to run or if the clock changes, the loop 
 * will catch up to the proper number of iterations.
 * Methods are not safe to call from different threads (unless noted otherwise)
 * @author Joris
 */
public class TickedEventLoop implements Workable, Timerable
{
        public final long TICK; // length of a tick in nanoseconds
        public ClockSource clockSource;
        private long nano = 0;
        private long tick = 0;
        private boolean setup = false;
        private boolean breakdown = false;
        private volatile boolean interrupted = false;
        
        private static final int TASK_QUEUE_SIZE = 32; // per thread
        private static final int COMPLETED_TASK_QUEUE_SIZE = 32;
        
        private long loop_nanoTime;
        private long loop_systemNanoTime;
        
        Thread myThread;
        
        volatile long deadlock_tick = 0; // can be updated as often as desired
        volatile long deadlock_tick_lastseen = -1;
        
        // tick and loop events are not added to or removed frequently
        private ArrayList<TickEvent> tickEvents  = new ArrayList<>(8);
        private ArrayList<LoopEvent> loopEvents  = new ArrayList<>(8);
        
        // Timer events are added and removed frequently
        private LinkedListHead<TimerData> timerEvents = new LinkedListHead<>();
        
        // Tasks that have not yet been started
        private ArrayBlockingQueue<WorkerTask> tasks;
        
        
        /** Tasks that have been completed, will be fired as callbacks the next tick. */
        private ArrayBlockingQueue<TaskCompleteEntry> completedTasks;
        
        /** Same as the completedTasks, however this list is intended to be used by the consumer thread.
          * This is to prevent dead locks
          */
        private LinkedList<TaskCompleteEntry> completedTasks_local; // stuff added by the same thread that is consuming
        
        private WorkerThread[] workerThreads;
        
        /**
         * @param tickLength How long does a tick last in milliseconds
         * @param workerThreads How many worker threads to spawn (used by addWorkerTask). 0 to not spawn any threads
         * @param clockSource An interface providing relative time
         */
        public TickedEventLoop(long tickLength, int workerThreads, ClockSource clockSource)
        {
                int a;
                
                this.clockSource = clockSource;
                if (this.clockSource == null)
                {
                        this.clockSource = new DefaultClockSource();
                }
                
                if (workerThreads > 0)
                {
                        this.tasks = new ArrayBlockingQueue<>(workerThreads * TASK_QUEUE_SIZE); // with 4 threads, queue a maximum of 128 tasks
                        this.completedTasks = new ArrayBlockingQueue<>(COMPLETED_TASK_QUEUE_SIZE); // Fire a maximum of 32 callbacks a time
                        this.completedTasks_local = new LinkedList<>(); // Fire a maximum of 32 callbacks a time
                        this.workerThreads = new WorkerThread[workerThreads];
                }
                
                this.TICK = tickLength * 1000 * 1000;
                
                for (a = 0; a < workerThreads; ++a)
                {
                        this.workerThreads[a] = new WorkerThread(this, tasks, completedTasks);
                        this.workerThreads[a].setDaemon(true);
                }
        }

        /** The nanoTime at which the current loop began.
         * Using clockSource.nanoTime
         * @return nanoseconds
         */
        public long getLoopNanoTime()
        {
                return loop_nanoTime;
        }

        /** The nanoTime at which the current loop began.
         * Using System.nanoTime()
         * @return nanoseconds
         */
        public long getLoopSystemNanoTime()
        {
                return loop_systemNanoTime;
        }
        
        /** Replace the current clock source with a new one.
         * Any difference between the new and old clock source is corrected 
         * so that no extra ticks occur.
         * @param clockSource 
         */
        public void setClockSource(ClockSource clockSource)
        {
                long oldTime = this.clockSource.nanoTime();
                long newTime = clockSource.nanoTime();
                
                nano += newTime - oldTime;
                
                this.clockSource = clockSource;
        }
        
        /** When this EventLoop is part of another loop, use this method before starting the loop */
        public void setup()
        {
                int a;
                assert !setup;
                
                myThread = Thread.currentThread();
                
                Deadlock.add(this);
                
                if (workerThreads != null)
                {
                        for (a = 0; a < workerThreads.length; ++a)
                        {
                                workerThreads[a].start();
                        }
                }
                
                setup = true;
                interrupted = false;
                nano = nanoTime();
                tick = 0;
                
                breakdown = false;
        }
        
        /** When this EventLoop is part of another loop, use this method after ending the loop */
        public void breakdown()
        {
                int a;
                assert setup;
                assert !breakdown;
                
                Deadlock.remove(this);
                
                breakdown = true;
                setup = false;
                interrupted = true;
                
                if (workerThreads != null)
                {
                        for (a = 0; a < workerThreads.length; ++a)
                        {
                                workerThreads[a].interrupt();
                        }
                
                
                        for (a = 0; a < workerThreads.length; ++a)
                        {
                                while (true)
                                {
                                        try
                                        {
                                                workerThreads[a].join();
                                                break;
                                        }
                                        catch (InterruptedException ex)
                                        {
                                        }
                                }
                        }
                }
                
                myThread = null;
        }
        
        /** When this EventLoop is part of another loop, 
         *  call this method on every iteration. 
         *  As often as you want, but preferably at least once per TICK.
         */
        public void loop()
        {
                assert isLoopThreadCurrent();
                loop(false);
        }
        
        @SuppressWarnings("unchecked")
        private void loop(boolean internal)
        {
                assert setup;
                assert !breakdown;
                
                ++deadlock_tick;
                
                if (!internal)
                {
                        // Check for completed work
                        while (true)
                        {
                                TaskCompleteEntry t;
                                
                                t = completedTasks_local.poll();
                                
                                if (t == null)
                                {
                                        t = completedTasks.poll();
                                }
                                
                                if (t == null)
                                {
                                        break;
                                }

                                if (t.task != null)
                                {
                                        if (t.task.error != null)
                                        {
                                                t.task.promise.reject(t.task.error);
                                        }
                                        else
                                        {
                                                t.task.promise.resolve(t.task.ret);
                                        }
                                }

                                if (t.runFromMain != null)
                                {
                                        t.runFromMain.run();
                                }
                                
                                ++deadlock_tick;
                        }
                }
                
                loop_nanoTime = nanoTime();
                loop_systemNanoTime = this.clockSource instanceof DefaultClockSource ? loop_nanoTime : System.nanoTime();
                
                
                for (LoopEvent event : loopEvents)
                {
                        event.loop(loop_systemNanoTime, loop_nanoTime);
                        ++deadlock_tick;
                }
                

                // if nanoTime() wraps, this calculation will still be correct thanks to overflow
                //
                // 32 bit example:
                // Suppose the previous time (nanos) is 2147483547 (Integer.MAX_VALUE)
                // Time = 2147483647:                   2147483647 - 2147483547 = 100
                // Time = 2147483647+1 = -2147483648:  -2147483648 - 2147483547 = 101
                // Time = 2147483647+2 = -2147483647:  -2147483647 - 2147483547 = 102
                long delta = loop_nanoTime - nano;
                while (delta >= TICK)
                {
                        delta -= TICK;
                        nano += TICK;
                        ++tick;
                        tick();
                        ++deadlock_tick;
                }
        }
        
        /** Synchronize the loop so that the tick "tick" would have occurred at tickAtNano.
         * The difference should not be too large.
         * @param tickNano The nano time at which tick should have occurred. 
         * This value is relative to the ClockSource
         * @param tick The tick count at tickAtNano
         */
        public void synchronize(long tickNano, long tick)
        {
                long currentTickTime = this.nano - (this.tick - tick) * TICK;
                this.nano = this.nano - (currentTickTime - tickNano);
                
                // suppose:
                // TICK = 10ms
                // tick 20 occurred at 40'000ms
                // tick 21 occurred at 40'010ms
                // tick 22 occurred at 40'020ms (current tick)
                
                // synchronize(40'050, 20) is called:
                // currentTickTime = 40'020 - (22 - 20) * 10 = 40'000
                // this.nanos = 40'020 - (40'000 - 40'050) = 40070
                // tick 22 is now at 40'070ms
                
        }
        
        /** Blocks until stop() is called */
        @SuppressWarnings("unchecked")
        public void run()
        {
                setup();
                
                while (!interrupted)
                {
                        loop(true);
                        
                        try
                        {
                                // Check for completed work
                                boolean first = true;
                                while (true)
                                {
                                        TaskCompleteEntry t;
                                        
                                        t = completedTasks_local.poll();
                                
                                        if (t == null)
                                        {
                                                if (first)
                                                {
                                                        t = completedTasks.poll();
                                                        first = false;
                                                }
                                                else
                                                {
                                                        // Sleep for 1ms to avoid trashing the cpu
                                                        // This is the only place where we sleep
                                                        t = completedTasks.poll(1, TimeUnit.MILLISECONDS);
                                                }
                                        }
                                        
                                        if (t == null)
                                        {
                                                break;
                                        }
                                        
                                        if (t.task != null)
                                        {
                                                if (t.task.error != null)
                                                {
                                                        t.task.promise.reject(t.task.error);
                                                }
                                                else
                                                {
                                                        t.task.promise.resolve(t.task.ret);
                                                }
                                        }
                                        
                                        if (t.runFromMain != null)
                                        {
                                                t.runFromMain.run();
                                        }
                                }
                        }
                        catch (InterruptedException ex)
                        {
                                interrupt();
                                return;
                        }
                }
                
                breakdown();
        }
        
        @ThreadSafe
        public void interrupt()
        {
                interrupted = true;
        }
        
        /** Has this loop been interrupted?.
         * Unlike Thread.interrupted(), this method has no side effects.
         * @return 
         */
        @ThreadSafe
        public boolean isInterruped()
        {
                return interrupted;
        }
        
        /** The current nanoTime as given by the clock source.
         * This value may change by a large amount if the clock source was just replaced.
         * @return nanotime (10^-9 seconds)
         */
        public long nanoTime()
        {
                return this.clockSource.nanoTime();
        }
        
        /** Returns the nanoTime of the current tick. 
         * If a new clock source was just set, this value may change by a large 
         * amount (even for the same tick).
         * @return nanotime (10^-9 seconds)
         */
        public long currentNano()
        {
                return nano;
        }
        
        public long currentTick()
        {
                return tick;
        }
        
        private void tick()
        {
                LinkedListEntry<TimerData> timerEntry, timerEntryNext;
                TimerData timer;
                long now;
                
                now = currentTick();
                
                // Tick events
                
                for (TickEvent event : tickEvents)
                {
                        event.tick(now);
                }
                
                
                // Timers
                timerEntry = timerEvents.first;
                while (timerEntry != null)
                {
                        timerEntryNext = timerEntry.next;
                        timer = timerEntry.data;
                        
                        if (now >= timer.nextRun)
                        {
                                timer.nextRun = now + timer.interval;
                                if (!timer.event.timerElapsed(now))
                                {
                                        timerEntry.remove();
                                        continue;
                                }
                        }
                        
                        timerEntry = timerEntryNext;
                }
        }
        
        /** Adds an event that will be fired every tick.
         * @param event 
         */
        public void addTickEvent(TickEvent event)
        {
                if (event == null)
                {
                        throw new IllegalArgumentException();
                }
                
                tickEvents.add(event);
        }
        
        /** Adds an event that will be fired every tick.
         * @param events
         */
        public void addTickEvent(TickEvent[] events)
        {
                for (TickEvent event : events)
                {
                        addTickEvent(event);
                }
        }
        
        /** Adds an event that will be fired every tick before any event that has already been registered.
         * @param event 
         */
        public void prependTickEvent(TickEvent event)
        {
                if (event == null)
                {
                        throw new IllegalArgumentException();
                }
                tickEvents.add(0, event);
        }
        
        /** Adds an event that will be fired every tick before any event that has already been registered.
         * @param events
         */
        public void prependTickEvent(TickEvent[] events)
        {
                for (TickEvent event : events)
                {
                        prependTickEvent(event);
                }
        }
        
        /** Removes a tick event that was previously added.
         * @param event 
         * @return true if the tick event was present
         */
        public boolean removeTickEvent(TickEvent event)
        {
                for (int a = tickEvents.size()-1; a >= 0; --a)
                {
                        if (tickEvents.get(a) == event)
                        {
                                tickEvents.remove(a);
                                return true;
                        }
                }
                return false;
        }
        
        /** Removes a tick event that was previously added.
         * @param events 
         */
        public void removeTickEvent(TickEvent[] events)
        {
                for (TickEvent event : events)
                {
                        removeTickEvent(event);
                }
        }
        
        /** Adds an event that will be fired every loop.
         * @param event 
         */
        public void addLoopEvent(LoopEvent event)
        {
                if (event == null)
                {
                        throw new IllegalArgumentException();
                }
                loopEvents.add(event);
                
        }
        
        /** Adds an event that will be fired every loop.
         * @param events
         */
        public void addLoopEvent(LoopEvent[] events)
        {
                for (LoopEvent event : events)
                {
                        addLoopEvent(event);
                }
        }
        
        /** Removes a loop event that was previously added.
         * @param event
         * @return True if the tick event was present.
         */
        public boolean removeLoopEvent(LoopEvent event)
        {
                for (int a = loopEvents.size()-1; a >= 0; --a)
                {
                        if (loopEvents.get(a) == event)
                        {
                                loopEvents.remove(a);
                                return true;
                        }
                }
                return false;
        }
        
        /** Removes a loop event that was previously added.
         * @param events
         */
        public void removeLoopEvent(LoopEvent[] events)
        {
                for (LoopEvent event : events)
                {
                        removeLoopEvent(event);
                }
        }
        
        /** Adds an event that will be fired every X ticks.
         * @param interval The interval in ticks
         * @param event The callback to fire
         */
        @Override
        public void addTimerEvent(long interval, TimerEvent event)
        {
                TimerData timer;
                timer = new TimerData();
                timer.event = event;
                timer.interval = interval;
                timer.nextRun = currentTick() + interval;
                timerEvents.appendData(timer);
        }
        
        /** Removes a timer event that was previously added.
         * @param event
         * @return true if the timer event was present
         */
        @Override
        public boolean removeTimerEvent(TimerEvent event)
        {
                LinkedListEntry<TimerData> entry;
                
                entry = timerEvents.first;
                while (entry != null)
                {
                        if (entry.data.event == event)
                        {
                                entry.remove();
                                return true;
                        }
                        
                        entry = entry.next;
                }
                
                return false;
        }
        
        @ThreadSafe
        public boolean isLoopThreadCurrent()
        {
                return myThread.equals(Thread.currentThread());
        }
        
        private static class TimerData
        {
                TimerEvent event;
                long nextRun;
                long interval; // in ticks
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public AbstractPromise addWorkerTask(WorkerTask task, Object argument) throws IllegalArgumentException, IllegalStateException
        {
                if (workerThreads == null || workerThreads.length <= 0)
                {
                        throw new IllegalArgumentException("There are no worker threads present");
                }
                
                task.argument = argument;
                task.promise = new Promise(this);
                tasks.add(task); // throws an exception if full
                return task.promise;
        }
        
        @ThreadSafe
        @Override
        public void runOnMain(Runnable runnable)
        {
                addCompletedTask(new TaskCompleteEntry(runnable));
        }
        
        @ThreadSafe
        @Override
        public void taskCompleted(WorkerTask task)
        {
                addCompletedTask(new TaskCompleteEntry(task));
        }
        
        @ThreadSafe
        private void addCompletedTask(TaskCompleteEntry t)
        {
                // myThread is the one consuming.
                // So do not using a blocking queue, this would cause a dead lock
                
                if (isLoopThreadCurrent())
                {
                        completedTasks_local.add(t);
                }
                else
                {
                        try
                        {
                                while (!completedTasks.offer(t, 1, TimeUnit.MILLISECONDS));
                        }
                        catch (InterruptedException ex)
                        {
                                Thread.currentThread().interrupt();
                        }
                }
        }
        
        
        
        static final class TaskCompleteEntry
        {
                final WorkerTask task;
                final Runnable runFromMain;

                TaskCompleteEntry(WorkerTask task)
                {
                        this.task = task;
                        this.runFromMain = null;
                }
                
                TaskCompleteEntry(Runnable runFromMain)
                {
                        this.task = null;
                        this.runFromMain = runFromMain;
                }
        }
}
