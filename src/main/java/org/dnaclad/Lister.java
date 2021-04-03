package org.dnaclad;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.PriorityQueue;
import java.util.List;
import java.util.ArrayList;

public class Lister {
    
    private final int startCm;
    private final int endCm;
    private final PriorityQueue<ChromosomeMatch> matches = new PriorityQueue<>();
    
    public Lister(int startCm, int endCm) {
        this.startCm = startCm;
        this.endCm = endCm;
    }
    
    public void addMatch(final ChromosomeMatch match) {
        // Does it overlap?
        if (match.start > endCm || match.end < startCm) {
            return;
        }
        matches.add(match);
    }

    public void writeOutMatches(final MatchWriter out) {
        // Output organized by length. 
        while (true) {
            ChromosomeMatch longest = matches.poll();
            if (longest == null) {
                break;
            }
            out.write(longest);
        }
    }
    
    public static interface MatchWriter {
        public void write(ChromosomeMatch match);
    }

}