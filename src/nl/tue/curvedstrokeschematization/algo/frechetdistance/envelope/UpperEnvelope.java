/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization.algo.frechetdistance.envelope;

import nl.tue.geometrycore.geometry.Vector;


/**
 * Interface of the upper envelope data structure as required by the framework
 * of the frechetdistance.algo.FrechetDistance class.
 *
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public interface UpperEnvelope {
    
    public void add(int i, Vector P1, Vector P2, Vector Q);
    public void removeUpto(int i);
    public void clear();
    
    public double findMinimum(double... constants);
    
    public void truncateLast();
}
