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

import nl.tue.curvedstrokeschematization.algo.frechetdistance.implementations.polyhedral.PolyhedralFrechetDistance;
import nl.tue.curvedstrokeschematization.algo.frechetdistance.util.PolyhedralDistanceFunction;
import nl.tue.curvedstrokeschematization.algo.NetworkConstruction;
import nl.tue.curvedstrokeschematization.algo.Renderer;
import nl.tue.curvedstrokeschematization.algo.schematization.IterativeSchematization;
import nl.tue.curvedstrokeschematization.algo.store.SchematizationStore.QueryResult;
import nl.tue.curvedstrokeschematization.io.GraphmlIO;
import nl.tue.curvedstrokeschematization.data.metro.MetroNetwork;
import nl.tue.curvedstrokeschematization.data.metro.MetroStation;
import nl.tue.curvedstrokeschematization.data.rendered.RenderedNetwork;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeNetwork;
import nl.tue.curvedstrokeschematization.io.IpeIO;
import nl.tue.curvedstrokeschematization.io.WktIO;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.linear.Rectangle;
import nl.tue.geometrycore.gui.GUIUtil;
import nl.tue.geometrycore.io.ReadItem;
import nl.tue.geometrycore.io.ipe.IPEReader;
import nl.tue.geometrycore.io.ipe.IPEWriter;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class Data {

    public static double default_crossdist = 0.0075;
    public static double default_fdeps = 1.01;
    public static boolean default_useStore = true;
    public static int default_angles = 41;
    public static int default_numCandidates = 3;
    public static double default_straightFDfactor = 1.0;
    public static boolean default_allowhighdegree = true;
    protected MetroNetwork input;
    protected StrokeNetwork schematization;
    protected IterativeSchematization algorithm;
    protected boolean algorithmstuck;
    protected RenderedNetwork rendering;
    protected QueryResult query;
    protected boolean drawprogressive = true, drawinput = false, drawschematization = false, drawcoloredstrokes = true, drawrendering = false;
    protected boolean drawquery = true;
    protected double crossdist = default_crossdist;
    protected boolean drawcrossdist = false;
    protected boolean drawVertices = false;
    protected boolean strokeIdentification = false;
    protected boolean sidebyside = true;
    private static JFileChooser openchooser;
    private static JFileChooser savechooser;

    protected DrawPanel draw = new DrawPanel(this);
    protected SidePanel side = new SidePanel(this);

    public static void main(String[] args) {
        String defaultopenfolder = args.length < 1 ? "D:\\" : args[0];
        String defaultsavefolder = args.length < 2 ? "D:\\" : args[1];
        openchooser = new JFileChooser(defaultopenfolder);
        savechooser = new JFileChooser(defaultsavefolder);

        Data data = new Data();
        data.initData();
        GUIUtil.makeMainFrame("Curved Stroke Schematization", data.draw, data.side);
        //data.loadMetroFile();
    }

    protected void onDataChange() {
        side.onDataChange();
        draw.repaint();
    }

    protected void initData() {
        input = null;
        schematization = null;
        setAlgorithm(default_allowhighdegree, default_useStore, default_crossdist, default_angles, default_numCandidates, default_straightFDfactor, default_fdeps);
        rendering = null;
    }

    public void pasteIpeMetro() {
        MetroNetwork network = IpeIO.readFromClipboard();
        if (network != null) {
            input = network;
            schematization = null;
            algorithmstuck = true;
            rendering = null;
            if (algorithm.getStore() != null) {
                algorithm.getStore().clear();
            }
            query = null;

            draw.zoomToFit();
            onDataChange();
        }
    }

    public void loadMetroFile() {
        int result = openchooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            MetroNetwork network = GraphmlIO.loadFile(openchooser.getSelectedFile());
            if (network != null) {
                input = network;

                System.out.println("Input:");
                System.out.println("  " + input.getLines().size() + " lines");
                System.out.println("  " + input.getConnections().size() + " connections");
                System.out.println("  " + input.getStations().size() + " stations");

                schematization = null;
                algorithmstuck = true;
                rendering = null;
                if (algorithm.getStore() != null) {
                    algorithm.getStore().clear();
                }
                query = null;

                draw.zoomToFit();
                onDataChange();
            }
        }
    }

    public void loadWKTfile() {
        int result = openchooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            MetroNetwork network = WktIO.loadFile(openchooser.getSelectedFile());
            if (network != null) {
                input = network;

                System.out.println("Input:");
                System.out.println("  " + input.getLines().size() + " lines");
                System.out.println("  " + input.getConnections().size() + " connections");
                System.out.println("  " + input.getStations().size() + " stations");

                schematization = null;
                algorithmstuck = true;
                rendering = null;
                if (algorithm.getStore() != null) {
                    algorithm.getStore().clear();
                }
                query = null;

                draw.zoomToFit();
                onDataChange();
            }
        }
    }

    public void setAlgorithm(boolean allowhighdegree, boolean useStore, double maxcrossdistfrac, int anglesteps, int numCandidates, double straightfactor, double eps) {
        assert eps > 1;

        algorithm = new IterativeSchematization(allowhighdegree, useStore,
                maxcrossdistfrac,
                anglesteps, numCandidates,
                straightfactor,
                new PolyhedralFrechetDistance(PolyhedralDistanceFunction.epsApproximation2D(eps)));
        initializeAlgorithm();
    }

    public void initializeAlgorithm() {
        if (schematization != null) {
            algorithmstuck = !algorithm.init(schematization);
            query = null;
            onDataChange();
        } else {
            algorithmstuck = true;
        }

    }

    public void initializeSchematization(boolean fromscratch) {
        if (input != null) {
            schematization = NetworkConstruction.construct(input, fromscratch);
            algorithmstuck = true;
            rendering = null;
            if (algorithm.getStore() != null) {
                algorithm.getStore().clear();
            }
            query = null;

            onDataChange();
        }
    }

    public void mergeStrokesAngle() {
        if (input != null) {
            schematization = NetworkConstruction.mergeStrokesAngle(schematization);

            onDataChange();
        }
    }

    public void mergeStrokesLines() {
        if (input != null) {
            schematization = NetworkConstruction.mergeStrokesLines(schematization);

            onDataChange();
        }
    }

    public void performSchematizationSteps(int k, int redraw) {
        if (schematization != null) {

            rendering = null;
            int r = redraw;
            while (k > 0 && !algorithmstuck) {
                algorithmstuck = !algorithm.performStep();
                k--;
                r--;
                if (r == 0) {
                    r = redraw;
                    draw.repaintNow();
                }
            }

            onDataChange();
        }
    }

    public void performSchematizationComplexity(int k, int redraw) {
        if (schematization != null) {

            rendering = null;
            int r = redraw;
            while (k < algorithm.getComplexity() && !algorithmstuck) {
                algorithmstuck = !algorithm.performStep();
                r--;
                if (r == 0) {
                    r = redraw;
                    draw.repaintNow();
                }
            }

            onDataChange();
        }
    }

    public void renderSchematization(double linethickness, double interchangerimwidth, Renderer.InterchangeStyle style, double minStationsSize, double maxEdgeWidth, boolean uniformStations, boolean useStationColor) {
        if (query != null) {
            Renderer renderer = new Renderer();
            rendering = renderer.render(query, linethickness, interchangerimwidth, style, uniformStations, minStationsSize, maxEdgeWidth, useStationColor);
            onDataChange();
        } else if (schematization != null) {
            Renderer renderer = new Renderer();
            rendering = renderer.render(schematization, linethickness, interchangerimwidth, style, uniformStations, minStationsSize, maxEdgeWidth, useStationColor);
            onDataChange();
        }
    }

    public void saveToIpe() {
        int result = savechooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {

            try (IPEWriter write = IPEWriter.fileWriter(savechooser.getSelectedFile())) {
                write.initialize();
                draw.render(draw);
            } catch (IOException ex) {
                Logger.getLogger(Data.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void copyToIpe() {
        try (IPEWriter write = IPEWriter.clipboardWriter()) {
            write.initialize();
            draw.render(write);
        } catch (IOException ex) {
            Logger.getLogger(Data.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void query(int complexity) {
        if (algorithm.getStore() != null && algorithm.getStore().isInitialized()) {
            query = algorithm.getStore().query(complexity);
            onDataChange();
        }
    }

    public void clearQuery() {
        query = null;
    }

    public void rescale() {
        try {
            if (input == null) {
                return;
            }

            IPEReader read = IPEReader.clipboardReader();
            List<ReadItem> ris = read.read();

            Rectangle rescale = new Rectangle();
            for (ReadItem ri : ris) {
                rescale.includeGeometry(ri.getGeometry());
            }

            Vector vi = rescale.leftBottom();
            vi.invert();
            rescale.translate(vi);// set left bottom to 0,0

            Rectangle metrobox = new Rectangle();
            for (MetroStation ms : input.getStations()) {
                metrobox.include(ms);
            }

            Vector D = metrobox.leftBottom();
            D.invert();
            double S = Math.min(rescale.width() / metrobox.width(), rescale.height() / metrobox.height());

            for (MetroStation ms : input.getStations()) {
                ms.translate(D);
                ms.scale(S);
            }

            schematization = null;
            algorithmstuck = true;
            rendering = null;
            if (algorithm.getStore() != null) {
                algorithm.getStore().clear();
            }
            query = null;

            draw.zoomToFit();
            onDataChange();
        } catch (IOException ex) {
            Logger.getLogger(Data.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
