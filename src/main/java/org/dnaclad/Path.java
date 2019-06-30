package org.dnaclad;

/**
 * A Path object represents a section of path between either the root profile,
 * or another path section.  In this way, pieces of the full inheritance hierarchy
 * can be minimally described, while still providing desired linkages between match profiles
 * and the main profile tree.
 *
 * The terminus of a path is either a pair of parents, or just one or the other
 * parent.  Paths can only be extended if they do not contain both parents -- it is
 * always the unincluded parent(s) that is/are extended in that case.
 */
public class Path {
    /** Match profile */
    public final MatchProfile matchProfile;
    /** Number of intervening generations */
    public final int interveningGens;
    /** True if includes male parent */
    public final boolean includesMale;
    /** True if includes female parent */
    public final boolean includesFemale;
    /** True if the derivedFrom child is male, false if female */
    public final boolean isMaleChild;
    /** Either another path, or null meaning the root of the profile */
    public final Path derivedFrom;

    public Path(final MatchProfile matchProfile,
                final boolean includesMale,
                final boolean includesFemale,
                final int interveningGens,
                final boolean isMaleChild,
                final Path derivedFrom) {
        this.matchProfile = matchProfile;
        this.interveningGens = interveningGens;
        this.includesMale = includesMale;
        this.includesFemale = includesFemale;
        this.isMaleChild = isMaleChild;
        this.derivedFrom = derivedFrom;
    }

    @Override
    public int hashCode() {
        return matchProfile.hashCode() + (includesMale?19:0) + (includesFemale?31:0) + Integer.hashCode(interveningGens) + (isMaleChild?47:0) + ((derivedFrom != null)?derivedFrom.hashCode():0);
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof Path)) {
            return false;
        }
        final Path other = (Path)o;
        return matchProfile.equals(other.matchProfile) &&
            includesMale == other.includesMale &&
            includesFemale == other.includesFemale &&
            interveningGens == other.interveningGens &&
            isMaleChild == other.isMaleChild &&
            ((derivedFrom == null || other.derivedFrom == null)?derivedFrom == other.derivedFrom:derivedFrom.equals(other.derivedFrom));
    }
    
    @Override
    public String toString() {
        return matchProfile.toString() + ":" + (includesMale?"M":"") + (includesFemale?"F":"") + interveningGens + (isMaleChild?"M":"F") + ((derivedFrom != null)?":"+derivedFrom:"");
    }
}
