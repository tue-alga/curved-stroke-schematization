/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization.algo;

import nl.tue.curvedstrokeschematization.data.Triple;
import nl.tue.curvedstrokeschematization.data.metro.MetroConnection;
import nl.tue.curvedstrokeschematization.data.metro.MetroLine;
import nl.tue.curvedstrokeschematization.data.metro.MetroNetwork;
import nl.tue.curvedstrokeschematization.data.metro.MetroStation;
import nl.tue.curvedstrokeschematization.data.stroked.Stroke;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeArc;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeCross;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeNetwork;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeVertex;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import nl.tue.geometrycore.geometry.BaseGeometry;
import nl.tue.geometrycore.geometry.GeometryType;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.curved.CircularArc;
import nl.tue.geometrycore.util.Pair;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class NetworkConstruction {

    public static void planarize(MetroNetwork metro) {

        List<MetroConnection> connections = new ArrayList(metro.getConnections());

        int number = 1;
        int c1 = 0;
        while (c1 < connections.size()) {
            MetroConnection conn1 = connections.get(c1);
            int c2 = c1 + 1;
            while (c2 < connections.size()) {
                MetroConnection conn2 = connections.get(c2);

                if (conn1.getSharedStation(conn2) == null) {

                    List<BaseGeometry> intersections = conn1.toGeometry().intersect(conn2.toGeometry());
                    for (BaseGeometry igeom : intersections) {
                        // check that intersections do not correspond to some other intersection
                        if (igeom.getGeometryType() != GeometryType.VECTOR) {
                            System.err.println("Overlap found, planarization fails");
                            continue;
                        }

                        Vector intersection = (Vector) igeom;

                        MetroStation virt = metro.addStation(intersection, "virt" + number, "virtual non-planar" + (number / 2), true);

                        connections.add(metro.addConnection("connect" + number, conn1.getBeginStation(), virt, conn1.getLines()));
                        conn1.getBeginStation().removeConnectionTo(conn1.getEndStation());
                        conn1.setBeginStation(virt);
                        virt.addConnection(conn1);
                        number++;

                        connections.add(metro.addConnection("connect" + number, conn2.getBeginStation(), virt, conn2.getLines()));
                        conn2.getBeginStation().removeConnectionTo(conn2.getEndStation());
                        conn2.setBeginStation(virt);
                        virt.addConnection(conn2);
                        number++;
                    }
                }

                c2++;
            }
            c1++;
        }
    }

    public static StrokeNetwork construct(MetroNetwork metro, boolean fromscratch) {

        System.out.println("Constructing stroke network");

        // determine strokes based on same metro-sets
        planarize(metro);

        System.out.println("  planarized");

        List<MetroConnection> unhandled = new ArrayList(metro.getConnections());
        final Comparator<Vector> lexico = new Comparator<Vector>() {

            public int compare(Vector o1, Vector o2) {
                int x = Double.compare(o1.getX(), o2.getX());
                if (x != 0) {
                    return x;
                } else {
                    return Double.compare(o1.getY(), o2.getY());
                }
            }
        };
        Collections.sort(unhandled, new Comparator<MetroConnection>() {

            public int compare(MetroConnection o1, MetroConnection o2) {
                Vector o1_first, o1_second;
                if (lexico.compare(o1.getBeginStation(), o2.getEndStation()) == -1) {
                    o1_first = o1.getBeginStation();
                    o1_second = o1.getEndStation();
                } else {
                    o1_first = o1.getEndStation();
                    o1_second = o1.getBeginStation();
                }

                Vector o2_first, o2_second;
                if (lexico.compare(o2.getBeginStation(), o2.getEndStation()) == -1) {
                    o2_first = o2.getBeginStation();
                    o2_second = o2.getEndStation();
                } else {
                    o2_first = o2.getEndStation();
                    o2_second = o2.getBeginStation();
                }

                int first = lexico.compare(o1_first, o2_first);
                if (first != 0) {
                    return -first;
                } else {
                    return -lexico.compare(o1_second, o2_second);
                }
            }
        });
        System.out.println("  sorted connection list");

        List<List<MetroConnection>> strokes = new ArrayList();

        while (!unhandled.isEmpty()) {
            MetroConnection base = unhandled.remove(unhandled.size() - 1);

            List<MetroConnection> stroke = new ArrayList();
            strokes.add(stroke);

            stroke.add(base);
            unhandled.remove(base);

            MetroStation start = base.getBeginStation();
            MetroConnection first = base;
            MetroConnection last = base;
            MetroStation end = base.getEndStation();

            boolean continueforward = end != start;
            while (continueforward) {
                continueforward = false;
                List<MetroConnection> candidates = new ArrayList();
                if (!fromscratch) {
                    for (MetroConnection conn : end.getConnections()) {
                        if (unhandled.contains(conn) && conn != last && sameSet(conn.getLines(), base.getLines())) {
                            candidates.add(conn);
                        }
                    }
                }
                MetroConnection cand = findCandidate(last, end, candidates);

                if (cand != null) {
                    //if (stroke already contains last point // break off part)
                    //NB: first station on line is okay (circular)
                    boolean split = false;
                    MetroStation startStation = stroke.get(0).getBeginStation();
                    if (stroke.size() > 1) {
                        startStation = stroke.get(0).theOther(stroke.get(0).getSharedStation(stroke.get(1)));
                    }
                    for (int i = 0; i < stroke.size() - 1; i++) {
                        startStation = stroke.get(i).theOther(startStation);
                        if (cand.getBeginStation() == startStation || cand.getEndStation() == startStation) {
                            for (int j = stroke.size() - 1; j > i; j--) {
                                MetroConnection mc = stroke.remove(j);
                                unhandled.add(mc);
                            }
                            split = true;
                            break;
                        }
                    }

                    stroke.add(cand);
                    unhandled.remove(cand);

                    last = cand;
                    end = cand.theOther(end);

                    continueforward = end != start && !split;
                }
            }

            boolean continuebackward = end != start;
            while (continuebackward) {
                continuebackward = false;
                List<MetroConnection> candidates = new ArrayList();
                if (!fromscratch) {
                    for (MetroConnection conn : start.getConnections()) {
                        if (unhandled.contains(conn) && conn != first && sameSet(conn.getLines(), base.getLines())) {
                            candidates.add(conn);
                        }
                    }
                }
                MetroConnection cand = findCandidate(first, start, candidates);

                if (cand != null) {
                    //if (stroke already contains last point // break off part)
                    //NB: first station on line is okay (circular)
                    boolean split = false;
                    MetroStation startStation = stroke.get(0).getBeginStation();
                    if (stroke.size() > 1) {
                        startStation = stroke.get(0).theOther((stroke.get(0).getSharedStation(stroke.get(1))));
                    }
                    for (int i = 0; i < stroke.size() - 1; i++) {
                        startStation = stroke.get(i).theOther(startStation);
                        if (cand.getBeginStation() == startStation || cand.getEndStation() == startStation) {
                            for (int j = stroke.size() - 1; j > i; j--) {
                                MetroConnection mc = stroke.remove(j);
                                unhandled.add(mc);
                            }
                            split = true;
                            break;
                        }
                    }

                    stroke.add(0, cand);
                    unhandled.remove(cand);

                    first = cand;
                    start = cand.theOther(start);

                    continuebackward = end != start && !split;
                }
            }

        }

        System.out.println(" strokes: " + strokes.size());

        StrokeNetwork result = new StrokeNetwork();

        // construct StrokeVertex
        List<List<StrokeVertex>> crosses = new ArrayList();
        Map<MetroStation, Map<List<MetroConnection>, StrokeVertex>> strokevertexmap = new HashMap();
        for (MetroStation station : metro.getStations()) {
            Map<List<MetroConnection>, StrokeVertex> strokemap = new HashMap();
            strokevertexmap.put(station, strokemap);

            List<StrokeVertex> cross = new ArrayList();

            for (List<MetroConnection> stroke : strokes) {
                boolean goesThroughStation = false;
                for (MetroConnection conn : station.getConnections()) {
                    if (stroke.contains(conn)) {
                        goesThroughStation = true;
                        break;
                    }
                }

                if (goesThroughStation) {
                    StrokeVertex sv = new StrokeVertex(station);
                    cross.add(sv);
                    strokemap.put(stroke, sv);
                }
            }

            if (cross.size() > 1) {
                crosses.add(cross);
            }
        }

        // construct Stroke & StrokeArc
        for (List<MetroConnection> stroke : strokes) {
            List<StrokeVertex> vtxs = new ArrayList();

            MetroStation start = stroke.get(0).getBeginStation(); //ARTHUR
            if (stroke.size() > 1) {
                start = stroke.get(0).theOther((stroke.get(1).getSharedStation(stroke.get(0))));
            }
            vtxs.add(strokevertexmap.get(start).get(stroke));

            MetroStation last = start;
            for (int i = 0; i < stroke.size(); i++) {
                MetroConnection conn = stroke.get(i);
                last = stroke.get(i).theOther(last);

                StrokeVertex from = vtxs.get(vtxs.size() - 1);
                StrokeVertex to = strokevertexmap.get(last).get(stroke);
                vtxs.add(to);

                StrokeArc arc = new StrokeArc(from, to, conn);
                from.setOutgoing(arc);
                to.setIncoming(arc);
            }

            boolean circular = last == start;
            if (circular) {
                vtxs.remove(vtxs.size() - 1);
            }

            Stroke constructedstroke = new Stroke(vtxs, circular);
            result.addStroke(constructedstroke);
        }

        // construct StrokeCross
        for (List<StrokeVertex> cross : crosses) {
            StrokeCross constructedcross = new StrokeCross(cross.get(0).getOriginal());
            result.addCross(constructedcross);

            for (StrokeVertex sv : cross) {
                sv.setCross(constructedcross);
                constructedcross.addStroke(sv.getStroke(), sv);
            }
        }

//        for (StrokeArc sa : result.getArcs()) {
//            for (StrokeCross sc : sa.getVirtuals()) {
//                boolean safe = false;
//                for (StrokeArc sb : sc.getVirtual().values()) {
//                    if (sb == sa) {
//                        safe = true;
//                    }
//                }
//                if (!safe) {
//                    boolean halt = true;
//                }
//            }
//        }
        for (StrokeCross sc : result.getCrosses()) {
            if (sc.getOriginal().getLabel().startsWith("virtual")) {
                for (Stroke s : sc.getStrokes()) {
                    StrokeVertex vert = sc.getConcrete().get(s);
                    if (vert.getIncoming() == null) {
                        System.err.println("inc? "+sc.getOriginal().getLabel());
                        System.err.println("     "+vert.getOriginal());
                    }
                    if (vert.getOutgoing() == null) {
                        System.err.println("out? "+sc.getOriginal().getLabel());
                        System.err.println("     "+vert.getOriginal());
                    }
                    StrokeVertex start = vert.getIncoming().getStart();
                    StrokeVertex end = vert.getOutgoing().getEnd();
                    List<MetroConnection> origins = new ArrayList<MetroConnection>(vert.getIncoming().getOriginaledges());
                    origins.addAll(vert.getOutgoing().getOriginaledges());
                    if (origins.get(0).getBeginStation() != start.getOriginal()) {
                        MetroStation beginStation = start.getOriginal();
                        for (MetroConnection mc : origins) {
                            if (mc.getBeginStation() != beginStation) {
                                MetroStation temp = mc.getBeginStation();
                                mc.setBeginStation(mc.getEndStation());
                                mc.setEndStation(temp);
                            }
                            beginStation = mc.getEndStation();
                        }
                    }
                    List<StrokeCross> virts = vert.getIncoming().getVirtuals();
                    virts.add(sc);
                    virts.addAll(vert.getOutgoing().getVirtuals());
                    StrokeArc newarc = new StrokeArc(start, end, new CircularArc(null, start, end, false), virts, origins);
                    for (StrokeCross sd : virts) {
                        sd.changeStroke(s, newarc);
                    }
                    start.setOutgoing(newarc);
                    end.setIncoming(newarc);
                    s.getVertices().remove(vert);

                }
            }
        }

        System.out.println("  construction done");

        //todo:  test intersections
        //todo2: insert virtual cross vertices
        for (StrokeVertex sv : result.getVertices()) {
            if (sv.getOutgoing() != null) {
                StrokeVertex next = sv.getNext();
                assert sv.getStroke() == next.getStroke();
            }
            if (sv.getIncoming() != null) {
                StrokeVertex prev = sv.getPrevious();
                assert sv.getStroke() == prev.getStroke();
            }
        }
        System.out.println("  test passed");

        return result;
    }

    private static MetroConnection findCandidate(MetroConnection last, MetroStation end, List<MetroConnection> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        candidates.add(last);
        // make continuation pairs

        ArrayList<Triple<Double, MetroConnection, MetroConnection>> turningAngles = new ArrayList();
        for (int i = 0; i < candidates.size(); i++) {
            MetroConnection mc1 = candidates.get(i);
            Vector dirA = Vector.subtract(end, mc1.theOther(end)); // entering "end"

            for (int j = i + 1; j < candidates.size(); j++) {
                MetroConnection mc2 = candidates.get(j);
                Vector dirB = Vector.subtract(mc2.theOther(end), end); // exiting "end"

                turningAngles.add(new Triple(Math.abs(dirA.computeSignedAngleTo(dirB)), mc1, mc2));
            }
        }
        Collections.sort(turningAngles, new Comparator<Triple<Double, MetroConnection, MetroConnection>>() {

            @Override
            public int compare(Triple<Double, MetroConnection, MetroConnection> o1, Triple<Double, MetroConnection, MetroConnection> o2) {
                return -Double.compare(o1.getFirst(), o2.getFirst());
            }
        });

        while (turningAngles.size() > 0) {
            Triple<Double, MetroConnection, MetroConnection> match = turningAngles.remove(turningAngles.size() - 1);

            if (match.getThird() == last) {
                return match.getSecond();
            } else if (match.getSecond() == last) {
                return match.getThird();
            }

            Iterator<Triple<Double, MetroConnection, MetroConnection>> it = turningAngles.iterator();
            while (it.hasNext()) {
                Triple<Double, MetroConnection, MetroConnection> next = it.next();

                if (next.getSecond() == match.getSecond()
                        || next.getSecond() == match.getThird()
                        || next.getThird() == match.getSecond()
                        || next.getThird() == match.getThird()) {
                    it.remove();
                }
            }
        }

        return null;
    }

    public static StrokeNetwork mergeStrokesAngle(StrokeNetwork network) {

        //find the 2 best matching strokes that end at this cross
        ArrayList<StrokeCross> removeCrosses = new ArrayList<StrokeCross>();
        for (StrokeCross cs : network.getCrosses()) {
            List<StrokeVertex> endingStrokes = new ArrayList<StrokeVertex>();

            for (StrokeVertex v : cs.getConcreteVertices()) {
                if ((v.getIncoming() == null || v.getOutgoing() == null) && !v.getStroke().isCircular()) { // TODO: inc/outgoing == null => non circular?
                    endingStrokes.add(v);
                }
            }

            ArrayList<Triple<Double, StrokeVertex, StrokeVertex>> turningAngles = new ArrayList<Triple<Double, StrokeVertex, StrokeVertex>>();
            for (int i = 0; i < endingStrokes.size(); i++) {
                StrokeVertex s = endingStrokes.get(i);
                Vector dirA;
                if (s.getIncoming() != null) {
                    // s is end, get end tangent
                    dirA = s.getIncoming().toGeometry().getEndTangent();
                } else {
                    // s is start, get start tangent and invert
                    dirA = s.getOutgoing().toGeometry().getStartTangent().clone();
                    dirA.invert();
                }
                for (int j = i + 1; j < endingStrokes.size(); j++) {
                    StrokeVertex t = endingStrokes.get(j);
                    Vector dirB;
                    if (t.getIncoming() != null) {
                        // t is end, get end tangent and invert
                        dirB = t.getIncoming().toGeometry().getEndTangent().clone();
                        dirB.invert();
                    } else {
                        // t is start, get start tangent
                        dirB = t.getOutgoing().toGeometry().getStartTangent();
                    }
                    turningAngles.add(new Triple(Math.abs(dirA.computeSignedAngleTo(dirB)), s, t));
                }
            }

            Collections.sort(turningAngles, new Comparator<Triple<Double, StrokeVertex, StrokeVertex>>() {

                @Override
                public int compare(Triple<Double, StrokeVertex, StrokeVertex> o1, Triple<Double, StrokeVertex, StrokeVertex> o2) {
                    return -Double.compare(o1.getFirst(), o2.getFirst());
                }
            });

            while (turningAngles.size() > 0) {
                Triple<Double, StrokeVertex, StrokeVertex> firstCombi = turningAngles.remove(turningAngles.size() - 1);

                if (!endingStrokes.contains(firstCombi.getSecond()) || !endingStrokes.contains(firstCombi.getThird())) {
                    continue;
                }

                //merge both strokes
                StrokeVertex sv1 = firstCombi.getSecond();
                StrokeVertex sv2 = firstCombi.getThird();

                StrokeVertex endVertex1 = firstCombi.getSecond().getStroke().getStartVertex();
                if (endVertex1 == sv1) {
                    endVertex1 = firstCombi.getSecond().getStroke().getEndVertex();
                }

                StrokeVertex endVertex2 = firstCombi.getThird().getStroke().getStartVertex();
                if (endVertex2 == sv2) {
                    endVertex2 = firstCombi.getSecond().getStroke().getEndVertex();
                }

                boolean createsLoop = false;

                for (int i = 0; i < sv1.getStroke().getVertices().size(); i++) {
                    for (int j = 0; j < sv2.getStroke().getVertices().size(); j++) {
                        if ((i == 0 || i == sv1.getStroke().getVertices().size() - 1) && (j == 0 || j == sv2.getStroke().getVertices().size() - 1)) {
                            continue;
                        }
                        if (sv1.getStroke().getVertices().get(i).isApproximately(sv2.getStroke().getVertices().get(j))) {
                            createsLoop = true;
                        }
                    }
                }

                if (createsLoop) {
                    continue;
                }

                ArrayList<StrokeCross> virts1 = new ArrayList<StrokeCross>();
                ArrayList<StrokeCross> virts2 = new ArrayList<StrokeCross>();

                for (int i = 0; i < sv1.getStroke().getArcCount(); i++) {
                    StrokeArc sa = sv1.getStroke().getArc(i);
                    for (StrokeCross sc : sa.getVirtuals()) {
                        virts1.add(sc);
                    }
                }

                for (int i = 0; i < sv2.getStroke().getArcCount(); i++) {
                    StrokeArc sa = sv2.getStroke().getArc(i);
                    for (StrokeCross sc : sa.getVirtuals()) {
                        virts2.add(sc);
                    }
                }

                endingStrokes.remove(firstCombi.getSecond());
                endingStrokes.remove(firstCombi.getThird());

                List<StrokeVertex> strokeVerts = new ArrayList<StrokeVertex>(sv1.getStroke().getVertices());
                List<StrokeVertex> strokeVerts1 = new ArrayList<StrokeVertex>();
                if (strokeVerts.get(strokeVerts.size() - 1) != sv1) {
                    strokeVerts1 = new ArrayList<StrokeVertex>();
                    StrokeVertex beginVertex = strokeVerts.remove(strokeVerts.size() - 1);
                    beginVertex.setOutgoing(beginVertex.getIncoming() != null ? beginVertex.getIncoming().reverse() : null);
                    beginVertex.setIncoming(null);
                    strokeVerts1.add(beginVertex);
                    for (int i = strokeVerts.size() - 1; i >= 0; i--) {
                        StrokeVertex curVertex = strokeVerts.remove(i);
                        strokeVerts1.add(curVertex);
                        StrokeArc temp = curVertex.getOutgoing(); //already reversed !=null? curVertex.getOutgoing().reverse(): null;
                        curVertex.setOutgoing(curVertex.getIncoming() != null ? curVertex.getIncoming().reverse() : null);
                        curVertex.setIncoming(temp);
                    }
                } else {
                    strokeVerts1.addAll(strokeVerts);
                }

                strokeVerts.clear();
                strokeVerts.addAll(sv2.getStroke().getVertices());
                StrokeVertex bridgeVertex = null;
                if (strokeVerts.get(0) != sv2) {
                    StrokeVertex curVertex = null;
                    bridgeVertex = strokeVerts.get(strokeVerts.size() - 2);
                    for (int i = strokeVerts.size() - 2; i >= 0; i--) {
                        curVertex = strokeVerts.remove(i);
                        strokeVerts1.add(curVertex);
                        StrokeArc temp = curVertex.getIncoming(); //will be reversed next step (or == null)
                        curVertex.setIncoming(curVertex.getOutgoing() != null ? curVertex.getOutgoing().reverse() : null);
                        curVertex.setOutgoing(temp);
                    }
                } else {
                    strokeVerts.remove(0);
                    bridgeVertex = strokeVerts.get(0);
                    strokeVerts1.addAll(strokeVerts);
                }

                StrokeArc bridgeArc = new StrokeArc(sv1, bridgeVertex, new CircularArc(null, sv1, bridgeVertex, false), bridgeVertex.getIncoming().getVirtuals(), bridgeVertex.getIncoming().getOriginaledges());

                for (StrokeCross sc : bridgeVertex.getIncoming().getVirtuals()) {
                    sc.changeStroke(bridgeVertex.getIncoming().getStroke(), bridgeArc);
                }
                Stroke oldFirstStroke = sv1.getStroke();
                Stroke oldSecondStroke = sv2.getStroke();

                sv1.setOutgoing(bridgeArc);
                bridgeVertex.setIncoming(bridgeArc);

                network.removeStroke(sv1.getStroke());
                network.removeStroke(sv2.getStroke());

                boolean circular = strokeVerts1.get(0).isApproximately(strokeVerts1.get(strokeVerts1.size() - 1));
                if (circular) {
                    strokeVerts1.remove(strokeVerts1.size() - 1);
                    StrokeArc outgoing = strokeVerts1.get(strokeVerts1.size() - 1).getOutgoing();
                    StrokeArc replacement = new StrokeArc(outgoing.getStart(), strokeVerts1.get(0), outgoing.toGeometry(), outgoing.getVirtuals(), outgoing.getOriginaledges());
                    strokeVerts1.get(strokeVerts1.size() - 1).setOutgoing(replacement);
                    strokeVerts1.get(0).setIncoming(replacement);
                }

                Stroke stroke1 = sv1.getStroke();
                Stroke stroke2 = sv2.getStroke();
                Stroke newStroke = new Stroke(strokeVerts1, circular);

                for (StrokeCross sc : virts1) {
                    sc.replaceStroke(oldFirstStroke, newStroke);
                }
                for (StrokeCross sc : virts2) {
                    sc.replaceStroke(oldSecondStroke, newStroke);
                }

                for (StrokeVertex v : newStroke.getVertices()) {
                    if (v.getCross() != null) {
                        v.getCross().removeStroke(stroke1);
                        v.getCross().removeStroke(stroke2);
                        v.getCross().addStroke(newStroke, v);
                        if (v.getCross().getConcrete().size() + v.getCross().getVirtual().size() == 1) {
                            removeCrosses.add(v.getCross());
                        }
                    }
                }

                network.addStroke(newStroke);
            }
            //merge
            //continue untill strokes ending here == 1 || 0
        }

        for (StrokeCross sv : removeCrosses) {
            network.removeCross(sv);
        }

        return network;
    }

    public static boolean containsOriginal(Stroke s, MetroStation m) {
        for (StrokeVertex sv : s.getVertices()) {
            if (sv.getOriginal() == m) {
                return true;
            }
        }
        return false;
    }

    public static StrokeNetwork mergeStrokesLines(StrokeNetwork network) {
        HashMap<MetroLine, List<Stroke>> lineMap = new HashMap<MetroLine, List<Stroke>>();
        HashMap<MetroLine, Integer> lineSizeMap = new HashMap<MetroLine, Integer>();
        List<Pair<Integer, MetroLine>> lineLengthList = new ArrayList<Pair<Integer, MetroLine>>();

//        getAllNetwork lines
//        for all lines store hashmap line -> list<strokes>
        for (Stroke s : network.getStrokes()) {
            ArrayList<MetroLine> lines = s.getArc(0).getOriginaledges().get(0).getLines();
            for (MetroLine l : lines) {
                List<Stroke> strokeList = lineMap.get(l);
                if (strokeList == null) {
                    strokeList = new ArrayList<Stroke>();
                }
                strokeList.add(s);
                lineMap.put(l, strokeList);
                Integer size = lineSizeMap.get(l);
                if (size == null) {
                    lineSizeMap.put(l, s.getVertices().size() - 1);
                } else {
                    lineSizeMap.put(l, size + s.getVertices().size() - 1);
                }
            }
        }

//        sort strokes
        for (Entry<MetroLine, List<Stroke>> entry : lineMap.entrySet()) {
            List<Stroke> strokes = sort(entry.getValue());
            lineMap.put(entry.getKey(), strokes);
        }

//        create line length list + sort
        for (Entry<MetroLine, Integer> entry : lineSizeMap.entrySet()) {
            lineLengthList.add(new Pair(entry.getValue(), entry.getKey()));
        }

        Comparator c = new Comparator<Pair<Integer, MetroLine>>() {

            @Override
            public int compare(Pair<Integer, MetroLine> o1, Pair<Integer, MetroLine> o2) {
                return o2.getFirst() - o1.getFirst(); //sort reverse
            }
        };
        Collections.sort(lineLengthList, c);

        while (lineLengthList.size() > 0) {
            MetroLine longest = lineLengthList.get(0).getSecond();
            lineLengthList.remove(0);

            //test maximum length possible (might be less in meantime)
            int maxLength = findMaxLength(lineMap.get(longest));
            if (lineLengthList.size() > 0 && maxLength < lineLengthList.get(0).getFirst()) {
                lineLengthList = addSorted(lineLengthList, new Pair(maxLength, longest));
                continue;
            }

            //        merge longest line
            List<Stroke> strokes = lineMap.get(longest);

            int total = strokes.size();
            strokes = longestSubStretch(strokes);
            int sub = strokes.size();

//         remove strokes from others
            for (Stroke s : strokes) {
                ArrayList<MetroLine> lines = s.getArc(0).getOriginaledges().get(0).getLines();
                for (MetroLine line : lines) {
                    if (line == longest) {
                        continue;
                    }
                    List<Stroke> strokesLine = lineMap.get(line);
                    strokesLine.remove(s);
                    lineMap.put(line, strokesLine);
                }
            }
            Stroke stroke = mergeStrokes(strokes);
            network.addStroke(stroke);

            for (Stroke s : strokes) {
                network.removeStroke(s);
            }

            if (total == sub) {
                lineMap.remove(longest);
            } else {
                List<Stroke> strokesAll = lineMap.get(longest);
                for (Stroke s : strokes) {
                    strokesAll.remove(s);
                }
                lineMap.put(longest, strokesAll);
            }
        }

        //fixCrosses
        List<StrokeCross> removeCrosses = new ArrayList<StrokeCross>();
        for (StrokeCross sc : network.getCrosses()) {
            if (sc.getConcrete().size() + sc.getVirtual().size() == 1) {
                removeCrosses.add(sc);
            }
        }

        for (StrokeCross sc : removeCrosses) {
            network.removeCross(sc);
        }

        //fix crosses
        for (StrokeCross sv : network.getCrosses()) {
            ArrayList<StrokeArc> arcs = new ArrayList<StrokeArc>();
            arcs.addAll(sv.getVirtual().values());
            sv.getVirtual().clear();
            for (StrokeArc a : arcs) {
                sv.getVirtual().put(a.getStroke(), a);
                sv.addVirtualPos(a.getStroke(), new Vector(sv.getOriginal()));
            }
            ArrayList<StrokeVertex> verts = new ArrayList<StrokeVertex>();
            verts.addAll(sv.getConcrete().values());
            sv.getConcrete().clear();
            for (StrokeVertex v : verts) {
                sv.getConcrete().put(v.getStroke(), v);
            }
        }

        return network;
    }

    private static List<Stroke> longestSubStretch(List<Stroke> strokes) {
        if (strokes == null || strokes.size() == 0) {
            return strokes;
        }

        List<Stroke> returnStrokes = new ArrayList<Stroke>();
        List<Stroke> returnStrokesBackup = new ArrayList<Stroke>();

        returnStrokes.add(strokes.get(0));
        for (int i = 1; i < strokes.size(); i++) {
            if ((strokes.get(i - 1).getStartVertex().getOriginal() == strokes.get(i).getStartVertex().getOriginal())
                    || (strokes.get(i - 1).getStartVertex().getOriginal() == strokes.get(i).getEndVertex().getOriginal())
                    || (strokes.get(i - 1).getEndVertex().getOriginal() == strokes.get(i).getEndVertex().getOriginal())
                    || (strokes.get(i - 1).getEndVertex().getOriginal() == strokes.get(i).getStartVertex().getOriginal())) {
                boolean loop = false;
                for (int j = 0; j < returnStrokes.size(); j++) {
                    for (int k = 0; k < returnStrokes.get(j).getVertices().size(); k++) {
                        for (int l = 0; l < strokes.get(i).getVertices().size(); l++) {
                            if ((k == 0 || k == returnStrokes.get(j).getVertices().size() - 1) && (l == 0 || l == strokes.get(i).getVertices().size() - 1)) {
                                continue;
                            }
                            if (returnStrokes.get(j).getVertices().get(k).isApproximately(strokes.get(i).getVertices().get(l))) {
                                loop = true;
                                break;
                            }
                        }
                        if (loop) {
                            break;
                        }
                    }
                    if (loop) {
                        break;
                    }
                }
                if (!loop) {
                    returnStrokes.add(strokes.get(i));
                } else if (sizeOfStrokes(returnStrokes) > sizeOfStrokes(returnStrokesBackup)) {
                    returnStrokesBackup.clear();
                    returnStrokesBackup.addAll(returnStrokes);
                    returnStrokes.clear();
                    returnStrokes.add(strokes.get(i));
                }

            } else {
                if (sizeOfStrokes(returnStrokes) > sizeOfStrokes(returnStrokesBackup)) {
                    returnStrokesBackup.clear();
                    returnStrokesBackup.addAll(returnStrokes);
                }
                returnStrokes.clear();
                returnStrokes.add(strokes.get(i));
            }
        }

        if (sizeOfStrokes(returnStrokes) > sizeOfStrokes(returnStrokesBackup)) {
            return returnStrokes;
        } else {
            return returnStrokesBackup;
        }
    }

    private static int sizeOfStrokes(List<Stroke> strokes) {
        int returnValue = 0;
        for (Stroke s : strokes) {
            returnValue += s.getVertices().size() - 1;
        }
        return returnValue;
    }

    private static Stroke mergeStrokes(List<Stroke> strokes) {
        if (strokes == null || strokes.size() == 0) {
            return null;
        }

        Stroke finalStroke = strokes.get(0);
        for (int i = 1; i < strokes.size(); i++) {
            finalStroke = mergeStrokes(finalStroke, strokes.get(i));
        }

        return finalStroke;
    }

    private static Stroke mergeStrokes(Stroke a, Stroke b) {
        StrokeVertex connectVert1 = null, connectVert2 = null;

        if (a.getStartVertex().isApproximately(b.getStartVertex())) {
            connectVert1 = a.getStartVertex();
            connectVert2 = b.getStartVertex();
        } else if (a.getStartVertex().isApproximately(b.getEndVertex())) {
            connectVert1 = a.getStartVertex();
            connectVert2 = b.getEndVertex();
        } else if (a.getEndVertex().isApproximately(b.getStartVertex())) {
            connectVert1 = a.getEndVertex();
            connectVert2 = b.getStartVertex();
        } else if (a.getEndVertex().isApproximately(b.getEndVertex())) {
            connectVert1 = a.getEndVertex();
            connectVert2 = b.getEndVertex();
        }

        ArrayList<StrokeCross> virts1 = new ArrayList<StrokeCross>();
        ArrayList<StrokeCross> virts2 = new ArrayList<StrokeCross>();

        for (int i = 0; i < a.getArcCount(); i++) {
            StrokeArc sa = a.getArc(i);
            for (StrokeCross sc : sa.getVirtuals()) {
                virts1.add(sc);
            }
        }

        for (int i = 0; i < b.getArcCount(); i++) {
            StrokeArc sa = b.getArc(i);
            for (StrokeCross sc : sa.getVirtuals()) {
                virts2.add(sc);
            }
        }

        List<StrokeVertex> strokeVerts = connectVert1.getStroke().getVertices();
        List<StrokeVertex> strokeVerts1;
        if (strokeVerts.get(strokeVerts.size() - 1) != connectVert1) {
            strokeVerts1 = new ArrayList<StrokeVertex>();
            StrokeVertex beginVertex = strokeVerts.remove(strokeVerts.size() - 1);
            beginVertex.setOutgoing(beginVertex.getIncoming() != null ? beginVertex.getIncoming().reverse() : null);
            beginVertex.setIncoming(null);
            strokeVerts1.add(beginVertex);
            for (int i = strokeVerts.size() - 1; i >= 0; i--) {
                StrokeVertex curVertex = strokeVerts.remove(i);
                strokeVerts1.add(curVertex);
                StrokeArc temp = curVertex.getOutgoing(); //already reversed !=null? curVertex.getOutgoing().reverse(): null;
                curVertex.setOutgoing(curVertex.getIncoming() != null ? curVertex.getIncoming().reverse() : null);
                curVertex.setIncoming(temp);
            }
        } else {
            strokeVerts1 = strokeVerts;
        }

        strokeVerts = connectVert2.getStroke().getVertices();
        StrokeVertex bridgeVertex = null;
        if (strokeVerts.get(0) != connectVert2) {
            StrokeVertex curVertex = null;
            bridgeVertex = strokeVerts.get(strokeVerts.size() - 2);
            for (int i = strokeVerts.size() - 2; i >= 0; i--) {
                curVertex = strokeVerts.remove(i);
                strokeVerts1.add(curVertex);
                StrokeArc temp = curVertex.getIncoming(); //will be reversed next step (or == null)
                curVertex.setIncoming(curVertex.getOutgoing() != null ? curVertex.getOutgoing().reverse() : null);
                curVertex.setOutgoing(temp);
            }
        } else {
            strokeVerts.remove(0);
            bridgeVertex = strokeVerts.get(0);
            strokeVerts1.addAll(strokeVerts);
        }

        StrokeArc bridgeArc = new StrokeArc(connectVert1, bridgeVertex, new CircularArc(null, connectVert1, bridgeVertex, false), bridgeVertex.getIncoming().getVirtuals(), bridgeVertex.getIncoming().getOriginaledges());

        connectVert1.setOutgoing(bridgeArc);
        bridgeVertex.setIncoming(bridgeArc);

        boolean circular = strokeVerts1.get(0).isApproximately(strokeVerts1.get(strokeVerts1.size() - 1));
        if (circular) {
            strokeVerts1.remove(strokeVerts1.size() - 1);
        }

        Stroke stroke1 = connectVert1.getStroke();
        Stroke stroke2 = connectVert2.getStroke();
        Stroke newStroke = new Stroke(strokeVerts1, circular);

        for (StrokeCross sc : virts1) {
            sc.replaceStroke(a, newStroke);
        }
        for (StrokeCross sc : virts2) {
            sc.replaceStroke(b, newStroke);
        }

        return newStroke;
    }

    private static List<Stroke> sort(List<Stroke> strokes) {
        if (strokes == null || strokes.size() == 0) {
            return strokes;
        }

        //O(n^2)
        ArrayList<Stroke> partialStrokeOrder = new ArrayList<Stroke>();
        ArrayList<Stroke> strokeOrder = new ArrayList<Stroke>();
        partialStrokeOrder.add(strokes.get(0));
        strokes.remove(0);
        MetroStation startStation = partialStrokeOrder.get(0).getStartVertex().getOriginal();
        MetroStation endVertex = partialStrokeOrder.get(0).getEndVertex().getOriginal();
        while (strokes.size() > 0) {
            int before = strokes.size();
            for (int i = 0; i < strokes.size(); i++) {
                if (strokes.get(i).getStartVertex().getOriginal() == startStation || strokes.get(i).getEndVertex().getOriginal() == startStation) {
                    partialStrokeOrder.add(0, strokes.get(i));
                    if (strokes.get(i).getStartVertex().getOriginal() == startStation) {
                        startStation = strokes.get(i).getEndVertex().getOriginal();
                    } else {
                        startStation = strokes.get(i).getStartVertex().getOriginal();
                    }
                    strokes.remove(i);
                    i = -1;
                } else if (strokes.get(i).getStartVertex().getOriginal() == endVertex || strokes.get(i).getEndVertex().getOriginal() == endVertex) {
                    partialStrokeOrder.add(strokes.get(i));
                    if (strokes.get(i).getEndVertex().getOriginal() == endVertex) {
                        endVertex = strokes.get(i).getStartVertex().getOriginal();
                    } else {
                        endVertex = strokes.get(i).getEndVertex().getOriginal();
                    }
                    strokes.remove(i);
                    i = -1;
                }
            }
            int after = strokes.size();
            if (before == after) {
                strokeOrder.addAll(partialStrokeOrder);
                partialStrokeOrder.clear();
                partialStrokeOrder.add(strokes.remove(0));
                startStation = partialStrokeOrder.get(0).getStartVertex().getOriginal();
                endVertex = partialStrokeOrder.get(0).getEndVertex().getOriginal();
            }
        }
        strokeOrder.addAll(partialStrokeOrder);
        return strokeOrder;
    }

    //assumes strokes ordered
    private static int findMaxLength(List<Stroke> strokes) {
        int maxLength = 0;
        int curLength = 0;
        int i = 0;
        boolean matches = true;
        if (strokes.size() == 0) {
            return 0;
        }
        curLength = strokes.get(0).getArcCount();
        while (matches && i < strokes.size() - 1) {
            matches = strokes.get(i).getStartVertex().isApproximately(strokes.get(i + 1).getStartVertex())
                    || strokes.get(i).getStartVertex().isApproximately(strokes.get(i + 1).getEndVertex())
                    || strokes.get(i).getEndVertex().isApproximately(strokes.get(i + 1).getEndVertex())
                    || strokes.get(i).getEndVertex().isApproximately(strokes.get(i + 1).getStartVertex());
            if (matches) {
                curLength += strokes.get(i + 1).getArcCount();
            } else {
                if (curLength > maxLength) {
                    maxLength = curLength;
                }
                curLength = strokes.get(i + 1).getArcCount();
            }
            i++;
        }
        if (curLength > maxLength) {
            maxLength = curLength;
        }
        return maxLength;
    }

    private static List<Pair<Integer, MetroLine>> addSorted(List<Pair<Integer, MetroLine>> sortedList, Pair<Integer, MetroLine> newItem) {
        int min = 0;
        int max = sortedList.size();

        while (min < max - 1) {
            int testPos = (min + max) / 2;
            int test = sortedList.get(testPos).getFirst();
            if (test > newItem.getFirst()) {
                min = testPos;
            } else {
                max = testPos;
            }
        }
        sortedList.add(max, newItem);
        return sortedList;
    }

    private static boolean sameSet(List<MetroLine> set1, List<MetroLine> set2) {
        if (set1.size() != set2.size()) {
            return false;
        }

        for (MetroLine L : set1) {
            boolean hasL = false;
            for (MetroLine l2 : set2) {
                if (l2 == L) {
                    hasL = true;
                    break;
                }
            }
            if (!hasL) {
                return false;
            }
        }

        return true;
    }
}
