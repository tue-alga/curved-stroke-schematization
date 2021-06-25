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

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import nl.tue.geometrycore.geometry.Vector;

/**
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 */
public class MetroNetwork {

    Map<String, MetroStation> stations;
    Map<String, MetroConnection> connections;
    Map<String, MetroLine> lines;

    public MetroNetwork() {
        stations = new HashMap();
        connections = new HashMap();
        lines = new HashMap<String, MetroLine>();
    }

    public MetroConnection addToConnection(String id, MetroStation vA, MetroStation vB, MetroLine line) {
        MetroConnection connection = connections.get(id);
        if (connection == null) {
            connection = new MetroConnection(id, vA, vB, new ArrayList());
            connections.put(id, connection);
        }

        if (!vA.getLines().contains(line)) {
            vA.getLines().add(line);
        }
        if (!vB.getLines().contains(line)) {
            vB.getLines().add(line);
        }

        connection.getLines().add(line);
        line.addConnection(connection);
        return connection;
    }

    public MetroConnection addConnection(String id, MetroStation vA, MetroStation vB, ArrayList<MetroLine> lines) {
        MetroConnection m = new MetroConnection(id, vA, vB, lines);
        connections.put(id, m);
        for (MetroLine ml : lines) {
            ml.addConnection(m);
        }
        return m;
    }

    public MetroStation addStation(Vector pos, String id, String label, boolean planarizationStation) {
        MetroStation MS = new MetroStation(stations.size(), pos, id, label, planarizationStation);
        stations.put(id, MS);
        return MS;
    }

    public MetroLine addLine(String id, String label, Color c) {
        MetroLine ML = new MetroLine(label, c);
        lines.put(id, ML);
        return ML;
    }

    public MetroConnection getConnection(String id) {
        return connections.get(id);
    }

    public MetroStation getStation(String id) {
        return stations.get(id);
    }

    public MetroLine getLine(String id) {
        return lines.get(id);
    }

    public Collection<MetroConnection> getConnections() {
        return connections.values();
    }

    public Collection<MetroStation> getStations() {
        return stations.values();
    }

    public Collection<MetroLine> getLines() {
        return lines.values();
    }

    public void finish() {
        for (MetroLine l : lines.values()) {
            l.sortConnections();
        }
    }

    public void scale(double scale, Vector center) {
        for (MetroStation ms : stations.values()) {
            ms.scale(scale, center);
        }
    }
}
