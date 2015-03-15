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
package aphelion.shared.swissarmyknife;

import aphelion.shared.swissarmyknife.LinkedListHead;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author
 * Joris
 */
public class LinkedListHeadTest
{
        public LinkedListHeadTest()
        {
        }
        
        @Test public void testRemove1()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();
                listA.appendData("01");
                LinkedListEntry<String> target = listA.appendData("02");
                listA.appendData("03");
                
                target.remove();
                
                assertEquals("01 03", forwardString(listA));
                assertEquals("01 03", backwardString(listA));
                assertConsistentHead(listA);
        }
        
        @Test public void testRemove2()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();
                LinkedListEntry<String> target = listA.appendData("01");
                listA.appendData("02");
                listA.appendData("03");
                
                target.remove();
                
                assertEquals("02 03", forwardString(listA));
                assertEquals("02 03", backwardString(listA));
                assertConsistentHead(listA);
        }
        
        @Test public void testRemove3()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();
                listA.appendData("01");
                listA.appendData("02");
                LinkedListEntry<String> target = listA.appendData("03");
                
                target.remove();
                
                assertEquals("01 02", forwardString(listA));
                assertEquals("01 02", backwardString(listA));
                assertConsistentHead(listA);
        }
        
        @Test public void testRemoveByReference()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();
                String target;
                
                listA.appendData("01");
                listA.appendData(target = "02");
                listA.appendData("03");
                
                listA.removeByReference(target);
                
                assertEquals("01 03", forwardString(listA));
                assertEquals("01 03", backwardString(listA));
                assertConsistentHead(listA);
        }
        
        @Test public void testRemoveByEquals()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();
                String target;
                
                listA.appendData("01");
                listA.appendData("02");
                listA.appendData("03");
                
                listA.removeByEquals("02");
                
                assertEquals("01 03", forwardString(listA));
                assertEquals("01 03", backwardString(listA));
                assertConsistentHead(listA);
        }
        
        @Test
        public void testAppendForeign1()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();
                LinkedListHead<String> listB = new LinkedListHead<String>();

                listA.appendData("01");
                LinkedListEntry<String> start = listA.appendData("02");
                LinkedListEntry<String> end = listA.appendData("03");
                listA.appendData("04");
                
                listB.appendData("11");
                listB.appendData("12");
                LinkedListEntry<String> target = listB.appendData("13");
                listB.appendData("14");
                
                target.appendForeignRange(start, end);
                
                assertEquals("01 04", forwardString(listA));
                assertEquals("01 04", backwardString(listA));
                
                assertEquals("11 12 13 02 03 14", forwardString(listB));
                assertEquals("11 12 13 02 03 14", backwardString(listB));
                assertConsistentHead(listA);
                assertConsistentHead(listB);
        }
        
        @Test
        public void testAppendForeign2()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();
                LinkedListHead<String> listB = new LinkedListHead<String>();

                listA.appendData("01");
                LinkedListEntry<String> start = listA.appendData("02");
                LinkedListEntry<String> end = listA.appendData("03");
                listA.appendData("04");
                
                listB.appendData("11");
                listB.appendData("12");
                listB.appendData("13");
                LinkedListEntry<String> target = listB.appendData("14");
                
                target.appendForeignRange(start, end);
                
                assertEquals("01 04", forwardString(listA));
                assertEquals("01 04", backwardString(listA));
                
                assertEquals("11 12 13 14 02 03", forwardString(listB));
                assertEquals("11 12 13 14 02 03", backwardString(listB));
                assertConsistentHead(listA);
                assertConsistentHead(listB);
        }
        
        @Test
        public void testAppendForeign3()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();
                LinkedListHead<String> listB = new LinkedListHead<String>();

                LinkedListEntry<String> start = listA.appendData("01");
                listA.appendData("02");
                LinkedListEntry<String> end = listA.appendData("03");
                listA.appendData("04");
                
                listB.appendData("11");
                listB.appendData("12");
                LinkedListEntry<String> target = listB.appendData("13");
                listB.appendData("14");
                
                target.appendForeignRange(start, end);
                
                assertEquals("04", forwardString(listA));
                assertEquals("04", backwardString(listA));
                
                assertEquals("11 12 13 01 02 03 14", forwardString(listB));
                assertEquals("11 12 13 01 02 03 14", backwardString(listB));
                assertConsistentHead(listA);
                assertConsistentHead(listB);
        }
        
        @Test
        public void testAppendForeign4()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();
                LinkedListHead<String> listB = new LinkedListHead<String>();

                LinkedListEntry<String> start = listA.appendData("01");
                listA.appendData("02");
                listA.appendData("03");
                LinkedListEntry<String> end = listA.appendData("04");
                
                listB.appendData("11");
                listB.appendData("12");
                LinkedListEntry<String> target = listB.appendData("13");
                listB.appendData("14");
                
                target.appendForeignRange(start, end);
                
                assertEquals("", forwardString(listA));
                assertEquals("", backwardString(listA));
                
                assertEquals("11 12 13 01 02 03 04 14", forwardString(listB));
                assertEquals("11 12 13 01 02 03 04 14", backwardString(listB));
                assertConsistentHead(listA);
                assertConsistentHead(listB);
        }
        
        @Test
        public void testAppendForeign5()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();
                LinkedListHead<String> listB = new LinkedListHead<String>();

                LinkedListEntry<String> start = listA.appendData("01");
                listA.appendData("02");
                listA.appendData("03");
                LinkedListEntry<String> end = listA.appendData("04");
                
                listB.appendForeignRange(start, end);
                
                assertEquals("", forwardString(listA));
                assertEquals("", backwardString(listA));
                
                assertEquals("01 02 03 04", forwardString(listB));
                assertEquals("01 02 03 04", backwardString(listB));
                assertConsistentHead(listA);
                assertConsistentHead(listB);
        }
        
        @Test
        public void testAppendForeign6()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();

                LinkedListEntry<String> start = listA.appendData("01");
                listA.appendData("02");
                listA.appendData("03");
                LinkedListEntry<String> end = listA.appendData("04");
                
                // appendForeignRange should not allow adding from its own list
                try
                {
                        listA.appendForeignRange(start, end);
                        assertTrue(false);
                }
                catch(AssertionError ex)
                {
                }
                
                assertConsistentHead(listA);
        }
        
        @Test
        public void testAppendForeign7()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();

                listA.appendData("01");
                listA.appendData("02");
                listA.appendData("03");
                listA.appendData("04");
                
                LinkedListEntry<String> test = new LinkedListEntry<String>("10"); // headless
                
                listA.appendForeignRange(test, test);
                
                assertEquals("01 02 03 04 10", forwardString(listA));
                assertEquals("01 02 03 04 10", backwardString(listA));
                assertConsistentHead(listA);
        }
        
        @Test
        public void testPrependForeign1()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();
                LinkedListHead<String> listB = new LinkedListHead<String>();

                listA.appendData("01");
                LinkedListEntry<String> start = listA.appendData("02");
                LinkedListEntry<String> end = listA.appendData("03");
                listA.appendData("04");
                
                listB.appendData("11");
                listB.appendData("12");
                LinkedListEntry<String> target = listB.appendData("13");
                listB.appendData("14");
                
                target.prependForeignRange(start, end);
                
                assertEquals("01 04", forwardString(listA));
                assertEquals("01 04", backwardString(listA));
                
                assertEquals("11 12 02 03 13 14", forwardString(listB));
                assertEquals("11 12 02 03 13 14", backwardString(listB));
                assertConsistentHead(listA);
                assertConsistentHead(listB);
        }
        
        @Test
        public void testPrependForeign2()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();
                LinkedListHead<String> listB = new LinkedListHead<String>();

                listA.appendData("01");
                LinkedListEntry<String> start = listA.appendData("02");
                LinkedListEntry<String> end = listA.appendData("03");
                listA.appendData("04");
                
                LinkedListEntry<String> target = listB.appendData("11");
                listB.appendData("12");
                listB.appendData("13");
                listB.appendData("14");
                
                target.prependForeignRange(start, end);
                
                assertEquals("01 04", forwardString(listA));
                assertEquals("01 04", backwardString(listA));
                
                assertEquals("02 03 11 12 13 14", forwardString(listB));
                assertEquals("02 03 11 12 13 14", backwardString(listB));
                assertConsistentHead(listA);
                assertConsistentHead(listB);
        }
        
        @Test
        public void testPrependForeign3()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();
                LinkedListHead<String> listB = new LinkedListHead<String>();

                LinkedListEntry<String> start = listA.appendData("01");
                listA.appendData("02");
                LinkedListEntry<String> end = listA.appendData("03");
                listA.appendData("04");
                
                listB.appendData("11");
                listB.appendData("12");
                LinkedListEntry<String> target = listB.appendData("13");
                listB.appendData("14");
                
                target.prependForeignRange(start, end);
                
                assertEquals("04", forwardString(listA));
                assertEquals("04", backwardString(listA));
                
                assertEquals("11 12 01 02 03 13 14", forwardString(listB));
                assertEquals("11 12 01 02 03 13 14", backwardString(listB));
                assertConsistentHead(listA);
                assertConsistentHead(listB);
        }
        
        @Test
        public void testPrependForeign4()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();
                LinkedListHead<String> listB = new LinkedListHead<String>();

                LinkedListEntry<String> start = listA.appendData("01");
                listA.appendData("02");
                listA.appendData("03");
                LinkedListEntry<String> end = listA.appendData("04");
                
                listB.appendData("11");
                listB.appendData("12");
                LinkedListEntry<String> target = listB.appendData("13");
                listB.appendData("14");
                
                target.prependForeignRange(start, end);
                
                assertEquals("", forwardString(listA));
                assertEquals("", backwardString(listA));
                
                assertEquals("11 12 01 02 03 04 13 14", forwardString(listB));
                assertEquals("11 12 01 02 03 04 13 14", backwardString(listB));
                assertConsistentHead(listA);
                assertConsistentHead(listB);
        }
        
        @Test
        public void testPrependForeign5()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();
                LinkedListHead<String> listB = new LinkedListHead<String>();

                LinkedListEntry<String> start = listA.appendData("01");
                listA.appendData("02");
                listA.appendData("03");
                LinkedListEntry<String> end = listA.appendData("04");
                
                listB.appendForeignRange(start, end);
                
                assertEquals("", forwardString(listA));
                assertEquals("", backwardString(listA));
                
                assertEquals("01 02 03 04", forwardString(listB));
                assertEquals("01 02 03 04", backwardString(listB));
                assertConsistentHead(listA);
                assertConsistentHead(listB);
        }
        
        @Test
        public void testPrependForeign6()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();

                LinkedListEntry<String> start = listA.appendData("01");
                listA.appendData("02");
                listA.appendData("03");
                LinkedListEntry<String> end = listA.appendData("04");
                
                try
                {
                        listA.prependForeignRange(start, end);
                        assertTrue(false);
                }
                catch(AssertionError ex)
                {
                }
                
                assertConsistentHead(listA);
        }
        
        @Test
        public void testPrependForeign7()
        {
                LinkedListHead<String> listA  = new LinkedListHead<String>();

                listA.appendData("01");
                listA.appendData("02");
                listA.appendData("03");
                listA.appendData("04");
                
                LinkedListEntry<String> test = new LinkedListEntry<String>(null, "10"); // headless
                
                listA.prependForeignRange(test, test);
                
                assertEquals("10 01 02 03 04", forwardString(listA));
                assertEquals("10 01 02 03 04", backwardString(listA));
        }

        @Test
        public void testRemoveRange()
        {
                LinkedListHead<String> list = new LinkedListHead<>();
                list.appendData("01");
                list.appendData("02");
                LinkedListEntry<String> three = list.appendData("03");
                LinkedListEntry<String> four = list.appendData("04");
                LinkedListEntry<String> five = list.appendData("05");
                list.appendData("06");

                list.removeRange(three, five);

                assertEquals("01 02 06", forwardString(list));
                assertEquals("01 02 06", backwardString(list));
                assertUnlinked(three);
                assertUnlinked(four);
                assertUnlinked(five);
        }

        @Test
        public void testClear()
        {
                LinkedListHead<String> list = new LinkedListHead<>();
                LinkedListEntry<String> one = list.appendData("01");
                LinkedListEntry<String> two = list.appendData("02");
                LinkedListEntry<String> three = list.appendData("03");
                LinkedListEntry<String> four = list.appendData("04");
                LinkedListEntry<String> five = list.appendData("05");

                list.clear();
                assertEquals("", forwardString(list));
                assertEquals("", backwardString(list));
                assertUnlinked(one);
                assertUnlinked(two);
                assertUnlinked(three);
                assertUnlinked(four);
                assertUnlinked(five);
        }
        
        private String forwardString(LinkedListHead<String> list)
        {
                return forwardString(list.first);
        }

        private String forwardString(LinkedListEntry<String> first)
        {
                LinkedListEntry<String> link;
                StringBuilder ret = new StringBuilder();

                for (link = first; link != null; link = link.next)
                {
                        ret.append(link.data);
                        if (link.next != null)
                        {
                                ret.append(" ");
                        }

                }

                return ret.toString();
        }

        private String backwardString(LinkedListHead<String> list)
        {
                return backwardString(list.last);
        }

        private String backwardString(LinkedListEntry<String> last)
        {
                LinkedListEntry<String> link;
                StringBuilder ret = new StringBuilder();

                for (link = last; link != null; link = link.previous)
                {
                        if (link != last)
                        {
                                ret.insert(0, " ");
                        }
                        ret.insert(0, link.data);
                }

                return ret.toString();
        }
        
        private void assertConsistentHead(LinkedListHead<String> head)
        {
                LinkedListEntry<String> link;

                for (link = head.first; link != null; link = link.next)
                {
                        assertTrue(link.head == head);
                }  
        }

        private void assertUnlinked(LinkedListEntry entry)
        {
                assertNull(entry.head);
                assertNull(entry.previous);
                assertNull(entry.next);
        }
}
