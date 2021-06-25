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
import nl.tue.geometrycore.geometry.GeometryConvertable;
import nl.tue.geometrycore.geometry.linear.LineSegment;

/**
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 */
public class MetroConnection implements GeometryConvertable<LineSegment> {

    String id;
    MetroStation stationA, stationB;
    ArrayList<MetroLine> lines;

    public MetroConnection(String id, MetroStation vA, MetroStation vB) {
        this(id, vA, vB, null);
    }

    public MetroConnection(String id, MetroStation vA, MetroStation vB, ArrayList<MetroLine> lines) {
        this.stationA = vA;
        this.stationB = vB;
        this.lines = lines;
        this.id = id;
        vA.addConnection(this);
        vB.addConnection(this);
    }

    public String getId() {
        return id;
    }

    public MetroStation getBeginStation() {
        return stationA;
    }

    public void setBeginStation(MetroStation station) {
        stationA = station;
    }

    public MetroStation getEndStation() {
        return stationB;
    }

    public MetroStation getSharedStation(MetroConnection other) {
        if (other.getBeginStation() == stationA || other.getEndStation() == stationA) {
            return stationA;
        } else if (other.getBeginStation() == stationB || other.getEndStation() == stationB) {
            return stationB;
        } else {
//assert false : "Connections dont have common station";
            return null;
        }
    }

    public void setEndStation(MetroStation station) {
        stationB = station;
    }

    public MetroStation theOther(MetroStation m) {
        if (stationA == m) {
            return stationB;
        } else if (stationB == m) {
            return stationA;
        }
        assert false : "ERROR: station not part of connection : (" + stationA.getLabel() + " - " + stationB.getLabel() + ")  :  " + m.getLabel();
        return null;
    }

    public ArrayList<MetroLine> getLines() {
        return lines;
    }

    public void setLines(ArrayList<MetroLine> lines) {
        this.lines = lines;
    }

    @Override
    public String toString() {
        return stationA.getLabel() + " <-> " + stationB.getLabel();
    }

    @Override
    public LineSegment toGeometry() {
        return new LineSegment(stationA, stationB);
    }
}
