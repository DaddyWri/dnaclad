package org.dnaclad;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

/**
 * This class iterates over all possible trees given a list of match profiles.
 * The basic algorithm involves allowing up to N assignments between the main profile
 * and each of the match profiles.  Certain assignments are disallowed; for example,
 * no assignment that is the ancestor of another assignment to the same match profile
 * is allowed.  Also, male-male assignments and female-female assignments are enforced,
 * and cross-generational assignments are restricted by reproductive date ranges.
 * For the latter, a simple model that prohibits reproduction under age 18 for both
 * men and women, and over 48 for women, is used.  Date ranges for ancestors are computed
 * using the same methodology, which therefore allows more intergenerational assignments
 * the further away the generation is to modern day.
 *
 * This implementation keeps an iteration state for each match profile.  This state can be
 * "advanced" to the next state, which is described in terms of the constraints required.
 * The advancement logic will be "smart" and prevent mindless iteration.
 *
 * The main profile side of each assignment also has constraints, but it is possible for
 * main profile assignments to have paths that go through many assignments.  How we iterate
 * over all of the possible assignments there is still to be worked out.
 */

public class TreeIterator {

    final List<MatchIterator> matchIterators;
    
    public TreeIterator(final Collection<? extends MatchProfile> matches,
                        final int maxDepth,
                        final int maxAssignmentsPerMatch) {
        matchIterators = new ArrayList<>(matches.size());
        for (final MatchProfile mp : matches) {
            matchIterators.add(new MatchIterator(mp, maxDepth, maxAssignmentsPerMatch));
        }
        // Reset all
        for (final MatchIterator mi : matchIterators) {
            mi.reset();
        }
    }

    /**
     * Retrieve next ReducedTree, or null if there are no more.
     */
    public ReducedTree generateNext() {
        // MHL
        return null;
    }

    /**
     * Some paths are only used in the context of extension, so they
     * are built and extended but not added to the final set.
     */
    private static enum SexState {
        NEITHER,
        MALEONLY,
        FEMALEONLY,
        BOTH
    };

    private static enum MultiState {
        JUSTTHIS,
        THISPLUSNEXT
    };

    static class MatchIterator {

        private final MatchProfile matchProfile;
        private final MatchState matchState;
        
        public MatchIterator(final MatchProfile mp,
                             final int maxDepth,
                             final int maxAssignmentsPerMatch) {
            this.matchProfile = mp;
            this.matchState = new MatchState(mp.minimumMatchDepth, maxDepth, maxAssignmentsPerMatch, mp.isMale);
        }

        public void reset() {
            matchState.reset();
        }

        public Collection<? extends Path> getNextPathSet() {
            return matchState.getNextPathSet();
        }
    }
    
    /**
     * This private class represents the state of a single match.  Basically,
     * it produces a collection of Path objects for a single MatchProfile.
     */
    static class MatchState {

        // Note about implementation:
        //
        // This iterator is supposed to produce a collection of Path objects that represent
        // assignment points to the main profile.  The iterator has some constraints: a
        // minimum depth for assignments, a maximum depth under consideration, and a maximum
        // number of assignments.  Otherwise it is supposed to walk through all combinations
        // of assignments for a given match profile.
        //
        // The representation for a Path deliberately collapses stuff we do not care about
        // so that the iteration has the fewest steps possible.  We could, for example, have
        // go through combinations of all exact ancestors in the tree for a MatchProfile
        // and prune out what we've already seen, but that requires a lot more computation
        // than not generating stuff we don't care about in the first place.  The Path
        // representation is designed to help us do that, since it glosses over the precise
        // details of each path to concentrate only on the important variants, such as how
        // paths relate to one another.  But nevertheless, we must always keep in mind that
        // we are computing a set of Paths on every iteration that are self-consistent, and
        // consistent with the canonical ancestor hierarchy for the MatchProfile these apply
        // to.
        //
        // It is therefore critical to not hypothesize more ancestors at any given level than
        // there really can be.
        //
        // Note that if we limited the iteration to a max assignment count of 1, the
        // implementation would be trivial, because each path so computed would go all the
        // way back to the root of the profile with no intervening nodes.  As soon
        // as multiple assignments are possible, though, intervening nodes are essential
        // because the root profile has only one set of parents and all paths have to go
        // through them.  In fact, if there is no node representing the direct parents of
        // the root profile, then only one actual assignment with an unbroken path to the
        // root is possible.
        //
        // I've therefore structured the iterator around the selection of the lowest-level
        // intersection node.  The first level iteration goes over all possible choices for
        // this.  If the node has a high enough level, the female-only, male-only, and both
        // states will also be explored.  If the node has the maximum level, then the "neither"
        // state will *not* be explored, since no ancestors would be possible.
        //
        // Once that node is selected, then based on where it is, derivative Paths that
        // include it will also be explored.  These can be handled recursively.  I still have
        // to work through the terms of the recursion, however.  But if the number of
        // derived paths can be guaranteed to be no more than 4, we can avoid any need to
        // maintain auxilliary structures that limit the number of ancestors allocated at
        // every level.
        
        // Iterator characteristics
        
        private final int lowestLevelMatch;
        private final int maxDepth;
        private final int maximumPaths;
        private final boolean isRootProfileMale;
        
        // Link to next MatchState, for the next independent path

        private final MatchState nextIndependentPath;
        
        // Iterator state

        // Currently sufficient only for independent assignments; dependent ones need more work
        private SexState sexState;
        private int currentStateLevel;
        private MultiState includeNextState;
        
        public MatchState(int lowestLevelMatch, int maxDepth, int maximumPaths, boolean isMale) {
            if (maximumPaths > 4) {
                throw new IllegalArgumentException("Iterator can only work when maximum number of paths is 4 or less");
            }
            this.lowestLevelMatch = lowestLevelMatch;
            this.maxDepth = maxDepth;
            this.maximumPaths = maximumPaths;
            this.isRootProfileMale = isMale;
            if (maximumPaths > 1) {
                nextIndependentPath = new MatchState(lowestLevelMatch, maxDepth, maximumPaths-1, isMale);
            } else {
                nextIndependentPath = null;
            }
            reset();
        }
        
        /**
         * Reset to the beginning of the sequence.
         */
        public void reset() {
            sexState = SexState.FEMALEONLY;
            currentStateLevel = lowestLevelMatch;
            includeNextState = MultiState.JUSTTHIS;
            if (nextIndependentPath != null) {
                nextIndependentPath.reset();
            }
        }

        /**
         * Get the next Path collection, or return null if at the end of the sequence.
         */
        public Collection<? extends Path> getNextPathSet() {
            // If we've hit the end, exit with null
            if (currentStateLevel == maxDepth) {
                return null;
            }
            
            final List<Path> rval = new ArrayList<>();

            // Generate a Path for this state and increment
            switch (sexState) {
            case FEMALEONLY:
                rval.add(new Path(false, true, currentStateLevel, isMale, null));
                break;
            case MALEONLY:
                rval.add(new Path(true, false, currentStateLevel, isMale, null));
                break;
            case BOTH:
                rval.add(new Path(true, true, currentStateLevel, isMale, null));
                break;
            default:
                break;
            }
            
            // The general sequence of "incrementing" the state is as follows:
            // (1) walk through the set states; if at end, go back to FEMALEONLY and set
            //     includeNextState
            // (2) If includeNextState is set, 
            if (includeNextState == MultiState.THISPLUSNEXT) {
                if (nextIndependentPath != null) {
                    final Collection<? extends Path> downstreamPaths = nextIndependentPath.getNextPathSet();
                    if (downstreamPaths == null) {
                        includeNextState = null;
                    } else {
                        rval.addAll(downstreamPaths);
                    }
                } else {
                    includeNextState = null;
                }
            } else {
                includeNextState = MultiState.THISPLUSNEXT;
            }

            // Advancement logic: increment the next levels

            // Multilevel state
            if (includeNextState == null) {
                // Advance to next sex
                switch (sexState) {
                case FEMALEONLY:
                    sexState = SexState.MALEONLY;
                    break;
                case MALEONLY:
                    sexState = SexState.BOTH;
                    break;
                case BOTH:
                    sexState = null;
                    break;
                default:
                    break;
                }
                includeNextState = MultiState.JUSTTHIS;
            }

            // Sex
            if (sexState == null) {
                // Advance to the next depth level
                sexState = SexState.FEMALEONLY;
                currentStateLevel++;
            }

            return rval;
        }
    }

}

