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


import aphelion.client.net.Ping;
import aphelion.client.net.PingListener;
import aphelion.shared.swissarmyknife.ThreadSafe;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 *
 * @author Joris
 */
public class MainFrame extends JFrame implements PingListener
{
        private static final Logger log = Logger.getLogger(MainFrame.class.getName());

        private ArrayList<ZoneEntry> zones = new ArrayList<>();
        private Ping ping;
        
        private JTextArea zoneDesc;
        
        private ZoneList zoneList;
        private JButton playButton;
        private JButton quitButton;
        
        private JTextField username;
        private JTextField password;
        
        public MainFrame() throws HeadlessException
        {
                JPanel wrap;
                JPanel left;
                JPanel right;
                JPanel profile;
                JPanel profile_username;
                JPanel profile_password;
                JPanel zonelistButtons;
                
                ping = new Ping(this);
                
                this.setIconImages(Main.getFrameIcons());
                this.setTitle("Aphelion");
                
                add(wrap = new JPanel());
                wrap.setBorder(new EmptyBorder(10, 10, 10, 10) );
                wrap.setLayout(new GridLayout(0, 2, 10, 10));
                
                wrap.add(left = new JPanel());
                wrap.add(right = new JPanel());
                left.setLayout(new GridLayout(0, 1));
                right.setLayout(new BorderLayout(10, 10));
                
                // todo: order by favorite first
                // display warning while unfavoriting if the entry is a saved favorite (not if it was just added)
                
                left.add(profile = new JPanel());
                profile.setLayout(new GridLayout(0, 1, 10, 10));
                profile.add(profile_username = new JPanel());
                profile_username.setLayout(new GridLayout(1, 2));
                profile_username.add(new JLabel("Username:"));
                profile_username.add(username = new JFormattedTextField(usernameFormatFactory));
                profile.add(profile_password = new JPanel());
                profile_password.setLayout(new GridLayout(1, 2));
                profile_password.add(new JLabel("Password:"));
                profile_password.add(password = new JTextField("(not implemented yet)"));
                profile.add(new JPanel());
                profile.add(new JPanel());
                profile.add(new JPanel());
                
                left.add(zoneDesc = new JTextArea());
                zoneDesc.setEditable(false);
                zoneDesc.setWrapStyleWord(true);
                zoneDesc.setLineWrap(true);
                
                right.add(zoneList = new ZoneList(zones), BorderLayout.CENTER);
                right.add(zonelistButtons = new JPanel(), BorderLayout.SOUTH);
                zonelistButtons.setLayout(new GridLayout(1, 0, 10, 10));
                zonelistButtons.add(playButton = new JButton("Play"));
                zonelistButtons.add(quitButton = new JButton("Quit"));
                
                setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                setSize(700, 400);
                setLocationRelativeTo(null);
                
                quitButton.addActionListener(new ActionListener()
                {
                        @Override
                        public void actionPerformed(ActionEvent e)
                        {
                                dispose();
                        }
                });
                
                addWindowListener(null);
                
                zoneList.addSelectionListener(new ListSelectionListener()
                {
                        @Override
                        public void valueChanged(ListSelectionEvent e)
                        {
                                ZoneEntry entry = zoneList.getSelectedZoneEntry();
                                zoneDesc.setText(entry == null ? "" : entry.description);
                        }
                });
                
                
                addWindowListener(new WindowAdapter()
                {
                        @Override
                        public void windowClosed(WindowEvent e)
                        {
                                ping.interrupt();
                                ping = null;
                        }
                });
                
                // TODO: from file or something
                addZoneEntry(true, null, "singeplayer", "Try a local game without any opponents!");
                try
                {
                        addZoneEntry(true, new URI("ws://localhost:80/aphelion"), "localhost", "Test your own locally hosted game server");
                        addZoneEntry(false, new URI("ws://aphelion-test.welcome-to-the-machine.com:81/aphelion"), "Test server", "JoWie's test server in Europe!");
                }
                catch (URISyntaxException ex)
                {
                        throw new Error(ex);
                }
                updatedZoneEntries();
                ping.start();
        }
        
        private static JFormattedTextField.AbstractFormatterFactory usernameFormatFactory = new JFormattedTextField.AbstractFormatterFactory()
        {
                private Pattern firstChar = Pattern.compile("[a-z]");
                private Pattern otherChars = Pattern.compile("[a-zA-Z0-9\\\\-\\\\[\\\\]\\\\\\\\`^{}_ ]*");
                
                @Override
                public JFormattedTextField.AbstractFormatter getFormatter(JFormattedTextField tf)
                {
                        return new JFormattedTextField.AbstractFormatter()
                        {
                                @Override
                                public Object stringToValue(String text) throws ParseException
                                {
                                        // legal: [a-z][a-zA-Z0-9\-\[\]\\`^{}_ ]*
                                        
                                        if (text == null || text.isEmpty())
                                        {
                                                return text;
                                        }
                                        
                                        StringBuilder ret = new StringBuilder(text.length());
                                        for (int i = 0; i < text.length(); ++i)
                                        {
                                                char c = text.charAt(i);
                                                
                                                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))
                                                {
                                                        // always legal
                                                        ret.append(c);
                                                        continue;
                                                }
                                                
                                                if (ret.length() > 0) // not the first char
                                                {
                                                        if (c >= '0' && c <= '9')
                                                        {
                                                                ret.append(c);
                                                                continue;
                                                        }
                                                        
                                                        if (   c == '-'
                                                            || c == '['
                                                            || c == ']'
                                                            || c == '\\'
                                                            || c == '`'
                                                            || c == '^'
                                                            || c == '{'
                                                            || c == '}'
                                                            || c == '_'
                                                            || c == ' ')
                                                        {
                                                                ret.append(c);
                                                                continue;
                                                        }
                                                }
                                        }
                                        
                                        return ret.toString();
                                }

                                @Override
                                public String valueToString(Object value) throws ParseException
                                {
                                        return (String) value;
                                }
                                
                        };
                }
        };
        
        public final void addZoneEntry(boolean favorite, URI url, String name, String description)
        {
                zones.add(new ZoneEntry(favorite, url, name, description));
                if (url != null)
                {
                        ping.startPing(url);
                }
        }
        
        /** Always call me after calling addZoneEntry 1 or more times. */
        public final void updatedZoneEntries()
        {
                zoneList.updatedEntries();
        }
        
        public String getUserName()
        {
                return username.getText();
        }
        
        public void addPlayActionListener(ActionListener l)
        {
                playButton.addActionListener(l);
        }
        
        public ZoneEntry getSelectedZoneEntry()
        {
                return zoneList.getSelectedZoneEntry();
        }
        
        public void addPingResult(URI uri, long rttNanos, int players, int playing)
        {
                for (ZoneEntry entry : zones)
                {
                        if (entry.url != null && entry.url.equals(uri))
                        {
                                entry.ping = rttNanos > 0 ? (int) (rttNanos / 1_000_000L) : -1;
                                entry.players = players;
                                entry.playing = playing;
                        }
                        
                        updatedZoneEntries();
                }
        }

        @Override
        @ThreadSafe
        public void pingResult(final URI uri, final long rttNanos, final int players, final int playing)
        {
                // run in swing thread
                SwingUtilities.invokeLater(new Runnable()
                {
                        @Override
                        public void run()
                        {
                                addPingResult(uri, rttNanos, players, playing);
                        }
                        
                });
                
        }
}
