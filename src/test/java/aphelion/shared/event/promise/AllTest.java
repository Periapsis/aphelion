/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
                }, new PromiseRejected()
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
                }, new PromiseRejected()
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
                }, new PromiseRejected()
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
