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

import java.util.Iterator;
import java.util.List;
import nl.tue.geometrycore.geometry.BaseGeometry;
import nl.tue.geometrycore.geometry.GeometryType;
import nl.tue.geometrycore.geometry.OrientedGeometry;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.curved.Circle;
import nl.tue.geometrycore.geometry.curved.CircularArc;
import nl.tue.geometrycore.geometry.linear.LineSegment;
import nl.tue.geometrycore.geometry.linear.PolyLine;
import nl.tue.geometrycore.util.DoubleUtil;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class FullCircleArc extends CircularArc {

    public static double distanceAlongArc(CircularArc arc, Vector v, boolean fromStart) {
        if (fromStart) {
            if (arc instanceof FullCircleArc) {
                FullCircleArc fca = (FullCircleArc) arc;
                return new CircularArc(fca.center, fca.point, v, !fca.clockwise).perimeter();
            } else {
                return new CircularArc(arc.getCenter(), arc.getStart(), v, arc.isCounterclockwise()).perimeter();
            }

        } else {
            if (arc instanceof FullCircleArc) {
                FullCircleArc fca = (FullCircleArc) arc;
                return new CircularArc(fca.center, v, fca.point, !fca.clockwise).perimeter();
            } else {
                return new CircularArc(arc.getCenter(), v, arc.getEnd(), arc.isCounterclockwise()).perimeter();
            }
        }
    }

    private static Vector[] circleCircleClosestPoints(Circle crlA, Circle crlB) {
        List<BaseGeometry> ints = crlA.intersect(crlB);
        if (ints.get(0).getGeometryType() != GeometryType.VECTOR) {
            // overlap, take arbitrary point on boundary
            Vector p = new Vector(crlA.getCenter().getX(), crlA.getCenter().getY() + crlA.getRadius());
            return new Vector[]{p, p};
        } else if (ints.size() > 0) {
            Vector v = (Vector) ints.get(0);
            return new Vector[]{v, v};
        } else {
            double d = crlA.getCenter().distanceTo(crlB.getCenter());
            Vector dirthis = null;
            Vector dircrl = null;
            if (d <= crlA.getRadius() + crlB.getRadius()) {
                // one is contained in the other
                if (crlA.getCenter().isApproximately(crlB.getCenter())) {
                    dirthis = new Vector(0, 1);
                } else if (crlA.getRadius() <= crlB.getRadius()) {
                    dirthis = Vector.subtract(crlA.getCenter(), crlB.getCenter());
                    dirthis.normalize();
                } else {
                    dirthis = Vector.subtract(crlB.getCenter(), crlA.getCenter());
                    dirthis.normalize();
                }
                dircrl = dirthis;
            } else {
                // disjoint, closest points are points on line between centers
                dirthis = Vector.subtract(crlB.getCenter(), crlA.getCenter());
                dirthis.normalize();
                dircrl = dirthis.clone();
                dircrl.invert();
            }
            dirthis.scale(crlA.getRadius());
            dircrl.scale(crlB.getRadius());
            return new Vector[]{
                Vector.add(crlA.getCenter(), dirthis),
                Vector.add(crlB.getCenter(), dircrl)};
        }
    }

    private static Vector[] segmentCircleClosestPoints(LineSegment seg, Circle crl) {
        List<BaseGeometry> ints = seg.intersect(crl);
        if (ints.get(0).getGeometryType() != GeometryType.VECTOR) {
            System.err.println("cannot occur, overlap between line segment and circle");
            return null;
        } else if (ints.size() > 0) {
            Vector v = (Vector) ints.get(0);
            return new Vector[]{v, v};
        } else {
            // closest point on line segment to center is closest to circle as well
            // unless it is one of the end points

            Vector[][] candidates = new Vector[3][];
            candidates[0] = new Vector[]{seg.getStart(), crl.closestPoint(seg.getStart())};
            candidates[1] = new Vector[]{seg.getEnd(), crl.closestPoint(seg.getEnd())};
            Vector segcp = seg.closestPoint(crl.getCenter());
            if (crl.contains(segcp)) {
                candidates[2] = null;
            } else {
                Vector v = Vector.subtract(segcp, crl.getCenter());
                v.normalize();
                v.scale(crl.getRadius());
                v.translate(crl.getCenter());
                candidates[2] = new Vector[]{segcp, v};
            }

            Vector[] opt = null;
            double dist = Double.POSITIVE_INFINITY;
            for (Vector[] cand : candidates) {
                if (cand != null) {
                    double d = cand[0].distanceTo(cand[1]);
                    if (d < dist) {
                        dist = d;
                        opt = cand;
                    }
                }
            }
            return opt;
        }
    }

    private static Vector[] arcCircleClosestPoints(CircularArc arc, Circle crl) {
        assert arc.getCenter() != null;

        List<BaseGeometry> ints = arc.intersect(crl);
        if (ints.get(0).getGeometryType() != GeometryType.VECTOR) {
            // overlap
            return new Vector[]{arc.getStart().clone(), arc.getStart().clone()};
        } else if (ints.size() > 0) {
            Vector v = (Vector) ints.get(0);
            return new Vector[]{v, v};
        } else {
            // there is no intersection
            // it suffices to look at the following pairs:
            Vector[][] candidates = new Vector[3][];
            candidates[0] = new Vector[]{arc.getStart().clone(), crl.closestPoint(arc.getStart())};
            candidates[1] = new Vector[]{arc.getEnd().clone(), crl.closestPoint(arc.getEnd())};
            candidates[2] = circleCircleClosestPoints(new Circle(arc.getCenter(), arc.radius()), crl);
            if (arc.inInfiniteSector(candidates[2][0])) {
                candidates[2] = null;
            }

            Vector[] opt = null;
            double dist = Double.POSITIVE_INFINITY;
            for (Vector[] cand : candidates) {
                if (cand != null) {
                    double d = cand[0].distanceTo(cand[1]);
                    if (d < dist) {
                        dist = d;
                        opt = cand;
                    }
                }
            }
            return opt;
        }
    }

    public static Vector[] closestPoints(CircularArc arc, Circle crl) {

        if (arc instanceof FullCircleArc) {
            Circle crlarc = ((FullCircleArc) arc).getCircle();
            return circleCircleClosestPoints(crlarc, crl);

        } else if (arc.getCenter() == null) {

            return segmentCircleClosestPoints(new LineSegment(arc.getStart(), arc.getEnd()), crl);

        } else {
            return arcCircleClosestPoints(arc, crl);
        }
    }

    public static List<BaseGeometry> intersect(BaseGeometry A, BaseGeometry B, boolean closed) {
        List<BaseGeometry> is;
        if (A instanceof FullCircleArc && B instanceof FullCircleArc) {
            FullCircleArc a = (FullCircleArc) A;
            FullCircleArc b = (FullCircleArc) B;
            is = a.getCircle().intersect(b.getCircle());
        } else if (A instanceof FullCircleArc) {
            FullCircleArc a = (FullCircleArc) A;
            is = a.getCircle().intersect(B);
        } else if (B instanceof FullCircleArc) {
            FullCircleArc b = (FullCircleArc) B;
            is = b.getCircle().intersect(A);
        } else {
            // regular geometry
            is = A.intersect(B);
        }

        if (!closed) {
            if (A instanceof OrientedGeometry) {
                filterIntersections(is, ((OrientedGeometry) A).getStart(), ((OrientedGeometry) A).getEnd());
            }
            if (B instanceof OrientedGeometry) {
                filterIntersections(is, ((OrientedGeometry) B).getStart(), ((OrientedGeometry) B).getEnd());
            }
        }
        return is;
    }

    public static void filterIntersections(List<BaseGeometry> is, Vector... exclude) {
        Iterator<BaseGeometry> it = is.iterator();
        while (it.hasNext()) {
            BaseGeometry next = it.next();
            if (next instanceof Vector) {
                Vector v = (Vector) next;
                for (Vector e : exclude) {
                    if (v.isApproximately(e)) {
                        it.remove();
                        break;
                    }
                }
            }
        }
    }

    private Vector center, point;
    private boolean clockwise;

    public FullCircleArc(Vector center, Vector point, boolean clockwise) {
        super(null, null, null, !clockwise);

        this.center = center;
        this.point = point;
        this.clockwise = clockwise;
    }

    public void setClockwise(boolean clockwise) {
        this.clockwise = clockwise;
    }

    public boolean isClockwise() {
        return clockwise;
    }

    public Vector theOther(Vector v) {
        return point;
    }

    public Vector getSharedVertex(CircularArc other) {
        if (point.isApproximately(other.getStart()) || point.isApproximately(other.getEnd())) {
            return point;
        } else {
            return null;
        }
    }

    public double unsignedArea() {
        double r = point.distanceTo(center);
        return Math.PI * r * r;
    }

    public double segmentArea() {
        return unsignedArea();
    }

    public double sectorArea() {
        return unsignedArea();
    }

    @Override
    public double radius() {
        return point.distanceTo(center);
    }

    @Override
    public double perimeter() {
        double r = point.distanceTo(center);
        return 2 * Math.PI * r;
    }

    @Override
    public double centralAngle() {
        return Math.PI * 2;
    }

    @Override
    public Vector getEndTangent() {
        return getStartTangent();
    }

    @Override
    public Vector getStartTangent() {
        Vector v = Vector.subtract(point, center);
        if (clockwise) {
            v.rotate90DegreesClockwise();
        } else {
            v.rotate90DegreesCounterclockwise();
        }
        return v;
    }

    @Override
    public void setEnd(Vector end) {
        assert false;
    }

    @Override
    public void setStart(Vector start) {
        assert false;
    }

    @Override
    public Vector getEnd() {
        return point;
    }

    @Override
    public Vector getStart() {
        return point;
    }

    @Override
    public void setCenter(Vector c) {
        center.set(c);
    }

    @Override
    public Vector getCenter() {
        return center;
    }

    public Circle getCircle() {
        return new Circle(center.clone(), point.distanceTo(center));
    }

    @Override
    public String toString() {
        return "FCA[" + center + "," + point + "]"; //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public CircularArc clone() {
        return new FullCircleArc(center.clone(), point.clone(), clockwise);
    }

    @Override
    public GeometryType getGeometryType() {
        return GeometryType.CIRCULARARC;
    }

    @Override
    public void reverse() {
        clockwise = !clockwise;
    }

    @Override
    public void rotate(double counterclockwiseangle) {
        point.rotate(counterclockwiseangle);
        center.rotate(counterclockwiseangle);
    }

    @Override
    public void translate(Vector d) {
        translate(d.getX(), d.getY());
    }

    @Override
    public void translate(double deltaX, double deltaY) {
        point.translate(deltaX, deltaY);
        center.translate(deltaX, deltaY);
    }

    @Override
    public void updateEndpoints(Vector start, Vector end) {
        assert false;
    }

    @Override
    public void intersect(BaseGeometry otherGeom, double prec, List<BaseGeometry> intersections) {
        assert false;
    }

    @Override
    public Vector closestPoint(Vector point) {
        return getCircle().closestPoint(point);
    }

    @Override
    public boolean inInfiniteSector(Vector point, double prec) {
        return super.inInfiniteSector(point, prec); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean inInfiniteSector(Vector point) {
        return super.inInfiniteSector(point); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean inSector(Vector point, double prec) {
        return getCircle().contains(point, prec);
    }

    @Override
    public boolean inSector(Vector point) {
        return inSector(point, DoubleUtil.EPS);
    }

    @Override
    public boolean onBoundary(Vector point, double prec) {
        return super.onBoundary(point, prec); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double segmentAreaSigned() {
        return super.segmentAreaSigned(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double sectorAreaSigned() {
        return super.sectorAreaSigned(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double areaSigned(double radius, double centralangle) {
        return super.areaSigned(radius, centralangle); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double areaSigned() {
        return super.areaSigned(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double centralAngle(double radius) {
        return Math.PI * 2; //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Vector getEndArm() {
        Vector arm = Vector.subtract(point, center);
        arm.normalize();
        return arm;
    }

    @Override
    public Vector getStartArm() {
        return getEndArm();
    }

    @Override
    public Vector getPointAt(double t) {

        Vector arm = Vector.subtract(getStart(), center);
        double ca = Math.PI * 2;
        arm.rotate(clockwise ? -ca * t : ca * t);
        arm.translate(center);
        return arm;
    }

    @Override
    public double getMaximumParameter() {
        return 1; //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double getMinimumParameter() {
        return 0; //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setCounterclockwise(boolean counterclockwise) {
        clockwise = !counterclockwise;
    }

    @Override
    public boolean isCounterclockwise() {
        return !clockwise;
    }

    @Override
    public PolyLine getApproximation(int points) {
        assert false;
        return null;
    }

    @Override
    public void updateEnd(Vector end) {
        assert false;
    }

    @Override
    public void updateStart(Vector start) {
        assert false;
    }

    @Override
    public CircularArc toGeometry() {
        assert false;
        return null;
    }

    @Override
    public void scale(double fx, double fy) {
        point.scale(fx, fy);
        center.scale(fx, fx);
    }

    @Override
    public double distanceTo(Vector point) {
        return getCircle().distanceTo(point);
    }

    @Override
    public boolean onBoundary(Vector point) {
        return getCircle().onBoundary(point);
    }

    @Override
    public void intersect(BaseGeometry other, List<BaseGeometry> intersections) {
        assert false;
    }

    @Override
    public List<BaseGeometry> intersect(BaseGeometry other, double prec) {
        assert false;
        return null;
    }

    @Override
    public List<BaseGeometry> intersect(BaseGeometry other) {
        assert false;
        return null;
    }

}
