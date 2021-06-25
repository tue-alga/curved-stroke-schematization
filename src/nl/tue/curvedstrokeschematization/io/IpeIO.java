/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization.io;

import nl.tue.curvedstrokeschematization.data.metro.MetroLine;
import nl.tue.curvedstrokeschematization.data.metro.MetroNetwork;
import nl.tue.curvedstrokeschematization.data.metro.MetroStation;
import java.awt.Color;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import nl.tue.geometrycore.geometry.BaseGeometry;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.linear.LineSegment;
import nl.tue.geometrycore.geometry.linear.PolyLine;
import nl.tue.geometrycore.geometry.linear.Polygon;
import nl.tue.geometrycore.geometry.mix.GeometryGroup;
import nl.tue.geometrycore.io.ReadItem;
import nl.tue.geometrycore.io.ipe.IPEReader;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class IpeIO {

    public static MetroNetwork readFromClipboard() {

        try {
            IPEReader ipe = IPEReader.clipboardReader();
            List<ReadItem> read = ipe.read();
            
            MetroNetwork network = new MetroNetwork();
            
            int i = 0;
            for (ReadItem item : read) {
                i++;
                add(item.getGeometry(), item.getStroke(), "Line" + i, network);
            }
            
            int stations = network.getStations().size();
            int interchanges = 0;
            int lines = network.getLines().size();
            int connections = network.getConnections().size();
            for (MetroStation ms : network.getStations()) {
                if (ms.getConnections().size() > 2) {
                    interchanges++;
                }
            }
            
            System.out.println("IPE Network");
            System.out.println("  stations     : "+stations);
            System.out.println("  interchanges : "+interchanges);
            System.out.println("  lines        : "+lines);
            System.out.println("  connections  : "+connections);
            
            return network;
        } catch (IOException ex) {
            Logger.getLogger(IpeIO.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    private static void add(BaseGeometry geom, Color color, String label, MetroNetwork network) {
        MetroLine line = network.addLine(label, label, color);
        if (geom instanceof LineSegment) {
            LineSegment LS = (LineSegment) geom;
            addConnection(LS, line, network);
        } else if (geom instanceof PolyLine) {
            PolyLine PL = (PolyLine) geom;

            for (int i = 0; i < PL.edgeCount(); i++) {
                addConnection(PL.edge(i), line, network);
            }
        } else if (geom instanceof Polygon) {
            Polygon SP = (Polygon) geom;

            for (int i = 0; i < SP.edgeCount(); i++) {
                addConnection(SP.edge(i), line, network);
            }
        } else if (geom instanceof GeometryGroup) {
            GeometryGroup<? extends BaseGeometry> CP = (GeometryGroup) geom;
            for (BaseGeometry G : CP.getParts()) {
                add(G, color, label, network);
            }
        } else {
            System.err.println("Unexpected geometry type: " + geom.getClass().getSimpleName());
        }
    }

    private static void addConnection(LineSegment LS, MetroLine line, MetroNetwork network) {
        MetroStation s1 = getStation(LS.getStart(), network);
        MetroStation s2 = getStation(LS.getEnd(), network);

        String connID;
        if (Integer.parseInt(s1.getLabel().substring(1)) < Integer.parseInt(s2.getLabel().substring(1))) {
            connID = s1.getLabel() + "_" + s2.getLabel();
        } else {
            connID = s2.getLabel() + "_" + s1.getLabel();
        }

        network.addToConnection(connID, s1, s2, line);
    }

    private static MetroStation getStation(Vector loc, MetroNetwork network) {
        for (MetroStation MS : network.getStations()) {
            if (MS.isApproximately(loc, 0.001)) {
                return MS;
            }
        }
        int num = (network.getStations().size() + 1);
        return network.addStation(loc, "S" + num, "S" + num, false);
    }
}
