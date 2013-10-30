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

import aphelion.shared.event.TickedEventLoop;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Joris
 */
public class PromiseTest
{
        
        public PromiseTest()
        {
        }

        private boolean called_A;
        private boolean called_B;
        
        
        @Test
        public void testResolve_earlyRegistration()
        {
                called_A = false;
                called_B = false;
                
                TickedEventLoop loop = new TickedEventLoop(10, 1, null);
                loop.setup();
                
                AbstractPromise p = new Promise(loop);
                
                p.then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                assertEquals((Integer) 123, ret);
                                assertFalse(called_A);
                                assertFalse(called_B);
                                
                                called_A = true;
                                return "Bla";
                        }
                }).then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                assertEquals("Bla", ret);
                                assertTrue(called_A);
                                assertFalse(called_B);
                                called_B = true;
                                return null;
                        }
                }).then(new PromiseRejected()
                {
                        @Override
                        public void rejected(PromiseException error)
                        {
                                assert false;
                        }
                });
                
                loop.loop();
                p.resolve(123);
                loop.loop();
                
                assertTrue(called_A);
                assertTrue(called_B);
        }
        
        @Test
        public void testResolve_lateRegistration()
        {
                called_A = false;
                called_B = false;
                
                TickedEventLoop loop = new TickedEventLoop(10, 1, null);
                loop.setup();
                
                AbstractPromise p = new Promise(loop);
                
                p.resolve(123);
                loop.loop();
                
                p.then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                assertEquals((Integer) 123, ret);
                                assertFalse(called_A);
                                assertFalse(called_B);
                                
                                called_A = true;
                                return "Bla";
                        }
                }).then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                assertEquals("Bla", ret);
                                assertTrue(called_A);
                                assertFalse(called_B);
                                called_B = true;
                                return null;
                        }
                }).then(new PromiseRejected()
                {
                        @Override
                        public void rejected(PromiseException error)
                        {
                                assert false;
                        }
                });
                
                loop.loop();
                
                assertTrue(called_A);
                assertTrue(called_B);
        }
        
        @Test
        public void testReject_earlyRegistration()
        {
                called_A = false;
                
                TickedEventLoop loop = new TickedEventLoop(10, 1, null);
                loop.setup();
                
                AbstractPromise p = new Promise(loop);
                
                p.then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                assert false;
                                return null;
                        }
                }).then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                assert false;
                                return null;
                        }
                }).then(new PromiseRejected()
                {
                        @Override
                        public void rejected(PromiseException error)
                        {
                                assertEquals("abc", error.getMessage());
                                called_A = true;
                        }
                });
                
                loop.loop();
                p.reject(new PromiseException("abc"));
                loop.loop();
                
                assertTrue(called_A);
        }
        
        @Test
        public void testReject_lateRegistration()
        {
                called_A = false;
                
                TickedEventLoop loop = new TickedEventLoop(10, 1, null);
                loop.setup();
                
                AbstractPromise p = new Promise(loop);
                
                p.reject(new PromiseException("abc"));
                loop.loop();
                
                p.then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                assert false;
                                return null;
                        }
                }).then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                assert false;
                                return null;
                        }
                }).then(new PromiseRejected()
                {
                        @Override
                        public void rejected(PromiseException error)
                        {
                                assertEquals("abc", error.getMessage());
                                called_A = true;
                        }
                });
                
                
                loop.loop();
                assertTrue(called_A);
        }
        
        
        @Test
        public void testResolve_ReturnedPromise_earlyRegistration()
        {
                called_A = false;
                called_B = false;
                
                TickedEventLoop loop = new TickedEventLoop(10, 1, null);
                loop.setup();
                
                AbstractPromise p = new Promise(loop);
                final AbstractPromise p2 = new Promise(loop);
                
                p.then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                assertEquals((Integer) 123, ret);
                                assertFalse(called_A);
                                assertFalse(called_B);
                                
                                called_A = true;
                                return p2;
                        }
                }).then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                assertEquals("Bla", ret);
                                assertTrue(called_A);
                                assertFalse(called_B);
                                called_B = true;
                                return null;
                        }
                }).then(new PromiseRejected()
                {
                        @Override
                        public void rejected(PromiseException error)
                        {
                                assert false;
                        }
                });
                
                loop.loop();
                p.resolve(123);
                loop.loop();
                assertTrue(called_A);
                assertFalse(called_B);
                p2.resolve("Bla");
                loop.loop();
                
                assertTrue(called_A);
                assertTrue(called_B);
        }
        
        @Test
        public void testResolve_ReturnedPromise_lateRegistration1()
        {
                called_A = false;
                called_B = false;
                
                TickedEventLoop loop = new TickedEventLoop(10, 1, null);
                loop.setup();
                

                
                AbstractPromise p = new Promise(loop);
                final AbstractPromise p2 = new Promise(loop);
                
                p.resolve(123);
                loop.loop();
                
                p.then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                assertEquals((Integer) 123, ret);
                                assertFalse(called_A);
                                assertFalse(called_B);
                                
                                called_A = true;
                                return p2;
                        }
                }).then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                assertEquals("Bla", ret);
                                assertTrue(called_A);
                                assertFalse(called_B);
                                called_B = true;
                                return null;
                        }
                }).then(new PromiseRejected()
                {
                        @Override
                        public void rejected(PromiseException error)
                        {
                                assert false;
                        }
                });
                
                loop.loop();
                
                assertTrue(called_A);
                assertFalse(called_B);
                p2.resolve("Bla");
                loop.loop();
                assertTrue(called_A);
                assertTrue(called_B);
        }
        
        @Test
        public void testResolve_ReturnedPromise_lateRegistration2()
        {
                called_A = false;
                called_B = false;
                
                TickedEventLoop loop = new TickedEventLoop(10, 1, null);
                loop.setup();
                

                
                AbstractPromise p = new Promise(loop);
                final AbstractPromise p2 = new Promise(loop);
                
                p.resolve(123);
                p2.resolve("Bla");
                loop.loop();
                
                p.then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                assertEquals((Integer) 123, ret);
                                assertFalse(called_A);
                                assertFalse(called_B);
                                
                                called_A = true;
                                return p2;
                        }
                }).then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                assertEquals("Bla", ret);
                                assertTrue(called_A);
                                assertFalse(called_B);
                                called_B = true;
                                return null;
                        }
                }).then(new PromiseRejected()
                {
                        @Override
                        public void rejected(PromiseException error)
                        {
                                assert false;
                        }
                });
                
                loop.loop();
                assertTrue(called_A);
                assertTrue(called_B);
        }
}
