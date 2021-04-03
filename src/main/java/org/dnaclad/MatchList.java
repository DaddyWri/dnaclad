package org.dnaclad;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.BufferedReader;

public class MatchList {


    public static void main(final String[] argv) throws Exception {
        if (argv.length != 4) {
            System.err.println("Usage: List <chromosome_csv_file> <chromosome_id> <range_start> <range_end>");
            System.exit(-1);
        }

        final MatchesData matchesData = new MatchesData(new File[]{new File(argv[0])});
        
        final String chromosomeID = argv[1];
        final int startCm = Integer.parseInt(argv[2]);
        final int endCm = Integer.parseInt(argv[3]);

        final int minimumCM = 5000;
        
        final Lister lister = new Lister(startCm, endCm);
        for (final String matchID : matchesData.getMatchIDs()) {
            final Collection<ChromosomeMatch> matches = matchesData.getMatches(matchID);
            for (final ChromosomeMatch cm : matches) {
                if (chromosomeID != null && cm.chromosomeNumber.equals(chromosomeID) &&
                    cm.end - cm.start >= minimumCM) {
                    lister.addMatch(cm);
                }
            }
        }
        

        // Create an output engine
        final OutputEngine engine = new OutputEngine();
        lister.writeOutMatches(engine);
        engine.finishUp();
        
    }
    
    public static class OutputEngine implements Lister.MatchWriter {
        int matchCount = 0;
        
        public OutputEngine() {
        }
        
        @Override
        public void write(final ChromosomeMatch cm) {
            System.out.println(cm.matchID + " (" + cm.start + " - " + cm.end + ") [" + (cm.end - cm.start) + "]");
        }
        
        public void finishUp() {
            System.out.println("\n");
            System.out.println("Total number of matches = "+matchCount);
            System.out.println("\n");
        }
    }
}

