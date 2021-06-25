/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization.algo.frechetdistance.implementations.polyhedral;

import nl.tue.curvedstrokeschematization.algo.frechetdistance.util.PolyhedralDistanceFunction;
import nl.tue.curvedstrokeschematization.algo.frechetdistance.envelope.UpperEnvelope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import nl.tue.geometrycore.datastructures.doublylinkedlist.DoublyLinkedList;
import nl.tue.geometrycore.datastructures.doublylinkedlist.DoublyLinkedListItem;
import nl.tue.geometrycore.geometry.Vector;

/**
 * The upper envelope data structure for polyhedral distance functions.
 * This is the brute-force method, using lists of parallel lines
 * for sorted facets.
 *
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class PolyhedralUpperEnvelope implements UpperEnvelope {

    protected class FacetList extends DoublyLinkedList<FacetListElement> {

        int facet;
        double slope;

        FacetList(int facet) {
            this.facet = facet;
            this.slope = distfunc.getFacetSlope(p1, p2, facet);
        }
    }

    protected class FacetListElement extends DoublyLinkedListItem<FacetListElement> {

        int index;
        double height;
        double slope;
    }
    protected PolyhedralDistanceFunction distfunc;
    protected Vector p1, p2;
    protected List<FacetList> sortedfacets;

    public PolyhedralUpperEnvelope(PolyhedralDistanceFunction distfunc, Vector p1, Vector p2) {
        this.distfunc = distfunc;
        this.p1 = p1;
        this.p2 = p2;

        sortedfacets = new ArrayList();

        for (int i = 0; i < distfunc.getComplexity(); i++) {
            sortedfacets.add(new FacetList(i));
        }

        Collections.sort(sortedfacets, new Comparator<FacetList>() {

            @Override
            public int compare(FacetList o1, FacetList o2) {
                return Double.compare(o1.slope, o2.slope);
            }
        });
    }

    @Override
    public void add(int i, Vector P1, Vector P2, Vector Q) {
        assert P1 == p1;
        assert P2 == p2;

        for (int f = 0; f < distfunc.getComplexity(); f++) {

            FacetList fl = sortedfacets.get(f);

            FacetListElement fle = new FacetListElement();
            fle.index = i;
            fle.slope = fl.slope;
            fle.height = distfunc.getFacetDistance(Vector.subtract(p1,Q), fl.facet);

            while (fl.size() > 0 && fl.getFirst().height <= fle.height) {
                fl.removeFirst();
            }
            fl.addFirst(fle);
        }
    }

    @Override
    public void removeUpto(int i) {
        for (FacetList fl : sortedfacets) {
            while (fl.size() > 0 && fl.getLast().index <= i) {
                fl.removeLast();
            }

            assert fl.size() > 0;
        }
    }

    @Override
    public void clear() {
        for (FacetList fl : sortedfacets) {
            fl.clear();
        }
    }

    private double findMinimumFullProcedure() {
        FacetListElement[] upperenvelope = new FacetListElement[distfunc.getComplexity()];

        FacetListElement first = sortedfacets.get(0).getLast();
        upperenvelope[0] = first;
        double[] intersectPreviousAt = new double[distfunc.getComplexity()];
        intersectPreviousAt[0] = Double.NEGATIVE_INFINITY;
        int n = 1;

        for (int i = 1; i < distfunc.getComplexity(); i++) {
            FacetList fl = sortedfacets.get(i);
            FacetListElement fle = fl.getLast();

            assert fle != null;

            upperenvelope[n] = fle;
            intersectPreviousAt[n] = findIntersection(upperenvelope[n - 1], fle);
            n++;

            if (Double.isInfinite(intersectPreviousAt[n - 1]) || Double.isNaN(intersectPreviousAt[n - 1])) {
                // NB: can only occur with the first previous one (slopes are unique in UE)
                if (upperenvelope[n - 1].height > upperenvelope[n - 2].height) {
                    // toss previous
                    upperenvelope[n - 2] = upperenvelope[n - 1];
                    if (n > 2) {
                        intersectPreviousAt[n - 2] = findIntersection(upperenvelope[n - 3], fle);
                    } else {
                        // nothing to intersect with
                    }
                    n--;
                } else {
                    // toss this one
                    n--;
                }
            }

            while (n > 1 && intersectPreviousAt[n - 1] < intersectPreviousAt[n - 2]) {
                // [n-2] not on upper envelope

                assert n > 2;

                upperenvelope[n - 2] = upperenvelope[n - 1];
                intersectPreviousAt[n - 2] = findIntersection(upperenvelope[n - 3], fle);

                n--;
            }
        }

        // upperenvelope contains only lines that actually occur on it

        // minimum given by first point with positve slop
        int min_index = -1;
        for (int i = 0; i < n; i++) {
            if (upperenvelope[i].slope > 0) {
                min_index = i;
                break;
            }
        }

        double min;
        if (intersectPreviousAt[min_index] < 0) {
            // minimum before interval [0,1]
            // find (first) intersection with 0 by going forward

            while (min_index < n - 1 && intersectPreviousAt[min_index + 1] < 0) {
                min_index++;
            }
            min = upperenvelope[min_index].height;

        } else if (intersectPreviousAt[min_index] > 1) {
            // minimum after interval [0,1]
            // find first intersection with 1 by going backward

            while (min_index > 0 && intersectPreviousAt[min_index] > 1) {
                min_index--;
            }
            min = upperenvelope[min_index].slope + upperenvelope[min_index].height;

        } else {
            // minimum point in interval [0,1]
            min = upperenvelope[min_index].slope * intersectPreviousAt[min_index] + upperenvelope[min_index].height;
        }

        return min;
    }

    private double findMinimumTrimmedProcedure() {
        FacetListElement[] upperenvelope = new FacetListElement[distfunc.getComplexity()];

        FacetListElement first = sortedfacets.get(0).getLast();
        upperenvelope[0] = first;
        double[] intersectPreviousAt = new double[distfunc.getComplexity()];
        intersectPreviousAt[0] = 0;
        int n = 1;

        for (int i = 1; i < distfunc.getComplexity(); i++) {
            FacetList fl = sortedfacets.get(i);
            FacetListElement fle = fl.getLast();

            assert fle != null;

            upperenvelope[n] = fle;
            intersectPreviousAt[n] = findIntersection(upperenvelope[n - 1], fle);
            n++;

            if (Double.isInfinite(intersectPreviousAt[n - 1]) || Double.isNaN(intersectPreviousAt[n - 1])) {
                // NB: can only occur with the first previous one (slopes are unique in UE)
                if (upperenvelope[n - 1].height > upperenvelope[n - 2].height) {
                    // toss previous
                    upperenvelope[n - 2] = upperenvelope[n - 1];
                    if (n > 2) {
                        intersectPreviousAt[n - 2] = findIntersection(upperenvelope[n - 3], fle);
                    } else {
                        // nothing to intersect
                    }
                    n--;
                } else {
                    // toss this one
                    n--;
                }
            }

            while (n > 1 && intersectPreviousAt[n - 1] < intersectPreviousAt[n - 2]) {
                // [n-2] not on upper envelope
                upperenvelope[n - 2] = upperenvelope[n - 1];
                if (n == 2) {
                    // intersectPreviousAt[n-2] = 0; // doesn't change                    
                } else {
                    intersectPreviousAt[n - 2] = findIntersection(upperenvelope[n - 3], fle);
                }
                n--;
            }

            if (intersectPreviousAt[n - 1] > 1) {
                assert n > 1;
                n--;
            } else if (n > 1 && upperenvelope[n - 2].slope > 0) {
                n--;
            }
        }

        // upper envelope now contains the decreasing part starting at zero 
        // up to the first increasing function before 1 on the upper envelope (if any)

        double min;
        if (upperenvelope[n - 1].slope > 0) {
            if (n > 1) {
                // get height at intersection with previous
                min = upperenvelope[n - 1].slope * intersectPreviousAt[n - 1] + upperenvelope[n - 1].height;
            } else {
                // get height at 0
                min = upperenvelope[n - 1].height;
            }
        } else {
            // get height at 1            
            min = upperenvelope[n - 1].slope + upperenvelope[n - 1].height;
        }

        return min;
    }

    @Override
    public double findMinimum(double... constants) {
        // find min in range [0,1]

        //double min = findMinimumFullProcedure();
        double min = findMinimumTrimmedProcedure();

        for (double c : constants) {
            min = Math.max(min, c);
        }
        return min;
    }

    private double findIntersection(FacetListElement fle1, FacetListElement fle2) {
        // find x of intersection point (assume slopes are different)
        // y = fle1.slope * x + fle1.height
        // y = fle2.slope * x + fle1.height
        //
        // (fle1.slope - fle2.slope) * x = fle2.height - fle1.height
        // x = (fle2.height - fle1.height) / (fle1.slope - fle2.slope)

        double xint = (fle2.height - fle1.height) / (fle1.slope - fle2.slope);
        return xint;
    }

    @Override
    public void truncateLast() {
        int i = distfunc.getComplexity() - 1;
        while (i >= 0 && sortedfacets.get(i).slope > 0) {
            sortedfacets.get(i).clear();
            i--;
        }
    }
}
