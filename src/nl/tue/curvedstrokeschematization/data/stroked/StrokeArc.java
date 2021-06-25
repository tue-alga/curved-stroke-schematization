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

import nl.tue.curvedstrokeschematization.algo.schematization.FullCircleArc;
import nl.tue.curvedstrokeschematization.data.metro.MetroConnection;
import nl.tue.curvedstrokeschematization.data.metro.MetroStation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nl.tue.geometrycore.geometry.BaseGeometry;
import nl.tue.geometrycore.geometry.GeometryConvertable;
import nl.tue.geometrycore.geometry.GeometryType;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.curved.Circle;
import nl.tue.geometrycore.geometry.curved.CircularArc;
import nl.tue.geometrycore.geometry.linear.Line;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class StrokeArc implements GeometryConvertable<CircularArc> {

    private List<MetroConnection> originaledges;
    // geometry info
    private StrokeVertex start, end;
    private Vector center;
    private boolean clockwise;
    private CircularArc arc = null;
    // interchange stations along the arc
    private List<StrokeCross> virtuals;

    public StrokeArc(StrokeVertex point, FullCircleArc fca, List<StrokeCross> virtuals, List<MetroConnection> originaledges) {
        this.start = point;
        this.end = point;
        this.center = fca.getCenter();
        this.clockwise = fca.isClockwise();
        this.virtuals = virtuals;
        this.originaledges = originaledges;
        this.arc = fca;
    }

    public StrokeArc(StrokeVertex start, StrokeVertex end, MetroConnection originaledge) {
        this.start = start;
        this.end = end;
        this.center = null;
        this.clockwise = true;
        this.virtuals = new ArrayList();
        this.originaledges = new ArrayList();
        this.originaledges.add(originaledge);
    }

    public StrokeArc(StrokeVertex start, StrokeVertex end, CircularArc geom, List<StrokeCross> virtuals, List<MetroConnection> originaledges) {
        this.start = start;
        this.end = end;
        this.center = geom.getCenter();
        this.clockwise = !geom.isCounterclockwise();
        this.virtuals = virtuals;
        this.originaledges = originaledges;
    }

    public List<MetroConnection> getOriginaledges() {
        return originaledges;
    }

    @Override
    public CircularArc toGeometry() {
        if (arc == null) {
            arc = new CircularArc(center, start, end, !clockwise);
        }
        return arc;
    }

    public StrokeVertex getEnd() {
        return end;
    }

    public StrokeVertex getStart() {
        return start;
    }

    public Vector getCenter() {
        return center;
    }

    public boolean isClockwise() {
        return clockwise;
    }

    public StrokeArc getNext() {
        return end.getOutgoing();
    }

    public StrokeArc getPrevious() {
        return start.getIncoming();
    }

    public List<StrokeCross> getVirtuals() {
        return virtuals;
    }

    public Stroke getStroke() {
        return start.getStroke();
    }

    public void addVirtual(StrokeCross sc) {
        virtuals.add(sc);
    }

    public boolean extendArcTo(StrokeVertex sv, StrokeArc sa) {
        //TODO: if center == null!!!
        if (arc.getCenter() == null) {
            List<BaseGeometry> intersects = sa.toGeometry().intersect(new Line(arc.getStart(), arc.getEnd()));
            boolean fromStart = true;
            if (sv == end) //measure from end backwards
            {
                fromStart = false;
            } else if (sv != start) {
                return false;
            }

            double bestDist = Double.POSITIVE_INFINITY;
            Vector firstIntersect = null;
            for (BaseGeometry intersect : intersects) {
                if (intersect.getGeometryType() != GeometryType.VECTOR) {
                    continue;
                }
                Vector ivec = (Vector) intersect;
                double dist = intersect.distanceTo(start);
                if (dist < bestDist == fromStart) {
                    bestDist = dist;
                    firstIntersect = ivec;
                }
            }
            sv.set(firstIntersect);
            return true;
        }

        Circle C = new Circle(arc.getCenter(), arc.radius());
        List<BaseGeometry> intersects = sa.toGeometry().intersect(C);

        if (intersects.isEmpty()) {
            return false;
        }

        boolean measureCW = true;

        if (sv == start) //measure from end backwards
        {
            measureCW = !clockwise;
        } else if (sv == end) //measure from start forwards
        {
            measureCW = clockwise;
        } else {
            System.out.println("ERROR: extending arc from vertex not part of arc");
            assert false;
        }

        double bestAngle = Double.POSITIVE_INFINITY;
        Vector firstIntersect = null;
        for (BaseGeometry intersect : intersects) {
            if (intersect.getGeometryType() != GeometryType.VECTOR) {
                continue;
            }
            Vector ivec = (Vector) intersect;
            double angle = Vector.subtract(start, center).computeClockwiseAngleTo(Vector.subtract(ivec, center));
            if (!measureCW) {
                angle = 2.0 * Math.PI - angle;
            }

            if (angle < bestAngle) {
                bestAngle = angle;
                firstIntersect = ivec;
            }
        }

        if (firstIntersect == null) {
            return false;
        }
        sv.set(firstIntersect);
        return true;
    }

    public StrokeArc reverse() {
        StrokeVertex temp = start;
        start = end;
        end = temp;
        clockwise = !clockwise;
        for (MetroConnection mc : originaledges) {
            MetroStation tempstation = mc.getBeginStation();
            mc.setBeginStation(mc.getEndStation());
            mc.setEndStation(tempstation);
        }
        Collections.reverse(originaledges);
        arc = null;
        return this;
    }
}
