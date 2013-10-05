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


import aphelion.server.http.HttpServer;
import aphelion.shared.event.Deadlock;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;

/**
 *
 * @author Joris
 */
public class ServerDaemon implements Daemon
{
        private static final Logger log = Logger.getLogger("aphelion.daemon");

        private ServerSocketChannel ssChannel;
        private AphelionServerThread serverThread;
        
        @Override
        public void init(DaemonContext dc) throws DaemonInitException, Exception
        {
                // At this point we are running as a super user. 
                // Do priviliged stuff like opening a socket ports < 1024
                
                String[] args = dc.getArguments();
                
                String hostname;
                int port;
                
                if (args.length >= 1)
                {
                        port = Integer.parseInt(args[0]); // may throw
                }
                else
                {
                        port = 80;
                }
                
                if (args.length >= 2)
                {
                        hostname = args[1];
                }
                else
                {
                        hostname = "0.0.0.0";
                }
                
                InetSocketAddress listen = new InetSocketAddress(hostname, port);
                this.ssChannel = HttpServer.openServerChannel(listen);
                
                Deadlock.start(false, null);
        }

        @Override
        public void start() throws Exception
        {
                // No longer super user
                // This method must return after starting threads
                
                log.log(Level.INFO, "Deamon start");
                
                serverThread = new AphelionServerThread(false, this.ssChannel);
                serverThread.start();
        }

        @Override
        public void stop() throws Exception
        {
                log.log(Level.INFO, "Deamon stop");
                serverThread.stopServer();
        }

        @Override
        public void destroy()
        {
                // start and stop may be called any number of times, but destroy only once
                // do stuff like freeing sockets here
                
                Deadlock.stop();
                
                try
                {
                        ssChannel.close();
                }
                catch (IOException ex)
                {
                        log.log(Level.SEVERE, null, ex);
                }
        }
        
        
}
