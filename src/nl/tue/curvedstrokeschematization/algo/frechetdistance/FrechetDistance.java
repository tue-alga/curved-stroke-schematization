/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization.algo.frechetdistance;

import nl.tue.curvedstrokeschematization.algo.frechetdistance.envelope.UpperEnvelope;
import nl.tue.geometrycore.datastructures.doublylinkedlist.DoublyLinkedList;
import nl.tue.geometrycore.datastructures.doublylinkedlist.DoublyLinkedListItem;
import nl.tue.geometrycore.geometry.Vector;

/**
 * Generic class for the computation of the Frechet distance.
 * Implements the algorithm framework described in https://doi.org/10.1007/s00454-016-9800-8
 * Abstracts from the upper envelope, which is distance-measure specific.
 *
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public abstract class FrechetDistance {

    protected int N, M;
    protected Vector[] P, Q;

    public double computeDistance(Vector[] P, Vector[] Q) {

        this.P = P;
        this.Q = Q;

        this.N = P.length - 1;
        this.M = Q.length - 1;

        double dist = compute();

        this.P = null;
        this.Q = null;

        return dist;
    }

    private double compute() {

        DIntList[] column_queues = new DIntList[N];
        UpperEnvelope[] column_envelopes = new UpperEnvelope[N];
        for (int i = 0; i < N; i++) {
            column_queues[i] = new DIntList();
            column_envelopes[i] = initializeColumnUpperEnvelope(i);
        }

        DIntList[] row_queues = new DIntList[M];
        UpperEnvelope[] row_envelopes = new UpperEnvelope[M];
        for (int j = 0; j < M; j++) {
            row_queues[j] = new DIntList();
            row_envelopes[j] = initializeRowUpperEnvelope(j);
        }

        double[][] L_opt = new double[N][M];
        L_opt[0][0] = distance(P[0],Q[0]);
        for (int j = 1; j < M; j++) {
            L_opt[0][j] = Double.POSITIVE_INFINITY;
        }

        double[][] B_opt = new double[N][M];
        B_opt[0][0] = L_opt[0][0];
        for (int i = 1; i < N; i++) {
            B_opt[i][0] = Double.POSITIVE_INFINITY;
        }

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < M; j++) {

                //System.out.println("Computing cell [" + i + "," + j + "]");
                if (i < N - 1) {
                    // compute L_opt[i+1][j]
                    //System.out.println("  L_opt[i+1][j]");

                    DIntList queue = row_queues[j];
                    UpperEnvelope upperenv = row_envelopes[j];

                    while (queue.size() > 0 && B_opt[queue.getLast().value][j] > B_opt[i][j]) {
                        queue.removeLast();
                    }
                    queue.addLast(new DInt(i));

                    if (queue.size() == 1) {
                        upperenv.clear();
                    }

                    upperenv.add(i + 1, Q[j], Q[j + 1], P[i + 1]);

                    int h = queue.getFirst().value;
                    double min = h < i ? upperenv.findMinimum(L_opt[i][j], B_opt[h][j]) : upperenv.findMinimum(B_opt[h][j]);

                    while (queue.size() > 1 && B_opt[queue.getFirst().getNext().value][j] <= min) {
                        queue.removeFirst();

                        h = queue.getFirst().value;
                        assert h <= i;
                        upperenv.removeUpto(h);

                        min = h < i ? upperenv.findMinimum(L_opt[i][j], B_opt[h][j]) : upperenv.findMinimum(B_opt[h][j]);
                    }

                    L_opt[i + 1][j] = min;
                    upperenv.truncateLast();
                }

                if (j < M - 1) {
                    // compute B_opt[i][j+1]
                    //System.out.println("  B_opt[i][j+1]");

                    DIntList queue = column_queues[i];
                    UpperEnvelope upperenv = column_envelopes[i];

                    while (queue.size() > 0 && L_opt[i][queue.getLast().value] >= L_opt[i][j]) {
                        queue.removeLast();
                    }
                    queue.addLast(new DInt(j));

                    if (queue.size() == 1) {
                        upperenv.clear();
                    }

                    upperenv.add(j + 1, P[i], P[i + 1], Q[j + 1]);

                    int h = queue.getFirst().value;
                    double min = h < j ? upperenv.findMinimum(B_opt[i][j], L_opt[i][h]) : upperenv.findMinimum(L_opt[i][h]);

                    while (queue.size() > 1 && L_opt[i][queue.getFirst().getNext().value] <= min) {
                        queue.removeFirst();

                        h = queue.getFirst().value;
                        assert h <= j;
                        upperenv.removeUpto(h);

                        min = h < j ? upperenv.findMinimum(B_opt[i][j], L_opt[i][h]) : upperenv.findMinimum(L_opt[i][h]);
                    }

                    B_opt[i][j + 1] = min;
                    upperenv.truncateLast();
                }
            }
        }

        return Math.max(distance(P[N],Q[M]), Math.min(L_opt[N - 1][M - 1], B_opt[N - 1][M - 1]));
    }

    public abstract double distance(Vector p, Vector q);

    protected abstract UpperEnvelope initializeRowUpperEnvelope(int row);

    protected abstract UpperEnvelope initializeColumnUpperEnvelope(int column);

    private class DIntList extends DoublyLinkedList<DInt> {
        
    }
    
    private class DInt extends DoublyLinkedListItem<DInt> {

        int value;

        public DInt(int value) {
            this.value = value;
        }
    }
}
