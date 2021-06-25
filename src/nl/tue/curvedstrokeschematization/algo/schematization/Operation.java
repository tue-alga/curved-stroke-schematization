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

import nl.tue.curvedstrokeschematization.data.stroked.StrokeArc;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeCross;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import nl.tue.geometrycore.geometry.curved.CircularArc;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class Operation {

    // cost
    protected double cost;
    // interactions
    protected Set<StrokeArc> startExtension = new HashSet();
    protected Set<StrokeArc> endExtension = new HashSet();
    protected Set<StrokeArc> fixedCrosses = new HashSet();
    protected ArrayList<CircularArc> extensions = new ArrayList<CircularArc>();
    
    // topology verification
    protected Set<StrokeArc> relatedArcBlocked = new HashSet();
    protected Set<StrokeArc> unrelatedArcBlocked = new HashSet();
    protected Set<StrokeCross> crossBlocked = new HashSet();

    protected boolean isBlocked() {
        return !relatedArcBlocked.isEmpty() || !unrelatedArcBlocked.isEmpty() ||!crossBlocked.isEmpty();
    }

    protected void clear() {
        startExtension.clear();
        endExtension.clear();
        fixedCrosses.clear();
        extensions.clear();
        
        relatedArcBlocked.clear();
        unrelatedArcBlocked.clear();
        crossBlocked.clear();
    }
}
