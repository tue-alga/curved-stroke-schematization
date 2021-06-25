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

import nl.tue.curvedstrokeschematization.data.metro.MetroStation;
import java.awt.Color;
import nl.tue.geometrycore.geometry.BaseGeometry;
import nl.tue.geometrycore.geometry.GeometryConvertable;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class RenderedStation implements GeometryConvertable<BaseGeometry> {

    private Color strokecolor;
    private double strokewidth;
    private Color fillcolor;
    private BaseGeometry geom;
    private MetroStation original;

    public RenderedStation(Color strokecolor, double strokewidth, Color fillcolor, MetroStation original, BaseGeometry geom) {
        this.strokecolor = strokecolor;
        this.strokewidth = strokewidth;
        this.fillcolor = fillcolor;
        this.geom = geom;
        this.original = original;
    }

    @Override
    public BaseGeometry toGeometry() {
        return geom;
    }

    public Color getFillcolor() {
        return fillcolor;
    }

    public Color getStrokecolor() {
        return strokecolor;
    }

    public double getStrokewidth() {
        return strokewidth;
    }

    public MetroStation getOriginal() {
        return original;
    }
    
    
}
