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

import aphelion.shared.swissarmyknife.ThreadSafe;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

/**
 *
 * @author Joris
 */
public class HttpUtil
{
        private HttpUtil()
        {
        }

        @ThreadSafe
        public static boolean isCHAR(byte by)
        {
                return by >= 0 && by <= 127;
        }

        @ThreadSafe
        public static boolean isUPALPHA(byte by)
        {
                return by >= 'A' && by <= 'Z';
        }

        @ThreadSafe
        public static boolean isLOALPHA(byte by)
        {
                return by >= 'a' && by <= 'z';
        }

        @ThreadSafe
        public static boolean isALPHA(byte by)
        {
                return isUPALPHA(by) || isLOALPHA(by);
        }

        @ThreadSafe
        public static boolean isDIGIT(byte by)
        {
                return by >= '0' && by <= '9';
        }

        @ThreadSafe
        public static boolean isCTL(byte by)
        {
                return (by >= 0 && by <= 34) || by == 127;
        }

        @ThreadSafe
        public static boolean isCR(byte by)
        {
                return by == '\r'; // (13)
        }

        @ThreadSafe
        public static boolean isLF(byte by)
        {
                return by == '\n'; // (10)
        }

        @ThreadSafe
        public static boolean isSP(byte by)
        {
                return by == ' ';
        }

        @ThreadSafe
        public static boolean isHT(byte by)
        {
                return by == '\t';
        }

        @ThreadSafe
        public static boolean isQ(byte by)
        {
                return by == '"';
        }
        // Request-Line   = Method SP Request-URI SP HTTP-Version CRLF
        // Request-URI    = "*" | absoluteURI | abs_path | authority
        public static Pattern requestLine = Pattern.compile("^([A-Z]+) ([\\x20-\\x7E]+) HTTP/1\\.(\\d+)$");
        // message-header = field-name ":" [ field-value ]
        // field-name     = token
        // field-value    = *( TEXT | LWS )
        // TEXT           = <any OCTET except CTLs, but including LWS>
        public static Pattern headerLine = Pattern.compile("^([!#$%&'*+\\-.0-9A-Z^_`a-z|~]+):[ \t\r\n]*([\\x20-\\x7E\n\r\t]+)$");
        // Simple range header (only read the first range)
        public static Pattern simpleRange = Pattern.compile("^bytes[ \t\r\n]*=[ \t\r\n]*(\\d*)[ \t\r\n]*-[ \t\r\n]*(\\d*)?");

        /**
         * Attempts to read a line (up to CRLF) from the current position of buf. If there is no CRLF left in the
         * ByteBuffer, this method will return false and leave the position intact. If a line is found, it is append to
         * dest and the position of the buf is moved past CRLF.
         *
         * @param dest The string builder to put the read line
         * @param buf The buffer to read from
         * @param lws If set, [CRLF] 1*( SP | HT ) is not the end of a line.
         * @return false if no line was found (CRLF)
         */
        @ThreadSafe
        public static boolean readLine(StringBuilder dest, ByteBuffer buf, boolean lws)
        {
                int crlf = lws ? findCRLFIgnoreLWS(buf, buf.position()) : findCRLF(buf, buf.position());
                if (crlf < 0)
                {
                        return false;
                }

                while (buf.position() < crlf)
                {
                        byte by = buf.get();

                        if (isCHAR(by))
                        {
                                dest.append((char) by); // ASCII
                        }
                }

                // Place the position past CRLF
                buf.get();
                buf.get();

                return true;
        }

        @ThreadSafe
        public static int findCRLF(ByteBuffer buf, int offset)
        {
                //  do not loop over the last character, so that a + 1 does not fail
                for (int a = offset; a < buf.limit() - 1; a++)
                {
                        if (isCR(buf.get(a)) && isLF(buf.get(a + 1)))
                        {
                                return a;
                        }
                }

                return -1;
        }

        @ThreadSafe
        public static int findCRLFIgnoreLWS(ByteBuffer buf, int offset)
        {
                //  LWS            = [CRLF] 1*( SP | HT )

                //  do not loop over the last character, so that a + 2 does not fail
                for (int a = offset; a < buf.limit() - 2; a++)
                {
                        if (isCR(buf.get(a)) && isLF(buf.get(a + 1)))
                        {
                                // linear white space? Has the header been split over multiple lines?
                                if (!isSP(buf.get(a + 2)) && !isHT(buf.get(a + 2)))
                                {
                                        return a;
                                }
                        }
                }

                return -1;
        }

        @ThreadSafe
        public static int findSP(ByteBuffer buf, int offset)
        {
                for (int a = offset; a < buf.limit(); a++)
                {
                        if (isSP(buf.get(a)))
                        {
                                return a;
                        }
                }

                return -1;
        }

        public static enum METHOD
        {
                UNKNOWN,
                OPTIONS,
                GET, // MUST be supported
                HEAD, // MUST be supported
                POST,
                PUT,
                DELETE,
                TRACE,
                CONNECT;

                public static METHOD fromRequestLine(String method)
                {
                        if ("GET".equals(method))
                        {
                                return GET;
                        }

                        if ("HEAD".equals(method))
                        {
                                return HEAD;
                        }

                        if ("POST".equals(method))
                        {
                                return POST;
                        }

                        if ("OPTIONS".equals(method))
                        {
                                return OPTIONS;
                        }

                        if ("PUT".equals(method))
                        {
                                return PUT;
                        }

                        if ("DELETE".equals(method))
                        {
                                return DELETE;
                        }

                        if ("TRACE".equals(method))
                        {
                                return TRACE;
                        }

                        if ("CONNECT".equals(method))
                        {
                                return CONNECT;
                        }

                        return UNKNOWN;
                }
        }

        public static class HttpException extends Exception
        {
                public int status;
                public boolean fatal;

                public HttpException(int status, boolean fatal, String message, Throwable cause)
                {
                        super(message, cause);
                        this.status = status;
                        this.fatal = fatal;
                }

                public HttpException(int status, boolean fatal, String message)
                {
                        this(status, fatal, message, null);
                }
        }

        @ThreadSafe
        public static int binarySizeUTF8(String str)
        {
                // Java strings are UTF-16

                int bytes = 0;
                for (int a = 0; a < str.length(); ++a)
                {
                        int codePoint = str.codePointAt(a);

                        if (codePoint <= 0x7F)
                        {
                                bytes += 1;
                        }
                        else if (codePoint <= 0x7FF)
                        {
                                bytes += 2;
                        }
                        else if (codePoint <= 0xFFFF) // End of BMP
                        {
                                bytes += 3;
                        }
                        else
                        {
                                bytes += 4;
                                a++; // skip the next one, becuase it will be a low surrogate
                        }
                }

                return bytes;
        }
        public final static Charset UTF8 = Charset.forName("UTF-8");

        @ThreadSafe
        public static File findDirectoryIndex(File dir)
        {
                File ret;
                assert dir.isDirectory();

                ret = new File(dir.getPath() + File.separator + "index.html");
                if (ret.isFile())
                {
                        return ret;
                }

                ret = new File(dir.getPath() + File.separator + "index.htm");
                if (ret.isFile())
                {
                        return ret;
                }

                ret = new File(dir.getPath() + File.separator + "index.xhtml");
                if (ret.isFile())
                {
                        return ret;
                }

                ret = new File(dir.getPath() + File.separator + "index.txt");
                if (ret.isFile())
                {
                        return ret;
                }

                return null;
        }
}
