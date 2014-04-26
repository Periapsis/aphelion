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
 * different from the original version
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
 * If you modify this library, you may extend this exception to your version
 * of the library, but you are not obliged to do so. If you do not wish to do
 * so, delete this exception statement from your version.
 */

package aphelion.client.graphics;

import aphelion.client.Client;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;



/**
 *
 * @author Joris
 */
public class ConfigPanel extends JPanel
{
        private final HashMap<String, JComponent> fields = new HashMap<>();
        private final JCheckBox debug;
        private final JCheckBox dualRunner;
        
        public ConfigPanel()
        {
                setLayout(new GridLayout(0, 2));
                
                add(new JLabel("debug"));
                add(debug = new JCheckBox());
                add(new JLabel("DualRunner"));
                add(dualRunner = new JCheckBox());
                
                addIntegerConfig("render-delay", 0);
                addIntegerConfig("render-delay-latency-ratio", 0);
                addBooleanConfig("render-delay-projectiles", true);
                addBooleanConfig("render-delay-maximize-local-time", false);
                addIntegerConfig("render-delay-update-ship-delay-every-ticks", 50);
                addIntegerConfig("render-delay-update-projectile-delay-every-ticks", 2);
                
                addEnumConfig("smoothing-algorithm", new String[]{"NONE", "LINEAR"}, "NONE");
                addIntegerConfig("smoothing-look-ahead-ticks", 10);
                addIntegerConfig("smoothing-step-ratio", 100000);
                addIntegerConfig("smoothing-distance-limit", 500);
                addBooleanConfig("smoothing-projectile-collisions", true);
                
                debug.addChangeListener(new ChangeListener()
                {
                        @Override
                        public void stateChanged(ChangeEvent e)
                        {
                                Client.showDebug = debug.isSelected();
                        }
                });
        }
        
        public boolean isDualRunner()
        {
                return dualRunner.isSelected();
        }
        
        private void addIntegerConfig(String key, int def)
        {
                JTextField field = new JFormattedTextField(NumberFormat.getIntegerInstance());
                add(new JLabel(key));
                add(field);
                field.setText(Integer.toString(def));
                fields.put(key, field);
        }
        
        private void addBooleanConfig(String key, boolean def)
        {
                JCheckBox field = new JCheckBox();
                add(new JLabel(key));
                add(field);
                field.setSelected(def);
                fields.put(key, field);
        }
        
        private void addEnumConfig(String key, String[] values, String def)
        {
                JComboBox<String> field = new JComboBox<>(values);
                add(new JLabel(key));
                add(field);
                field.setSelectedItem(def);
                fields.put(key, field);
        }
        
        public String getValue()
        {
                StringBuilder ret = new StringBuilder();
                
                boolean first = true;
                
                for (Map.Entry<String, JComponent> e : fields.entrySet())
                {
                        if (first)
                        {
                                ret.append("- selector: {importance: 1}\n");
                                first = false;
                        }
                        
                        ret.append("  ");
                        
                        ret.append(e.getKey());
                        ret.append(": ");
                        
                        JComponent val = e.getValue();
                        if (val instanceof JTextField)
                        {
                                ret.append(((JTextField)val).getText());
                        }
                        else if (val instanceof JCheckBox)
                        {
                                ret.append(((JCheckBox)val).isSelected() ? "true" : "false");
                        }
                        else if (val instanceof JComboBox)
                        {
                                ret.append(((JComboBox<String>)val).getSelectedItem());
                        }
                        else
                        {
                                assert false;
                        }
                        ret.append("\n");
                }
                
                return ret.toString();
        }
}
