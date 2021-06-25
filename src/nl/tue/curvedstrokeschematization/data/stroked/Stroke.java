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

import java.util.List;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class Stroke {

    private List<StrokeVertex> vertices;
    private boolean circular;

    public Stroke(List<StrokeVertex> vertices, boolean circular) {
        this.vertices = vertices;
        this.circular = circular;
        for (StrokeVertex sv : vertices) {
            sv.setStroke(this);
        }
    }

    public boolean isCircular() {
        return circular;
    }

    public List<StrokeVertex> getVertices() {
        return vertices;
    }
    
    public StrokeVertex getStartVertex() {
        if (vertices != null && vertices.size() > 0)
            return vertices.get(0);
        else
            return null;
    }
    
    public StrokeVertex getEndVertex() {
        if (vertices != null && vertices.size() > 0)
            return vertices.get(vertices.size() - 1);
        else
            return null;
    }
    
    public boolean contains(StrokeVertex sv) {
        for (StrokeVertex v : vertices)
        {
            if (v == sv)
                return true;
        }
        return false;
    }

    public int getArcCount() {
        return circular ? vertices.size() : vertices.size() - 1;
    }

    public StrokeArc getArc(int index) {
        return vertices.get(index).getOutgoing();
    }
}
