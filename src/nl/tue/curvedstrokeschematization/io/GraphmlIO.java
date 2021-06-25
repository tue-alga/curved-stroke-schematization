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

import nl.tue.curvedstrokeschematization.data.metro.MetroNetwork;
import nl.tue.curvedstrokeschematization.data.metro.MetroLine;
import nl.tue.curvedstrokeschematization.data.metro.MetroStation;
import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.linear.Rectangle;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 */
public class GraphmlIO {

    public static MetroNetwork loadFile(File file) {
        MetroNetwork graph = new MetroNetwork();
        Rectangle bb = new Rectangle();

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(file);
            doc.getDocumentElement().normalize();
            System.out.println("Root element " + doc.getDocumentElement().getNodeName());

            //get all vertices
            NodeList nodeLst = doc.getElementsByTagName("node");
            System.out.println("Information of all nodes (" + nodeLst.getLength() + ")");

            for (int s = 0; s < nodeLst.getLength(); s++) {
                Node curNode = nodeLst.item(s);
                if (curNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element curElement = (Element) curNode;
                    String id = curElement.getAttribute("id");

                    NodeList nodeData = curElement.getElementsByTagName("data");

                    double x = 0;
                    double y = 0;
                    String label = "";
                    for (int i = 0; i < nodeData.getLength(); i++) {
                        Element data = (Element) nodeData.item(i);
                        Node value = data.getChildNodes().item(0);
                        String key = data.getAttribute("key");

                        if (key.equals("x")) {
                            x = Double.parseDouble(value.getNodeValue());
                        } else if (key.equals("y")) {
                            y = Double.parseDouble(value.getNodeValue());
                        } else if (key.equals("label")) {
                            label = value.getNodeValue();
                        }
                    }

                    graph.addStation(new Vector(x, y), id, label, false);
                    bb.include(x, y);
                }
            }

            //get all lines
            nodeLst = doc.getElementsByTagName("key");

            for (int s = 0; s < nodeLst.getLength(); s++) {
                Node curNode = nodeLst.item(s);
                if (curNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element curElement = (Element) curNode;
                    if (!curElement.getAttribute("for").equals("edge")) {
                        continue;
                    }

                    String id = curElement.getAttribute("id");
                    String label = curElement.getAttribute("attr.name");
                    int r = Integer.parseInt(curElement.getAttribute("color.r"));
                    int b = Integer.parseInt(curElement.getAttribute("color.b"));
                    int g = Integer.parseInt(curElement.getAttribute("color.g"));
                    Color c = new Color(r, g, b);
                    graph.addLine(id, label, c);

                }
            }

//          //get all edges
            nodeLst = doc.getElementsByTagName("edge");
            System.out.println("Information of all edges (" + nodeLst.getLength() + ")");
            for (int s = 0; s < nodeLst.getLength(); s++) {
                Node curNode = nodeLst.item(s);
                if (curNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element curElement = (Element) curNode;
                    String id = curElement.getAttribute("id");
                    MetroStation start = graph.getStation(curElement.getAttribute("source"));
                    MetroStation end = graph.getStation(curElement.getAttribute("target"));

                    NodeList nodeData = curElement.getElementsByTagName("data");

                    ArrayList<MetroLine> lines = new ArrayList<MetroLine>();
                    for (int i = 0; i < nodeData.getLength(); i++) {
                        Element data = (Element) nodeData.item(i);
                        Node value = data.getChildNodes().item(0);

                        if (value.getNodeValue().toLowerCase().equals("true")) {
                            String key = data.getAttribute("key");
                            lines.add(graph.getLine(key));
                        }
                    }

                    graph.addConnection(id, start, end, lines);
                    bb.include(start);
                    bb.include(end);
                }
            }
            graph.finish();
        } catch (Exception e) {
            e.printStackTrace();
        }
        double scale = Math.min(defaultpagewidth / bb.width(), defaultpageheight / bb.height());
        graph.scale(scale, bb.center());
        return graph;
    }
    private static final double defaultpagewidth = 3000; //nice and large
    private static final double defaultpageheight = 4000;
}
