/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization;

import nl.tue.curvedstrokeschematization.algo.frechetdistance.implementations.polyhedral.PolyhedralFrechetDistance;
import nl.tue.curvedstrokeschematization.algo.frechetdistance.util.PolyhedralDistanceFunction;
import nl.tue.curvedstrokeschematization.algo.NetworkConstruction;
import nl.tue.curvedstrokeschematization.algo.schematization.IterativeSchematization;
import nl.tue.curvedstrokeschematization.data.metro.MetroNetwork;
import nl.tue.curvedstrokeschematization.data.stroked.StrokeNetwork;
import nl.tue.curvedstrokeschematization.io.WktIO;
import java.io.File;

/**
 *
 * @author Wouter Meulemans (w.meulemans@tue.nl)
 */
public class HeadlessMode {

    public static void main(String[] args) {

        // arguments (NB: include the dash!)
        // -in S  -> input filepath S
        // -out S -> output filepath S
        // -f D   -> set frechet threshold to D (default = infinity)
        // -c I   -> set complexity threshold to I (default = 0)
        // -a I   -> set number of angles to test to I (default = 41)
        // -s D   -> set fraction of Frechet distance used for straight replacements (default = 1.0)
        // -eps D -> set the approximation factor of the Frechet distance computation (default = 1.01)
        // -cd D  -> set crossing distance, as a factor of the bounding box diagonal (default = 0.0075)
        // -kpv B -> set keeping planarization vertices in the output (true/false; default = false)
        File inputfile = new File(findArgument(args, "-in", null));
        File outputfile = new File(findArgument(args, "-out", null));
        double frechet = findArgument(args, "-f", Double.POSITIVE_INFINITY);
        int complexity = findArgument(args, "-c", 0);
        int angles = findArgument(args, "-a", 41);
        int numCandidates = findArgument(args, "-nc", 3);
        double straightreduc = findArgument(args, "-s", 1.0);
        double eps = findArgument(args, "-eps", 1.01);
        double crossdist = findArgument(args, "-cd", 0.0075);
        boolean keepPlanarizationVertices = findArgument(args, "-kpv", false);
        
        String settingsPrint = "\nRUNNING:\n"
                +"\n  in:  "+inputfile.getAbsolutePath()
                +"\n  out: "+outputfile.getAbsolutePath()
                +"\n  f:   "+frechet
                +"\n  c:   "+complexity
                +"\n  a:   "+angles
                +"\n  nc:  "+numCandidates
                +"\n  s:   "+straightreduc
                +"\n  eps: "+eps
                +"\n  cd:  "+crossdist
                +"\n  kpv: "+keepPlanarizationVertices;
        System.out.println(settingsPrint);
        System.err.println(settingsPrint);

        // read
        MetroNetwork network = WktIO.loadFile(inputfile);

        // make strokes
        StrokeNetwork stroked = NetworkConstruction.construct(network, false);
        NetworkConstruction.mergeStrokesAngle(stroked);

        // simplify
        IterativeSchematization algorithm = new IterativeSchematization(
                // high degree
                true,
                // no store
                false,
                crossdist,
                angles,
                numCandidates,
                straightreduc,
                new PolyhedralFrechetDistance(PolyhedralDistanceFunction.epsApproximation2D(eps)));
        algorithm.init(stroked);

        while (algorithm.performStep(complexity, frechet)) {
            // step
        }

//        // render
//        Renderer R = new Renderer();
//        RenderedNetwork render = R.render(stroked,
//                // line settings
//                0.1, 0.1,
//                // station settings
//                Renderer.InterchangeStyle.SMALLEST_ENCLOSING_DISK, true, 0.1, 0.1, true);

        // save
        WktIO.saveFile(outputfile, network, stroked, keepPlanarizationVertices);
    }

    public static boolean findArgument(String[] args, String label, boolean deft) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(label)) {
                return Boolean.parseBoolean(args[i + 1]);
            }
        }
        return deft;
    }

    public static double findArgument(String[] args, String label, double deft) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(label)) {
                return Double.parseDouble(args[i + 1]);
            }
        }
        return deft;
    }

    public static int findArgument(String[] args, String label, int deft) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(label)) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        return deft;
    }

    public static String findArgument(String[] args, String label, String deft) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(label)) {
                return args[i + 1];
            }
        }
        return deft;
    }
}
