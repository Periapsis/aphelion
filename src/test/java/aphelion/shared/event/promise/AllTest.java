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
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Joris
 */
public class AllTest
{
        private boolean called_A;
        
        @Test
        public void testResolve_earlyRegistration()
        {
                called_A = false;
                
                TickedEventLoop loop = new TickedEventLoop(10, 1, null);
                loop.setup();
                
                AbstractPromise p0 = new Promise(loop);
                AbstractPromise p1 = new Promise(loop);
                AbstractPromise p2 = new Promise(loop);
                AbstractPromise p3 = new Promise(loop);
                AbstractPromise p4 = new Promise(loop);
                AbstractPromise p5 = new Promise(loop);
                
                All all = new All(loop, p0, p1, p2, p3, p4, p5);
                
                all.then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                List list = (List) ret;
                                assertEquals(6, list.size());
                                
                                assertEquals("a", list.get(0));
                                assertEquals("b", list.get(1));
                                assertEquals("c", list.get(2));
                                assertEquals("d", list.get(3));
                                assertEquals("e", list.get(4));
                                assertEquals("f", list.get(5));
                                
                                called_A = true;
                                return null;
                        }
                });
                all.then(new PromiseRejected()
                {
                        @Override
                        public void rejected(PromiseException error)
                        {
                                assert false;
                        }
                });
                
                loop.loop();
                assertFalse(called_A);
                
                p0.resolve("a");
                p1.resolve("b");
                p2.resolve("c");
                p3.resolve("d");
                p4.resolve("e");
                
                loop.loop();
                assertFalse(called_A);
                
                p5.resolve("f");
                
                loop.loop();
                assertTrue(called_A);
        }
        
        @Test
        public void testResolve_lateRegistration1()
        {
                called_A = false;
                
                TickedEventLoop loop = new TickedEventLoop(10, 1, null);
                loop.setup();
                
                AbstractPromise p0 = new Promise(loop);
                AbstractPromise p1 = new Promise(loop);
                AbstractPromise p2 = new Promise(loop);
                AbstractPromise p3 = new Promise(loop);
                AbstractPromise p4 = new Promise(loop);
                AbstractPromise p5 = new Promise(loop);
                
                All all = new All(loop, p0, p1, p2, p3, p4, p5);
                
                loop.loop();
                p0.resolve("a");
                p1.resolve("b");
                p2.resolve("c");
                p3.resolve("d");
                p4.resolve("e");
                p5.resolve("f");
                loop.loop();
                
                
                all.then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                List list = (List) ret;
                                assertEquals(6, list.size());
                                
                                assertEquals("a", list.get(0));
                                assertEquals("b", list.get(1));
                                assertEquals("c", list.get(2));
                                assertEquals("d", list.get(3));
                                assertEquals("e", list.get(4));
                                assertEquals("f", list.get(5));
                                
                                called_A = true;
                                return null;
                        }
                });
                all.then(new PromiseRejected()
                {
                        @Override
                        public void rejected(PromiseException error)
                        {
                                assert false;
                        }
                });
                loop.loop();
                
                assertTrue(called_A);
        }
        
        @Test
        public void testResolve_lateRegistration2()
        {
                called_A = false;
                
                TickedEventLoop loop = new TickedEventLoop(10, 1, null);
                loop.setup();
                
                AbstractPromise p0 = new Promise(loop);
                AbstractPromise p1 = new Promise(loop);
                AbstractPromise p2 = new Promise(loop);
                AbstractPromise p3 = new Promise(loop);
                AbstractPromise p4 = new Promise(loop);
                AbstractPromise p5 = new Promise(loop);
                
                loop.loop();
                p0.resolve("a");
                p1.resolve("b");
                p2.resolve("c");
                p3.resolve("d");
                p4.resolve("e");
                p5.resolve("f");
                loop.loop();
                
                All all = new All(loop, p0, p1, p2, p3, p4, p5);
                
                loop.loop();
                
                all.then(new PromiseResolved()
                {
                        @Override
                        public Object resolved(Object ret) throws PromiseException
                        {
                                List list = (List) ret;
                                assertEquals(6, list.size());
                                
                                assertEquals("a", list.get(0));
                                assertEquals("b", list.get(1));
                                assertEquals("c", list.get(2));
                                assertEquals("d", list.get(3));
                                assertEquals("e", list.get(4));
                                assertEquals("f", list.get(5));
                                
                                called_A = true;
                                return null;
                        }
                });
                all.then(new PromiseRejected()
                {
                        @Override
                        public void rejected(PromiseException error)
                        {
                                assert false;
                        }
                });
                loop.loop();
                
                assertTrue(called_A);
        }
}
