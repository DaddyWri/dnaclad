package org.dnaclad;

public class ChromosomeMatch implements Comparable<ChromosomeMatch> {
    public final String matchID;
    public final String chromosomeNumber;
    public final int start;
    public final int end;
    public final double centimorgans;
    public final int SNPs;

    public ChromosomeMatch(final String matchID,
                           final String chromosomeNumber,
                           final int start,
                           final int end,
                           final double centimorgans,
                           final int SNPs) {
        this.matchID = matchID;
        this.chromosomeNumber = chromosomeNumber;
        this.start = start;
        this.end = end;
        this.centimorgans = centimorgans;
        this.SNPs = SNPs;
    }
    
    @Override
    public int compareTo(final ChromosomeMatch other) {
        final int size = end-start;
        final int otherSize = other.end-other.start;
        if (size > otherSize) {
            return -1;
        }
        if (size < otherSize) {
            return 1;
        }
        return 0;
    }
    
}
