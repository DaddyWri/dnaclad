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

public class Run {

    private static void processLine(final Map<String, List<ChromosomeMatch>> keyedSet,
                                    final String line) throws Exception {
        final String[] columns = line.split(",");
        if (columns.length != 7) {
            throw new Exception("Bad line: '"+line+"'");
        }
        final String matchID = columns[1];
        final String chromosomeNumber = columns[2];
        final int start = Integer.parseInt(columns[3]);
        final int end = Integer.parseInt(columns[4]);
        final double centimorgans = new Double(columns[5]);
        final int SNPs = Integer.parseInt(columns[6]);

        final ChromosomeMatch cm = new ChromosomeMatch(chromosomeNumber, start, end, centimorgans, SNPs);
        List<ChromosomeMatch> cl = keyedSet.get(matchID);
        if (cl == null) {
            cl = new ArrayList<>();
            // Minimum match level to be pulled from other file in the future
            keyedSet.put(matchID, cl);
        }
        cl.add(cm);
    }
    
    private static List<MatchProfile> readMatchFile(final File file) throws Exception {
        final Map<String, List<ChromosomeMatch>> keyedSet = new HashMap<>();
        final InputStream is = new FileInputStream(file);
        try {
            final Reader r = new InputStreamReader(is, "utf-8");
            final BufferedReader br = new BufferedReader(r);
            boolean firstLine = true;
            while (true) {
                final String line = br.readLine();
                if (line == null) {
                    break;
                }
                if (firstLine) {
                    firstLine = false;
                } else {
                    processLine(keyedSet, line.trim());
                }
            }
        } finally {
            is.close();
        }

        // Convert to set of MatchProfiles
        final List<MatchProfile> profiles = new ArrayList<>();
        for (final String matchID : keyedSet.keySet()) {
            final List<ChromosomeMatch> cm = keyedSet.get(matchID);
            // Match depth set to 2 for now
            // Sex is male for now
            profiles.add(new MatchProfile(matchID, true, 2, cm));
        }

        return profiles;
    }
    
    public static void main(final String[] argv) throws Exception {
        if (argv.length != 1) {
            System.err.println("Usage: Run <chromosome_csv_file>");
            System.exit(-1);
        }

        final Collection<MatchProfile> matches = readMatchFile(new File(argv[0]));

        final TreeIterator treeIterator = new TreeIterator(matches, 10, 2);
        while (true) {
            final ReducedTree rt = treeIterator.generateNext();
            if (rt == null) {
                break;
            }
        }
        
    }
}

