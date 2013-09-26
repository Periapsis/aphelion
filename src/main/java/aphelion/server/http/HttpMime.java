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
import java.util.HashMap;

/**
 * Mime types of common open formats.
 *
 * @author Joris
 */
public class HttpMime
{
        private static final HashMap<String, String> mime = new HashMap<String, String>();

        static
        {
                // Archives
                mime.put("7z", "application/x-7z-compressed");
                mime.put("jar", "application/java-archive");
                mime.put("zip", "application/zip");

                // Images
                mime.put("bmp", "image/x-ms-bmp");
                mime.put("gif", "image/gif");
                mime.put("jpe", "image/jpeg");
                mime.put("jpeg", "image/jpeg");
                mime.put("jpg", "image/jpeg");
                mime.put("png", "image/png");
                mime.put("svg", "image/svg+xml");
                mime.put("svgz", "image/svg+xml");
                mime.put("tif", "image/tiff");
                mime.put("tiff", "image/tiff");

                // Source code
                mime.put("css", "text/css; charset=UTF-8");
                mime.put("htc", "text/x-component; charset=UTF-8");
                mime.put("htm", "text/html; charset=UTF-8");
                mime.put("html", "text/html; charset=UTF-8");
                mime.put("java", "text/x-java; charset=UTF-8");
                mime.put("js", "application/javascript; charset=UTF-8");
                mime.put("mml", "text/mathml; charset=UTF-8");
                mime.put("rdf", "application/rdf+xml; charset=UTF-8");
                mime.put("rss", "application/rss+xml; charset=UTF-8");
                mime.put("shtml", "text/html; charset=UTF-8");
                mime.put("xht", "application/xhtml+xml; charset=UTF-8");
                mime.put("xhtml", "application/xhtml+xml; charset=UTF-8");
                mime.put("xml", "application/xml; charset=UTF-8");
                mime.put("xsd", "application/xml; charset=UTF-8");
                mime.put("xsd", "application/xml; charset=UTF-8");
                mime.put("xsl", "application/xml-dtd; charset=UTF-8");
                mime.put("xspf", "application/xspf+xml; charset=UTF-8");
                mime.put("xul", "application/vnd.mozilla.xul+xml; charset=UTF-8");

                // Plain text & Config
                mime.put("cfg", "text/plain; charset=UTF-8");
                mime.put("conf", "text/plain; charset=UTF-8");
                mime.put("csv", "text/csv; charset=UTF-8");
                mime.put("ini", "text/plain; charset=UTF-8");
                mime.put("set", "text/plain; charset=UTF-8");
                mime.put("text", "text/plain; charset=UTF-8");
                mime.put("txt", "text/plain; charset=UTF-8");
                mime.put("yaml", "text/x-yaml; charset=UTF-8");

                // Java
                mime.put("class", "application/java-vm");
                mime.put("ser", "application/java-serialized-object");

                // Fonts
                mime.put("woff", "application/x-font-woff");
                mime.put("ttf", "application/x-font-ttf");




                // Audio
                mime.put("flac", "audio/flac");
                mime.put("kar", "audio/midi");
                mime.put("mid", "audio/midi");
                mime.put("midi", "audio/midi");
                mime.put("oga", "audio/ogg");
                mime.put("ogg", "audio/ogg");
                mime.put("spx", "audio/ogg");
                mime.put("wav", "audio/x-wav");

                // Video
                mime.put("ogv", "video/ogg");
                mime.put("mng", "video/x-mng");

                // Torrent
                mime.put("torrent", "application/x-bittorrent");

                // LibreOffice
                mime.put("odb", "application/vnd.oasis.opendocument.database");
                mime.put("odf", "application/vnd.oasis.opendocument.formula");
                mime.put("odg", "application/vnd.oasis.opendocument.graphics");
                mime.put("odi", "application/vnd.oasis.opendocument.image");
                mime.put("odm", "application/vnd.oasis.opendocument.text-master");
                mime.put("odp", "application/vnd.oasis.opendocument.presentation");
                mime.put("ods", "application/vnd.oasis.opendocument.spreadsheet");
                mime.put("odt", "application/vnd.oasis.opendocument.text");
                mime.put("otg", "application/vnd.oasis.opendocument.graphics-template");
                mime.put("oth", "application/vnd.oasis.opendocument.text-web");
                mime.put("otp", "application/vnd.oasis.opendocument.presentation-template");
                mime.put("ots", "application/vnd.oasis.opendocument.spreadsheet-template");
                mime.put("ott", "application/vnd.oasis.opendocument.text-template");
                mime.put("odc", "application/vnd.oasis.opendocument.chart");

                // Adobe crap
                mime.put("pdf", "application/pdf");
        }

        /** Gets a mimetype string by file extension.
         * @param extension A file extension without a dot. for example "html"
         * @return A mime type string, for example "text/html; charset=UTF-8"
         */
        @ThreadSafe // The HashMap can not be modified after static initialization and is thus thread safe (http://docs.oracle.com/javase/6/docs/api/java/util/HashMap.html)
        public static String getMime(String extension)
        {
                String m = mime.get(extension);
                if (m == null)
                {
                        return "application/octet-stream";
                }
                return m;
        }

        /** Gets a mimetype string by file name.
         * @param file 
         * @return A mime type string, for example "text/html; charset=UTF-8"
         */
        @ThreadSafe
        public static String getMime(File file)
        {
                String path = file.getPath();
                int i = path.lastIndexOf('.');
                if (i < 0)
                {
                        return "application/octet-stream";
                }

                return getMime(path.substring(i + 1));
        }
}
