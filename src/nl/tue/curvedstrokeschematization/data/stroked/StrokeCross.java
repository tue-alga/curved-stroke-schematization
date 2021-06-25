/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization.data.stroked;

import nl.tue.curvedstrokeschematization.algo.SECcomputation;
import nl.tue.curvedstrokeschematization.algo.schematization.FullCircleArc;
import nl.tue.curvedstrokeschematization.algo.schematization.IterativeSchematization;
import nl.tue.curvedstrokeschematization.data.Triple;
import nl.tue.curvedstrokeschematization.data.metro.MetroStation;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import nl.tue.geometrycore.geometry.BaseGeometry;
import nl.tue.geometrycore.geometry.GeometryType;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.curved.Circle;
import nl.tue.geometrycore.geometry.curved.CircularArc;
import nl.tue.geometrycore.util.DoubleUtil;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class StrokeCross {

    private MetroStation original;
    private Map<Stroke, StrokeVertex> concrete;
    private Map<Stroke, StrokeArc> virtual;
    private Map<Stroke, Vector> virtualPos;
    private ArrayList<Vector> intersections;
    private Circle centerDisc;

    public StrokeCross(StrokeCross sc) {
        this.original = sc.original;
        this.concrete = new HashMap<Stroke, StrokeVertex>();
        for (Entry<Stroke, StrokeVertex> entry : sc.concrete.entrySet()) {
            this.concrete.put(entry.getKey(), entry.getValue());
        }

        this.virtual = new HashMap<Stroke, StrokeArc>();
        for (Entry<Stroke, StrokeArc> entry : sc.virtual.entrySet()) {
            this.virtual.put(entry.getKey(), entry.getValue());
        }

        this.virtualPos = new HashMap<Stroke, Vector>();
        for (Entry<Stroke, Vector> entry : sc.virtualPos.entrySet()) {
            this.virtualPos.put(entry.getKey(), entry.getValue());
        }

        intersections = new ArrayList<Vector>();
        intersections.addAll(sc.intersections);

        centerDisc = sc.centerDisc.clone();
    }

    public StrokeCross(MetroStation original) {
        this.original = original;
        concrete = new HashMap();
        virtual = new HashMap();
        virtualPos = new HashMap();
        assert original != null;
        centerDisc = new Circle(original, 0.0);
        intersections = new ArrayList<Vector>();
    }

    public boolean isExtensible() {
        // it is extensible if and only if it is a degree-3 where one of the vertices is in fact
        // the endpoint of a noncircular stroke

        return getExtendingArc() != null;
    }

    public StrokeArc getExtendingArc() {
        if (concrete.size() + virtual.size() == 2) {

            for (StrokeVertex end : concrete.values()) {
                if (end.getIncoming() == null) {
                    return end.getOutgoing();
                } else if (end.getOutgoing() == null) {
                    return end.getIncoming();
                }
            }

            return null;
        } else {
            return null;
        }
    }

    public boolean isMovable() {
        // it is movable if and only if it is a degree-4 with 2 strokes that are both simplified

        if (concrete.isEmpty() && virtual.size() == 2) {
            return true;
        }
        return false;
    }

    public StrokeVertex getExtensible() {
        for (StrokeVertex end : concrete.values()) {
            if (end.isStrokeEndpoint()) {
                return end;
            }
        }
        return null;
    }

    public Map<Stroke, StrokeVertex> getConcrete() {
        return concrete;
    }

    public Map<Stroke, StrokeArc> getVirtual() {
        return virtual;
    }

    public void addStroke(Stroke stroke, StrokeVertex vertex) {
        concrete.put(stroke, vertex);
    }

    public void removeStroke(Stroke stroke) {
        concrete.remove(stroke);
    }

    public void changeStroke(Stroke stroke, StrokeArc arc) {
        if (!concrete.containsKey(stroke) && !virtual.containsKey(stroke)) {
            boolean halt = true;
        }
        concrete.remove(stroke);
        virtual.put(stroke, arc);
        computeSmallestDisc();
        Vector closestPoint = arc.toGeometry().closestPoint(centerDisc.getCenter());
        virtualPos.put(stroke, closestPoint);
    }

    public void computeSmallestDisc() {
        //compute intersections
        //remember concrete pos
        //for all arcs not represented by either, find closest point to any
        intersections.clear();
        ArrayList<StrokeArc> allArcs = new ArrayList<StrokeArc>();
        for (StrokeVertex v : getConcreteVertices()) {
            if (v.getIncoming() != null) {
                allArcs.add(v.getIncoming());
            }
            if (v.getOutgoing() != null) {
                allArcs.add(v.getOutgoing());
            }
        }
        for (StrokeArc a : getVirtual().values()) {
            allArcs.add(a);
        }

        HashSet<Stroke> set = new HashSet<Stroke>();
        for (StrokeVertex sv : concrete.values()) {
            set.add(sv.getStroke());
        }
        for (StrokeArc sa : virtual.values()) {
            set.add(sa.getStroke());
        }

        if (this.original.getLabel().equals("virtual non-planar2")) {
            boolean halt = true;
            System.out.println("arcsSize" + allArcs.size());
        }
        Vector guess = centerDisc.getCenter();

        for (int i = 0; i < allArcs.size(); i++) {
            StrokeArc s = allArcs.get(i);
            for (int j = i + 1; j < allArcs.size(); j++) {
                StrokeArc t = allArcs.get(j);
                List<BaseGeometry> intersectionArray = FullCircleArc.intersect(s.toGeometry(), t.toGeometry(), true);
                for (BaseGeometry igeom : intersectionArray) {
                    // check that intersections do not correspond to some other intersection
                    if (igeom.getGeometryType() != GeometryType.VECTOR) {
                        continue;
                    }

                    Vector intersection = (Vector) igeom;
                    double guessdistance = guess.distanceTo(intersection);
                    boolean forthiscross = s.getStart().distanceTo(intersection) > DoubleUtil.EPS && s.getEnd().distanceTo(intersection) > DoubleUtil.EPS
                            && t.getStart().distanceTo(intersection) > DoubleUtil.EPS && t.getEnd().distanceTo(intersection) > DoubleUtil.EPS;
                    if (forthiscross) {
                        for (StrokeCross sc : s.getVirtuals()) {
                            if (sc != this && t.getVirtuals().contains(sc)) {
                                if (sc.getCenterDisc().distanceTo(intersection) < guessdistance) {
                                    forthiscross = false;
                                    break;
                                }
                            }
                        }
                    }

                    if (forthiscross) {
                        intersections.add(intersection);
                        virtualPos.put(s.getStroke(), intersection);
                        virtualPos.put(t.getStroke(), intersection);
                        set.remove(s.getStroke());
                        set.remove(t.getStroke());
                    }
                }

            }
        }
        ArrayList<Vector> positions = new ArrayList<Vector>();
        positions.addAll(intersections);
//        System.out.println("intersections");
//        for (Vector v : intersections)
//            System.out.println(v);
        positions.addAll(concrete.values());
//        System.out.println("concrete");
//        for (Vector v : concrete.values())
//            System.out.println(v);       
//        System.out.println("nearby");
        set.removeAll(concrete.keySet());

        if (positions.isEmpty()) {
            positions.add(centerDisc.getCenter());
        }
        for (Stroke s : set) {
            StrokeArc sa = virtual.get(s);
            double bestDist = Double.POSITIVE_INFINITY;
            Vector bestPoint = null;
            for (Vector v : positions) {
                Vector closestPoint = sa.toGeometry().closestPoint(v);
                double curDist = closestPoint.distanceTo(v);
                if (curDist < bestDist) {
                    bestDist = curDist;
                    bestPoint = closestPoint;
                }
            }
            virtualPos.put(s, bestPoint);
//            System.out.println(bestPoint);
            positions.add(bestPoint);
        }

        centerDisc = SECcomputation.getSmallestEnclosingDisc(positions);
        assert centerDisc.getCenter() != null;
    }

    public Circle computeSmallestDiscAfterReplacement(StrokeArc hypArc1, StrokeArc hypArc2, CircularArc hypReplacement) {

        //compute intersections
        //remember concrete pos
        //for all arcs not represented by either, find closest point to any
        List<Vector> positions = new ArrayList();

        // pair of arc/virtuals
        List<Triple<CircularArc, Stroke, List<StrokeCross>>> allArcs = new ArrayList();

        for (StrokeVertex v : getConcreteVertices()) {
            if (v.getIncoming() != null && v.getIncoming() != hypArc1 && v.getIncoming() != hypArc2) {
                allArcs.add(new Triple(v.getIncoming().toGeometry(), v.getStroke(), v.getIncoming().getVirtuals()));
            }
            if (v.getOutgoing() != null && v.getOutgoing() != hypArc1 && v.getOutgoing() != hypArc2) {
                allArcs.add(new Triple(v.getOutgoing().toGeometry(), v.getStroke(), v.getOutgoing().getVirtuals()));
            }
        }
        for (StrokeArc a : getVirtual().values()) {
            if (a != hypArc1 && a != hypArc2) {
                allArcs.add(new Triple(a.toGeometry(), a.getStroke(), a.getVirtuals()));
            }
        }
        List<StrokeCross> hypVirtlist = new ArrayList();
        Stroke S = null;
        if (hypArc1 != null) {
            hypVirtlist.addAll(hypArc1.getVirtuals());
            S = hypArc1.getStroke();
        }
        if (hypArc2 != null) {
            hypVirtlist.addAll(hypArc2.getVirtuals());
            S = hypArc2.getStroke();
        }

        assert S != null;

        allArcs.add(new Triple(hypReplacement, S, hypVirtlist));

        HashSet<Stroke> set = new HashSet<Stroke>();
        for (StrokeVertex sv : concrete.values()) {
            set.add(sv.getStroke());
        }
        for (StrokeArc sa : virtual.values()) {
            set.add(sa.getStroke());
        }

        assert hypArc1.getStroke() == hypArc2.getStroke();

        Vector guess = centerDisc.getCenter();

        for (int i = 0; i < allArcs.size(); i++) {
            CircularArc sGeom = allArcs.get(i).getFirst();
            Stroke sStroke = allArcs.get(i).getSecond();
            List<StrokeCross> sVirtual = allArcs.get(i).getThird();

            for (int j = i + 1; j < allArcs.size(); j++) {
                CircularArc tGeom = allArcs.get(j).getFirst();
                Stroke tStroke = allArcs.get(j).getSecond();
                List<StrokeCross> tVirtual = allArcs.get(j).getThird();

                List<BaseGeometry> intersectionArray = FullCircleArc.intersect(sGeom, tGeom, true);
                for (BaseGeometry igeom : intersectionArray) {
                    // check that intersections do not correspond to some other intersection
                    if (igeom.getGeometryType() != GeometryType.VECTOR) {
                        continue;
                    }

                    Vector intersection = (Vector) igeom;

                    double guessdistance = guess.distanceTo(intersection);

                    boolean forthiscross = sGeom.getStart().distanceTo(intersection) > DoubleUtil.EPS && sGeom.getEnd().distanceTo(intersection) > DoubleUtil.EPS
                            && tGeom.getStart().distanceTo(intersection) > DoubleUtil.EPS && tGeom.getEnd().distanceTo(intersection) > DoubleUtil.EPS;

                    if (forthiscross) {
                        for (StrokeCross sc : sVirtual) {
                            if (sc != this && tVirtual.contains(sc)) {
                                if (sc.getCenterDisc().distanceTo(intersection) < guessdistance) {
                                    forthiscross = false;
                                    break;
                                }
                            }
                        }
                    }

                    if (forthiscross) {
                        positions.add(intersection);
                        set.remove(sStroke);
                        set.remove(tStroke);
                    }
                }
            }
        }

        IterativeSchematization.debug("intersections");
        for (Vector v : positions) {
            IterativeSchematization.debug("" + v);
            IterativeSchematization.debugGeometry(Color.orange, v);
        }

        for (Entry<Stroke, StrokeVertex> e : concrete.entrySet()) {
            set.remove(e.getKey());
            positions.add(e.getValue());
        }
        //positions.addAll(concrete.values());
        IterativeSchematization.debug("concrete");
        for (Vector v : concrete.values()) {
            IterativeSchematization.debug("" + v);
            IterativeSchematization.debugGeometry(Color.orange, v);
        }
        IterativeSchematization.debug("nearby");
        //set.removeAll(concrete.keySet());

        if (positions.isEmpty()) {
            positions.add(centerDisc.getCenter());
        }
        for (Stroke s : set) {
            StrokeArc sa = virtual.get(s);
            CircularArc saGeom;
            if (sa == hypArc1 || sa == hypArc2) {
                saGeom = hypReplacement;
            } else {
                if (sa == null) {
                    boolean halt = true;
                }
                saGeom = sa.toGeometry();
            }

            double bestDist = Double.POSITIVE_INFINITY;
            Vector bestPoint = null;
            for (Vector v : positions) {
                Vector closestPoint = saGeom.closestPoint(v);
                double curDist = closestPoint.distanceTo(v);
                if (curDist < bestDist) {
                    bestDist = curDist;
                    bestPoint = closestPoint;
                }
            }
            IterativeSchematization.debug("" + bestPoint);
            IterativeSchematization.debugGeometry(Color.orange, bestPoint);
            positions.add(bestPoint);
        }

        Circle sec = SECcomputation.getSmallestEnclosingDisc(positions);

        IterativeSchematization.debugGeometry(Color.orange, sec);

        return sec;
    }

    private boolean isForThisCross(StrokeVertex sv, Vector intersection, double guessdistance) {
        return sv.getCross() == this || sv.distanceTo(intersection) > guessdistance;
    }

    public MetroStation getOriginal() {
        return original;
    }

    public Collection<StrokeVertex> getConcreteVertices() {
        return concrete.values();
    }

    public Collection<Vector> getVirtualPos() {
        return virtualPos.values();
    }

    public ArrayList<Vector> getIntersections() {
        return intersections;
    }

    public Vector getVirtualPos(Stroke s) {
        return virtualPos.get(s);
    }

    public Vector getCenterDisc() {
        return centerDisc.getCenter();
    }

    public Circle getSmallestDisc() {
        return centerDisc;
    }

    public ArrayList<Stroke> getStrokes() {
        HashSet<Stroke> setStrokes = new HashSet<Stroke>();
        setStrokes.addAll(concrete.keySet());
        setStrokes.addAll(virtual.keySet());
        ArrayList<Stroke> returnStrokes = new ArrayList<Stroke>();
        returnStrokes.addAll(setStrokes);
        return returnStrokes;
    }

    public ArrayList<StrokeArc> getIncomingArcs() {
        ArrayList<StrokeArc> strokeArcs = new ArrayList<StrokeArc>();
        for (StrokeVertex sv : concrete.values()) {
            if (sv.getIncoming() != null) {
                strokeArcs.add(sv.getIncoming());
            }
            if (sv.getOutgoing() != null) {
                strokeArcs.add(sv.getOutgoing());
            }
        }

        strokeArcs.addAll(virtual.values());

        return strokeArcs;
    }

    public void replaceStroke(Stroke a, Stroke b) {
        if (!(concrete.containsKey(a) || virtual.containsKey(a))) {
            boolean halt = true;
        }
        if (concrete.get(a) != null) {
            StrokeVertex sv = concrete.remove(a);
            concrete.put(b, sv);
        }

        if (virtual.get(a) != null) {
            StrokeArc arc = virtual.remove(a);
            virtual.put(b, arc);
        }

        if (virtualPos.get(a) != null) {
            Vector v = virtualPos.remove(a);
            virtualPos.put(b, v);
        }
    }

    public void addVirtualPos(Stroke s, Vector v) {
        virtualPos.put(s, v);
    }
}
