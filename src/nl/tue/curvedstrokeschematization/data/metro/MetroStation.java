/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization.data.metro;

import java.util.ArrayList;
import nl.tue.geometrycore.geometry.Vector;

/**
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 */
public class MetroStation extends Vector {

    int index;
    String id;
    String label;
    ArrayList<MetroConnection> connections;
    ArrayList<MetroLine> lines;
    boolean planarizationStation;

    public MetroStation(int index, Vector pos, String id, String label, boolean planarizationStation) {
        super(pos);
        this.index = index;
        this.id = id;
        this.label = label;
        this.planarizationStation = planarizationStation;
        connections = new ArrayList<MetroConnection>();
        lines = new ArrayList<MetroLine>();
    }

    public boolean isPlanarizationStation() {
        return planarizationStation;
    }

    public int getIndex() {
        return index;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public ArrayList<MetroConnection> getConnections() {
        return connections;
    }

    public MetroConnection getConnectionTo(MetroStation m) {
        for (MetroConnection c : connections) {
            if ((c.getBeginStation() == this && c.getEndStation() == m)
                    || (c.getEndStation() == this && c.getBeginStation() == m)) {
                return c;
            }
        }
        System.out.println("ERROR: no connection to metro station");
        return null;
    }

    public void addConnection(MetroConnection m) {
        connections.add(m);
        if (m.getLines() != null) {
            for (MetroLine ml : m.getLines()) {
                if (!lines.contains(ml)) {
                    lines.add(ml);
                }
            }
        }
    }

    public void removeConnectionTo(MetroStation m) {
        for (int i = 0; i < connections.size(); i++) {
            MetroConnection mc = connections.get(i);
            if (mc.theOther(this) == m) {
                connections.remove(mc);
                return;
            }
        }
        assert false : "Connection not found";
    }

    public ArrayList<MetroLine> getLines() {
        return lines;
    }

    public int getMaxIncomingLines() {
        int most = 0;
        for (MetroConnection c : connections) {
            int numberLines = c.getLines().size();
            if (numberLines > most) {
                most = numberLines;
            }
        }
        return most;
    }

    @Override
    public String toString() {
        return label + " (" + getX() + "," + getY() + ")";
    }
}
