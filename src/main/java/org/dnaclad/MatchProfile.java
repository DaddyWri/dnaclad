package org.dnaclad;

import java.util.List;

/**
 */

public class MatchProfile {

    public final String matchUniqueID;
    public final boolean isMale;
    public final int minimumMatchDepth;
    public final List<? extends ChromosomeMatch> matches;

    public MatchProfile(final String matchUniqueID,
                        final boolean isMale,
                        final int minimumMatchDepth,
                        final List<? extends ChromosomeMatch> matches) {
        this.matchUniqueID = matchUniqueID;
        this.isMale = isMale;
        this.minimumMatchDepth = minimumMatchDepth;
        this.matches = matches;
    }

    @Override
    public int hashCode() {
        return matchUniqueID.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof MatchProfile)) {
            return false;
        }
        final MatchProfile other = (MatchProfile)o;
        return matchUniqueID.equals(other.matchUniqueID);
    }
}

