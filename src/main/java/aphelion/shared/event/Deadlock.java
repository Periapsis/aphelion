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
import aphelion.shared.swissarmyknife.ThreadSafe;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class Deadlock
{
        private static final Logger log = Logger.getLogger("aphelion.eventloop.deadlock");
        private final static LinkedList<TickedEventLoop> eventLoops = new LinkedList<>();
        private static DeadlockThread thread;
        private static boolean gui;
        
        public static void start(boolean showGUI)
        {
                gui = showGUI;
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
                synchronized (eventLoops)
                {
                        eventLoops.add(eventLoop);
                }
        }

        @ThreadSafe
        public static void remove(TickedEventLoop eventLoop)
        {
                synchronized (eventLoops)
                {
                        eventLoops.remove(eventLoop);
                }
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
                                synchronized (eventLoops)
                                {
                                        it = eventLoops.iterator();
                                        while (it.hasNext())
                                        {
                                                eventLoop = it.next();

                                                if (eventLoop.deadlock_tick == eventLoop.deadlock_tick_lastseen)
                                                {
                                                        it.remove();
                                                        String trace = "";
                                                        Thread thread = eventLoop.myThread;
                                                        
                                                        for (StackTraceElement stack : thread.getStackTrace())
                                                        {
                                                                trace += stack.toString() + "\n";
                                                        }
                                                        
                                                        log.log(Level.SEVERE, "Deadlock or infinite loop detected in thread {0}. Stack trace: \n{1}" , 
                                                                new Object[] {
                                                                        thread.getName(),
                                                                        trace
                                                                });
                                                        
                                                        System.out.println("Attemping interrupt");
                                                        thread.interrupt();
                                                        
                                                        try
                                                        {
                                                                Thread.sleep(1000);
                                                        }
                                                        catch (InterruptedException ex)
                                                        {
                                                                return;
                                                        }
                                                        
                                                        System.out.println("Attemping stop");
                                                        thread.stop();
                                                        
                                                        
                                                        if (gui)
                                                        {
                                                                ErrorDialog diag;
                                                                diag = new ErrorDialog();
                                                                diag.setTitle("Deadlock: " + thread.getName());
                                                                diag.setErrorText(trace);
                                                        }
                                                }
                                                eventLoop.deadlock_tick_lastseen = eventLoop.deadlock_tick;
                                        }
                                }
                                
                                try
                                {
                                        Thread.sleep(5000);
                                }
                                catch (InterruptedException ex)
                                {
                                        return;
                                }
                        }
                }
        }
}
