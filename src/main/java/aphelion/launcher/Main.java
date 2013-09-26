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

package aphelion.launcher;


import aphelion.client.Client;
import aphelion.shared.swissarmyknife.ThreadSafe;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

/**
 *
 * @author Joris
 */
public class Main
{
        private static final Logger log = Logger.getLogger(Main.class.getName());
        
        private static final ActionListener playAction = new ActionListener()
        {
                @Override
                public void actionPerformed(ActionEvent e) // swing thread
                {
                        ZoneEntry entry = mainFrame.getSelectedZoneEntry();

                        if (entry == null)
                        {
                                return;
                        }
                        
                        String username = mainFrame.getUserName();
                        if (username.isEmpty())
                        {
                                JOptionPane.showMessageDialog(mainFrame, "Please think up a name for yourself before playing", "Aphelion", JOptionPane.ERROR_MESSAGE);
                                return;
                        }
                        
                        runUri = entry.url;
                        client = new Client();
                }
        };
        
        private static final WindowListener windowListener = new WindowAdapter()
        {
                @Override
                public void windowClosed(WindowEvent e)
                {
                        mainThread.interrupt();
                }
        };
        
        private final static ArrayList<BufferedImage> frameIcons = new ArrayList<>(6);
        
        /** Get or load the main icon for frames.
         * Blocks
         * @return 
         */
        @ThreadSafe
        public static ArrayList<BufferedImage> getFrameIcons()
        {
                synchronized (frameIcons)
                {
                        if (frameIcons.isEmpty())
                        {
                                try
                                {
                                        frameIcons.add(ImageIO.read(Main.class.getResourceAsStream("/aphelion/launcher/subspace16.png")));
                                        frameIcons.add(ImageIO.read(Main.class.getResourceAsStream("/aphelion/launcher/subspace32.png")));
                                        frameIcons.add(ImageIO.read(Main.class.getResourceAsStream("/aphelion/launcher/subspace48.png")));
                                        frameIcons.add(ImageIO.read(Main.class.getResourceAsStream("/aphelion/launcher/subspace64.png")));
                                        frameIcons.add(ImageIO.read(Main.class.getResourceAsStream("/aphelion/launcher/subspace128.png")));
                                        frameIcons.add(ImageIO.read(Main.class.getResourceAsStream("/aphelion/launcher/subspace256.png")));
                                }
                                catch (IOException ex)
                                {
                                        log.log(Level.WARNING, "Unable to load frame icon", ex);
                                }
                        }

                        return frameIcons;
                }
        }
        
        @ThreadSafe
        public static void getFrameIcons(ByteBuffer[] bufferList)
        {
                ArrayList<BufferedImage> icons = getFrameIcons();
                
                for (int i = 0; i < bufferList.length && i < icons.size(); ++i)
                {
                        BufferedImage image = icons.get(i);
                        byte[] bytes = new byte[image.getWidth() * image.getHeight() * 4];
                        
                        for (int y = 0; y < image.getHeight(); y++)
                        {
                                for (int x = 0; x < image.getWidth(); x++)
                                {
                                        int pixel = image.getRGB(x, y);
                                        for (int k = 0; k < 3; k++) // red, green, blue
                                        {
                                                bytes[(y*16+x)*4 + k] = (byte)(((pixel>>(2-k)*8))&255);
                                        }
                                        bytes[(y*16+x)*4 + 3] = (byte)(((pixel>>(3)*8))&255); // alpha
                                }
                        }
                        
                        bufferList[i] = ByteBuffer.wrap(bytes);
                }
        }
        

        private static Thread mainThread;
        private static MainFrame mainFrame;
        private static volatile Client client = null;
        private static volatile URI runUri = null;
        
        public static void main(String[] args) throws Exception
        {
                mainThread = Thread.currentThread();
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                
                
                while (!mainThread.isInterrupted())
                {
                        if (mainFrame == null)
                        {
                                mainFrame = new MainFrame();
                                mainFrame.addPlayActionListener(playAction);
                                mainFrame.addWindowListener(windowListener);
                                mainFrame.setVisible(true);
                        }
                        
                        if (client != null)
                        {
                                String username = mainFrame.getUserName();
                                mainFrame.dispose();
                                mainFrame = null;
                                client.run(runUri, username); // blocks as long as the game runs
                                client = null;
                                runUri = null;
                        }
                        else
                        {
                                try 
                                {
                                        Thread.sleep(100);
                                }
                                catch (InterruptedException ex)
                                {
                                        return;
                                }
                        }
                }
        }
}
