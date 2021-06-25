/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization.gui;

import nl.tue.curvedstrokeschematization.algo.schematization.FullCircleArc;
import nl.tue.curvedstrokeschematization.algo.schematization.IterativeSchematization;
import nl.tue.curvedstrokeschematization.algo.store.SchematizationStore.ConnectionNode;
import nl.tue.curvedstrokeschematization.algo.store.SchematizationStore.StationNode;
import nl.tue.curvedstrokeschematization.data.metro.MetroConnection;
import nl.tue.curvedstrokeschematization.data.metro.MetroLine;
import nl.tue.curvedstrokeschematization.data.metro.MetroStation;
import nl.tue.curvedstrokeschematization.data.rendered.RenderedArc;
import nl.tue.curvedstrokeschematization.data.rendered.RenderedStation;
import nl.tue.curvedstrokeschematization.data.stroked.Stroke;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeArc;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeCross;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeVertex;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.curved.Circle;
import nl.tue.geometrycore.geometry.linear.LineSegment;
import nl.tue.geometrycore.geometry.linear.Rectangle;
import nl.tue.geometrycore.geometryrendering.GeometryPanel;
import nl.tue.geometrycore.geometryrendering.glyphs.PointStyle;
import nl.tue.geometrycore.geometryrendering.styling.Dashing;
import nl.tue.geometrycore.geometryrendering.styling.ExtendedColors;
import nl.tue.geometrycore.geometryrendering.styling.Hashures;
import nl.tue.geometrycore.geometryrendering.styling.SizeMode;
import nl.tue.geometrycore.geometryrendering.styling.TextAnchor;
import nl.tue.geometrycore.util.DoubleUtil;

/**
 *
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class DrawPanel extends GeometryPanel {

    private Data data;
    private Stroke hoover = null;

    public DrawPanel(Data data) {
        this.data = data;
    }

    @Override
    protected void mouseMove(Vector loc, int button, boolean ctrl, boolean shift, boolean alt) {
        if (data.schematization != null) {
            double threshold = convertViewToWorld(5);
            Stroke old = hoover;
            hoover = null;
            if (data.strokeIdentification) {
                for (StrokeArc arc : data.schematization.getArcs()) {
                    if (arc.toGeometry().distanceTo(loc) < threshold) {
                        hoover = arc.getStroke();
                        break;
                    }
                }
            }
            if (hoover != old) {
                repaint();
            }
        }
    }

    @Override
    protected void mousePress(Vector loc, int button, boolean ctrl, boolean shift, boolean alt) {
        if (alt) {
            if (button == MouseEvent.BUTTON1) {
                IterativeSchematization.debugregion = new Circle(loc, 0);
            } else {
                IterativeSchematization.debugregion = null;
            }
            repaint();
        } else if (shift) {
            if (button == MouseEvent.BUTTON1) {
                if (data.schematization != null) {
                    double threshold = convertViewToWorld(5);
                    for (StrokeVertex sv : data.schematization.getVertices()) {
                        if (sv.distanceTo(loc) < threshold) {
                            data.algorithm.testVertex(sv);
                            break;
                        }
                    }
                    for (Stroke s : data.schematization.getStrokes()) {
                        for (int i = 0; i < s.getArcCount(); i++) {
                            for (StrokeCross sc : s.getArc(i).getVirtuals()) {
                                if (sc.getCenterDisc().distanceTo(loc) < threshold) {
                                    data.algorithm.testVertex(sc);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (button == MouseEvent.BUTTON1) {
                if (hoover != null) {
                    System.out.println("Stroke:");
                    System.out.println("  circular: " + hoover.isCircular());
                    System.out.println("  arcs: " + hoover.getArcCount());
                    System.out.println("  vertices: ");
                    for (StrokeVertex sv : hoover.getVertices()) {
                        System.out.println("    " + sv.hashCode() + " - " + sv);
                    }
                }
            }
        }
    }

    @Override
    protected void mouseDrag(Vector loc, Vector prevloc, int button, boolean ctrl, boolean shift, boolean alt) {
        if (alt && button == MouseEvent.BUTTON1) {
            IterativeSchematization.debugregion.setRadius(loc.distanceTo(IterativeSchematization.debugregion.getCenter()));
            repaint();
        }
    }

    @Override
    protected void mouseRelease(Vector loc, int button, boolean ctrl, boolean shift, boolean alt) {
        if (alt && button == MouseEvent.BUTTON1) {
            IterativeSchematization.debugregion.setRadius(loc.distanceTo(IterativeSchematization.debugregion.getCenter()));
            if (IterativeSchematization.debugregion.getRadius() <= DoubleUtil.EPS) {
                IterativeSchematization.debugregion = null;
            }
            repaint();
        }
    }

    @Override
    protected void keyPress(int keycode, boolean ctrl, boolean shift, boolean alt) {
        switch (keycode) {
            case KeyEvent.VK_C:
                data.copyToIpe();
                break;
            case KeyEvent.VK_V:
                data.pasteIpeMetro();
                break;
        }
    }

    @Override
    public Rectangle getBoundingRectangle() {

        Rectangle bb = getInputBoundingBox();

        if (data.sidebyside) {
            bb.scale(4, 1, bb.leftBottom());
        }

        return bb;
    }

    public Rectangle getInputBoundingBox() {
        Rectangle bb = new Rectangle();

        if (data.input != null) {
            for (MetroStation ms : data.input.getStations()) {
                bb.include(ms);
            }
        }

        return bb;
    }

    private static Color[] colors = {
        ExtendedColors.darkBlue,
        ExtendedColors.darkOrange,
        ExtendedColors.darkGreen,
        ExtendedColors.darkRed,
        ExtendedColors.darkPurple,
        ExtendedColors.lightBlue,
        ExtendedColors.lightOrange,
        ExtendedColors.lightGreen,
        ExtendedColors.lightRed,
        ExtendedColors.lightPurple
    };

    @Override
    protected void drawScene() {

        double linewidth = 2;
        double linewidthEmph = 4;
        double radius = 5;
        double textsize = 10;
        double sidebyside_dx = getInputBoundingBox().width();

        setSizeMode(SizeMode.VIEW);

        if (data.input != null && (data.sidebyside || data.drawinput || (data.drawprogressive && data.schematization == null))) {

            setLayer("input");

            double delta = convertViewToWorld(linewidth);
            for (MetroConnection mc : data.input.getConnections()) {
                LineSegment ls = mc.toGeometry().clone();
                Vector normal = ls.getDirection();
                normal.rotate90DegreesClockwise();
                normal.scale(delta);
                Vector off = normal.clone();
                off.scale(-(mc.getLines().size() - 1) / 2.0);
                ls.translate(off);
                for (MetroLine ml : mc.getLines()) {
                    setStroke(ml.getColor(), linewidth, Dashing.SOLID);
                    draw(ls);
                    ls.translate(normal);
                }
            }

            if (data.drawVertices) {
                setPointStyle(PointStyle.CIRCLE_WHITE, radius);
                for (MetroStation ms : data.input.getStations()) {
                    setStroke(ms.getLines().size() > 1 ? Color.black : ms.getLines().get(0).getColor(), linewidth, Dashing.SOLID);
                    draw(ms);
                }
            }
        }

        if (data.schematization != null && (data.sidebyside || data.drawschematization || (data.drawprogressive && data.rendering == null))) {

            if (data.sidebyside) {
                pushMatrix(AffineTransform.getTranslateInstance(sidebyside_dx, 0));
            }

            setLayer("schematization");

            int color = 0;
            StrokeArc prev = null;
            for (StrokeArc arc : data.schematization.getArcs()) {
                if (prev == null || prev.getStroke() != arc.getStroke()) {
                    color++;
                }

                setStroke(data.drawcoloredstrokes ? colors[color % colors.length] : Color.black, hoover == arc.getStroke() ? linewidthEmph : linewidth, Dashing.SOLID);

                if (arc.toGeometry() instanceof FullCircleArc) {
                    Circle c = ((FullCircleArc) arc.toGeometry()).getCircle();
                    draw(c);
                } else {
                    draw(arc);
                }

                prev = arc;
            }

            if (data.drawVertices) {

                setPointStyle(PointStyle.CIRCLE_WHITE, radius);
                setStroke(Color.black, linewidth, Dashing.SOLID);
                for (StrokeVertex sv : data.schematization.getVertices()) {
                    draw(sv);
                }

                for (StrokeCross sc : data.schematization.getCrosses()) {
                    //                Circle C = SECcomputation.getSmallestEnclosingDisc(sc);
                    Circle C = sc.getSmallestDisc();
                    if (C != null && C.getRadius() > DoubleUtil.EPS) {
                        draw(C);
                    }
                }
            }

            if (data.sidebyside) {
                popMatrix();
            }
        }

        if (data.rendering != null && (data.sidebyside || data.drawrendering || data.drawprogressive)) {

            if (data.sidebyside) {
                pushMatrix(AffineTransform.getTranslateInstance(3 * sidebyside_dx, 0));
            }

            setLayer("rendering");

            for (RenderedArc arc : data.rendering.getArcs()) {
                setStroke(arc.getStrokeColor(), arc.getStrokewidth(), Dashing.SOLID);
                draw(arc);
            }

            for (RenderedStation station : data.rendering.getStations()) {
                setStroke(station.getStrokecolor(), station.getStrokewidth(), Dashing.SOLID);
                setFill(station.getFillcolor(), Hashures.SOLID);
                draw(station.toGeometry());
            }
            setFill(null, Hashures.SOLID);

            if (data.sidebyside) {
                popMatrix();
            }
        }

        if (data.query != null && (data.sidebyside || data.drawquery)) {

            if (data.sidebyside) {
                pushMatrix(AffineTransform.getTranslateInstance(2 * sidebyside_dx, 0));
            }

            setStroke(Color.black, linewidth, Dashing.SOLID);
            for (ConnectionNode cn : data.query.connections) {
                if (cn.getArc() instanceof FullCircleArc) {
                    Circle c = ((FullCircleArc) cn.getArc()).getCircle();
                    draw(c);
                } else {
                    draw(cn.getArc());
                }
            }

            if (data.drawVertices) {
                setPointStyle(PointStyle.CIRCLE_WHITE, radius);
                setStroke(Color.black, linewidth, Dashing.SOLID);
                for (StationNode sn : data.query.stations) {
                    draw(sn.getMindisk().getCenter());
                }
            }

            if (data.sidebyside) {
                popMatrix();
            }
        }

        if (data.drawcrossdist) {
            Rectangle bb = getInputBoundingBox();
            if (bb.diagonal() > 0) {
                double dist = bb.diagonal() * data.crossdist;

                Vector v = Vector.subtract(bb.rightTop(), bb.leftBottom());
                v.normalize();
                v.scale(dist);

                LineSegment LS = LineSegment.byStartAndOffset(bb.leftBottom(), v);
                setStroke(Color.orange, linewidth, Dashing.SOLID);
                draw(LS);
            }
        }

        if (IterativeSchematization.debugregion != null) {
            setStroke(Color.red, linewidth / 2, Dashing.SOLID);
            draw(IterativeSchematization.debugregion);
        }

        if (data.algorithm != null) {
            setStroke(Color.black, linewidth, Dashing.SOLID);
            setTextStyle(TextAnchor.TOP_LEFT, textsize);
            draw(convertViewToWorld(getView().leftTop()), "# Arcs: " + data.algorithm.getComplexity());
        }
    }

}
