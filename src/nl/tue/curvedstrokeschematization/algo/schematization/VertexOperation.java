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
import nl.tue.geometrycore.geometry.curved.CircularArc;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class VertexOperation  extends Operation  {

    protected StrokeVertex vertex;
    protected CircularArc replacement;
    
    public VertexOperation() {}
    public VertexOperation(StrokeVertex vertex, CircularArc replacement)
    {
        super();
        this.vertex = vertex;
        this.replacement = replacement;
    }
}
