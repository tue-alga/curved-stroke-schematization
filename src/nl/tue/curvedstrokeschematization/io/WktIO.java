/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization.io;

import nl.tue.curvedstrokeschematization.data.metro.MetroConnection;
import nl.tue.curvedstrokeschematization.data.metro.MetroLine;
import nl.tue.curvedstrokeschematization.data.metro.MetroNetwork;
import nl.tue.curvedstrokeschematization.data.metro.MetroStation;
import nl.tue.curvedstrokeschematization.data.rendered.RenderedArc;
import nl.tue.curvedstrokeschematization.data.rendered.RenderedNetwork;
import nl.tue.curvedstrokeschematization.data.rendered.RenderedStation;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeArc;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeCross;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeNetwork;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeVertex;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.curved.CircularArc;
import nl.tue.geometrycore.geometry.linear.Rectangle;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class WktIO {

    public static MetroNetwork loadFile(File file) {
        try (BufferedReader read = new BufferedReader(new FileReader(file))) {

            MetroNetwork network = new MetroNetwork();

            int nVertices = Integer.parseInt(read.readLine().split(" ")[1]);
            MetroStation[] stations = new MetroStation[nVertices];
            for (int i = 0; i < nVertices; i++) {
                String[] xy = read.readLine().split(" ");
                Vector v = new Vector(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]));
                stations[i] = network.addStation(v, "S" + i, "S" + i, false);
            }

            int nEdges = Integer.parseInt(read.readLine().split(" ")[1]);
            for (int i = 0; i < nEdges; i++) {
                String[] stxy = read.readLine().split(" ");
                MetroLine ml = network.addLine("E" + i, "E" + i, Color.black);
                int s = Integer.parseInt(stxy[0]);
                int t = Integer.parseInt(stxy[1]);
                if (stxy.length > 2) {
                    System.err.println("Warning: ignoring center on edge number " + i);
                }
                network.addToConnection(s + " " + t, stations[s], stations[t], ml);
            }

            return network;
        } catch (IOException ex) {
            Logger.getLogger(IpeIO.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public static void saveFile(File file, MetroNetwork input, StrokeNetwork output, boolean keepPlanarizationVertices) {
        try (BufferedWriter write = new BufferedWriter(new FileWriter(file))) {

            List<MetroStation> stations = new ArrayList();
            Map<String, Vector> locations = new HashMap(); // metrostation-id
            List<String> connectionStrings = new ArrayList();

            for (MetroStation ms : input.getStations()) {
                if (keepPlanarizationVertices || !ms.isPlanarizationStation()) {
                    stations.add(ms);
                }
            }
            stations.sort((a, b) -> Integer.compare(
                    a.getIndex(),
                    b.getIndex()));

            for (StrokeCross sc : output.getCrosses()) {
                MetroStation ms = sc.getOriginal();
                if (keepPlanarizationVertices || !ms.isPlanarizationStation()) {
                    locations.put(ms.getId(), sc.getCenterDisc());
                }
            }

            for (StrokeVertex sv : output.getVertices()) {
                if (sv.isCross()) {
                    continue;
                }
                MetroStation ms = sv.getOriginal();
                if (keepPlanarizationVertices || !ms.isPlanarizationStation()) {
                    locations.put(ms.getId(), sv);
                }
            }

            for (StrokeArc arc : output.getArcs()) {

                boolean ccw = !arc.isClockwise();

                if (arc.getStart().getOriginal().isPlanarizationStation() || arc.getEnd().getOriginal().isPlanarizationStation()) {
                    System.err.println("planarization vertex not removed??");
                }

                List<MetroConnection> edges = arc.getOriginaledges();

                Vector center = arc.getCenter();
                Vector startLoc = locations.get(arc.getStart().getOriginal().getId());
                MetroStation prev = arc.getStart().getOriginal();

                int i = 0;
                while (i < edges.size()) {
                    List<MetroStation> toPlace = new ArrayList();
                    MetroStation end = null;
                    while (true) {
                        if (i == edges.size() - 1) {
                            // reached the end
                            end = arc.getEnd().getOriginal();
                            i++;
                            break;
                        }
                        MetroStation common = edges.get(i).getSharedStation(edges.get(i + 1));
                        if (common.getConnections().size() > 2) {
                            // must be virtual
                            end = common;
                            i++;
                            break;
                        } else {
                            // cannot be planarization vertex
                            if (common.isPlanarizationStation()) {
                                System.err.println("Degree-2 planarization vertex??");
                            }
                            toPlace.add(common);
                            i++;
                        }
                    }

                    Vector endLoc = locations.get(end.getId());
                    if (endLoc == null) {
                        // find virtual location
                        for (StrokeCross sc : arc.getVirtuals()) {
                            if (sc.getOriginal() == end) {
                                endLoc = sc.getCenterDisc();
                                break;
                            }
                        }
                        if (endLoc == null) {
                            System.err.println("Couldnt find virtual?");
                        }
                    }

                    CircularArc arcpiece = new CircularArc(center, startLoc, endLoc, ccw);
                    int index = 1;
                    double size = toPlace.size() + 1;
                    for (MetroStation ms : toPlace) {
                        Vector v = arcpiece.getPointAt(index / size);
                        index++;
                        locations.put(ms.getId(), v);

                        String s = prev.getIndex() + " " + ms.getIndex();
                        if (center != null) {
                            s += " " + center.getX() + " " + center.getY() + " " + (ccw ? "1" : "0");
                        }
                        connectionStrings.add(s);

                        prev = ms;
                    }

                    startLoc = endLoc;
                    if (keepPlanarizationVertices || !end.isPlanarizationStation()) {
                        String s = prev.getIndex() + " " + end.getIndex();
                        if (center != null) {
                            s += " " + center.getX() + " " + center.getY() + " " + (ccw ? "1" : "0");
                        }
                        connectionStrings.add(s);
                        prev = end;
                    }
                }

            }

            write.write("# " + stations.size());
            write.newLine();

            for (MetroStation ms : stations) {

                Vector v = locations.get(ms.getId());
                write.write(v.getX() + " " + v.getY());
                write.newLine();
            }

            write.write("# " + connectionStrings.size());
            write.newLine();

            for (String s : connectionStrings) {
                write.write(s);
                write.newLine();
            }

        } catch (IOException ex) {
            Logger.getLogger(IpeIO.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void saveFile(File file, RenderedNetwork network) {
        try (BufferedWriter write = new BufferedWriter(new FileWriter(file))) {

            write.write("# " + network.getStations().size());
            write.newLine();

            network.getStations().sort((a, b) -> Integer.compare(
                    a.getOriginal().getIndex(),
                    b.getOriginal().getIndex()));

            int i = 0;
            for (RenderedStation station : network.getStations()) {
                assert station.getOriginal().getId().equals("" + i);
                i++;
                Vector v = Rectangle.byBoundingBox(station.toGeometry()).center();
                write.write(v.getX() + " " + v.getY());
                write.newLine();
            }

            write.write("# " + network.getArcs().size());
            write.newLine();

            network.getArcs().sort((a, b) -> {
                int c = Integer.compare(
                        Integer.parseInt(a.getOriginal().getBeginStation().getId()),
                        Integer.parseInt(b.getOriginal().getBeginStation().getId()));
                if (c == 0) {
                    c = Integer.compare(
                            Integer.parseInt(a.getOriginal().getEndStation().getId()),
                            Integer.parseInt(b.getOriginal().getEndStation().getId()));
                }
                return c;
            });

            for (RenderedArc rarc : network.getArcs()) {

                CircularArc arc = rarc.toGeometry();

                write.write(rarc.getOriginal().getId());
                if (arc.getCenter() != null) {
                    Vector v = arc.getPointAt(0.5);
                    write.write(" " + v.getX() + " " + v.getY());
                }

                write.newLine();
            }

        } catch (IOException ex) {
            Logger.getLogger(IpeIO.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
