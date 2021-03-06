/*
 * Copyright 2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rioproject.tools.ui.prefs;

import org.rioproject.tools.ui.*;
import org.rioproject.resources.ui.Util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;

/**
 * A dialog for allowing the user to provide preferences like color for
 * displayed elements
 *
 * @author Dennis Reedy
 */
public class PreferencesDialog extends JDialog {

    public PreferencesDialog(final Main frame,
                             final GraphView graphView,
                             final CybernodeUtilizationPanel cybernodeUtil) {
        super(frame);
        String frameTitle = frame.getTitle();
        frameTitle = (frameTitle.equals("")?"Rio UI":frameTitle);
        setTitle(frameTitle+" Preferences");

        JPanel pane = new JPanel();
        pane.setLayout(new BorderLayout(8, 8));
        final ColorManager colorManager = graphView.getColorManager();
        final ColorPanel colorPanel =
            new ColorPanel(colorManager.getAltRowColor(),
                           colorManager.getFailureColor(),
                           colorManager.getOkayColor(),
                           colorManager.getWarningColor());

        final TableSortPanel tableSortPanel =
            new TableSortPanel(cybernodeUtil.getAutoSort());

        final CybernodePanel cybernodePanel =
            new CybernodePanel(frame.getCybernodeRefreshRate(),
                               frame.getUtilizationColumnManager().getSelectedColumns());

        final JTabbedPane tabs = new JTabbedPane();
        
        tabs.add("Utilization Table", cybernodePanel);
        tabs.add("Color Settings", colorPanel);
        tabs.add("Sort Options", tableSortPanel);
        
        //pane.add(colorPanel, BorderLayout.CENTER);
        pane.add(tabs, BorderLayout.CENTER);
        JButton okay = new JButton("Okay");
        okay.setToolTipText("Accept settings and close dialog");
        okay.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                boolean runVis = false;

                int refreshRate = cybernodePanel.getRefreshRate();
                if(refreshRate==0) {
                    JOptionPane.showMessageDialog(null,
                                                  "The Cybernode Table " +
                                                  "RefreshRate must be > 0",
                                                  "Invalid Value",
                                                  JOptionPane.ERROR_MESSAGE);
                    tabs.setSelectedIndex(2);
                    return;
                }

                if(!colorManager.getAltRowColor().equals(
                    colorPanel.getAltRowColor()))
                    colorManager.setAltRowColor(colorPanel.getAltRowColor());

                if(!colorManager.getFailureColor().equals(
                    colorPanel.getFailureColor())) {
                    colorManager.setFailureColor(colorPanel.getFailureColor());
                    runVis = true;
                }

                if(!colorManager.getOkayColor().equals(
                    colorPanel.getOkayColor())) {
                    colorManager.setOkayColor(colorPanel.getOkayColor());
                    runVis = true;
                }

                if(!colorManager.getWarningColor().equals(
                    colorPanel.getWarningColor())) {
                    colorManager.setWarningColor(colorPanel.getWarningColor());
                    runVis = true;
                }

                frame.setCybernodeRefreshRate(cybernodePanel.getRefreshRate());
                if(runVis)
                    graphView.getVisualization().run("repaint");

                String[] cols = cybernodePanel.getSelectedColumns();
                frame.getUtilizationColumnManager().setSelectedColumns(cols);
                cybernodeUtil.setSelectedColumns(cols);
                cybernodeUtil.setAutoSort(tableSortPanel.getAutoSort());
                cybernodeUtil.sortTable();

                //System.out.println(getSize());
                dispose();
            }
        });
        JButton cancel = new JButton("Cancel");
        cancel.setToolTipText("Close the dialog without changing any settings");
        cancel.addActionListener(new Util.DisposeActionListener(this));
        JButton reset = new JButton("Reset");
        reset.setToolTipText("Restore system settings");
        reset.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent event) {
                Map<String, Color> colorMap = colorManager.getDefaultColorMap();
                colorPanel.setAltRowColor(colorMap.get(Constants.ALT_ROW_COLOR));
                colorPanel.setFailureColor(colorMap.get(Constants.FAILURE_COLOR));
                colorPanel.setOkayColor(colorMap.get(Constants.OKAY_COLOR));
                colorPanel.setWarningColor(colorMap.get(Constants.WARNING_COLOR));
            }
        });
        JPanel buttonPane = new JPanel();
        buttonPane.add(okay);
        buttonPane.add(cancel);
        buttonPane.add(reset);
        pane.add(buttonPane, BorderLayout.SOUTH);
        getContentPane().add(pane);
    }

    public void setVisible(boolean visible) {
        if(visible) {
            int width = 600;
            int height = 435;
            pack();
            setSize(width, height);
            Dimension screenSize =
                Toolkit.getDefaultToolkit().getScreenSize();
            int widthLoc = screenSize.width / 2 - width / 2;
            int heightLoc = screenSize.height / 2 - height / 2;
            setLocation(widthLoc, heightLoc);
        }
        super.setVisible(visible);
    }

    class DisposeActionListener implements ActionListener {
        JDialog dialog;

        public DisposeActionListener(JDialog dialog) {
            this.dialog = dialog;
        }

        public void actionPerformed(ActionEvent ae) {
            //System.out.println("Size="+dialog.getSize());
            //System.out.println("Source="+ae.getSource());
            dialog.dispose();
        }
    }

}
