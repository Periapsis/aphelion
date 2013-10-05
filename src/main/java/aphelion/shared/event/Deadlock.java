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

import aphelion.client.ErrorDialog;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import aphelion.shared.swissarmyknife.ThreadSafe;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class Deadlock
{
        private static final Logger log = Logger.getLogger("aphelion.eventloop.deadlock");
        private final static CopyOnWriteArrayList<TickedEventLoop> eventLoops = new CopyOnWriteArrayList<>();
        private static DeadlockThread thread;
        private static boolean gui;
        private static DeadLockListener listener;
        
        /** If an event loop has not updated its watchdog counter for this many milliseconds, do something. */
        private static final int CHECK_INTERVAL_MILLIS = 5000;
        
        public static interface DeadLockListener
        {
                /** Called if a deadlock has been detect in an event loop.
                 * At this point the event loop is no longer monitored. 
                 * If the deadlock is resolved by this method, it should call Deadlock.add() to 
                 * re-add the event loop.
                 * If this method returns true the thread is forcefully stopped instead.
                 * @param eventLoop
                 * @param thread
                 * @return True if the thread should be forcefully stopped 
                 */
                boolean deadlockDetected(TickedEventLoop eventLoop, Thread thread);
                
                /** Called after a deadlock has been detected and the thread has been forcefully stopped.
                 * @param eventLoop
                 * @param thread  
                 */
                void deadlockAfterStop(TickedEventLoop eventLoop, Thread thread);
        }
        
        public static void start(boolean showGUI, DeadLockListener deadlockListener)
        {
                gui = showGUI;
                listener = deadlockListener;
                if (thread != null)
                {
                        throw new IllegalStateException();
                }
                thread = new DeadlockThread();
                thread.setDaemon(true);
                thread.start();
        }
        
        public static void stop()
        {
                thread.interrupt();
                thread = null;
        }
        
        @ThreadSafe
        public static void add(TickedEventLoop eventLoop)
        {
                eventLoops.add(eventLoop);
        }

        @ThreadSafe
        public static void remove(TickedEventLoop eventLoop)
        {
                eventLoops.remove(eventLoop);
        }

        private static class DeadlockThread extends Thread
        {
                @Override
                public void run()
                {
                        Iterator<TickedEventLoop> it;
                        TickedEventLoop eventLoop;

                        while (true)
                        {
                                it = eventLoops.iterator();
                                while (it.hasNext())
                                {
                                        eventLoop = it.next();

                                        if (eventLoop.deadlock_tick != eventLoop.deadlock_tick_lastseen)
                                        {
                                                eventLoop.deadlock_tick_lastseen = eventLoop.deadlock_tick;
                                                continue;
                                        }
                                        
                                        eventLoops.remove(eventLoop);
                                        String trace = "";
                                        Thread thread = eventLoop.myThread;

                                        for (StackTraceElement stack : thread.getStackTrace())
                                        {
                                                trace += stack.toString() + "\n";
                                        }

                                        boolean kill = true;

                                        if (listener != null)
                                        {
                                                kill = listener.deadlockDetected(eventLoop, thread);
                                        }

                                        if (kill)
                                        {
                                                log.log(Level.SEVERE, "Deadlock or infinite loop detected in thread {0}, attemping to stop ... Stack trace: \n{1}", 
                                                        new Object[] 
                                                        {
                                                                thread.getName(),
                                                                trace
                                                        }
                                                );
                                                
                                                SwissArmyKnife.logTraceOfAllThreads(log);
                                                
                                                thread.interrupt();

                                                try
                                                {
                                                        Thread.sleep(1000);
                                                }
                                                catch (InterruptedException ex)
                                                {
                                                        return;
                                                }

                                                log.log(Level.SEVERE, "Attemping stop of thread {0}", thread.getName());
                                                thread.stop();

                                                if (listener != null)
                                                {
                                                        listener.deadlockAfterStop(eventLoop, thread);
                                                }

                                                if (gui)
                                                {
                                                        ErrorDialog diag;
                                                        diag = new ErrorDialog();
                                                        diag.setTitle("Deadlock: " + thread.getName());
                                                        diag.setErrorText(trace);
                                                }
                                        }
                                        else
                                        {
                                                log.log(Level.SEVERE, "Deadlock or infinite loop detected in thread {0} (no action). Stack trace: \n{1}", 
                                                        new Object[] {
                                                                thread.getName(),
                                                                trace
                                                });
                                        }
                                }

                                
                                try
                                {
                                        Thread.sleep(CHECK_INTERVAL_MILLIS);
                                }
                                catch (InterruptedException ex)
                                {
                                        return;
                                }
                        }
                }
        }
}
