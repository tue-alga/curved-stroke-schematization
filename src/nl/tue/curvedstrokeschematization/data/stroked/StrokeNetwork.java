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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class StrokeNetwork {

    private List<Stroke> strokes;
    private List<StrokeCross> crosses;

    public StrokeNetwork() {
        strokes = new ArrayList();
        crosses = new ArrayList();
    }

    public void addStroke(Stroke stroke) {
        strokes.add(stroke);
    }
    
    public void removeStroke(Stroke s) {
        strokes.remove(s);
    }

    public void removeCross(StrokeCross sc) {
        crosses.remove(sc);
        for (StrokeVertex sv : sc.getConcreteVertices())
        {
            sv.setCross(null);
        }
    }
    
    public List<Stroke> getStrokes() {
        return strokes;
    }

    public Iterable<StrokeVertex> getVertices() {
        return new Iterable<StrokeVertex>() {

            public Iterator<StrokeVertex> iterator() {
                return new Iterator<StrokeVertex>() {

                    // indicates current
                    int stroke = 0;
                    int vertex = -1;

                    public boolean hasNext() {
                        if (stroke < strokes.size() - 1) {
                            // there is another stroke
                            return true;
                        } else if (stroke > strokes.size() - 1) {
                            // no more strokes
                            return false;
                        } else { // stroke == strokes.size() - 1
                            // current stroke has a next vertex
                            return vertex < strokes.get(stroke).getVertices().size() - 1;
                        }
                    }

                    public StrokeVertex next() {
                        if (vertex < strokes.get(stroke).getVertices().size() - 1) {
                            vertex++;
                        } else {
                            stroke++;
                            vertex = 0;
                        }

                        return strokes.get(stroke).getVertices().get(vertex);
                    }

                    public void remove() {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }
                };
            }
        };
    }

    public Iterable<StrokeArc> getArcs() {
        return new Iterable<StrokeArc>() {

            public Iterator<StrokeArc> iterator() {
                return new Iterator<StrokeArc>() {

                    // indicates current
                    int stroke = 0;
                    int arc = -1;

                    public boolean hasNext() {
                        if (stroke < strokes.size() - 1) {
                            // there is another stroke
                            return true;
                        } else if (stroke > strokes.size() - 1) {
                            // no more strokes
                            return false;
                        } else { // stroke == strokes.size() - 1
                            // current stroke has a next vertex
                            return arc < strokes.get(stroke).getArcCount() - 1;
                        }
                    }

                    public StrokeArc next() {
                        if (arc < strokes.get(stroke).getArcCount() - 1) {
                            arc++;
                        } else {
                            stroke++;
                            arc = 0;
                        }

                        return strokes.get(stroke).getArc(arc);
                    }

                    public void remove() {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }
                };
            }
        };
    }

    public void addCross(StrokeCross cross) {
        crosses.add(cross);
    }

    public List<StrokeCross> getCrosses() {
        return crosses;
    }
}
