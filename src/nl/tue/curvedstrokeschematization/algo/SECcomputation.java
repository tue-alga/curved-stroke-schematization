/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization.algo;

import nl.tue.curvedstrokeschematization.algo.schematization.IterativeSchematization;
import java.util.List;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.curved.Circle;

/**
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class SECcomputation {

    public static Circle getSmallestEnclosingDisc(List<Vector> vertices) {
        
        IterativeSchematization.debug("SEC vertices pre : "+vertices.size());
        
        //filter out duplicates
        for (int i = 0; i < vertices.size(); i++)
        {
            assert vertices.get(i) != null;
            for (int j = i + 1; j < vertices.size(); j++)
            {
                if (vertices.get(i).isApproximately(vertices.get(j)))
                {
                    vertices.remove(j);
                    j--;
                }
            }
        }
        
        IterativeSchematization.debug("SEC vertices post: "+vertices.size());
        
        //degenerate cases
        if (vertices.isEmpty()) {
            return null;
        }

        if (vertices.size() == 1) {
            return new Circle(vertices.get(0), 0.0);
        }

        if (vertices.size() == 2) {
            return Circle.byDiametricPoints(vertices.get(0), vertices.get(1));
        }

        //Compute a random permutation -> purely for expected runtime
        //Collections.shuffle(vertices);

        Circle circ = Circle.byDiametricPoints(vertices.get(0), vertices.get(1));
        for (int i = 2; i < vertices.size(); i++) {
            //if pi not in disc
            if (!circ.contains(vertices.get(i))) {
                circ = getSmallestEnclosingDisc(vertices.subList(0, i), vertices.get(i));
            }
        }

        return circ;
    }

    private static Circle getSmallestEnclosingDisc(List<Vector> vertices, Vector v) {
        //Collections.shuffle(vertices); -> purely for expected runtime

        Circle circ = Circle.byDiametricPoints(vertices.get(0), v);
        for (int j = 1; j < vertices.size(); j++) {
            //if pj not in disc
            if (!circ.contains(vertices.get(j))) {
                circ = getSmallestEnclosingDisc(vertices.subList(0, j), vertices.get(j), v);
            }
        }
        return circ;
    }

    private static Circle getSmallestEnclosingDisc(List<Vector> vertices, Vector v, Vector w) {
        Circle circ = Circle.byDiametricPoints(v, w);
        for (int k = 0; k < vertices.size(); k++) {
            //if pk not in disc
            if (!circ.contains(vertices.get(k))) {
                circ = Circle.byThreePoints(vertices.get(k), v, w);
            }
        }
        return circ;
    }
}
