/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization.gui;

import nl.tue.curvedstrokeschematization.algo.Renderer.InterchangeStyle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import nl.tue.geometrycore.gui.sidepanel.SideTab;
import nl.tue.geometrycore.gui.sidepanel.TabbedSidePanel;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class SidePanel extends TabbedSidePanel {

    private Data data;
    private JButton buttonRender;
    private JButton buttonInitSchematization;
    private JButton buttonInitAlgorithm;
    private JButton buttonPerformSteps;
    private JButton buttonMergeStrokeAngle;
    private JButton buttonMergeStrokeLine;
    private JButton buttonQuery;
    private JSlider sliderQuery;
    private JLabel labelQuery;
    private JButton buttonClearQuery;
    private JSpinner spinnerRedraw;
    private JSpinner spinnerSteps;
    private JSpinner spinnerNumArcs;
    private JButton buttonRunComplexity;

    public SidePanel(Data data) {
        this.data = data;

        init();
    }

    public void onDataChange() {
        buttonInitSchematization.setEnabled(data.input != null);
        buttonInitAlgorithm.setEnabled(data.schematization != null);
        buttonMergeStrokeAngle.setEnabled(data.schematization != null);
        buttonMergeStrokeLine.setEnabled(data.schematization != null);
        buttonPerformSteps.setEnabled(!data.algorithmstuck);
        buttonRunComplexity.setEnabled(!data.algorithmstuck);
        buttonRender.setEnabled(data.schematization != null);

        if (data.algorithm.getStore() != null && data.algorithm.getStore().isInitialized()) {
            if (sliderQuery.isEnabled()) {
                sliderQuery.setMinimum(data.algorithm.getStore().getMinimumComplexity());
            } else {
                sliderQuery.setEnabled(true);
                sliderQuery.setMinimum(data.algorithm.getStore().getMinimumComplexity());
                sliderQuery.setMaximum(data.algorithm.getStore().getMaximumComplexity());
                sliderQuery.setValue(data.algorithm.getStore().getMinimumComplexity());
                labelQuery.setText("" + sliderQuery.getValue());
                buttonQuery.setEnabled(true);
                buttonClearQuery.setEnabled(true);
            }
        } else {
            sliderQuery.setEnabled(false);
            sliderQuery.setMinimum(0);
            sliderQuery.setMaximum(0);
            sliderQuery.setValue(0);
            labelQuery.setText("");
            buttonQuery.setEnabled(false);
            buttonClearQuery.setEnabled(false);
        }
    }

    private void init() {
        preTab();
        algoTab();
        postTab();
        drawTab();
    }

    private void preTab() {
        SideTab tab = addTab("Pre");

        tab.addButton("Paste from IPE", (e) -> {
            data.pasteIpeMetro();
        });

        tab.addButton("Load GraphML", (e) -> {
            data.loadMetroFile();
        });
        
        
        tab.addButton("Load WKT", (e) -> {
            data.loadWKTfile();
        });

        tab.addButton("Paste rescale box", (e) -> {
            data.rescale();
        });

        final JCheckBox checkboxFromScratch = tab.addCheckbox("Strokes from scratch", false, null);

        tab.addButton("Load & go", (e) -> {
            // load
            data.loadMetroFile();
            long time = System.currentTimeMillis();
            // init strokes
            data.initializeSchematization(checkboxFromScratch.isSelected());
            buttonMergeStrokeAngle.setEnabled(true);
            buttonMergeStrokeLine.setEnabled(true);
            // merge stroke angles
            data.mergeStrokesAngle();
            // init algorithm
            data.initializeAlgorithm();
            // run steps
            data.performSchematizationSteps((Integer) spinnerSteps.getValue(), -1); // (Integer) spinnerRedraw.getValue());
            long posttime = System.currentTimeMillis();
            System.out.println("Duration: " + (posttime - time) + " ms");
        });

        buttonInitSchematization = tab.addButton("Init strokes", (e) -> {
            data.initializeSchematization(checkboxFromScratch.isSelected());
            buttonMergeStrokeAngle.setEnabled(true);
            buttonMergeStrokeLine.setEnabled(true);
        });

        tab.addSpace(10);

        buttonMergeStrokeAngle = tab.addButton("Merge stroke (angle)", (e) -> {
            data.mergeStrokesAngle();
        });

        buttonMergeStrokeAngle.setEnabled(false);

        buttonMergeStrokeLine = tab.addButton("Merge stroke (line)", (e) -> {
            data.mergeStrokesLines();
        });

        buttonMergeStrokeLine.setEnabled(false);
    }

    private void algoTab() {
        SideTab tab = addTab("Algo");

        final JCheckBox checkAllowHighdegree = tab.addCheckbox("Allow high-degree operations", Data.default_allowhighdegree, null);

        final JCheckBox checkStore = tab.addCheckbox("Make store", Data.default_useStore, null);

        tab.makeSplit(4, 2);
        tab.addLabel("Frechet prec");
        final JSpinner spinnerEps = tab.addDoubleSpinner((int) (100 * Data.default_fdeps), 100.5, 200, 0.5, null);

        tab.makeCustomSplit(4, 0.5, 0.4, 0.1);
        tab.addLabel("Cross dist");
        final JSpinner spinnerMaxCrossDist = tab.addDoubleSpinner(100 * Data.default_crossdist, 0, 100, 0.5, (e, v) -> {
            data.crossdist = v / 100.0;
            if (data.drawcrossdist) {
                data.onDataChange();
            }
        });
        tab.addCheckbox("", data.drawcrossdist, (e, v) -> {
            data.drawcrossdist = v;
            data.onDataChange();
        });

        tab.makeSplit(4, 2);
        tab.addLabel("# angles");
        final JSpinner spinnerAngles = tab.addIntegerSpinner(Data.default_angles, 1, 100, 1, null);
        tab.makeSplit(4, 2);
        tab.addLabel("# candidates");
        final JSpinner spinnerCandidates = tab.addIntegerSpinner(Data.default_numCandidates, 1, 100, 1, null);
        
        
        tab.makeSplit(4, 2);
        tab.addLabel("Straight FD");
        final JSpinner spinnerStraight = tab.addDoubleSpinner(Data.default_straightFDfactor, 0, 200, 0.05, null);

        tab.addButton("Set algo", (e) -> {
            data.setAlgorithm(checkAllowHighdegree.isSelected(), checkStore.isSelected(), 
                    (Double) spinnerMaxCrossDist.getValue() / 100.0, 
                    (Integer) spinnerAngles.getValue(), 
                    (Integer) spinnerCandidates.getValue(),  
                    (Double) spinnerStraight.getValue(),
                    (Double) spinnerEps.getValue() / 100.0);
        });

        buttonInitAlgorithm = tab.addButton("Init algo", (e) -> {
            data.initializeAlgorithm();
        });

        tab.makeSplit(4, 2);
        tab.addLabel("Redraw");
        spinnerRedraw = tab.addIntegerSpinner(3, 0, Integer.MAX_VALUE, 5, null);

        tab.makeSplit(4, 2);
        tab.addLabel("Steps");
        spinnerSteps = tab.addIntegerSpinner(1000, 1, Integer.MAX_VALUE, 5, null);

        buttonPerformSteps = tab.addButton("Run steps", (e) -> {
            data.performSchematizationSteps((Integer) spinnerSteps.getValue(), (Integer) spinnerRedraw.getValue());
        });

        tab.makeSplit(4, 2);
        tab.addLabel("# arcs");
        spinnerNumArcs = tab.addIntegerSpinner(4, 1, Integer.MAX_VALUE, 1, null);

        buttonRunComplexity = tab.addButton("Run to #", (e) -> {
            data.performSchematizationComplexity((Integer) spinnerNumArcs.getValue(), (Integer) spinnerRedraw.getValue());
        });

        tab.addSpace(5);

        final JCheckBox autoUpdate = tab.addCheckbox("Auto-update", true, null);

        tab.makeCustomSplit(4, 0.75, 0.25);
        sliderQuery = tab.addIntegerSlider(0, 0, 0, (e, v) -> {
            labelQuery.setText("" + v);
            if (autoUpdate.isSelected()) {
                data.query(v);
            }
        });
        labelQuery = tab.addLabel("");

        buttonQuery = tab.addButton("Query", (e) -> {
            data.query(sliderQuery.getValue());
        });
        buttonClearQuery = tab.addButton("Clear query", (e) -> {
            data.clearQuery();
        });
    }

    private void postTab() {
        SideTab tab = addTab("Post");

        final JComboBox combostyle = tab.addComboBox(InterchangeStyle.values(), null);

        final JCheckBox checkBoxUniformStat = tab.addCheckbox("Uniform stations", false, null);

        final JCheckBox checkBoxUseStationColor = tab.addCheckbox("Use Line Color", true, null);

        tab.makeSplit(4, 2);
        tab.addLabel("Min station size");
        final JSpinner spinnerMinStationSize = tab.addDoubleSpinner(5, 1, 50, 1, null);

        tab.addSpace();

        tab.makeSplit(4, 2);
        tab.addLabel("Max edge width");
        final JSpinner spinnerMaxEdgeWidth = tab.addDoubleSpinner(50, 1, 50, 1, null);

        tab.makeSplit(4, 2);
        tab.addLabel("Line Thickness");
        final JSpinner spinnerLineThickness = tab.addDoubleSpinner(5, 1, 50, 1, null);

        tab.makeSplit(4, 2);
        tab.addLabel("Rim Width (%)");

        final JSpinner spinnerRimThickness = tab.addDoubleSpinner(15, 1, 100, 1, null);

        ChangeListener renderChange = (ChangeEvent e) -> {
            if ((data.drawrendering || data.drawprogressive) && data.rendering != null) {
                data.renderSchematization((Double) spinnerLineThickness.getValue(),
                        (Double) spinnerRimThickness.getValue(),
                        (InterchangeStyle) combostyle.getSelectedItem(),
                        (Double) spinnerMinStationSize.getValue(),
                        (Double) spinnerMaxEdgeWidth.getValue(),
                        checkBoxUniformStat.isSelected(),
                        checkBoxUseStationColor.isSelected());
            }
        };
        spinnerMaxEdgeWidth.addChangeListener(renderChange);
        spinnerRimThickness.addChangeListener(renderChange);
        spinnerLineThickness.addChangeListener(renderChange);
        checkBoxUniformStat.addChangeListener(renderChange);
        checkBoxUseStationColor.addChangeListener(renderChange);
        spinnerMinStationSize.addChangeListener(renderChange);

        buttonRender = tab.addButton("Make rendering", (e) -> {
            data.renderSchematization((Double) spinnerLineThickness.getValue(),
                    (Double) spinnerRimThickness.getValue(),
                    (InterchangeStyle) combostyle.getSelectedItem(),
                    (Double) spinnerMinStationSize.getValue(),
                    (Double) spinnerMaxEdgeWidth.getValue(),
                    checkBoxUniformStat.isSelected(),
                    checkBoxUseStationColor.isSelected());
        });

        tab.addButton("Save to IPE", (e) -> {
            data.saveToIpe();
        });

        tab.addButton("Copy to IPE", (e) -> {
            data.copyToIpe();
        });
    }

    private void drawTab() {
        SideTab tab = addTab("Draw");

        tab.addCheckbox("Draw side-by-side", data.sidebyside, (e, v) -> {
            data.sidebyside = v;
            data.onDataChange();
        });

        tab.addCheckbox("Draw progressive", data.drawprogressive, (e, v) -> {
            data.drawprogressive = v;
            data.onDataChange();
        });

        tab.addCheckbox("Draw input", data.drawinput, (e, v) -> {
            data.drawinput = v;
            data.onDataChange();
        });

        tab.addCheckbox("Draw schematization", data.drawschematization, (e, v) -> {
            data.drawschematization = v;
            data.onDataChange();
        });

        tab.addCheckbox("Color schematization", data.drawcoloredstrokes, (e, v) -> {
            data.drawcoloredstrokes = v;
            data.onDataChange();
        });

        tab.addCheckbox("Draw rendering", data.drawrendering, (e, v) -> {
            data.drawrendering = v;
            data.onDataChange();
        });

        tab.addCheckbox("Draw query", data.drawquery, (e, v) -> {
            data.drawquery = v;
            data.onDataChange();
        });

        tab.addCheckbox("Stroke identification", data.strokeIdentification, (e, v) -> {
            data.strokeIdentification = v;
            data.onDataChange();
        });

        tab.addCheckbox("Draw vertices", data.drawVertices, (e, v) -> {
            data.drawVertices = v;
            data.onDataChange();
        });
    }
}
