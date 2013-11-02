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

package aphelion.shared.event.promise;


import aphelion.shared.event.Workable;
import java.util.LinkedList;

/**
 *
 * @author Joris
 */
public abstract class AbstractPromise
{
        protected final Workable workable;
        private boolean resolved = false;
        private boolean rejected = false;
        private Object resolveResult;
        private PromiseException rejectException;
        protected final LinkedList<Resolution> resolutions = new LinkedList<>();

        public AbstractPromise(Workable workable)
        {
                this.workable = workable;
        }
        
        protected class Resolution implements Runnable
        {
                final PromiseResolved resolvedCallback;
                final PromiseRejected rejectedCallback;
                // or
                final All all;
                final int myIndex;
                
                
                final Promise chainedPromise;

                Resolution(PromiseResolved resolvedCallback, PromiseRejected rejectedCallback)
                {
                        this.resolvedCallback = resolvedCallback;
                        this.rejectedCallback = rejectedCallback;
                        this.all = null;
                        this.myIndex = 0;
                        
                        this.chainedPromise = new Promise(workable);
                }

                Resolution(All all, int myIndex)
                {
                        this.resolvedCallback = null;
                        this.rejectedCallback = null;
                        this.all = all;
                        this.myIndex = myIndex;
                        
                        this.chainedPromise = null;
                }

                @Override
                public void run()
                {
                        assert all == null;
                        if (rejected)
                        {
                                if (this.rejectedCallback != null)
                                {
                                        this.rejectedCallback.rejected(rejectException);
                                }
                                else if (this.chainedPromise != null)
                                {
                                        // only fire it up the chain if it was not caught
                                        this.chainedPromise.reject(rejectException);
                                }
                        }
                        else
                        {
                                assert resolved;

                                if (this.resolvedCallback != null)
                                {
                                        Object ret;
                                        try
                                        {
                                                ret = this.resolvedCallback.resolved(resolveResult);
                                        }
                                        catch (PromiseException ex)
                                        {
                                                this.chainedPromise.reject(ex);
                                                return;
                                        }

                                        if (ret instanceof AbstractPromise)
                                        {
                                                chainedPromise.setParent((AbstractPromise) ret);
                                        }
                                        else
                                        {
                                                this.chainedPromise.resolve(ret);
                                        }
                                }
                        }
                }
        }
        
        public void resolve(Object ret)
        {
                if (resolved || rejected)
                {
                        throw new IllegalStateException("Already resolved / rejected");
                }

                resolved = true;
                resolveResult = ret;
                doResolutions();
                resolutions.clear();
        }


        public void reject(PromiseException ex)
        {
                if (resolved || rejected)
                {
                        throw new IllegalStateException("Already resolved / rejected");
                }

                rejected = true;
                rejectException = ex;
                doResolutions();
                resolutions.clear();
        }
        
        public AbstractPromise then(PromiseResolved resolved, PromiseRejected rejected)
        {
                then(rejected);
                return then(resolved);
        }
        
        public AbstractPromise then(PromiseResolved callback)
        {
                if (callback == null)
                {
                        throw new IllegalArgumentException();
                }
                
                Resolution resolution = new Resolution(callback, null);

                if (resolved || rejected)
                {
                        workable.runOnMain(resolution);
                }
                else
                {
                        resolutions.add(resolution);
                }

                return resolution.chainedPromise;
        }
        
        public AbstractPromise then(PromiseRejected callback)
        {
                if (callback == null)
                {
                        throw new IllegalArgumentException();
                }
                
                final Resolution resolution = new Resolution(null, callback);

                if (resolved || rejected)
                {
                        workable.runOnMain(resolution);
                }
                else
                {
                        resolutions.add(resolution);
                }

                return resolution.chainedPromise;
        }

        
        public void then(All all, int myIndex)
        {
                if (all == null)
                {
                        throw new IllegalArgumentException();
                }
                
                if (resolved)
                {
                        all.markListResolve(myIndex, resolveResult);
                }
                else if (rejected)
                {
                        all.markListReject(myIndex, rejectException);
                }
                else
                {
                        Resolution resolution = new Resolution(all, myIndex);
                        resolutions.add(resolution);
                }
        }

        private void doResolutions()
        {
                for (Resolution resolution : resolutions)
                {
                        if (resolution.resolvedCallback != null || resolution.rejectedCallback != null)
                        {
                                workable.runOnMain(resolution);
                        }
                        else
                        {
                                // have to mark our index as resolved in a list
                                assert resolution.chainedPromise == null;
                                assert resolution.rejectedCallback == null;
                                assert resolution.all != null;

                                if (rejected)
                                {
                                        resolution.all.markListReject(resolution.myIndex, rejectException);
                                }
                                else
                                {
                                        assert resolved;
                                        resolution.all.markListResolve(resolution.myIndex, resolveResult);
                                }
                        }
                }
        }
}
