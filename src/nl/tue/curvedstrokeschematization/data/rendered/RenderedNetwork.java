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

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class RenderedNetwork {

    private List<RenderedArc> arcs;
    private List<RenderedStation> stations;

    public RenderedNetwork() {
        arcs = new ArrayList();
        stations = new ArrayList();
    }

    public List<RenderedArc> getArcs() {
        return arcs;
    }

    public List<RenderedStation> getStations() {
        return stations;
    }

    public void addArc(RenderedArc arc) {
        arcs.add(arc);
    }

    public void addStation(RenderedStation station) {
        stations.add(station);
    }
}
