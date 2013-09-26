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

import aphelion.shared.swissarmyknife.ThreadSafe;

/**
 *
 * @author Joris
 */
public interface Workable
{
        /** Adds a task to be executed by a worker thread
         * 
         * Tasks are not supposed to access data shared by the non worker threads,
         * the only interaction takes place using "argument" and the return value in the callback
         * (the callback runs in the EventLoop thread)
         * @param task An instance that handles the execution of the actual task. 
         *             Its sole method is called from one of the worker threads.
         * @param argument An optional argument that is passed to the worker thread. 
         *                 This argument should ofcourse be safe to use in another thread.
         *                 The type of this argument should be the same as the &lt;ARGUMENT&gt;
         *                 generic in your WorkerTask.
         * @param callback Is fired from the main thread (not a worker thread). Its &lt;RETURN&gt; generic 
         *                 is of the same type as the WorkerTask &lt;RETURN&gt; generic.
         * @throws IllegalStateException If this class was constructed with 0 worker threads.
         */
        @ThreadSafe
        public void addWorkerTask(WorkerTask task, Object argument, WorkerTaskCallback callback);
        
        /** Schedule a method to be called on the main thread.
         * The "main" thread is the the thread that is running the event loop.
         * @param runnable The object that run() is called on (once). This method should not perform any blocking operations.
         */
        @ThreadSafe
        public void runOnMain(Runnable runnable);
        
        /** Called by a worker thread when a task has been completed.
         * The callback will then be called from the main thread.
         * @param task The task that holds the callback and return value
         */
        @ThreadSafe
        public void taskCompleted(WorkerTask task);
}
