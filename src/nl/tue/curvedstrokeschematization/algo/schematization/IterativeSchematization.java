/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization.algo.schematization;

import nl.tue.curvedstrokeschematization.algo.frechetdistance.FrechetDistance;
import nl.tue.curvedstrokeschematization.algo.store.SchematizationStore;
import nl.tue.curvedstrokeschematization.data.Triple;
import nl.tue.curvedstrokeschematization.data.metro.MetroConnection;
import nl.tue.curvedstrokeschematization.data.metro.MetroStation;
import nl.tue.curvedstrokeschematization.data.stroked.Stroke;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeArc;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeCross;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeNetwork;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeVertex;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import nl.tue.geometrycore.geometry.BaseGeometry;
import nl.tue.geometrycore.geometry.GeometryType;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.curved.Circle;
import nl.tue.geometrycore.geometry.curved.CircularArc;
import nl.tue.geometrycore.geometry.linear.Line;
import nl.tue.geometrycore.geometry.linear.Rectangle;
import nl.tue.geometrycore.geometryrendering.glyphs.PointStyle;
import nl.tue.geometrycore.geometryrendering.styling.Dashing;
import nl.tue.geometrycore.geometryrendering.styling.SizeMode;
import nl.tue.geometrycore.io.ipe.IPEWriter;
import nl.tue.geometrycore.util.DoubleUtil;
import nl.tue.geometrycore.util.Pair;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class IterativeSchematization {

    private final FrechetDistance FD;
    private StrokeNetwork network;
    private final List<Double> angles;
    private final double maxcrossdistfrac;
    private final boolean allowhighdegree;
    private double maxcrossdist;
    private double straightreduc;
    private int numCandidates;
    private final Map<StrokeVertex, List<VertexOperation>> vertexoperations;
    private final SchematizationStore store;
    private int complexity;
    private static boolean debug = false;
    private static IPEWriter debuggeom = null;
    public static Circle debugregion = null;
    public static boolean abort = false;
    private static String debugindent = "";

    public static void startDebugGeometry() {
        if (debug && debuggeom == null) {
            debuggeom = IPEWriter.stringWriter(false);
            try {
                debuggeom.initialize();
                debuggeom.setSizeMode(SizeMode.VIEW);
                debuggeom.setPointStyle(PointStyle.SQUARE_WHITE, 2);
            } catch (IOException ex) {
                Logger.getLogger(IterativeSchematization.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static void stopDebugGeometry() {
        if (debuggeom != null) {
            try {
                debug(debuggeom.closeWithResult());
            } catch (IOException ex) {
                Logger.getLogger(IterativeSchematization.class.getName()).log(Level.SEVERE, null, ex);
            }
            debuggeom = null;
        }
    }

    public static void setDebug(Vector v) {
        debug = v != null && debugregion != null && debugregion.contains(v);
    }

    public static void debugIndent() {
        debugindent += "  ";
    }

    public static void debugDedent() {
        debugindent = debugindent.substring(2);
    }

    public static void debug(String s) {
        if (debug) {
            System.out.println(debugindent + s.replaceAll("\n", "\n" + debugindent));
        }
    }

    public static void debugGeometry(Color color, BaseGeometry... gs) {
        if (debug && debuggeom != null) {
            debug("geom");
            debuggeom.setStroke(color, 0.4, Dashing.SOLID);
            debuggeom.draw(gs);
        }
    }

    public SchematizationStore getStore() {
        return store;
    }

    public IterativeSchematization(boolean allowhighdegree, boolean useStore, double maxcrossdistfrac, int anglesteps, int numCandidates, double straightreduc, FrechetDistance fd) {
        vertexoperations = new HashMap();
        this.numCandidates = numCandidates;
        this.FD = fd;
        this.maxcrossdistfrac = maxcrossdistfrac;
        if (useStore) {
            store = new SchematizationStore();
        } else {
            store = null;
        }
        this.allowhighdegree = allowhighdegree;
        this.straightreduc = straightreduc;

        angles = new ArrayList();
        double step = 1.8 * Math.PI / (anglesteps - 1);

        angles.add(0.0);
        double angle = step;
        while (angle < Math.PI * 0.95) {
            angles.add(angle);
            angles.add(-angle);
            angle += step;

        }
        //System.out.println(angles.size());
    }

    public boolean init(StrokeNetwork map) {

        // sanity check
        for (StrokeVertex sv : map.getVertices()) {
            if (sv.getOutgoing() != null) {
                StrokeVertex next = sv.getNext();
                assert sv.getStroke() == next.getStroke();
            }
            if (sv.getIncoming() != null) {
                StrokeVertex prev = sv.getPrevious();
                assert sv.getStroke() == prev.getStroke();
            }
        }

        abort = false;

        network = map;

        if (store != null) {
            store.initialize(network);
            complexity = store.getMaximumComplexity();
        } else {
            complexity = 0;
            for (StrokeArc arc : network.getArcs()) {
                complexity++;
            }
        }

        Rectangle bb = new Rectangle();
        for (StrokeVertex sv : network.getVertices()) {
            bb.include(sv);
        }

        maxcrossdist = bb.diagonal() * maxcrossdistfrac;

        vertexoperations.clear();

        boolean hasAdmissible = false;

        // create deg2 operation (also across crosses)
        for (StrokeVertex sv : network.getVertices()) {
            List<VertexOperation> ops = makeOperations(sv);
            vertexoperations.put(sv, ops);

            //check if admissible operation exists
            if (!hasAdmissible) {
                for (VertexOperation op : ops) {
                    if (!op.isBlocked()) {
                        hasAdmissible = true;
                        break;
                    }
                }
            }
        }        

        return hasAdmissible;
    }

    private double computeDistance(CircularArc arc, List<Vector> curve) {

        double signedCentral = arc.centralAngle();
        int extra = arc.getCenter() == null ? 0 : (int) Math.ceil((80.0 / Math.PI) * Math.abs(signedCentral));

        Vector[] sampled = new Vector[2 + extra];
        sampled[0] = arc.getStart();
        for (int e = 1; e <= extra; e++) {
            sampled[e] = Vector.subtract(arc.getStart(), arc.getCenter());
            sampled[e].rotate(signedCentral * e / (double) (extra + 1));
            sampled[e].translate(arc.getCenter());
        }
        sampled[extra + 1] = arc.getEnd();

        double fd = FD.computeDistance(curve.toArray(new Vector[curve.size()]), sampled);
        if (arc.getCenter() == null) {
            fd = fd * straightreduc;
        }
        return fd;
    }

    private List<VertexOperation> makeOperations(StrokeVertex sv) {

        setDebug(sv);
        debug("" + sv);
        debugIndent();
        List<VertexOperation> ops = new ArrayList();

        //if (deg == 2 vert on respective stroke)
        if (sv.getIncoming() != null && sv.getOutgoing() != null && (sv.getCross() == null || allowhighdegree)) {
            // replacement may be possible

            //Make best 3 candidates (no topo check)
            startDebugGeometry();
            Pair<CircularArc, Double>[] candidates = makeCandidates(sv);
            stopDebugGeometry();
            // make operations and check their topology
            if (candidates != null) {
                for (Pair<CircularArc, Double> candidate : candidates) {
                    if (candidate == null) {
                        continue;
                    }
                    VertexOperation op = new VertexOperation();
                    op.vertex = sv;
                    op.replacement = candidate.getFirst();
                    op.cost = candidate.getSecond();
                    recheckTopology(op);
                    ops.add(op);
                }
            }
        }

        debugDedent();
        setDebug(null);

        return ops;
    }

    private void recheckTopology(VertexOperation op) {
        op.clear();

        setDebug(op.vertex);
        startDebugGeometry();
        debug("\nRechecking topology " + op.vertex);
        debugIndent();
        debugGeometry(Color.black, op.replacement, op.vertex, op.vertex.getPrevious(), op.vertex.getNext());

        if (op.replacement instanceof FullCircleArc) {
            // nothing to check
        } else {
            assert op.replacement.getStart().isApproximately(op.vertex.getPrevious());
            assert op.replacement.getEnd().isApproximately(op.vertex.getNext());
            //  assert op.replacement.getCenter() == null || op.replacement.getCenter().intersects(Line.bisector(op.vertex.getPrevious(), op.vertex.getNext()), true, true)
            //          : op.replacement.getCenter() + " != bisec "+op.vertex.getPrevious() + " - "+op.vertex.getNext();
        }

        // NB: this initiliazes all extension-storages 
        for (Stroke stroke : network.getStrokes()) {
            for (int i = 0; i < stroke.getArcCount(); i++) {
                StrokeArc arc = stroke.getArc(i);
                checkArcIntoOperation(arc, op);
            }
        }

        debug("post arc check blocked? " + op.isBlocked());

        // check circular ordering
        StrokeVertex v = op.vertex;
        StrokeArc inc = v.getIncoming();
        StrokeArc out = v.getOutgoing();

        if (v.getCross() != null) {
            if (!v.getCross().isExtensible()) {
                debug("check v");
                debugIndent();
                checkOrder(op, v.getCross(), inc, out, null);
                debug("  " + op.isBlocked());
                debugDedent();
            }
        }
        if (inc.getStart().getCross() != null) {
            debug("check start");
            debugIndent();
            checkOrder(op, inc.getStart().getCross(), null, inc, null);
            debug("  " + op.isBlocked());
            debugDedent();
        }
        if (out.getEnd().getCross() != null) {
            debug("check end");
            debugIndent();
            checkOrder(op, out.getEnd().getCross(), out, null, null);
            debug("  " + op.isBlocked());
            debugDedent();
        }
        for (StrokeCross virtual : inc.getVirtuals()) {
            if (!virtual.isExtensible()) {
                debug("check virtual 1");
                debugIndent();
                checkOrder(op, virtual, null, null, inc);
                debug("  " + op.isBlocked());
                debugDedent();
            }
        }
        for (StrokeCross virtual : out.getVirtuals()) {
            if (!virtual.isExtensible()) {
                debug("check virtual 2");
                debugIndent();
                checkOrder(op, virtual, null, null, out);
                debug("  " + op.isBlocked());
                debugDedent();
            }
        }

        debug("post order check blocked? " + op.isBlocked());

        stopDebugGeometry();
        debugDedent();
        setDebug(null);
    }

    private boolean addOrderPairs(List<Pair<StrokeArc, Vector>> list, Circle SEC, CircularArc arc, StrokeArc associateSA, int expect) {

        assert expect == 1 || expect == 2;

        if (SEC.getRadius() < DoubleUtil.EPS) {
            // compute tangets

            Vector tp = arc.closestPoint(SEC.getCenter());

            if (expect == 1) {
                // tp should be (close to) one of the endpoints
                if (tp.distanceTo(arc.getStart()) < tp.distanceTo(arc.getEnd())) {
                    Vector v = arc.getStartTangent();
                    v.normalize();
                    list.add(new Pair(associateSA, v));
                } else {
                    Vector v = arc.getEndTangent();
                    v.invert();
                    v.normalize();
                    list.add(new Pair(associateSA, v));
                }
            } else {
                Vector tan;
                if (arc.getCenter() == null) {
                    tan = Vector.subtract(arc.getEnd(), arc.getStart());
                } else {
                    Vector v = arc.getCenter().clone();
                    v.rotate90DegreesClockwise();
                    tan = Vector.subtract(tp, v);
                }
                tan.normalize();
                list.add(new Pair(associateSA, tan));
                tan = tan.clone();
                tan.invert();
                list.add(new Pair(associateSA, tan));
            }
            return true;

        } else {
            List<BaseGeometry> is = FullCircleArc.intersect(arc, SEC, true);
            if (is == null) {
                debug("Nullptr in addOrderPairs");
                return false;
            }

            if (is.size() == 0) {
                debug("No intersections in addOrderPairs");
                debugGeometry(Color.red, SEC, arc);
                return false;
            } else if (is.size() > expect) {
                debug("Too many intersections in addOrderPairs?");
                debugGeometry(Color.red, SEC, arc);
                //return false;
            }

            // assert 1 <= is.length && is.length <= expect : "Length: " + is.length + "; If this triggers, arc-cross relation or circle radius is messed up? " + SEC;
            Vector vec0 = (Vector) is.get(0);
            Vector dirVec = Vector.subtract(vec0, SEC.getCenter());
            dirVec.normalize();
            list.add(new Pair(associateSA, dirVec));
            if (expect == 2 && is.size() == 2) {
                Vector vec1 = (Vector) is.get(1);
                Vector v = Vector.subtract(vec1, SEC.getCenter());
                v.normalize();
                list.add(new Pair(associateSA, v));
            } else if (expect == 2) {
                // arc touching circle, duplicate this point
                list.add(new Pair(associateSA, dirVec.clone()));
            }
            return true;
        }
    }

    private Pair<StrokeArc, Vector> makeOrderPair(StrokeArc arc, Vector intersection, Circle C) {
        Vector v = Vector.subtract(intersection, C.getCenter());
        v.normalize();
        return new Pair(arc, v);
    }

    private void checkOrder(VertexOperation op, StrokeCross sc, StrokeArc inReplace, StrokeArc outReplace, StrokeArc replaceThrough) {
        if (!orderCorrect(op, sc, inReplace, outReplace, replaceThrough)) {
            // add arcs

            op.crossBlocked.add(sc);
        }
    }

    private Circle computeSECAfter(VertexOperation op, StrokeCross sc) {

        StrokeArc ext = sc.getExtendingArc();
        if (ext != null) {

            Vector ctr;
            boolean extStart = op.startExtension.contains(ext);
            boolean extEnd = op.endExtension.contains(ext);
            if (extStart && extEnd) {
                Vector[] extPoints = findDoubleExtension(ext, op);
                if (ext.getEnd().getCross() == sc) {
                    ctr = extPoints[1];
                } else {
                    ctr = extPoints[0];
                }
            } else if (extStart) {
                ctr = findStartExtension(ext, op);
            } else if (extEnd) {
                ctr = findEndExtension(ext, op);
            } else {
                // extensible but this operation does not involve the extension
                assert sc == op.vertex.getNext().getCross() || sc == op.vertex.getPrevious().getCross();
                ctr = null;
            }

            if (ctr != null) {
                return new Circle(ctr, 0.0);
            }
        }

        return sc.computeSmallestDiscAfterReplacement(op.vertex.getIncoming(), op.vertex.getOutgoing(), op.replacement);
    }

    private boolean orderCorrect(VertexOperation op, StrokeCross sc, StrokeArc inReplace, StrokeArc outReplace, StrokeArc throughReplace) {

        debug("op: " + op.vertex + " :: " + op.replacement);
        debug("sc: " + sc.getSmallestDisc());
        if (inReplace != null) {
            debug("inReplace: " + inReplace.getStart() + " --> " + inReplace.getEnd());
        }
        if (outReplace != null) {
            debug("outReplace: " + outReplace.getStart() + " --> " + outReplace.getEnd());
        }
        if (throughReplace != null) {
            debug("throughReplace: " + throughReplace.getStart() + " --> " + throughReplace.getEnd());
        }

        CircularArc replacement = op.replacement;
        Circle SEC_before = sc.getSmallestDisc();
        assert SEC_before != null;
        Circle SEC_after = computeSECAfter(op, sc);
        //throughReplace != null
        //        ? sc.computeSmallestDiscAfterReplacement(throughReplace, null, replacement)
        //       : sc.computeSmallestDiscAfterReplacement(inReplace, outReplace, replacement);
        assert SEC_after != null;

        List<Pair<StrokeArc, Vector>> originalOrder = new ArrayList();
        List<Pair<StrokeArc, Vector>> replacedOrder = new ArrayList();

        boolean success;

        if (throughReplace != null) {

            success = addOrderPairs(originalOrder, SEC_before, throughReplace.toGeometry(), throughReplace, 2);
            if (!success) {
                debug("failed add order pairs (1a)");
                return false;
            }

            success = addOrderPairs(replacedOrder, SEC_after, replacement, throughReplace, 2);
            if (!success) {
                debug("failed add order pairs (1b)");
                return false;
            }

        } else if (inReplace != null && outReplace != null) {

            success = addOrderPairs(originalOrder, SEC_before, inReplace.toGeometry(), inReplace, 1);
            if (!success) {
                debug("failed add order pairs (2a)");
                return false;
            }
            // NB: associate BOTH points with inReplace, as this is what the replaced points will also associate with
            // this is INTENDED
            success = addOrderPairs(originalOrder, SEC_before, outReplace.toGeometry(), inReplace, 1);
            if (!success) {
                debug("failed add order pairs (2b)");
                return false;
            }

            success = addOrderPairs(replacedOrder, SEC_after, replacement, inReplace, 2);
            if (!success) {
                debug("failed add order pairs (2c)");
                return false;
            }

        } else if (inReplace != null) {

            success = addOrderPairs(originalOrder, SEC_before, inReplace.toGeometry(), inReplace, 1);
            if (!success) {
                debug("failed add order pairs (3a)");
                return false;
            }

            success = addOrderPairs(replacedOrder, SEC_after, replacement, inReplace, 1);
            if (!success) {
                debug("failed add order pairs (3b)");
                return false;
            }

        } else {
            assert outReplace != null;

            success = addOrderPairs(originalOrder, SEC_before, outReplace.toGeometry(), outReplace, 1);
            if (!success) {
                debug("failed add order pairs (4a)");
                return false;
            }

            success = addOrderPairs(replacedOrder, SEC_after, replacement, outReplace, 1);
            if (!success) {
                debug("failed add order pairs (4b)");
                return false;
            }
        }

        for (StrokeArc sa : sc.getIncomingArcs()) {
            if (sa == inReplace || sa == outReplace || sa == throughReplace) {
                continue;
            }

            List<BaseGeometry> is = FullCircleArc.intersect(sa.toGeometry(), SEC_before, true);

            if (sa.getStart().getCross() == sc) {
                // outgoing arc
//                assert is.length == 1 : "Length: " + is.length + "; If this triggers, arc-cross relation or circle radius is messed up?";

                if (is.size() != 1) {
                    debug("failed add order pairs (5a)");
                    return false;
                }
                Vector vec0 = (Vector) is.get(0);

                originalOrder.add(makeOrderPair(sa, vec0, SEC_before));
                replacedOrder.add(originalOrder.get(originalOrder.size() - 1));

            } else if (sa.getEnd().getCross() == sc) {
                // incoming arc
//                assert is.length == 1 : "Length: " + is.length + "; If this triggers, arc-cross relation or circle radius is messed up?";

                if (is.size() != 1) {
                    debug("failed add order pairs (5b)" + is.size());
                    return false;
                }
                Vector vec0 = (Vector) is.get(0);

                originalOrder.add(makeOrderPair(sa, vec0, SEC_before));
                replacedOrder.add(originalOrder.get(originalOrder.size() - 1));

            } else {
                // arc passing through
//                assert 1 <= is.length && is.length <= 2 : "Length: " + is.length + "; If this triggers, arc-cross relation or circle radius is messed up?";

                if (!(1 <= is.size() && is.size() <= 2)) {
                    debug("failed add order pairs (5c)");
                    return false;
                }
                Vector vec0 = (Vector) is.get(0);

                originalOrder.add(makeOrderPair(sa, vec0, SEC_before));
                replacedOrder.add(originalOrder.get(originalOrder.size() - 1));

                if (is.size() == 2) {
                    Vector vec1 = (Vector) is.get(1);
                    originalOrder.add(makeOrderPair(sa, vec1, SEC_before));
                    replacedOrder.add(originalOrder.get(originalOrder.size() - 1));
                } else {
                    // arc touching circle, duplicate this point
                    originalOrder.add(makeOrderPair(sa, vec0, SEC_before));
                    replacedOrder.add(originalOrder.get(originalOrder.size() - 1));
                }
            }
        }

//        for (Pair<StrokeArc, Vector> p : originalOrder) {
//            System.err.println(" "+p.getSecond());
//        }
        // sort
        final Vector ref = new Vector(0, 1);
        Comparator<Pair<StrokeArc, Vector>> comp = (Pair<StrokeArc, Vector> o1, Pair<StrokeArc, Vector> o2) -> {
            return Double.compare(ref.computeCounterClockwiseAngleTo(o1.getSecond()), ref.computeCounterClockwiseAngleTo(o2.getSecond()));
        };

        Collections.sort(originalOrder, comp);
        Collections.sort(replacedOrder, comp);

        // try all shifts
        boolean hasSame = false;
        assert originalOrder.size() == replacedOrder.size();
        int n = originalOrder.size();
        for (int shift = 0; shift < n; shift++) {
            boolean same = true;
            for (int i = 0; i < n; i++) {
                if (originalOrder.get(i).getFirst() != replacedOrder.get((i + shift) % n).getFirst()) {
                    same = false;
                    break;
                }
            }
            if (same) {
                hasSame = true;
                break;
            }
        }

        if (!hasSame) {
            debug("Wrong order");
            debugGeometry(Color.blue, SEC_before, replacement);
        }

        return hasSame;
    }

    // return true is operation requires a recheck
    private boolean uncheckArcFromOperation(StrokeArc arc, VertexOperation op) {
        boolean recheck = false;

        if (op.startExtension.remove(arc)) {
            recheck = true;
        }
        if (op.endExtension.remove(arc)) {
            recheck = true;
        }
        if (op.fixedCrosses.remove(arc)) {
            recheck = true;
        }

        if (op.relatedArcBlocked.remove(arc)) {
            recheck = true;
        }

        op.unrelatedArcBlocked.remove(arc);

        return recheck;
    }

    private boolean checkArcExtension(StrokeArc arc, VertexOperation op, Vector ext, boolean startExt) {
        return checkArcExtension(arc, new ArrayList<StrokeArc>(), op, ext, startExt);
    }

    private boolean checkArcExtension(StrokeArc arc, List<StrokeArc> exceptions, VertexOperation op, Vector ext, boolean startExt) {
        // check if the extension doesnt cause new intersections
        // NB: shrinking cannot cause problems

        CircularArc arcgeom = arc.toGeometry();

        if (arcgeom.perimeter() < FullCircleArc.distanceAlongArc(arcgeom, ext, !startExt)) {
            CircularArc extarc = arcgeom.clone();
            extarc.setStart(startExt ? ext.clone() : arc.getEnd().clone());
            extarc.setEnd(startExt ? arc.getStart().clone() : ext.clone());
            // extarc is the extended part, should not intersect with anything!

            op.extensions.add(extarc);

            for (Stroke stroke : network.getStrokes()) {
                for (int i = 0; i < stroke.getArcCount(); i++) {
                    StrokeArc arcOther = stroke.getArc(i);
                    if (arcOther != arc && !exceptions.contains(arcOther) && arcOther != op.vertex.getIncoming() && arcOther != op.vertex.getOutgoing()) {
                        if (!FullCircleArc.intersect(extarc, arcOther.toGeometry(), false).isEmpty()) {
                            debug("Intersection something:");
                            debugGeometry(Color.magenta, extarc, arcOther.toGeometry());
                            op.relatedArcBlocked.add(arcOther);
                            return true;
                        }
                    }
                }
            }

            for (CircularArc oa : op.extensions) {
                if (oa != extarc && !FullCircleArc.intersect(extarc, oa, false).isEmpty()) {
                    debug("Intersect other extension:");
                    debugGeometry(Color.magenta, extarc, oa);
                    op.relatedArcBlocked.add(arc);
                    return true;
                }
            }
        }

        return false;
    }

    //how does this arc interact with this operation
    private void checkArcIntoOperation(final StrokeArc arc, VertexOperation op) {

        if (arc == op.vertex.getIncoming() || arc == op.vertex.getOutgoing()) {
            return;
        }

        List<StrokeCross> arcCrossOp = new ArrayList();
        for (StrokeCross sc : arc.getVirtuals()) {
            if (op.vertex.getCross() == sc) {
                arcCrossOp.add(sc);
            } else if (op.vertex.getIncoming().getVirtuals().contains(sc)) {
                arcCrossOp.add(sc);
            } else if (op.vertex.getOutgoing().getVirtuals().contains(sc)) {
                arcCrossOp.add(sc);
            } // else: unrelated cross
        }

        debug("crossOps: " + arcCrossOp.size());

        VirtualType vtStart = getVirtualType(arc.getStart(), op);
        VirtualType vtEnd = getVirtualType(arc.getEnd(), op);

        debug("vts: " + vtStart + " - " + vtEnd);

        int virtuals = 0;
        if (vtStart != VirtualType.NONE) {
            if (vtStart == VirtualType.EXTENSIBLE) {
                op.startExtension.add(arc);
            }
            virtuals++;
        }
        if (vtEnd != VirtualType.NONE) {
            if (vtEnd == VirtualType.EXTENSIBLE) {
                op.endExtension.add(arc);
            }
            virtuals++;
        }

        if (arcCrossOp.size() + virtuals > 2) {
            // cannot have more than 2 intersections between circles
            debug("block on virtuals");
            debugGeometry(Color.cyan, arc.toGeometry());
            op.relatedArcBlocked.add(arc);
        } else if (vtStart == VirtualType.EXTENSIBLE && vtEnd == VirtualType.EXTENSIBLE) {

            Vector[] ext = findDoubleExtension(arc, op);
            if (ext == null) {
                debug("block on double extension");
                debugGeometry(Color.cyan, arc.toGeometry());
                op.relatedArcBlocked.add(arc);
            } else {
                //op.extensions.add(CircularArc.fromStartToEnd(arc.getStart(), ext[0], arc.getCenter(), !arc.isClockwise()));
                //op.extensions.add(CircularArc.fromStartToEnd(arc.getEnd(), ext[1], arc.getCenter(), arc.isClockwise()));
                if (checkArcExtension(arc, op, ext[0], true)) {
                    debug("block on double start topology");
                    debugGeometry(Color.cyan, arc.toGeometry());
                }
                if (checkArcExtension(arc, op, ext[1], false)) {
                    debug("block on double end topology");
                    debugGeometry(Color.cyan, arc.toGeometry());
                }

            }
        } else if (vtStart == VirtualType.EXTENSIBLE) {

            // NB: other end does not move during a single-sided extension.
            // So, checking closeness is not required.
            Vector ext = findStartExtension(arc, op);
            if (ext == null) {
                debug("block on start extension");
                debugGeometry(Color.cyan, arc.toGeometry());
                op.relatedArcBlocked.add(arc);
            } else {
                //op.extensions.add(CircularArc.fromStartToEnd(arc.getStart(), ext, arc.getCenter(), !arc.isClockwise()));
                if (checkArcExtension(arc, op, ext, true)) {
                    debug("block on start topology");
                    debugGeometry(Color.cyan, arc.toGeometry());
                }
            }

        } else if (vtEnd == VirtualType.EXTENSIBLE) {

            // NB: other end does not move during a single-sided extension.
            // So, checking closeness is not required.
            Vector ext = findEndExtension(arc, op);
            if (ext == null) {
                debug("block on end extension");
                debugGeometry(Color.cyan, arc.toGeometry());
                op.relatedArcBlocked.add(arc);
            } else {
                //op.extensions.add(CircularArc.fromStartToEnd(arc.getEnd(), ext, arc.getCenter(), arc.isClockwise()));
                if (checkArcExtension(arc, op, ext, false)) {
                    debug("block on end topology");
                    debugGeometry(Color.cyan, arc.toGeometry());
                }
            }

        } else if (arcCrossOp.size() + virtuals > 0) {
            // arc is expecting some intersections

            List<BaseGeometry> intsGeneric = FullCircleArc.intersect(arc.toGeometry(), op.replacement, false);
            List<Vector> ints = new ArrayList();
            for (BaseGeometry bg : intsGeneric) {
                if (bg.getGeometryType() == GeometryType.VECTOR) {
                    ints.add((Vector) bg);
                }
            }
            if (ints.size() != arcCrossOp.size()) {
                debug("block on virtual vs intersections: " + ints.size() + " vs " + arcCrossOp.size());
                debugGeometry(Color.cyan, arc.toGeometry());
                op.relatedArcBlocked.add(arc);
            } else {

                // sort intersections along arc
                // (arcCrossOp is already sorted as such by construction)
                Collections.sort(ints, (Vector o1, Vector o2) -> {
                    double d1 = FullCircleArc.distanceAlongArc(arc.toGeometry(), o1, true);
                    double d2 = FullCircleArc.distanceAlongArc(arc.toGeometry(), o2, true);
                    return Double.compare(d1, d2);
                });

                for (int i = 0; i < ints.size(); i++) {
                    Vector intersection = ints.get(i);
                    StrokeCross cross = arcCrossOp.get(i);

                    if (!cross.isMovable() && !closeEnough(cross, intersection)) {
                        debug("block on not close enough");
                        debugGeometry(Color.cyan, arc.toGeometry());
                        op.relatedArcBlocked.add(arc);
                        break;
                    }
                }
            }

        } else {
            // no relation between arc and operation
            // do simple intersection check
            // NB: open to avoid problems with arcs neighboring the operation
            if (!FullCircleArc.intersect(arc.toGeometry(), op.replacement, false).isEmpty()) {
                debug("block on unrelated intersection");
                debugGeometry(Color.cyan, arc.toGeometry());
                op.unrelatedArcBlocked.add(arc);
            }
        }

    }

    public int getComplexity() {
        return complexity;
    }

    private enum VirtualType {

        NONE, FIXED, EXTENSIBLE;
    }

    private VirtualType getVirtualType(StrokeVertex sv, VertexOperation op) {
        StrokeCross cross = sv.getCross();
        if (cross == null) {
            return VirtualType.NONE;
        } else if (cross.getConcrete().get(op.vertex.getStroke()) == op.vertex) {

            if (cross.isExtensible()) {
                return VirtualType.EXTENSIBLE;
            } else {
                return VirtualType.FIXED;
            }
        } else {
            StrokeArc virtualOn = cross.getVirtual().get(op.vertex.getStroke());
            if (virtualOn == op.vertex.getIncoming() || virtualOn == op.vertex.getOutgoing()) {
                if (cross.isExtensible()) {
                    return VirtualType.EXTENSIBLE;
                } else {
                    return VirtualType.FIXED;
                }
            } else {
                return VirtualType.NONE;
            }
        }
    }

    private boolean closeEnough(StrokeCross sc, BaseGeometry g) {
        return g.distanceTo(sc.getCenterDisc()) < maxcrossdist;
    }

    private Vector findStartExtension(StrokeArc arc, VertexOperation op) {
        return findStartExtension(arc, op.replacement, op.vertex);
    }

    private Vector findStartExtension(StrokeArc arc, CircularArc replacement, StrokeVertex vertex) {

        BaseGeometry C;
        if (arc.getCenter() == null) {
            C = Line.byThroughpoints(arc.getStart(), arc.getEnd());
        } else {
            CircularArc a = arc.toGeometry();
            C = new Circle(a.getCenter(), a.radius());
        }

        List<BaseGeometry> ints = FullCircleArc.intersect(replacement, C, false); // NB: open,closed originally instead of open,open

        if (ints.isEmpty()) {
            // overlap / no intersection: cannot extend
            return null;
        } else if (!(ints.get(0) instanceof Vector)) {
            return null;
        } else if (ints.size() > 1 && !(ints.get(1) instanceof Vector)) {
            return null;
        } else {
            // find closest extension point

            Vector vec0 = (Vector) ints.get(0);
            Vector vec1 = ints.size() > 1 ? (Vector) ints.get(1) : null;
            Vector[] vecs = {vec0, vec1};

            Vector closest = null;
            if (arc.getCenter() == null) {
                Vector dir = Vector.subtract(arc.getStart(), arc.getEnd()); // normalization is not really necessary

                double lowerbound = DoubleUtil.EPS;
                for (StrokeCross sc : arc.getVirtuals()) {
                    Vector firstIntersect = sc.getVirtualPos(arc.getStroke());
                    if (sc.getVirtual().values().contains(vertex.getIncoming()) || sc.getVirtual().values().contains(vertex.getOutgoing())) {
                        if (vec1 != null && FullCircleArc.distanceAlongArc(replacement, vec1, false) < FullCircleArc.distanceAlongArc(replacement, vec0, false)) {
                            firstIntersect = vec1;
                        } else {
                            firstIntersect = vec0;
                        }
                    }
                    assert sc.getVirtualPos(arc.getStroke()) != null;
                    double dot = Vector.dotProduct(Vector.subtract(firstIntersect, arc.getEnd()), dir);
                    lowerbound = Math.max(lowerbound, dot + DoubleUtil.EPS);
                }

                double mindist = Double.POSITIVE_INFINITY;
                for (Vector i : vecs) {
                    if (i == null) {
                        continue;
                    }
                    double dot = Vector.dotProduct(Vector.subtract(i, arc.getEnd()), dir);
                    if (dot > lowerbound && dot < mindist) {
                        mindist = dot;
                        closest = i;
                    }
                }

            } else {
                Vector ref = Vector.subtract(arc.getEnd(), arc.getCenter());

                double lowerbound = DoubleUtil.EPS;
                for (StrokeCross sc : arc.getVirtuals()) {
                    Vector firstIntersect = sc.getVirtualPos(arc.getStroke());
                    if (sc.getVirtual().values().contains(vertex.getIncoming()) || sc.getVirtual().values().contains(vertex.getOutgoing())) {
                        if (vec1 != null && FullCircleArc.distanceAlongArc(replacement, vec1, false) < FullCircleArc.distanceAlongArc(replacement, vec0, false)) {
                            firstIntersect = vec1;
                        } else {
                            firstIntersect = vec0;
                        }
                    }
                    Vector vv = Vector.subtract(firstIntersect, arc.getCenter());
                    double angle = arc.isClockwise()
                            ? ref.computeCounterClockwiseAngleTo(vv)
                            : ref.computeClockwiseAngleTo(vv);
                    lowerbound = Math.max(lowerbound, angle + DoubleUtil.EPS);
                }

                double minangle = Double.POSITIVE_INFINITY;
                for (Vector i : vecs) {
                    if (i == null) {
                        continue;
                    }
                    Vector vv = Vector.subtract(i, arc.getCenter());
                    double angle = arc.isClockwise()
                            ? ref.computeCounterClockwiseAngleTo(vv)
                            : ref.computeClockwiseAngleTo(vv);
                    if (angle > lowerbound && angle < minangle) {
                        minangle = angle;
                        closest = i;
                    }
                }
            }

            return closest;
        }
    }

    private Vector findEndExtension(StrokeArc arc, VertexOperation op) {
        return findEndExtension(arc, op.replacement, op.vertex);
    }

    private Vector findEndExtension(StrokeArc arc, CircularArc replacement, StrokeVertex vertex) {
        BaseGeometry C;
        if (arc.getCenter() == null) {
            C = Line.byThroughpoints(arc.getStart(), arc.getEnd());
        } else {
            CircularArc a = arc.toGeometry();
            C = new Circle(a.getCenter(), a.radius());
        }

        List<BaseGeometry> ints = FullCircleArc.intersect(replacement, C, false); // NB: used to be open,closed instead of open,open

        if (ints.isEmpty()) {
            // overlap / no intersection: cannot extend
            return null;
        } else if (!(ints.get(0) instanceof Vector)) {
            return null;
        } else if (ints.size() > 1 && !(ints.get(1) instanceof Vector)) {
            return null;
        } else {
            // find closest extension point <-> what if first intersects and then returns!!!

            Vector vec0 = (Vector) ints.get(0);
            Vector vec1 = ints.size() > 1 ? (Vector) ints.get(1) : null;
            Vector[] vecs = {vec0, vec1};

            Vector closest = null;
            if (arc.getCenter() == null) {
                Vector dir = Vector.subtract(arc.getEnd(), arc.getStart()); // normalization is not really necessary

                double lowerbound = DoubleUtil.EPS;

                //add new position intersection
                for (StrokeCross sc : arc.getVirtuals()) {
                    Vector firstIntersect = sc.getVirtualPos(arc.getStroke());
                    if (sc.getVirtual().values().contains(vertex.getIncoming()) || sc.getVirtual().values().contains(vertex.getOutgoing())) {
                        if (vec1 != null && FullCircleArc.distanceAlongArc(replacement, vec1, true) < FullCircleArc.distanceAlongArc(replacement, vec0, true)) {
                            firstIntersect = vec1;
                        } else {
                            firstIntersect = vec0;
                        }
                    }

                    assert sc.getVirtualPos(arc.getStroke()) != null;
                    double dot = Vector.dotProduct(Vector.subtract(firstIntersect, arc.getStart()), dir);
                    lowerbound = Math.max(lowerbound, dot + DoubleUtil.EPS);
                }

                double mindist = Double.POSITIVE_INFINITY;
                for (Vector i : vecs) {
                    if (i == null) {
                        continue;
                    }
                    double dot = Vector.dotProduct(Vector.subtract(i, arc.getStart()), dir);
                    if (dot > lowerbound && dot < mindist) {
                        mindist = dot;
                        closest = i;
                    }
                }
            } else {
                Vector ref = Vector.subtract(arc.getStart(), arc.getCenter());

                double lowerbound = DoubleUtil.EPS;
                for (StrokeCross sc : arc.getVirtuals()) {
                    Vector firstIntersect = sc.getVirtualPos(arc.getStroke());
                    if (sc.getVirtual().values().contains(vertex.getIncoming()) || sc.getVirtual().values().contains(vertex.getOutgoing())) {
                        if (vec1 != null && FullCircleArc.distanceAlongArc(replacement, vec1, true) < FullCircleArc.distanceAlongArc(replacement, vec0, true)) {
                            firstIntersect = vec1;
                        } else {
                            firstIntersect = vec0;
                        }
                    }
                    Vector vv = Vector.subtract(firstIntersect, arc.getCenter());
                    double angle = arc.isClockwise()
                            ? ref.computeClockwiseAngleTo(vv)
                            : ref.computeCounterClockwiseAngleTo(vv);
                    lowerbound = Math.max(lowerbound, angle + DoubleUtil.EPS);
                }

                double minangle = Double.POSITIVE_INFINITY;
                for (Vector i : vecs) {
                    if (i == null) {
                        continue;
                    }
                    Vector vv = Vector.subtract(i, arc.getCenter());
                    double angle = arc.isClockwise()
                            ? ref.computeClockwiseAngleTo(vv)
                            : ref.computeCounterClockwiseAngleTo(vv);
                    if (angle > lowerbound && angle < minangle) {
                        minangle = angle;
                        closest = i;
                    }
                }
            }

            return closest;
        }
    }

    private Vector[] findDoubleExtension(StrokeArc arc, VertexOperation op) {
        return findDoubleExtension(arc, op.replacement, op.vertex);
    }

    private Vector[] findDoubleExtension(StrokeArc arc, CircularArc replacement, StrokeVertex vertex) {

        BaseGeometry C;
        if (arc.getCenter() == null) {
            C = Line.byThroughpoints(arc.getStart(), arc.getEnd());
        } else {
            CircularArc a = arc.toGeometry();
            C = new Circle(a.getCenter(), a.radius());
        }

        List<BaseGeometry> ints = FullCircleArc.intersect(replacement, C, false); // NB: used to be open,closed instead of open,open

        if (ints.size() <= 1 || !(ints.get(0) instanceof Vector) || !(ints.get(1) instanceof Vector)) {
            // overlap / no two intersections: cannot extend
            return null;
        } else {
            assert ints.size() == 2;

            Vector vec0 = (Vector) ints.get(0);
            Vector vec1 = (Vector) ints.get(1);

            // found out which corresponds to start/end
            Vector int_firstOP;
            Vector int_secondOP;

            if (FullCircleArc.distanceAlongArc(replacement, vec0, true) < FullCircleArc.distanceAlongArc(replacement, vec1, true)) {
                int_firstOP = vec0;
                int_secondOP = vec1;
            } else {
                int_firstOP = vec1;
                int_secondOP = vec0;
            }

            int startloc; // -1: inc; 0: vtx; 1: outgoing
            if (arc.getStart().getCross() == vertex.getCross()) {
                startloc = 0;
            } else if (vertex.getIncoming().getVirtuals().contains(arc.getStart().getCross())) {
                startloc = -1;
            } else {
                assert vertex.getOutgoing().getVirtuals().contains(arc.getStart().getCross());
                startloc = 1;
            }

            int endloc; // -1: inc; 0: vtx; 1: outgoing
            if (arc.getEnd().getCross() == vertex.getCross()) {
                endloc = 0;
            } else if (vertex.getIncoming().getVirtuals().contains(arc.getEnd().getCross())) {
                endloc = -1;
            } else {
                assert vertex.getOutgoing().getVirtuals().contains(arc.getEnd().getCross());
                endloc = 1;
            }

            boolean startBeforeEnd;
            if (startloc < endloc) {
                startBeforeEnd = true;
            } else if (startloc > endloc) {
                startBeforeEnd = false;
            } else if (startloc == -1) { // and thus endloc == -1
                Vector posStart = arc.getStart().getCross().getVirtualPos(vertex.getStroke());
                Vector posEnd = arc.getEnd().getCross().getVirtualPos(vertex.getStroke());
                startBeforeEnd = FullCircleArc.distanceAlongArc(vertex.getIncoming().toGeometry(), posStart, true)
                        < FullCircleArc.distanceAlongArc(vertex.getIncoming().toGeometry(), posEnd, true);
            } else if (startloc == 1) { // and thus endloc == 1
                Vector posStart = arc.getStart().getCross().getVirtualPos(vertex.getStroke());
                Vector posEnd = arc.getEnd().getCross().getVirtualPos(vertex.getStroke());
                startBeforeEnd = FullCircleArc.distanceAlongArc(vertex.getOutgoing().toGeometry(), posStart, true)
                        < FullCircleArc.distanceAlongArc(vertex.getIncoming().toGeometry(), posEnd, true);
            } else {
                assert false; // cant both be 0
                startBeforeEnd = true;
            }

            Vector[] startend;
            if (startBeforeEnd) {
                startend = new Vector[]{int_firstOP, int_secondOP};
            } else {
                startend = new Vector[]{int_secondOP, int_firstOP};
            }

            // construct new arc
            CircularArc newarc = arc.toGeometry().clone();
            newarc.setStart(startend[0]);
            newarc.setEnd(startend[1]);

            // check that all crosses are still on
            for (StrokeCross sc : arc.getVirtuals()) {
                if (newarc.distanceTo(sc.getVirtualPos(arc.getStroke())) > DoubleUtil.EPS) {
                    return null;
                }
            }

            return startend;
        }
    }

    // returns true if original contains a point within distance tobeat of the point along arc
    private boolean precheck(CircularArc arc, double fraction, List<Vector> original, double tobeat) {
        Vector point = arc.getPointAt(fraction);
        for (Vector v : original) {
            if (v.distanceTo(point) < tobeat) {
                return true;
            }
        }

        return false;
    }

    private void makeAngleCandidates(StrokeVertex start, StrokeVertex end, Pair<CircularArc, Double>[] best, List<Vector> original) {

        for (double angle : angles) {

            CircularArc arc = constructCandidate(start, end, angle);

            if (best[best.length - 1] != null) {
                double tobeat = best[best.length - 1].getSecond();
                // do a precheck
                if (!precheck(arc, 0.5, original, tobeat)) {
                    continue;
                }
            }

            double dist = computeDistance(arc, original);

            insertSorted(best, arc, dist);
        }
    }

    private void makeCrossCandidates(StrokeCross cross, StrokeVertex start, StrokeVertex end, Pair<CircularArc, Double>[] best, List<Vector> original) {
        Vector center = cross.getCenterDisc();

        // through center
        CircularArc arc_center = CircularArc.byThroughPoint(start.clone(), center, end.clone());
        debugGeometry(Color.blue, arc_center, start, center, end);
        double dist_center = computeDistance(arc_center, original);

        insertSorted(best, arc_center, dist_center);

        // min/max
        if (center.distanceTo(start) > maxcrossdist && center.distanceTo(end) > maxcrossdist) {
            // NB: this is only approximate
            Circle C = new Circle(center, maxcrossdist * 0.975);
            Line L;
            if (arc_center.getCenter() == null) {
                L = Line.perpendicularAt(center, Vector.subtract(end, start));
            } else {
                L = Line.byThroughpoints(center, arc_center.getCenter());
            }
            List<BaseGeometry> is = C.intersect(L);
            assert is.size() == 2;

            CircularArc arc_1 = CircularArc.byThroughPoint(start.clone(), (Vector) is.get(0), end.clone());
            double dist_1 = computeDistance(arc_1, original);
            insertSorted(best, arc_1, dist_1);
            debugGeometry(Color.red, arc_1, start, (Vector) is.get(0), end);

            CircularArc arc_2 = CircularArc.byThroughPoint(start.clone(), (Vector) is.get(1), end.clone());
            double dist_2 = computeDistance(arc_2, original);
            insertSorted(best, arc_2, dist_2);
            debugGeometry(Color.yellow, arc_2, start, (Vector) is.get(1), end);
        }
    }

    //return best 3
    private Pair<CircularArc, Double>[] makeCandidates(StrokeVertex sv) {

        StrokeVertex start = sv.getPrevious();
        StrokeVertex end = sv.getNext();

        //first create best 3 -> later check
        if (sv == end) {
            // nothing to do: stroke is a circle!
            return null;
        }

        Pair<CircularArc, Double>[] best = new Pair[numCandidates];
        List<Vector> original = getOriginalStations(sv);

        if (start == end) {
            // make a circle
            debug("circle candidate");
            makeCircleCandidates(sv, start, best, original);

        } else if (sv.getCross() != null && !sv.getCross().isExtensible()) {
            debug("cross candidates");
            makeCrossCandidates(sv.getCross(), start, end, best, original);
        } else {

            debug("general candidates");
            assert !start.isApproximately(end) : "\nstart: " + start + "\n  " + start.getStroke() + "\nend: " + end + "\n  " + end.getStroke() + "\nsv: " + sv + "\n  " + sv.getStroke() + "\nstart == end: " + (start == end);

            boolean crossed = false;
            for (StrokeCross sc : sv.getIncoming().getVirtuals()) {
                if (!sc.isExtensible() && !sc.isMovable()) {
                    crossed = true;
                    debug("crossed in");
                    makeCrossCandidates(sc, start, end, best, original);
                }
            }
            if (!crossed) {
                for (StrokeCross sc : sv.getOutgoing().getVirtuals()) {
                    if (!sc.isExtensible() && !sc.isMovable()) {
                        crossed = true;
                        debug("crossed out");
                        makeCrossCandidates(sc, start, end, best, original);
                    }
                }
            }
            if (!crossed) {
                debug("angles");
                makeAngleCandidates(start, end, best, original);
            }
        }

        return best;
    }

    private void makeCircleCandidates(StrokeVertex operand, StrokeVertex other, Pair<CircularArc, Double>[] best, List<Vector> original) {
        // constraints: center points of any crosses along either arcs
        List<Vector> constraints = new ArrayList();
        if (other.isCross()) {
            constraints.add(other.getCross().getCenterDisc());
        }

        for (StrokeCross sc : operand.getIncoming().getVirtuals()) {
            if (!sc.isExtensible()) {
                constraints.add(sc.getCenterDisc());
            }
        }

        if (operand.isCross()) {
            constraints.add(operand.getCross().getCenterDisc());
        }

        for (StrokeCross sc : operand.getOutgoing().getVirtuals()) {
            if (!sc.isExtensible()) {
                constraints.add(sc.getCenterDisc());
            }
        }

        // NB: constraints should be circularly sorted
        if (constraints.isEmpty()) {
            constraints.add(operand);
            constraints.add(other);
        } else if (constraints.size() == 1) {
            if (other.isCross()) {
                constraints.add(operand);
            } else {
                constraints.add(other);
            }
        }
        assert constraints.size() >= 2;

        Vector C1 = constraints.remove(0);
        Vector C2 = constraints.remove(0);
        if (constraints.isEmpty()) {

            for (double angle : angles) {

                CircularArc oa = constructCandidate(C1, C2, angle);
                if (oa.getCenter() == null) {
                    // cant make infinitely large circle
                    continue;
                }
                Circle circ = new Circle(oa.getCenter(), oa.radius());
                FullCircleArc fca = new FullCircleArc(oa.getCenter(), FullCircleArc.closestPoints(oa, circ)[1], !oa.isCounterclockwise());

                if (best[best.length - 1] != null) {
                    double tobeat = best[best.length - 1].getSecond();
                    // do a precheck
                    if (!precheck(fca, 0.5, original, tobeat)) {
                        continue;
                    }
                }

                double dist = computeDistance(fca, original);

                insertSorted(best, fca, dist);
            }

        } else {
            Vector C3 = constraints.remove(0);
            CircularArc oa = CircularArc.byThroughPoint(C1, C2, C3);
            if (oa == null) {
                // colinear points...
                return;
            }
            Circle circ = new Circle(oa.getCenter(), oa.radius());
            FullCircleArc fca = new FullCircleArc(oa.getCenter(), FullCircleArc.closestPoints(oa, circ)[1], !oa.isCounterclockwise());

            // check if others are close enough
            for (Vector v : constraints) {
                if (fca.distanceTo(v) > maxcrossdist - DoubleUtil.EPS) {
                    // nothing to be done
                    return;
                }
            }

            double dist = computeDistance(fca, original);

            insertSorted(best, fca, dist);
        }

    }

    private void insertSorted(Pair<CircularArc, Double>[] best, CircularArc arc, double dist) {
        if (best[best.length - 1] == null || dist < best[best.length - 1].getSecond()) {
            best[best.length - 1] = new Pair(arc, dist);

            for (int i = best.length - 2; i >= 0; i--) {
                if (best[i] == null) {
                    best[i] = best[i + 1];
                    best[i + 1] = null;
                } else if (best[i + 1].getSecond() < best[i].getSecond()) {
                    Pair<CircularArc, Double> tA = best[i + 1];
                    best[i + 1] = best[i];
                    best[i] = tA;
                } else {
                    break;
                }
            }
        }
    }

    private CircularArc constructCandidate(Vector start, Vector end, double angle) {
        Vector tan = Vector.subtract(end, start);
        tan.rotate(angle);
        tan.normalize();
        return CircularArc.fromStartTangent(start.clone(), tan, end.clone());
    }

    public List<Vector> getOriginalStations(StrokeVertex sv) {
        List<Vector> original = new ArrayList();

        MetroStation station = sv.getPrevious().getOriginal();
        original.add(station);
        for (MetroConnection connect : sv.getIncoming().getOriginaledges()) {
            MetroStation next = connect.theOther(station);
            original.add(next);
            station = next;
        }

        assert station == sv.getOriginal();
        for (MetroConnection connect : sv.getOutgoing().getOriginaledges()) {
            MetroStation next = connect.theOther(station);
            original.add(next);
            station = next;
        }

        return original;
    }

    public void printState(String delim) {
        System.out.println("-------------------------------");
        System.out.println("  " + delim);
        System.out.println("");
        System.out.println("VertexOperations");
        for (List<VertexOperation> vos : vertexoperations.values()) {
            System.out.println("  vertex " + vos.size());
            for (VertexOperation vo : vos) {
                System.out.println("  * " + vo.isBlocked());
                System.out.println("    " + vo.cost);
            }
        }
    }

    public boolean performStep() {
        return performStep(0, Double.POSITIVE_INFINITY);
    }

    public boolean performStep(int complexity, double frechetthreshold) {
        // only perform if complexity isnt reached yet or if frechetthreshold isnt exceeded
        if (abort) {
            System.out.println("Algorithm is stuck, reinitialization required");
            return false;
        }

        if (this.complexity <= complexity) {
            System.out.println("Complexity reached");
            return false;
        }

        //printState("PRE OPERATION");
        Operation best = null;

        if (debugregion != null) {
            System.out.println("  ");
            System.out.println("OPERATIONS");
        }
        for (Entry<StrokeVertex, List<VertexOperation>> vos : vertexoperations.entrySet()) {
            setDebug(vos.getKey());
            startDebugGeometry();
            debug("" + vos.getKey());
            //debugGeometry(Color.black, vos.getKey(), vos.getKey().getIncoming().toGeometry(), vos.getKey().getOutgoing().toGeometry());
            for (VertexOperation vo : vos.getValue()) {
                debug("> blocked: " + vo.isBlocked());
                debug("  cost: " + vo.cost);
                debug("  geom: " + vo.replacement);
                debugGeometry(Color.red, vo.replacement);
                if (!vo.isBlocked() && (best == null || vo.cost < best.cost)) {
                    best = vo;
                }
            }
            stopDebugGeometry();
            setDebug(null);
        }

//        for (List<CrossOperation> cos : crossoperations.values()) {
//            for (CrossOperation co : cos) {
//                if (!co.isBlocked() && (best == null || co.cost < best.cost)) {
//                    best = co;
//                }
//            }
//        }
        if (best == null) {
            System.out.println("Algorithm stuck");
            return false;
        }

        if (best.cost > frechetthreshold) {
            System.out.println("Frechet threshold exceeded");
            return false;
        }

//        for (int i = 0; i < network.getCrosses().size(); i++)
//        {
//            if (network.getCrosses().get(i).getOriginal().getLabel().equals("virtual non-planar2"))
//            {
//                if (network.getCrosses().get(i).getVirtual().values().size() > 2)
//                {
//                    boolean halt = true;
//                }
//            }
//        }
        perform(best);

//        for (int i = 0; i < network.getCrosses().size(); i++)
//        {
//            if (network.getCrosses().get(i).getOriginal().getLabel().equals("virtual non-planar2"))
//            {
//                if (network.getCrosses().get(i).getVirtual().values().size() > 2)
//                {
//                    boolean halt = true;
//                }
//            }//        }
        //printState("POST OPERATION");
        if (abort) {
            System.out.println("ALGORITHM ABORTED");
            return false;
        } else {
            return true;
        }
    }

    private void perform(Operation operation) {
        Set<StrokeVertex> removeVertex = new HashSet();
        Set<StrokeCross> removeCross = new HashSet();
        Set<StrokeArc> uncheck = new HashSet();
        Set<Pair<StrokeVertex, CircularArc>> replacements = new HashSet();
        Set<StrokeVertex> addVertex = new HashSet();
        Set<StrokeCross> addCross = new HashSet();
        Set<VertexOperation> recheckVertex = new HashSet();

        if (operation instanceof VertexOperation) {
            VertexOperation vop = (VertexOperation) operation;

            StrokeVertex vtx = vop.vertex;
            removeVertex.add(vtx);
            if (vtx.getCross() != null) {
                removeCross.add(vtx.getCross());

                for (StrokeVertex sv : vtx.getCross().getConcrete().values()) {
                    if (sv != vtx) {
                        removeVertex.add(sv);
                        addVertex.add(sv);
                    }
                }
            }

            StrokeVertex prev = vtx.getPrevious();
            removeVertex.add(prev);
            addVertex.add(prev);
            if (prev.getCross() != null) {
                removeCross.add(prev.getCross());
                addCross.add(prev.getCross());
            }

            StrokeVertex next = vtx.getNext();
            removeVertex.add(next);
            addVertex.add(next);
            if (next.getCross() != null) {
                removeCross.add(next.getCross());
                addCross.add(next.getCross());
            }

            for (StrokeArc sa : operation.startExtension) {
                if (!sa.getEnd().isStrokeEndpoint()) {
                    removeVertex.add(sa.getEnd());
                    addVertex.add(sa.getEnd());
                }
            }
            for (StrokeArc sa : operation.endExtension) {
                if (!sa.getStart().isStrokeEndpoint()) {
                    removeVertex.add(sa.getStart());
                    addVertex.add(sa.getStart());
                }
            }

            uncheck.add(vtx.getIncoming());
            uncheck.add(vtx.getOutgoing());

            replacements.add(new Pair(vtx, vop.replacement));
        } else {
            CrossOperation cop = (CrossOperation) operation;

            for (VertexOperation vop : cop.getOperations()) {
                StrokeVertex vtx = vop.vertex;
                removeVertex.add(vtx);
                if (vtx.getCross() != null) {
                    removeCross.add(vtx.getCross());

                    for (StrokeVertex sv : vtx.getCross().getConcrete().values()) {
                        if (sv != vtx) {
                            removeVertex.add(sv);
                            //addVertex.add(sv); purposefully removed
                        }
                    }
                }

                StrokeVertex prev = vtx.getPrevious();
                removeVertex.add(prev);
                addVertex.add(prev);
                if (prev.getCross() != null) {
                    removeCross.add(prev.getCross());
                    addCross.add(prev.getCross());
                }

                StrokeVertex next = vtx.getNext();
                removeVertex.add(next);
                addVertex.add(next);
                if (next.getCross() != null) {
                    removeCross.add(next.getCross());
                    addCross.add(next.getCross());
                }

                uncheck.add(vtx.getIncoming());
                uncheck.add(vtx.getOutgoing());

                replacements.add(new Pair(vtx, vop.replacement));
            }

            for (StrokeArc sa : cop.startExtension) {
                if (!sa.getEnd().isStrokeEndpoint()) {
                    removeVertex.add(sa.getEnd());
                    addVertex.add(sa.getEnd());
                }
            }
            for (StrokeArc sa : cop.endExtension) {
                if (!sa.getStart().isStrokeEndpoint()) {
                    removeVertex.add(sa.getStart());
                    addVertex.add(sa.getStart());
                }
            }
        }

        // remove operations
        for (StrokeVertex sv : removeVertex) {
            vertexoperations.remove(sv);
        }

        // uncheck old geometry
        for (List<VertexOperation> ops : vertexoperations.values()) {
            for (VertexOperation op : ops) {
                for (StrokeArc oldarc : uncheck) {
                    boolean recheck = uncheckArcFromOperation(oldarc, op);
                    if (recheck) {
                        recheckVertex.add(op);
                        break;
                    }
                }
            }
        }

        // perform replacement
        Set<StrokeArc> check = new HashSet();
        complexity = complexity - replacements.size();

        for (Pair<StrokeVertex, CircularArc> replacement : replacements) {
            StrokeVertex mid = replacement.getFirst();
            StrokeVertex from = mid.getPrevious();
            StrokeVertex to = mid.getNext();
            StrokeArc midInc = mid.getIncoming();
            StrokeArc midOut = mid.getOutgoing();

            List<StrokeCross> jointvirtual = new ArrayList<StrokeCross>(from.getOutgoing().getVirtuals());
            jointvirtual.addAll(to.getIncoming().getVirtuals());

            if (mid.getCross() != null) {
                jointvirtual.add(mid.getCross());
            }

            List<MetroConnection> jointoriginal = new ArrayList<MetroConnection>(from.getOutgoing().getOriginaledges());
            jointoriginal.addAll(to.getIncoming().getOriginaledges());

            // find extensibles
            Set<Triple<StrokeArc, Vector, Vector>> extensibles = new HashSet();
            for (StrokeArc arc : operation.startExtension) {
                if (operation.endExtension.contains(arc)) {
                    // double ext
                    Vector[] ext = findDoubleExtension(arc, replacement.getSecond(), replacement.getFirst());
                    assert ext != null;
                    extensibles.add(new Triple(arc, ext[0], ext[1]));
                } else {
                    // start ext
                    Vector ext = findStartExtension(arc, replacement.getSecond(), replacement.getFirst());
                    assert ext != null;
                    extensibles.add(new Triple(arc, ext, null));
                }
            }
            for (StrokeArc arc : operation.endExtension) {
                if (!operation.startExtension.contains(arc)) {
                    // end ext
                    Vector ext = findEndExtension(arc, replacement.getSecond(), replacement.getFirst());
                    assert ext != null;
                    extensibles.add(new Triple(arc, null, ext));
                } // else: double -- already dealt with!
            }

            StrokeArc newarc;
            if (replacement.getSecond() instanceof FullCircleArc) {
                newarc = new StrokeArc(from, (FullCircleArc) replacement.getSecond(), jointvirtual, jointoriginal);
            } else {
                newarc = new StrokeArc(from, to, replacement.getSecond(), jointvirtual, jointoriginal);
            }

            from.setOutgoing(newarc);
            to.setIncoming(newarc);

            check.add(newarc);

            // perform extensions
            for (Triple<StrokeArc, Vector, Vector> exttrip : extensibles) {
                StrokeArc ext = exttrip.getFirst();
                Vector start = exttrip.getSecond();
                Vector end = exttrip.getThird();

                if (start != null) {
                    ext.getStart().set(start);
                }
                if (end != null) {
                    ext.getEnd().set(end);
                }
            }

            if (mid.getCross() != null) {
//                newarc.addVirtual(mid.getCross());
                mid.getCross().changeStroke(mid.getStroke(), newarc);
            }
            if (jointvirtual.size() > 0) {
                for (StrokeCross sc : newarc.getVirtuals()) {
                    sc.changeStroke(newarc.getStroke(), newarc);
                }
            }
            mid.getStroke().getVertices().remove(mid);

            // update store
            if (store != null) {
                if (mid.getCross() == null) {
                    store.removeStation(complexity, operation.cost, mid);
                } else {
                    store.updateCross(complexity, operation.cost, mid.getCross());
                }

                for (StrokeCross sc : newarc.getVirtuals()) {
                    if (sc != mid.getCross()) {
                        store.updateCross(complexity, operation.cost, sc);
                    }

                }
                store.replaceArc(complexity, operation.cost, midInc, midOut, newarc);

                for (Triple<StrokeArc, Vector, Vector> exttrip : extensibles) {
                    store.replaceArc(complexity, operation.cost, exttrip.getFirst(), null, exttrip.getFirst());
                }

            }
        }

        // check new geometry
        for (List<VertexOperation> ops : vertexoperations.values()) {
            for (VertexOperation op : ops) {
                for (StrokeArc newarc : check) {
                    checkArcIntoOperation(newarc, op);
                }
            }
        }

        for (VertexOperation op : recheckVertex) {
            recheckTopology(op);
        }

        // add operations
        for (StrokeVertex sv : addVertex) {
            vertexoperations.put(sv, makeOperations(sv));
        }
    }

    public void testVertex(StrokeCross sc) {
        System.out.println(sc.getOriginal().getLabel() + " " + sc);
        sc.computeSmallestDisc();
    }

    public void testVertex(StrokeVertex sv) {
        System.out.println(sv.getOriginal().getLabel() + " " + sv);
        makeOperations(sv);
        if (sv.getCross() != null) {
            for (StrokeVertex s : sv.getCross().getConcreteVertices()) {
                makeOperations(s);
            }
        }
    }
}
