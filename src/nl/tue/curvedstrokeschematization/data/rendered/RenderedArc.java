/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization.data.rendered;

import nl.tue.curvedstrokeschematization.data.metro.MetroConnection;
import java.awt.Color;
import nl.tue.geometrycore.geometry.GeometryConvertable;
import nl.tue.geometrycore.geometry.curved.CircularArc;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class RenderedArc implements GeometryConvertable<CircularArc> {

    private Color strokecolor;
    private double strokewidth;
    private CircularArc arc;
    private MetroConnection original;

    public RenderedArc(Color strokecolor, double strokewidth, MetroConnection original, CircularArc arc) {
        this.strokecolor = strokecolor;
        this.strokewidth = strokewidth;
        this.arc = arc;
        this.original = original;
    }

    @Override
    public CircularArc toGeometry() {
        return arc;
    }

    public Color getStrokeColor() {
        return strokecolor;
    }

    public double getStrokewidth() {
        return strokewidth;
    }

    public MetroConnection getOriginal() {
        return original;
    }

}
