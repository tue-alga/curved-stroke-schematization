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

import nl.tue.curvedstrokeschematization.data.metro.MetroStation;
import nl.tue.geometrycore.geometry.Vector;


/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class StrokeVertex extends Vector {

    private MetroStation original;
    private Stroke stroke;
    private StrokeCross cross;
    private StrokeArc incoming, outgoing;

    public StrokeVertex(MetroStation original) {
        super(original);
        this.original = original;
    }

    public MetroStation getOriginal() {
        return original;
    }

    public boolean isStrokeEndpoint() {
        return incoming == null || outgoing == null;
    }

    public void setStroke(Stroke stroke) {
        this.stroke = stroke;
    }

    public void setIncoming(StrokeArc incoming) {
        this.incoming = incoming;
    }

    public void setOutgoing(StrokeArc outgoing) {
        this.outgoing = outgoing;
    }

    public StrokeArc getOutgoing() {
        return outgoing;
    }

    public StrokeCross getCross() {
        return cross;
    }

    public void setCross(StrokeCross cross) {
        this.cross = cross;
    }

    public boolean isCross() {
        return cross != null;
    }

    public StrokeVertex getNext() {
        return outgoing.getEnd();
    }

    public StrokeArc getIncoming() {
        return incoming;
    }

    public StrokeVertex getPrevious() {
        return incoming.getStart();
    }

    public Stroke getStroke() {
        return stroke;
    }
}
