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

public class MatchesData {
    
    private final Map<String, List<ChromosomeMatch>> keyedSet = new HashMap<>();
    
    public MatchesData(final File[] files) throws Exception {
        for (File file : files) {
            readMatchFile(file);
        }
    }

    private void processMatchLine(final Map<String, List<ChromosomeMatch>> keyedSet,
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
    
    private void readMatchFile(final File file) throws Exception {
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
                    processMatchLine(keyedSet, line.trim());
                }
            }
        } finally {
            is.close();
        }
    }

    public Collection<String> getMatchIDs() {
        return keyedSet.keySet();
    }
    
    public Collection<ChromosomeMatch> getMatches(final String matchID) {
        return keyedSet.get(matchID);
    }
}
