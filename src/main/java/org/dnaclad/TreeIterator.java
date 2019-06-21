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

    private static enum SexState {
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
        // If the maximumPaths parameter was one, this iterator would be trivially
        // simple: it would walk from the lowest level match to the max depth, and for each
        // level it would return three Paths: one male, one female, and one both.
        // When we add a second path, then we cycle through both of these in the same way.
        // BUT we also have to add another iteration: for each time there's a male or
        // female path, we also iterate through the extension of those paths as well.
        //
        // Three maximum paths would require a more complex treatment, where for
        // every Path we find, we generate only that path, then 1 additional extension to the
        // path, then 2 additional extensions to the path, then 1 unrelated path, then 1 unrelated path plus
        // an extension, then 2 unrelated paths.
        //
        // This can, of course, be generalized, which is what I'm going to attempt to do here.
        
        // Iterator characteristics
        
        private final int lowestLevelMatch;
        private final int maxDepth;
        private final int maximumPaths;
        private final boolean isMale;
        
        // Link to next MatchState, for the next independent path

        private final MatchState nextIndependentPath;
        
        // Iterator state

        // Currently sufficient only for independent assignments; dependent ones need more work
        private SexState sexState;
        private int currentStateLevel;
        private MultiState includeNextState;
        
        public MatchState(int lowestLevelMatch, int maxDepth, int maximumPaths, boolean isMale) {
            this.lowestLevelMatch = lowestLevelMatch;
            this.maxDepth = maxDepth;
            this.maximumPaths = maximumPaths;
            this.isMale = isMale;
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

