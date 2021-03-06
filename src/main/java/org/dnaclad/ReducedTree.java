package org.dnaclad;

import java.util.Collection;

/**
 * DNA Cladistics is designed to hypthesize reasonable trees between autosomal DNA matches, as
 * returned by testing outfits like FamilyTreeDNA.  The idea is that you present your set of matches
 * to the DNA Cladistics package, and it comes up with a ranked list of ordered "trees", where each tree
 * includes you and all of your matches, with ancestors in common.  The ancestors are described
 * positionally, not by name, since existing trees for matches are both error prone and not always available.
 *
 * The basic way the software functions is as follows:
 * (1) All possible trees are looked at
 * (2) For each tree, a simulation is run to evaluate the tree's level of match against objective DNA reality
 * (3) The trees are ranked in order from best to worst, and the top N are returned
 *
 * A key data structure in this exercise is the way a tree is represented.  The representation is a
 * deliberate and careful simplification designed to collapse together a multitude of different but functionally
 * identical actual inheritance trees down so that there is exactly one representative for any give set of
 * functionally identical trees.
 *
 * Visualize the person using the program (the "main profile") as having some depth of direct ancestors that we
 * care about (e.g. 10 levels).  Each match for that person ("match profile") also has a similar individual tree.
 * What we are interested in, though, are "assignments", where an ancestor of the main profile is deemed to be the
 * same as an ancestor of the match profile.  Indeed, we say further than an overall unique "tree" is precisely
 * described by the specific assignments made.
 *
 * Some assignments are clearly implied; when you create an assignment between two different ancestors, all
 * ancestors of the assigned ancestors must by definition be assigned as well.
 *
 * The key to making all this work is therefore a tree representation that glosses over impertinent details.
 * By making the tree be nothing more than a set of matches and a set of assignments per match, we neatly
 * accomplish this goal.
 *
 * If only one assignment is possible between a match profile and the main profile, then a single assignment
 * can be described this way:
 * - Whether it's male or female
 * - The number of generations back to the profile
 * If multiple assignments are possible, however, the assignments become more complex and describe relationships
 * to other assignments OR back to the profile.  But since we are not permitting anything other than straight
 * trees, it is possible to represent even multiple assignments as simple chains of assignment. So each assignment
 * has:
 * (1) the ID of the match profile that has the shared ancestor
 * (2) the path to the root of the match profile for the match ancestor
 * (3) the path to either the root of the main profile, or to another assignment, for the main profile
 *
 * Note that assignments in the same chain can go to different match profiles; that is not only allowed but
 * critically important in the multi-assignment world.
 *
 * Choosing a set of valid assignments between the main profile and a match profile requires both hierarchy constraints
 * and logic that is based on the closeness of the match.  This topic is likely to be where all the main optimization
 * work is done for this package.  The tree representation does not contain this business logic.
 */

public class ReducedTree {
    public final Collection<? extends Assignment> matchAssignments;
    public final Collection<? extends MatchProfile> matches;

    public ReducedTree(final Collection<? extends Assignment> assignments,
                       final Collection<? extends MatchProfile> matches) {
        this.matchAssignments = assignments;
        this.matches = matches;
    }

}

