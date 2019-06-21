package org.dnaclad;

import java.util.Map;
import java.util.HashMap;

/**
 * An assignment represents a linkage between a node in a matched profile and the corresponding
 * node in the main profile.
 * But, while a single Path is enough to describe the particular ancestor in question in the match profile,
 * in the main profile we may have multiple assignments to the same node, across multiple match profiles.
 * Therefore, paths related to the main profile include a set of match paths.
 *
 * Note also that the MatchProfile Path in question determines what part of the ancestor pair is linked
 * to this node; it can be the male, the female, or both. 
 */ 
public class Assignment {

    public final int numGenerations;
    public final Map<MatchProfile, Path> mappedPaths = new HashMap<>();
    public final boolean maleChild;
    public final Assignment root;

    /**
     * Create an assignment which initially has no links to it.  The assignment is the given number of
     * generations above the root Assignment, which might be null (indicating the main profile).  For
     * the immediate parents of the root node, the number would be zero.
     * The root represents both a male and female.  Use the maleChild parameter
     * to indicate which of these is the descendant of this assignment.
     */
    public Assignment(final int numGenerations,
                      final boolean maleChild,
                      final Assignment root) {
        this.numGenerations = numGenerations;
        this.maleChild = maleChild;
        this.root = root;
    }
}
