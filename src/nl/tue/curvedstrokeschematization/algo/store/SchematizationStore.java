/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization.algo.store;

import nl.tue.curvedstrokeschematization.algo.schematization.FullCircleArc;
import nl.tue.curvedstrokeschematization.data.metro.MetroConnection;
import nl.tue.curvedstrokeschematization.data.metro.MetroStation;
import nl.tue.curvedstrokeschematization.data.stroked.Stroke;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeArc;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeCross;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeNetwork;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeVertex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.curved.Circle;
import nl.tue.geometrycore.geometry.curved.CircularArc;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class SchematizationStore {

    public class ConnectionNode {
        // store

        private int appearsAtComplexity;
        private double appearsAtCost;
        private ConnectionNode parent;
        private List<ConnectionNode> children;
        // network
        private CircularArc arc;
        private List<MetroConnection> connections;
        private List<StationNode> virtuals;
        private List<StrokeCross> realVirtuals;
        private Stroke stroke;

        public CircularArc getArc() {
            return arc;
        }

        public List<MetroConnection> getConnections() {
            return connections;
        }

        private void convertVirtuals(double complexity) {
            realVirtuals = new ArrayList<StrokeCross>();
            for (StationNode sn : virtuals)
            {
                StationNode trav = sn;
                while (trav.parent != null && complexity < trav.parent.appearsAtComplexity) {
                    trav = trav.parent;
                }
                if (trav.cross != null) {
                    // station actually exists...
                    realVirtuals.add(trav.cross);
                }
            }
        }
        
        public List<StrokeCross> getVirtualCrosses() {
            return realVirtuals;            
        }
        
        public Stroke getStroke() {
            return stroke;
        }
    }

    public class StationNode {
        // store

        private int appearsAtComplexity;
        private double appearsAtCost;
        private StationNode parent;
        private StationNode child;
        // network
        private MetroStation station;
        private Circle mindisk;
        private List<Vector> points;
        private StrokeCross cross;

        public Circle getMindisk() {
            return mindisk;
        }

        public List<Vector> getPoints() {
            return points;
        }

        public MetroStation getStation() {
            return station;
        }
    }

    public class QueryResult {

        public List<StationNode> stations;
        public List<ConnectionNode> connections;
    }
    private Map<StrokeArc, ConnectionNode> connectionMap;
    private Map<StrokeVertex, StationNode> stationMap;
    private Map<StrokeCross, StationNode> interchangeMap;
    private int maxComplexity;

    public SchematizationStore() {
        connectionMap = new HashMap();
        stationMap = new HashMap();
        interchangeMap = new HashMap();
        maxComplexity = -1;
    }

    public int getMaximumComplexity() {
        return maxComplexity;
    }

    public int getMinimumComplexity() {
        return connectionMap.size();
    }

    public void clear() {
        connectionMap.clear();
        stationMap.clear();
        interchangeMap.clear();
        maxComplexity = -1;
    }

    public boolean isInitialized() {
        return maxComplexity > 0;
    }

    public void initialize(StrokeNetwork network) {
        clear();
        
        for (StrokeVertex sv : network.getVertices()) {
            if (sv.getCross() == null) {
                // simple station
                StationNode sn = new StationNode();

                sn.appearsAtComplexity = Integer.MAX_VALUE;
                sn.appearsAtCost = 0;
                sn.parent = null;
                sn.child = null;
                sn.station = sv.getOriginal();
                sn.mindisk = new Circle(sv.clone(), 0.0);
                sn.points = new ArrayList();
                sn.points.add(new Vector(sv));

                stationMap.put(sv, sn);
            }
        }

        for (StrokeCross sc : network.getCrosses()) {
            // interchange
            StationNode sn = new StationNode();

            sn.appearsAtComplexity = Integer.MAX_VALUE;
            sn.appearsAtCost = 0;
            sn.parent = null;
            sn.child = null;
            sn.station = sc.getOriginal();
            sn.mindisk = sc.getSmallestDisc().clone();
            sn.points = new ArrayList(); // TODO

            interchangeMap.put(sc, sn);
        }

        maxComplexity = 0;
        for (StrokeArc sa : network.getArcs()) {
            ConnectionNode cn = new ConnectionNode();

            cn.appearsAtComplexity = Integer.MAX_VALUE;
            cn.appearsAtCost = 0;
            cn.parent = null;
            cn.children = null;
            cn.arc = sa.toGeometry().clone();
            cn.connections = new ArrayList(sa.getOriginaledges());
            cn.virtuals = new ArrayList();
            cn.stroke = sa.getStroke();

            connectionMap.put(sa, cn);

            maxComplexity++;
        }
    }

    public void removeStation(int newcomplexity, double operationcost, StrokeVertex sv) {
        StationNode old = stationMap.get(sv);

        StationNode sn = new StationNode();

        sn.appearsAtComplexity = newcomplexity;
        sn.appearsAtCost = Math.max(operationcost, old.appearsAtCost);
        sn.parent = null;
        sn.child = old;
        old.parent = sn;
        sn.station = null;
        sn.mindisk = null;
        sn.points = null;

        stationMap.put(sv, sn);
    }

    public void replaceArc(int newcomplexity, double operationcost, StrokeArc oldArc1, StrokeArc oldArc2, StrokeArc newArc) {
        ConnectionNode old1 = connectionMap.remove(oldArc1);
        ConnectionNode old2 = oldArc2 == null ? null : connectionMap.remove(oldArc2);

        ConnectionNode cn = new ConnectionNode();

        cn.virtuals = new ArrayList<StationNode>();
        for (StrokeCross sc : newArc.getVirtuals())
            cn.virtuals.add(interchangeMap.get(sc));
        
        cn.appearsAtComplexity = newcomplexity;
        cn.appearsAtCost = Math.max(operationcost, old2 == null ? old1.appearsAtCost : Math.max(old1.appearsAtCost, old2.appearsAtCost));
        cn.parent = null;
        cn.children = new ArrayList();
        cn.children.add(old1);
        old1.parent = cn;
        if (old2 != null) {
            cn.children.add(old2);
            old2.parent = cn;
        }
        if (newArc.toGeometry() instanceof FullCircleArc) {
            cn.arc = new FullCircleArc(newArc.getCenter(), newArc.getStart(), newArc.isClockwise());
        } else {
            cn.arc = newArc.toGeometry().clone();
        }
        cn.connections = new ArrayList(newArc.getOriginaledges());
        cn.stroke = oldArc1.getStroke();

        connectionMap.put(newArc, cn);
    }

    public void updateCross(int newcomplexity, double operationcost, StrokeCross sc) {
        StationNode old = interchangeMap.get(sc);

        StationNode sn = new StationNode();

        sn.appearsAtComplexity = newcomplexity;
        sn.appearsAtCost = Math.max(operationcost, old.appearsAtCost);
        sn.parent = null;
        sn.child = old;
        old.parent = sn;
        sn.station = old.station;
        sn.mindisk = sc.getSmallestDisc().clone();
        sn.points = new ArrayList(); // TODO
        sn.cross = new StrokeCross(sc);

        interchangeMap.put(sc, sn);
    }

    public QueryResult query(int maxcomplexity) {
        QueryResult result = new QueryResult();

        // gather stations
        result.stations = new ArrayList();
        for (StationNode sn : stationMap.values()) {
            StationNode trav = sn;
            while (maxcomplexity > trav.appearsAtComplexity) {
                trav = trav.child;
            }
            if (trav.station != null) {
                // station actually exists...
                result.stations.add(trav);
            }
        }
        for (StationNode sn : interchangeMap.values()) {
            StationNode trav = sn;
            while (maxcomplexity > trav.appearsAtComplexity) {
                trav = trav.child;
            }
            if (trav.station != null) {
                // station actually exists...
                result.stations.add(trav);
            }
        }

        // gather connections
        result.connections = new ArrayList();
        for (ConnectionNode cn : connectionMap.values()) {
            LinkedList<ConnectionNode> travs = new LinkedList();
            travs.add(cn);
            while (!travs.isEmpty()) {
                ConnectionNode trav = travs.removeFirst();

                if (maxcomplexity > trav.appearsAtComplexity) {
                    // queue children
                    travs.addAll(trav.children);
                } else {
                    // add it!
                    trav.convertVirtuals(maxcomplexity);
                    result.connections.add(trav);
                }
            }
        }

        return result;
    }

    public QueryResult query(double maxcost) {
        QueryResult result = new QueryResult();

        // gather stations
        result.stations = new ArrayList();
        for (StationNode sn : stationMap.values()) {
            StationNode trav = sn;
            while (maxcost > trav.appearsAtCost) {
                trav = trav.child;
            }
            if (trav.station != null) {
                // station actually exists...
                result.stations.add(trav);
            }
        }
        for (StationNode sn : interchangeMap.values()) {
            StationNode trav = sn;
            while (maxcost > trav.appearsAtCost) {
                trav = trav.child;
            }
            if (trav.station != null) {
                // station actually exists...
                result.stations.add(trav);
            }
        }

        // gather connections
        result.connections = new ArrayList();
        for (ConnectionNode cn : connectionMap.values()) {
            LinkedList<ConnectionNode> travs = new LinkedList();
            travs.add(cn);
            while (!travs.isEmpty()) {
                ConnectionNode trav = travs.removeFirst();

                if (maxcost > trav.appearsAtCost) {
                    // queue children
                    travs.addAll(trav.children);
                } else {
                    // add it!
                    result.connections.add(trav);
                }
            }
        }

        return result;
    }
}
