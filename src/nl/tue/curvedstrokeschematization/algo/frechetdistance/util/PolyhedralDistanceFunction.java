/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization.algo.frechetdistance.util;

import nl.tue.geometrycore.geometry.Vector;

/**
 * A polyhedral distance function.
 * Supports a number of constructors via static functions.
 * L-1, L-infinity, and 2D-approximation functions are supported.
 * For irregular polyhedrons, use the custom() function. *
 *
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class PolyhedralDistanceFunction {

    public static PolyhedralDistanceFunction L1() {
        
        Vector[] facetdescripts = new Vector[] {
            new Vector(0.5, 0.5),
            new Vector(-0.5, 0.5),
            new Vector(-0.5, -0.5),
            new Vector(0.5, -0.5),
        };
        return new PolyhedralDistanceFunction(facetdescripts);
    }

    public static PolyhedralDistanceFunction LInfinity(int dimensions) {
        assert dimensions >= 2;

        Vector[] facetdescripts = new Vector[] {
            new Vector(1, 0),
            new Vector(0, 1),
            new Vector(-1, 0),
            new Vector(0, -1),
        };

        return new PolyhedralDistanceFunction(facetdescripts);
    }

    public static PolyhedralDistanceFunction epsApproximation2D(double eps) {
        assert eps > 1;

        int k;
        if (eps >= Math.sqrt(2)) {
            k = 4;
        } else {
            k = (int) Math.ceil(Math.PI * 2.0 / Math.acos(1.0 / eps));
            if (k % 2 == 1) {
                k++;
            }
        }

        return kRegular2D(k);
    }

    public static PolyhedralDistanceFunction kRegular2D(int k) {
        assert k >= 4 && k % 2 == 0;

        Vector[] facetdescripts = new Vector[k];
        double alpha = 2 * Math.PI / (double) k;

        Vector prev = Vector.right();

        for (int i = 0; i < k; i++) {
            Vector next = prev.clone();
            next.rotate(alpha);
            facetdescripts[i] = Vector.multiply(0.5, Vector.add(prev,next));
            prev = next;
        }

        return new PolyhedralDistanceFunction(facetdescripts);
    }

    // facet is described by the closest point to the origin
    // it is directly the normal and its length encodes normalization
    //
    private Vector[] facets;
    private double[] facetSqrLength;

    private PolyhedralDistanceFunction(Vector[] facets) {
        this.facets = facets;
        this.facetSqrLength = new double[facets.length];
        for (int i = 0; i < this.facetSqrLength.length; i++) {
            this.facetSqrLength[i] = this.facets[i].squaredLength();
        }
    }

    public int getComplexity() {
        return facets.length;
    }

    public Vector getFacet(int facet) {
        return facets[facet];
    }

    public double getDistance(Vector p, Vector q) {
        return getDistance(Vector.subtract(q,p));
    }

    public double getDistance(Vector d) {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < facets.length; i++) {
            double fd = getFacetDistance(d, i);
            max = Math.max(max, fd);
        }
        return max;
    }

    public double getFacetDistance(Vector p, Vector q, int facet) {
        return getFacetDistance(Vector.subtract(q,p), facet);
    }

    public double getFacetDistance(Vector d, int facet) {
        return Vector.dotProduct(facets[facet],d) / facetSqrLength[facet];
    }

    public double getFacetSlope(Vector p1, Vector p2, int facet) {
        return getFacetDistance(Vector.subtract(p2,p1), facet);
    }
}
