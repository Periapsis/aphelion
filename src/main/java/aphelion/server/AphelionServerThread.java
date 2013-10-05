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
package aphelion.server;

import aphelion.client.ErrorDialog;
import aphelion.server.http.HttpServer;
import aphelion.shared.swissarmyknife.ThreadSafe;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class AphelionServerThread extends Thread
{
        private static final Logger log = Logger.getLogger("aphelion.server");
        private ServerMain serverMain;
        private int listenPort;
        private boolean gui;
        
        private ServerSocketChannel privateSsChannel; // If we opened it ourselves, we need to close it too

        public AphelionServerThread(boolean gui) throws IOException
        {
                super();

                this.gui = gui;
                // use an ephemeral port. aka a temporary port number

                privateSsChannel = HttpServer.openServerChannel(new InetSocketAddress("127.0.0.1", 0));
                
                serverMain = new ServerMain(privateSsChannel);
                // run setup() in this thread so that a client thread will be able to connect immediately
                serverMain.setup();
                listenPort = serverMain.getHTTPListeningPort();
        }
        
        public AphelionServerThread(boolean gui, ServerSocketChannel ssChannel) throws IOException
        {
                super();

                this.gui = gui;
                // use an ephemeral port. aka a temporary port number

                serverMain = new ServerMain(ssChannel);
                // run setup() in this thread so that a client thread will be able to connect immediately
                serverMain.setup();
                listenPort = serverMain.getHTTPListeningPort();
        }

        public int getHTTPListeningPort()
        {
                return listenPort;
        }

        @Override
        public void run()
        {
                this.setName("AphelionServerThread-" + this.getId());
                try
                {
                        serverMain.run();
                }
                catch (Throwable ex)
                {
                        log.log(Level.SEVERE, "", ex);
                        if (gui)
                        {
                                new ErrorDialog().setErrorText(ex);
                        }
                        throw ex;
                }
        }

        @ThreadSafe
        public void stopServer()
        {
                serverMain.stop();
                try
                {
                        if (privateSsChannel != null)
                        {
                                privateSsChannel.close();
                        }
                }
                catch (IOException ex)
                {
                        log.log(Level.SEVERE, null, ex);
                }
        }
}
