/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization.algo.frechetdistance.implementations.polyhedral;

import nl.tue.curvedstrokeschematization.algo.frechetdistance.util.PolyhedralDistanceFunction;
import nl.tue.curvedstrokeschematization.algo.frechetdistance.FrechetDistance;
import nl.tue.curvedstrokeschematization.algo.frechetdistance.envelope.UpperEnvelope;
import nl.tue.geometrycore.geometry.Vector;

/**
 * Computing the Frechet distance under polyhedral distances. Constructor
 * requires a polyhedral distance function to be specified.
 *
 * This follows the approximation algorithm described in
 * https://doi.org/10.1007/s00454-016-9800-8
 *
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class PolyhedralFrechetDistance extends FrechetDistance {

    protected PolyhedralDistanceFunction distfunc;

    public PolyhedralFrechetDistance(PolyhedralDistanceFunction distfunc) {
        this.distfunc = distfunc;
    }

    @Override
    public double distance(Vector p, Vector q) {
        return distfunc.getDistance(p, q);
    }

    @Override
    protected UpperEnvelope initializeRowUpperEnvelope(int row) {
        return new PolyhedralUpperEnvelope(distfunc, Q[row], Q[row + 1]);
    }

    @Override
    protected UpperEnvelope initializeColumnUpperEnvelope(int column) {
        return new PolyhedralUpperEnvelope(distfunc, P[column], P[column + 1]);
    }
}
