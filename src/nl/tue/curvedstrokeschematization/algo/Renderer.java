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

import nl.tue.curvedstrokeschematization.algo.store.SchematizationStore.ConnectionNode;
import nl.tue.curvedstrokeschematization.algo.store.SchematizationStore.QueryResult;
import nl.tue.curvedstrokeschematization.algo.store.SchematizationStore.StationNode;
import nl.tue.curvedstrokeschematization.data.metro.MetroConnection;
import nl.tue.curvedstrokeschematization.data.metro.MetroLine;
import nl.tue.curvedstrokeschematization.data.metro.MetroStation;
import nl.tue.curvedstrokeschematization.data.rendered.RenderedArc;
import nl.tue.curvedstrokeschematization.data.rendered.RenderedNetwork;
import nl.tue.curvedstrokeschematization.data.rendered.RenderedStation;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeArc;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeCross;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeNetwork;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeVertex;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.curved.Circle;
import nl.tue.geometrycore.geometry.curved.CircularArc;
import nl.tue.geometrycore.geometry.linear.Line;
import nl.tue.geometrycore.geometry.linear.Polygon;
import nl.tue.geometrycore.util.Pair;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class Renderer {

    static double linethick;
    static double rimwidth;
    static double uniformRadius;
    static boolean uniformStations;
    static double minStationSize;
    static double maxEdgeWidth;
    static boolean useColor;

    public enum InterchangeStyle {

        SMALLEST_ENCLOSING_DISK,
        CONVEX_HULL;
    }

    public Renderer() {
    }

    public double getLargestNormalStationRadius(Iterable<StrokeVertex> vertices, double linethick) {
        //get diameter required to fit all stations across incoming lines
        int maxIncomingLines = 0;
        for (StrokeVertex s : vertices) {
            ArrayList<MetroConnection> connections = s.getOriginal().getConnections();
            for (int i = 0; i < connections.size(); i++) {
                int lines = connections.get(i).getLines().size();
                if (lines > maxIncomingLines) {
                    maxIncomingLines = lines;
                }
            }
        }

        return linethick * maxIncomingLines / 2.0;
    }

    //can still generate odd crossings
    private void preprocessLineOrder(StrokeNetwork stroked) {
        Iterable<StrokeVertex> vertices = stroked.getVertices();
        ArrayList<StrokeVertex> verticesList = new ArrayList();
        for (StrokeVertex v : vertices) {
            verticesList.add(v);
        }

        Collections.sort(verticesList, (StrokeVertex o1, StrokeVertex o2) -> {
            if (o1.getX() < o2.getX()) {
                return -1;
            } else if (o1.getX() > o2.getX()) {
                return 1;
            } else if (o1.getY() < o2.getY()) {
                return -1;
            } else if (o1.getY() > o2.getY()) {
                return 1;
            } else {
                return 0;
            }
        });

        for (StrokeVertex sc : verticesList) {
//            check connections in l-to-r order
            ArrayList<MetroConnection> connections = new ArrayList();
            connections.addAll(sc.getOriginal().getConnections());
            ArrayList<MetroLine> order = new ArrayList();

            //above vs below
            connections = sort(connections, sc.getOriginal());
            if (connections.isEmpty()) {
                continue;
            }
            Vector top = Vector.subtract(connections.get(0).theOther(sc.getOriginal()), sc.getOriginal());
            top.normalize();
            for (MetroConnection mc : connections) {
                boolean above = false;
                double angle = top.computeClockwiseAngleTo(Vector.subtract(mc.theOther(sc.getOriginal()), sc.getOriginal()));
                if (angle < Math.PI && angle > 0.0) {
                    above = true;
                }
                List<MetroLine> lines = new ArrayList();
                lines.addAll(mc.getLines());
                if (mc.getBeginStation().isApproximately(sc)) {
                    Collections.reverse(lines);
                }
                if (above) {
                    Collections.reverse(lines);
                }
                for (MetroLine ml : lines) {
                    if (!order.contains(ml)) {
                        if (above) {
                            order.add(0, ml);
                        } else {
                            order.add(ml);
                        }
                    }
                }
                //sort outgoing
                ArrayList<MetroLine> sortedOutgoing = new ArrayList();
                for (MetroLine m : order) {
                    if (lines.contains(m)) {
                        sortedOutgoing.add(m);
                    }
                }
                if (isLarger(mc.theOther(sc.getOriginal()), sc.getOriginal())) {
                    if (!mc.getBeginStation().isApproximately(sc)) {
                        Collections.reverse(sortedOutgoing);
                    }
                    mc.setLines(sortedOutgoing);
                }
            }

//            if (stroke on edge is incoming move to right)
//            else (sort rest by predetermined order)
//            propagate in directions till next cross
        }
    }

    private ArrayList<MetroConnection> sort(ArrayList<MetroConnection> connections, MetroStation center) {
        if (connections == null || connections.size() <= 1) {
            return connections;
        }

        ArrayList<MetroConnection> sortedConnections = new ArrayList();
        sortedConnections.add(connections.remove(0));
        while (connections.size() > 0) {
            MetroConnection m = connections.remove(0);

            int min = 0;
            int max = sortedConnections.size();
            int testPos = max;
            while (min < max && testPos != min) {
                testPos = (min + max) / 2;
                if (isLarger(sortedConnections.get(testPos).theOther(center), m.theOther(center))) {
                    max = testPos;
                } else {
                    min = testPos;
                }
            }
            sortedConnections.add(max, m);
        }

        return sortedConnections;
    }

    private boolean isLarger(MetroStation a, MetroStation b) {
        if (a.getX() < b.getX()) {
            return false;
        } else if (a.getX() > b.getX()) {
            return true;
        } else if (a.getY() < b.getY()) {
            return false;
        } else {
            return true;
        }
    }

    public RenderedNetwork render(QueryResult query,
            double linethick, double rimwidth,
            InterchangeStyle style, boolean uniformStations, double minStationSize, double maxEdgeWidth, boolean useColorStations) {
        ArrayList<StrokeVertex> vertices = new ArrayList();
        ArrayList<StrokeArc> arcs = new ArrayList();
        HashSet<StrokeCross> crossSet = new HashSet();
        ArrayList<StrokeCross> crosses = new ArrayList();

        int i = 0;
        for (ConnectionNode cn : query.connections) {
            StrokeVertex sv = new StrokeVertex(new MetroStation(i, cn.getArc().getStart(), "F" + i, "F" + i, false)); // TODO, probably not quite right?
            i++;
            sv.setStroke(cn.getStroke());
            arcs.add(new StrokeArc(sv, new StrokeVertex(new MetroStation(i, cn.getArc().getEnd(), "F" + i, "F" + i, false)), cn.getArc(), cn.getVirtualCrosses(), cn.getConnections()));
            i++;
            crossSet.addAll(cn.getVirtualCrosses());
        }

        crosses.addAll(crossSet);

        StrokeCross cross = null;
        for (StationNode sn : query.stations) {
            cross = null;
            for (StrokeCross sc : crosses) {
                if (sc.getOriginal() == sn.getStation()) {
                    cross = sc;
                    break;
                }
            }
            StrokeVertex sv = new StrokeVertex(sn.getStation());
            if (cross != null) {
                sv.setCross(cross);
            }
            vertices.add(sv);
        }
        return render(vertices, arcs, crosses, linethick, rimwidth, style, uniformStations, minStationSize, maxEdgeWidth, useColorStations);
//        return null;
    }

    public RenderedNetwork render(StrokeNetwork stroked,
            double linethick, double rimwidth,
            InterchangeStyle style, boolean uniformStations, double minStationSize, double maxEdgeWidth, boolean useColorStations) {
        preprocessLineOrder(stroked);
        ArrayList<StrokeArc> arcs = new ArrayList();
        ArrayList<StrokeVertex> vertices = new ArrayList();
        for (StrokeArc arc : stroked.getArcs()) {
            arcs.add(arc);
        }
        for (StrokeVertex sv : stroked.getVertices()) {
            vertices.add(sv);
        }
        return render(vertices, arcs, stroked.getCrosses(), linethick, rimwidth, style, uniformStations, minStationSize, maxEdgeWidth, useColorStations);
    }

    public RenderedNetwork render(List<StrokeVertex> vertices, List<StrokeArc> arcs, List<StrokeCross> crosses,
            double linethick, double rimwidth,
            InterchangeStyle style, boolean uniformStations, double minStationSize, double maxEdgeWidth, boolean useColorStations) {
        Renderer.linethick = linethick;
        Renderer.rimwidth = rimwidth;
        Renderer.uniformStations = uniformStations;
        Renderer.minStationSize = minStationSize;
        Renderer.maxEdgeWidth = maxEdgeWidth;
        Renderer.useColor = useColorStations;

        RenderedNetwork rendered = new RenderedNetwork();

        uniformRadius = getLargestNormalStationRadius(vertices, linethick); //TODO: this is wrong! what about virt stations?

        //** Do arcs + Virt Verts **
        for (StrokeArc sa : arcs) {
            Pair<ArrayList<RenderedArc>, ArrayList<RenderedStation>> renderArcs = breakArc(sa);

            for (RenderedArc arc : renderArcs.getFirst()) {
                rendered.addArc(arc);
            }
            for (RenderedStation station : renderArcs.getSecond()) {
                rendered.addStation(station);
            }
        }

        //** Do vertices **
        for (StrokeVertex s : vertices) {
            //regular vertex
            if (s.getCross() == null) {
                //rendered.addStation(makeStation(s.getOriginal(), s.getOriginal().getMaxIncomingLines()));
                rendered.addStation(makeStation(s.getOriginal(), useColor));
            }
        }

        for (StrokeCross cv : crosses) {
            if (cv.getOriginal().getLabel().length() > 7 && cv.getOriginal().getLabel().substring(0, 7).equals("virtual")) {
                continue;
            }
            //handle 1 cross vertex only once -> move out
            Collection<StrokeVertex> concreteVerts = cv.getConcreteVertices();
            List<Vector> concretePositions = new ArrayList();
            for (StrokeVertex v : concreteVerts) {
                concretePositions.add(v);
            }

            switch (style) {
                case SMALLEST_ENCLOSING_DISK:
                    Circle smallestDisc = cv.getSmallestDisc().clone();
                    smallestDisc.setRadius(smallestDisc.getRadius() + linethick / 2.0);
                    //If radius smaller than regular vertices, upgrade to regular radius
                    if (uniformStations && smallestDisc.getRadius() < uniformRadius) {
                        smallestDisc.setRadius(uniformRadius);
                    } else {
                        int numberLines = cv.getOriginal().getMaxIncomingLines();
                        double lineWidth = linethick;
                        if (lineWidth * numberLines > maxEdgeWidth) {
                            lineWidth = maxEdgeWidth / numberLines;
                        }

                        double radius = uniformRadius;
                        if (!uniformStations) {
                            radius = lineWidth * numberLines / 2.0;
                        }

                        if (radius < minStationSize) {
                            radius = minStationSize;
                        }

                        if (smallestDisc.getRadius() < radius) {
                            smallestDisc.setRadius(radius);
                        }
                    }
                    if (smallestDisc.getRadius() < minStationSize) {
                        smallestDisc.setRadius(minStationSize);
                    }
                    rendered.addStation(new RenderedStation(Color.black, rimwidth * smallestDisc.getRadius() / 50.0, Color.white, cv.getOriginal(), smallestDisc));
                    break;
                case CONVEX_HULL:
                    concreteVerts = cv.getConcreteVertices();
                    concretePositions = new ArrayList<Vector>();
                    concretePositions.addAll(concreteVerts);
                    concretePositions.addAll(cv.getVirtualPos());
                    concretePositions.addAll(cv.getIntersections());
                    if (concretePositions.size() == 1) //TODO: (take virtual pos into account!!)
                    {
                        rendered.addStation(makeStation(concretePositions.get(0), cv.getOriginal(), cv.getOriginal().getMaxIncomingLines()));
                    } else {
                        Polygon convexHull = getConvexHull(concretePositions);

                        //OPT: inflate convex hull
                        if (convexHull.vertexCount() == 2 && convexHull.vertex(0).isApproximately(convexHull.vertex(1))) {
                            rendered.addStation(makeStation(concretePositions.get(0), cv.getOriginal(), cv.getOriginal().getMaxIncomingLines()));
                        } else {
                            convexHull = inflateConvexHull(convexHull, 5.0);
                            rendered.addStation(new RenderedStation(Color.black, rimwidth, Color.white, cv.getOriginal(), convexHull));
                        }
                    }
                    break;
                default:
                    System.out.println("ERROR: style unknown");
                    break;
            }
        }
        return rendered;
    }

    private Polygon inflateConvexHull(Polygon convexIn, double inflateBy) {
        if (convexIn.vertexCount() == 2) {
            Vector dir = Vector.subtract(convexIn.vertex(1), convexIn.vertex(0));
            dir.normalize();

            List<Vector> inflatedVerts = new ArrayList<Vector>();

            inflatedVerts.add(Vector.add(convexIn.vertex(1), Vector.multiply(inflateBy, dir)));
            inflatedVerts.add(Vector.add(convexIn.vertex(1), Vector.multiply(inflateBy, Vector.rotate(dir, -90))));
            inflatedVerts.add(Vector.add(convexIn.vertex(0), Vector.multiply(inflateBy, Vector.rotate(dir, -90))));
            inflatedVerts.add(Vector.add(convexIn.vertex(0), Vector.multiply(-inflateBy, dir)));
            inflatedVerts.add(Vector.add(convexIn.vertex(0), Vector.multiply(inflateBy, Vector.rotate(dir, 90))));
            inflatedVerts.add(Vector.add(convexIn.vertex(1), Vector.multiply(inflateBy, Vector.rotate(dir, 90))));
            return new Polygon(inflatedVerts);
        }
        System.out.println("OPT: inflate convex hull with more than 2 vertices");
        //convex hull is convex :)

        List<Vector> newVerts = new ArrayList();
        inflateBy *= 2;
        for (int i = 0; i < convexIn.vertices().size(); i++) {
            Vector lastEdgeDir = Vector.subtract(convexIn.vertex(i - 1), convexIn.vertex(i));
            lastEdgeDir.normalize();
            Vector nextEdgeDir = Vector.subtract(convexIn.vertex(i + 1), convexIn.vertex(i));
            nextEdgeDir.normalize();
            Vector outDirection = Vector.add(lastEdgeDir, nextEdgeDir);
            outDirection.invert();
            outDirection.normalize();
//            System.out.println(lastEdgeDir.length() + " " + nextEdgeDir.length() + " " + outDirection.length());
            //ASSUMES VERTICES IN CW-order
            newVerts.add(Vector.add(convexIn.vertex(i), Vector.multiply(inflateBy, Vector.rotate(lastEdgeDir, 90))));
            newVerts.add(Vector.add(convexIn.vertex(i), Vector.multiply(inflateBy, outDirection)));
            newVerts.add(Vector.add(convexIn.vertex(i), Vector.multiply(inflateBy, Vector.rotate(nextEdgeDir, -90))));
        }

        return new Polygon(newVerts);
    }

    private Pair<ArrayList<RenderedArc>, ArrayList<RenderedStation>> breakArc(StrokeArc sa) {
        ArrayList<RenderedArc> returnArcs = new ArrayList();
        ArrayList<RenderedStation> returnStations = new ArrayList();
        List<StrokeCross> virtVerts = new ArrayList();
        if (sa.getVirtuals() != null) {
            virtVerts.addAll(sa.getVirtuals());
        }
//        for (int i = 0; i < virtVerts.size(); i++)
//        {
//            if (virtVerts.get(i).getOriginal().getLabel().length() > 7 && virtVerts.get(i).getOriginal().getLabel().substring(0, 7).equals("virtual"))
//            {
//                virtVerts.remove(i);
//                i--;
//            }
//        }
        List<MetroConnection> origConnects = sa.getOriginaledges();

        if (origConnects.size() == 1) {
            returnArcs.addAll(createArcs(sa.toGeometry().getCenter(), sa.toGeometry().getStart(), sa.toGeometry().getEnd(), origConnects.get(0), linethick));
            return new Pair(returnArcs, returnStations);
        }

        Vector center = sa.toGeometry().getCenter();
        int start = 0;
        int end = 0;
        boolean isCW = !sa.toGeometry().isCounterclockwise();
        Vector lastMainVert = sa.getStart();
        MetroStation lastStation = origConnects.get(0).theOther(origConnects.get(1).getSharedStation(origConnects.get(0)));

        //break at virtVerts into different arcs        
        //for (StrokeCross sc : virtVerts)
        while (virtVerts.size() > 0) {
            if (end >= origConnects.size()) {
                System.out.println("ERROR");
                return null;
            }
            boolean nextFound = false;
            StrokeCross sc = null;
            while (!nextFound) {
                for (StrokeCross cross : virtVerts) {
                    if (origConnects.get(end).theOther(lastStation).isApproximately(cross.getOriginal())) {
                        nextFound = true;
                        sc = cross;
                        break;
                    }
                }
                if (!nextFound) {
                    lastStation = origConnects.get(end).theOther(lastStation);
                    end++;
                    if (end >= origConnects.size()) {
                        System.out.println("ERROR: end reached before station found");
                        return null;
                    }
                }
            }

            double angle = 0.0;
            //Vector crossCenter = SECcomputation.getSmallestEnclosingDisc(sc).getCenter();
            Vector crossIntersect = sc.getVirtualPos(sa.getStroke());
            Vector endPos = sa.getStart();

            if (center != null) {
                if (isCW) {
                    angle = Vector.subtract(lastMainVert, center).computeClockwiseAngleTo(Vector.subtract(crossIntersect, center));
                } else {
                    angle = Vector.subtract(lastMainVert, center).computeCounterClockwiseAngleTo(Vector.subtract(crossIntersect, center));
                }

                angle /= (end + 1.0 - start);
                if (isCW) {
                    angle = -angle;
                }

                Vector lastDir = Vector.subtract(lastMainVert, center);
                Vector startPos = lastMainVert;
                for (int i = start; i <= end; i++) {
                    lastDir = Vector.rotate(lastDir, angle);
                    endPos = Vector.add(center, lastDir);
                    ArrayList<RenderedArc> arcs;
                    if (isCW) {
                        arcs = createArcs(center, startPos, endPos, origConnects.get(i), linethick);
                    } else {
                        arcs = createArcs(center, endPos, startPos, origConnects.get(i), linethick);
                    }
                    if (i < end) {
                        returnStations.add(makeStation(endPos, sa.getOriginaledges().get(i).getSharedStation(sa.getOriginaledges().get(i + 1)), useColor));
                    }
                    returnArcs.addAll(arcs);
                    startPos = endPos;
                }
            } else {
                Vector shiftFraction = Vector.subtract(crossIntersect, lastMainVert);
                shiftFraction.scale(1.0 / (end + 1.0 - start));
                Vector startPos = lastMainVert;
                for (int i = start; i <= end; i++) {
                    endPos = Vector.add(startPos, shiftFraction);
                    ArrayList<RenderedArc> arcs = createArcs(center, startPos, endPos, origConnects.get(i), linethick);
                    if (i < end) {
                        returnStations.add(makeStation(endPos, sa.getOriginaledges().get(i).getSharedStation(sa.getOriginaledges().get(i + 1)), useColor));
                    }
                    returnArcs.addAll(arcs);
                    startPos = endPos;
                }
            }
            lastMainVert = endPos;
            lastStation = origConnects.get(end).theOther(lastStation);
            end++;
            start = end;
            virtVerts.remove(sc);
        }

//        while (!origConnects.get(end).theOther(lastStation).close(sa.getEnd()))
//        {
//            lastStation = origConnects.get(end).theOther(lastStation);
//            end++;
//            if (end >= origConnects.size())
//            {
//                System.out.println("ERROR");
//                return null;
//            }
//        }
        end = origConnects.size() - 1;

        double angle = 0.0;
        if (center != null) {
            if (isCW) {
                angle = Vector.subtract(lastMainVert, center).computeClockwiseAngleTo(Vector.subtract(sa.getEnd(), center));
            } else {
                angle = Vector.subtract(lastMainVert, center).computeCounterClockwiseAngleTo(Vector.subtract(sa.getEnd(), center));
            }

            angle /= (end + 1.0 - start);
            if (isCW) {
                angle = -angle;
            }

            Vector lastDir = Vector.subtract(lastMainVert, center);
            for (int i = start; i <= end; i++) {
                Vector startPos = Vector.add(center, lastDir);
                lastDir = Vector.rotate(lastDir, angle);
                Vector endPos = Vector.add(center, lastDir);
                ArrayList<RenderedArc> arcs;
                if (isCW) {
                    arcs = createArcs(center, startPos, endPos, origConnects.get(i), linethick);
                } else {
                    arcs = createArcs(center, endPos, startPos, origConnects.get(i), linethick);
                }
                if (i < end) {
                    returnStations.add(makeStation(endPos, sa.getOriginaledges().get(i).getSharedStation(sa.getOriginaledges().get(i + 1)), useColor));
//                    returnStations.add(makeStation(endPos, sa, Math.max(sa.getOriginaledges().get(i).getLines().size(), sa.getOriginaledges().get(i + 1).getLines().size())));
                }
                returnArcs.addAll(arcs);
            }
        } else {
            Vector shiftFraction = Vector.subtract(sa.getEnd(), lastMainVert);
            shiftFraction.scale(1.0 / (end + 1.0 - start));
            Vector startPos = lastMainVert;
            for (int i = start; i <= end; i++) {
                Vector endPos = Vector.add(startPos, shiftFraction);
                ArrayList<RenderedArc> arcs;
                arcs = createArcs(center, startPos, endPos, origConnects.get(i), linethick);
                if (i < end) {
                    returnStations.add(makeStation(endPos, sa.getOriginaledges().get(i).getSharedStation(sa.getOriginaledges().get(i + 1)), useColor));
                }
                returnArcs.addAll(arcs);
                startPos = endPos;
            }
        }

        return new Pair(returnArcs, returnStations);
    }

    //start and end should be given ccw
    private static ArrayList<RenderedArc> createArcs(Vector center, Vector start, Vector end, MetroConnection connection, double linethick) {
        List<MetroLine> lines = connection.getLines();
        ArrayList<RenderedArc> multipleArcs = new ArrayList<RenderedArc>();

        int number = lines.size();
        double lineWidth = linethick;

        if (lineWidth * number > maxEdgeWidth) {
            lineWidth = maxEdgeWidth / number;
        }

        if (center != null) {
            double radius = start.distanceTo(center);
            Vector dirStart = Vector.subtract(start, center);
            dirStart.normalize();
            Vector dirEnd = Vector.subtract(end, center);
            dirEnd.normalize();

            for (int i = 0; i < number; i++) {
                double thisRadius = radius + (i - (number - 1.0) / 2.0) * lineWidth;
                CircularArc ca = new CircularArc(Vector.origin(), dirStart.clone(), dirEnd.clone(), false);
                ca.scale(thisRadius);
                ca.translate(center);
                multipleArcs.add(new RenderedArc(lines.get(i).getColor(), lineWidth, connection, ca));
            }
        } else {
            Vector shift = Vector.subtract(end, start);
            shift.normalize();
            shift.rotate90DegreesClockwise();
            shift.scale(lineWidth);
            for (int i = 0; i < number; i++) {
                shift.normalize();
                shift.scale(lineWidth);
                shift.scale(i - (number - 1.0) / 2.0);
                CircularArc ca = new CircularArc(null, start.clone(), end.clone(), false);
                ca.translate(shift);
                multipleArcs.add(new RenderedArc(lines.get(i).getColor(), lineWidth, connection, ca));
            }
        }
        return multipleArcs;
    }

    private Polygon getConvexHull(List<Vector> vertices) {
        //degenerate cases
        if (vertices.size() <= 2) {
            return new Polygon(vertices);
        }

        List<Vector> upper = new ArrayList<Vector>();
        List<Vector> lower = new ArrayList<Vector>();

        //sort items
        Collections.sort(vertices, new Comparator<Vector>() {

            public int compare(Vector a, Vector b) {
                if (a.getX() < b.getX()) {
                    return -1;
                } else if (a.getX() == b.getX() && a.getY() < b.getY()) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });

        upper.add(vertices.get(vertices.size() - 1));
        upper.add(vertices.get(vertices.size() - 2));

        for (int i = vertices.size() - 3; i >= 0; i--) {
            upper.add(vertices.get(i));
            while (upper.size() > 2 && isRightTurn(upper.get(upper.size() - 3), upper.get(upper.size() - 2), upper.get(upper.size() - 1))) {
                upper.remove(upper.size() - 2);
            }
        }

        lower.add(vertices.get(0));
        lower.add(vertices.get(1));

        for (int i = 2; i < vertices.size(); i++) {
            lower.add(vertices.get(i));
            while (lower.size() > 2 && isRightTurn(lower.get(lower.size() - 3), lower.get(lower.size() - 2), lower.get(lower.size() - 1))) {
                lower.remove(lower.size() - 2);
            }
        }

        lower.remove(0);
        lower.remove(lower.size() - 1);

        upper.addAll(lower);

        return new Polygon(upper);
    }

    private RenderedStation makeStation(Vector v, MetroStation m, int numberLines) {
        return makeStation(v, m, numberLines, Color.black);
    }

    private RenderedStation makeStation(MetroStation m, boolean useColor) {
        return makeStation(m, m, useColor);
    }

    private RenderedStation makeStation(Vector v, MetroStation m, boolean useColor) {
        if (!useColor) {
            return makeStation(v, m, m.getMaxIncomingLines(), Color.BLACK);
        }
        if (m.getMaxIncomingLines() != 1) {
            return makeStation(v, m, m.getMaxIncomingLines(), Color.BLACK);
        }

        MetroLine ml = m.getConnections().get(0).getLines().get(0);
        for (MetroConnection mc : m.getConnections()) {
            if (mc.getLines().get(0) != ml) {
                return makeStation(v, m, m.getMaxIncomingLines(), Color.BLACK);
            }
        }
        return makeStation(v, m, m.getMaxIncomingLines(), ml.getColor());
    }

    private RenderedStation makeStation(Vector v, MetroStation m, int numberLines, Color color) {
        double lineWidth = linethick;
        if (lineWidth * numberLines > maxEdgeWidth) {
            lineWidth = maxEdgeWidth / numberLines;
        }

        double radius = uniformRadius;
        if (!uniformStations) {
            radius = lineWidth * numberLines / 2.0;
        }

        if (radius < minStationSize) {
            radius = minStationSize;
        }

        return new RenderedStation(color, rimwidth * radius / 50.0, Color.white, m, new Circle(v, radius));
    }

    private boolean isRightTurn(Vector a, Vector b, Vector c) {
        if (new Line(a, b).isRightOf(c)) {
            return true;
        } else {
            return false;
        }
    }
}
