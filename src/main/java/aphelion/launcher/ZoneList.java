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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

/**
 *
 * @author Joris
 */
public class ZoneList extends JPanel
{
        private static final Logger log = Logger.getLogger("aphelion.launcher");
        private final ArrayList<ZoneEntry> entries;
        private JScrollPane scroll;
        private MyTableModel modal;
        private MyTable table;

        public ZoneList(final ArrayList<ZoneEntry> entries)
        {
                table = new MyTable(modal = new MyTableModel(entries), entries);
                scroll = new JScrollPane(table);
                add(scroll);
                this.entries = entries;
                
                scroll.setBorder(BorderFactory.createEmptyBorder());
                table.setShowGrid(false);
                
                table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                table.setColumnSelectionAllowed(false);
                table.setRowSelectionAllowed(true);
                
                table.getColumnModel().getColumn(0).setMinWidth(20);
                table.getColumnModel().getColumn(1).setMinWidth(30);
                table.getColumnModel().getColumn(3).setMinWidth(30);
                table.getColumnModel().getColumn(4).setMinWidth(30);
                
                table.getColumnModel().getColumn(0).setMaxWidth(60);
                table.getColumnModel().getColumn(1).setMaxWidth(60);
                table.getColumnModel().getColumn(3).setMaxWidth(60);
                table.getColumnModel().getColumn(4).setMaxWidth(60);
                
                

                addComponentListener(new ComponentAdapter()
                {
                        @Override
                        public void componentResized(final ComponentEvent e)
                        {
                                Dimension size = new Dimension();
                                getSize(size);
                                size.height -= 4; // TODO: ???
                                
                                scroll.setMaximumSize(size);
                                scroll.setPreferredSize(size);
                        }
                });
                
                table.addMouseListener(new MouseAdapter()
                {

                        @Override
                        public void mouseClicked(MouseEvent e)
                        {
                                Point p = e.getPoint();
                                int rowIndex = table.convertRowIndexToModel(table.rowAtPoint(p));
                                int realColumnIndex = table.convertColumnIndexToModel(table.columnAtPoint(p));
                                
                                if (realColumnIndex == 0) // favorite
                                {
                                        ZoneEntry entry = getEntryByRow(rowIndex);
                                        if (entry != null)
                                        {
                                                entry.favorite = !entry.favorite;
                                                updatedEntries();
                                        }
                                }
                        }
                });
        }
        
        private ZoneEntry getEntryByRow(int row)
        {
                if (row >= 0 && row < entries.size())
                {
                        return entries.get(row);
                }
                
                return null;
        }
        
        public void updatedEntries()
        {
                int r = table.getSelectedRow();
                modal.fireTableDataChanged();
                if (r >= 0 &&r < entries.size())
                {
                        table.getSelectionModel().setSelectionInterval(r, r);
                }
        }
        
        public ZoneEntry getSelectedZoneEntry()
        {
                return getEntryByRow(table.getSelectedRow());
        }
        
        public void addSelectionListener(ListSelectionListener e)
        {
                table.getSelectionModel().addListSelectionListener(e);
        }

        private static class MyTable extends JTable
        {
                private ArrayList<ZoneEntry> entries;

                MyTable(MyTableModel modal, ArrayList<ZoneEntry> entries)
                {
                        super(modal);
                        this.entries = entries;
                        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                }

                @Override
                public Component prepareRenderer(TableCellRenderer renderer, int row, int column)
                {       
                        Component c = super.prepareRenderer(renderer, row, column);

                        ZoneEntry entry = null;
                        
                        try
                        {
                                entry = entries.get(convertRowIndexToModel(row));
                        }
                        catch (IndexOutOfBoundsException ex)
                        {
                        }
                        
                        if (!(c instanceof JLabel))
                        {
                                return c;
                        }
                        
                        JLabel label = (JLabel) c;
                        label.setBorder(BorderFactory.createEmptyBorder()); // Hide the cell selection border
                        
                        final Color star = new Color(0x958800);
                        final Color serverDown = new Color(0x990000);
                        
                        if (column == 0)
                        {
                                label.setForeground(star);
                                label.setHorizontalAlignment(SwingConstants.CENTER);
                        }
                        else
                        {
                                boolean isServerDown = entry != null && entry.url != null && entry.ping < 0;
                                
                                label.setForeground(isServerDown ? serverDown : Color.BLACK);
                        }
                        
                        
                        
                        return c;
                }

                @Override
                public boolean getScrollableTracksViewportWidth()
                {
                        // if this table is smaller than the viewport, increase the size,
                        // otherwise use scrollbars
                        return getPreferredSize().width < getParent().getWidth();
                }

                @Override
                public String getToolTipText(MouseEvent e)
                {
                        java.awt.Point p = e.getPoint();
                        int rowIndex = convertRowIndexToModel(rowAtPoint(p));
                        //int colIndex = columnAtPoint(p);
                        //int realColumnIndex = convertColumnIndexToModel(colIndex);

                        try
                        {
                                ZoneEntry entry = entries.get(rowIndex);
                                return entry.url == null ? null : entry.url.toString();
                        }
                        catch (IndexOutOfBoundsException ex)
                        {
                                return null;
                        }
                }
        }

        private static class MyTableModel extends AbstractTableModel
        {
                private ArrayList<ZoneEntry> entries;

                public MyTableModel(ArrayList<ZoneEntry> entries)
                {
                        this.entries = entries;
                }

                @Override
                public String getColumnName(int column)
                {
                        switch (column)
                        {
                                case 0:
                                        return "Favorite";
                                case 1:
                                        return "Ping";
                                case 2:
                                        return "Zone name";
                                case 3:
                                        return "Players";
                                case 4:
                                        return "Playing";
                        }

                        return "???";

                }

                @Override
                public int getColumnCount()
                {
                        return 5;
                }

                @Override
                public boolean isCellEditable(int rowIndex, int columnIndex)
                {
                        return false;
                }

                @Override
                public int getRowCount()
                {
                        return entries.size();
                }

                @Override
                public Object getValueAt(int rowIndex, int columnIndex)
                {
                        if (rowIndex >= entries.size())
                        {
                                return ""; // todo other types
                        }
                        
                        ZoneEntry entry = entries.get(rowIndex);

                        switch (columnIndex)
                        {
                                case 0:
                                        return entry.favorite ? "\u2605" : "\u2606";
                                case 1:
                                        return entry.ping < 0 ? "" : entry.ping;
                                case 2:
                                        return entry.name;
                                case 3:
                                        return entry.players < 0 ? "" : entry.players;
                                case 4:
                                        return entry.playing < 0 ? "" : entry.playing;
                        }

                        return "???";
                }
        }
}
