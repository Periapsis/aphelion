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
package aphelion.server.http;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Joris
 */
public class HttpUtilTest_
{
        public HttpUtilTest_()
        {
        }

        @Test
        public void testBinarySizeUTF8()
        {
                testBinarySizeUTF8_string("$");
                testBinarySizeUTF8_string("¢");
                testBinarySizeUTF8_string("€");
                testBinarySizeUTF8_string("$Â¢â‚¬");
                testBinarySizeUTF8_string("\uD834\uDD1E");
        }
        
        private void testBinarySizeUTF8_string(String test)
        {
                assertEquals(test.getBytes(Charset.forName("UTF-8")).length, HttpUtil.binarySizeUTF8(test));
        }
        
        @Test
        public void testFindCRLF()
        {
                ByteBuffer buf = ByteBuffer.allocate(100);
                buf.put((byte) 't');
                buf.put((byte) 'e');
                buf.put((byte) 's');
                buf.put((byte) 't');
                buf.put((byte) '\r');
                buf.put((byte) '\n');
                buf.put((byte) 'b');
                buf.put((byte) 'l');
                buf.put((byte) 'a');
                buf.flip();
                assertEquals(4, HttpUtil.findCRLF(buf, 0));
                assertEquals(4, HttpUtil.findCRLF(buf, 1));
                assertEquals(4, HttpUtil.findCRLF(buf, 2));
                assertEquals(4, HttpUtil.findCRLF(buf, 3));
                assertEquals(4, HttpUtil.findCRLF(buf, 4));
                assertEquals(-1, HttpUtil.findCRLF(buf, 5));
                
                buf.clear();
                buf.put((byte) '\r');
                buf.put((byte) '\n');
                buf.flip();
                assertEquals(0, HttpUtil.findCRLF(buf, 0));
                assertEquals(-1, HttpUtil.findCRLF(buf, 1));
                
        }
        
        @Test
        public void testFindCRLFIgnoreLWS()
        {
                ByteBuffer buf = ByteBuffer.allocate(100);
                buf.put((byte) 't'); //0
                buf.put((byte) 'e'); //1
                buf.put((byte) 's'); //2
                buf.put((byte) 't'); //3
                buf.put((byte) '\r');//4
                buf.put((byte) '\n');//5
                buf.put((byte) ' '); //6
                buf.put((byte) 'a'); //7
                buf.put((byte) 'b'); //8
                buf.put((byte) 'c'); //9
                buf.put((byte) '\r');//10
                buf.put((byte) '\n');//11
                buf.put((byte) '\r');//12
                buf.put((byte) '\n');//13
                buf.flip();
                
                assertEquals(10, HttpUtil.findCRLFIgnoreLWS(buf, 0));
                assertEquals(10, HttpUtil.findCRLFIgnoreLWS(buf, 3));
                assertEquals(10, HttpUtil.findCRLFIgnoreLWS(buf, 4));
                assertEquals(10, HttpUtil.findCRLFIgnoreLWS(buf, 5));
                assertEquals(10, HttpUtil.findCRLFIgnoreLWS(buf, 6));
                assertEquals(10, HttpUtil.findCRLFIgnoreLWS(buf, 9));
                assertEquals(10, HttpUtil.findCRLFIgnoreLWS(buf, 10));
                assertEquals(-1, HttpUtil.findCRLFIgnoreLWS(buf, 11));
                
                buf.clear();
                buf.put((byte) '\r');
                buf.put((byte) '\n');
                buf.put((byte) 'C');
                buf.flip();
                assertEquals(0, HttpUtil.findCRLFIgnoreLWS(buf, 0));
                assertEquals(-1, HttpUtil.findCRLFIgnoreLWS(buf, 1));
                
        }
        
        
        @Test
        public void testReadLine()
        {
                StringBuilder dest = new StringBuilder();
                
                ByteBuffer buf = ByteBuffer.allocate(100);
                buf.put((byte) 't');
                buf.put((byte) 'e');
                buf.put((byte) 's');
                buf.put((byte) 't');
                buf.put((byte) '\r');
                buf.put((byte) '\n');
                buf.put((byte) ' ');
                buf.put((byte) 'b');
                buf.put((byte) 'l');
                buf.put((byte) 'a');
                buf.put((byte) '\r');
                buf.put((byte) '\n');
                buf.put((byte) '\r');
                buf.put((byte) '\n');
                buf.flip();
                
                dest.setLength(0);
                assertTrue(HttpUtil.readLine(dest, buf, false));
                assertEquals("test", dest.toString());
                
                dest.setLength(0);
                assertTrue(HttpUtil.readLine(dest, buf, false));
                assertEquals(" bla", dest.toString());
                
                dest.setLength(0);
                assertTrue(HttpUtil.readLine(dest, buf, false));
                assertEquals("", dest.toString());
                
                
                buf.position(0);
                
                dest.setLength(0);
                assertTrue(HttpUtil.readLine(dest, buf, true));
                assertEquals("test\r\n bla", dest.toString());
                
                dest.setLength(0);
                assertFalse(HttpUtil.readLine(dest, buf, true));
        }
}
