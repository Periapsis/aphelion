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
package aphelion.client.graphics;

import aphelion.client.graphics.scenario.*;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

/**
 *
 * @author Joris
 */
public class ScenarioSelector extends JFrame
{
        private final ScenarioSelectorListener listener;
        private final ConfigPanel configPanel;
        
        public ScenarioSelector(ScenarioSelectorListener listener) throws HeadlessException
        {
                this.listener = listener;
                setTitle("Scenario selector");
                setSize(600, 500);
                setLayout(new GridBagLayout());
                setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                
                JPanel buttons = new JPanel();
                buttons.setLayout(new GridLayout(0, 3));
                buttons.add(new ScenarioButton(FlyHorizontalLine.class));
                buttons.add(new ScenarioButton(SimpleHit.class));
                buttons.add(new ScenarioButton(DeadLocalHitWall.class));
                buttons.add(new ScenarioButton(HighSpeedTurning500msLag.class));
                buttons.add(new ScenarioButton(UnhitRemote.class));
                buttons.add(new ScenarioButton(UnhitLocal.class));
                buttons.add(new ScenarioButton(UnhitRemoteHitLocal.class));
                buttons.add(new ScenarioButton(UndeadRemote.class));
                buttons.add(new ScenarioButton(UndeadLocal.class));
                buttons.add(new ScenarioButton(UndeadRemoteHitLocal.class));
                buttons.add(new ScenarioButton(UnmissRemote.class));
                buttons.add(new ScenarioButton(UnmissLocal.class));
                buttons.add(new ScenarioButton(UnmissDeadRemote.class));
                buttons.add(new ScenarioButton(UnmissDeadLocal.class));
                
                GridBagConstraints c;
                c = new GridBagConstraints();
                c.gridx = 0;
                c.gridy = 0;
                c.fill = GridBagConstraints.HORIZONTAL;
                c.weightx = 1;
                c.weighty = 1;
                add(buttons, c);
                
                c = new GridBagConstraints();
                c.gridx = 0;
                c.gridy = 1;
                c.fill = GridBagConstraints.BOTH;
                c.weightx = 1;
                c.weighty = 0.5;
                add(configPanel = new ConfigPanel(), c);
        }
        
        
        
        private class ScenarioButton extends JButton
        {
                public ScenarioButton(final Class klas)
                {
                        super(klas.getSimpleName());
                        
                        if (!Scenario.class.isAssignableFrom(klas))
                        {
                                throw new IllegalArgumentException();
                        }
                        
                        addActionListener(new ActionListener()
                        {
                                @Override
                                public void actionPerformed(ActionEvent e)
                                {
                                        listener.selected(klas, configPanel.getValue(), configPanel.isDualRunner());
                                }
                        });
                }
        }
        
        public static interface ScenarioSelectorListener
        {
                void selected(Class klass, String config, boolean dualRunner);
        }
}
