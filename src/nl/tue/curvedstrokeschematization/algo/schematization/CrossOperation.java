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

import nl.tue.curvedstrokeschematization.data.stroked.StrokeVertex;
import java.util.ArrayList;
import nl.tue.geometrycore.geometry.curved.CircularArc;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class CrossOperation extends Operation {
    protected ArrayList<VertexOperation> replacements;
    
    public CrossOperation()
    {
        super();
        cost = 0.0;
        replacements = new ArrayList<VertexOperation>();
    }
    
    public void addReplacementArc(StrokeVertex sv, CircularArc oa)
    {
        replacements.add(new VertexOperation(sv, oa));
    }
    
    public ArrayList<VertexOperation> getOperations()
    {
        return replacements;
    }
}
