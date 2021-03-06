package org.dnaclad;

public class ChromosomeMatch {
    public final String chromosomeNumber;
    public final int start;
    public final int end;
    public final double centimorgans;
    public final int SNPs;

    public ChromosomeMatch(final String chromosomeNumber,
                           final int start,
                           final int end,
                           final double centimorgans,
                           final int SNPs) {
        this.chromosomeNumber = chromosomeNumber;
        this.start = start;
        this.end = end;
        this.centimorgans = centimorgans;
        this.SNPs = SNPs;
    }
}
