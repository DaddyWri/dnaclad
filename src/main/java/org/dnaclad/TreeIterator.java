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
        PATHONLY,
        INCLUDEEXTENSION
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
            if (matchState.atEnd()) {
                return null;
            }
            final Collection<? extends Path> rval = matchState.getCurrentPathSet();
            matchState.advance();
            return rval;
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

        /** Path to extend (or null if root profile) */
        private final Path pathToExtend;
        /** Starting depth; assume an articulation node here */
        private final int depth;  // 0 = root profile; 1 = parents of root profile
        /** The lowest level we're allowed to create an actual match for */
        private final int lowestLevelMatch; // 1 = parents; 2 = grandparents
        /** The maximum depth we're allowed to consider */
        private final int maxDepth;
        /** The maximum number of Paths we should generate from this iterator */
        private final int maximumPaths;
        /** This describes whether the profile for the node at the stated depth is male */
        private final boolean isPathProfileMale;
        
        // Iterator state

        // The sequence of states is as follows:
        // (1) We start in sexState NEITHER, and currentDepth one above the starting depth.
        // (2) For iteration:
        //     - If the maximum number of Paths to generate is zero, we return right away
        //     - If the currentLevel is maxDepth, return null
        //     - Generate a Path to extend the current Path, using the current SexState
        //     - Extend the male side, by creating a MatchState that represents it, and set
        //       a flag indicating it should be iterated over
        //     - Extend the female side, by creating a MatchState that represents it, and
        //       set the state indicating it should be iterated over
        //     - Advance to the next SexState, skipping as appropriate based on position in
        //       hierarchy
        //     - Advance currentLevel
        //
        // To do this we need an overall state variable describing where we are in the extension
        // process. This will be called "MultiState".
        
        /** The current sex state at the node this MatchState was created for */
        private SexState sexState;
        /** The current depth */
        private int currentDepth;
        /** The multi-state */
        private MultiState multiState;
        /** Depending on the multiState value, we keep an extension path */
        private Path extensionPath;

        // We can have zero, one, or two extension iterators in play.  We iterate through
        // all of these together.  The conformation of the root Path determines how many
        // extension iterators there are, and how they are constructed.  Each one provides
        // a minimum of ONE assignment and a maximum of whatever is passed in.
        //
        // The fact that there are two of these active for some nodes means that the number
        // of combinations of the two, and how they are allocated, must be coordinated.
        // How this is done needs to be worked out still.
        
        /** Male extension iterator, or null */
        private MatchState maleExtensionIterator;
        /** Female extension iterator, or null */
        private MatchState femaleExtensionIterator;
        
        public MatchState(final int lowestLevelMatch,
                          final int maxDepth,
                          final int maximumPaths,
                          final boolean rootProfileIsMale) {
            this(null, 0, lowestLevelMatch, maxDepth, maximumPaths, rootProfileIsMale);
        }
        
        public MatchState(final Path pathToExtend,
                          final int depth,
                          final int lowestLevelMatch,
                          final int maxDepth,
                          final int maximumPaths,
                          final boolean pathProfileIsMale) {
            if (maximumPaths > 4) {
                throw new IllegalArgumentException("Iterator can only work when maximum number of paths is 4 or less");
            }
            this.pathToExtend = pathToExtend;
            this.depth = depth;
            this.lowestLevelMatch = lowestLevelMatch;
            this.maxDepth = maxDepth;
            this.maximumPaths = maximumPaths;
            this.isPathProfileMale = pathProfileIsMale;
            reset();
        }
        
        /**
         * Reset to the beginning of the sequence.
         */
        public void reset() {
            currentDepth = depth + 1;
            sexState = SexState.NEITHER;
            multiState = MultiState.PATHONLY;
            extensionPath = null;
            maleExtensionIterator = null;
            femaleExtensionIterator = null;
        }

        /**
         * Check if we are at the end of the sequence.
         */
        public boolean atEnd() {
            moveToFirst();
            if (maximumPaths == 0) {
                return true;
            }
            // If we've hit the end, exit with null
            if (currentDepth == maxDepth) {
                return true;
            }
            return false;
        }
        
        /**
         * Get the current Path collection.  An exception will be thrown if we're beyond the end of the sequence.
         * Use atEnd() to prevent that.
         */
        public Collection<? extends Path> getCurrentPathSet() {
            moveToFirst();
            return computeResults();
        }

        /**
         * Compute the results, without any advancement.  We are guaranteed to be at a legal state.
         */
        private Collection<? extends Path> computeResults() {
            if (extensionPath == null) {
                // Generate a Path for this state and increment
                switch (sexState) {
                case NEITHER:
                    // We can't generate this if we are on the highest level
                    //if (currentDepth < maxDepth - 1) {
                    extensionPath = new Path(false, false, currentDepth, isPathProfileMale, pathToExtend);
                    break;
                case FEMALEONLY:
                    // We can't generate this if we're not deep enough
                    //if (currentDepth >= lowestLevelMatch) {
                    extensionPath = new Path(false, true, currentDepth, isPathProfileMale, pathToExtend);
                    break;
                case MALEONLY:
                    // We can't generate this if we're not deep enough
                    //if (currentDepth >= lowestLevelMatch) {
                    extensionPath = new Path(true, false, currentDepth, isPathProfileMale, pathToExtend);
                    break;
                case BOTH:
                    // We can't generate this if we're not deep enough
                    //if (currentDepth >= lowestLevelMatch) {
                    extensionPath = new Path(true, true, currentDepth, isPathProfileMale, pathToExtend);
                    break;
                default:
                    break;
                }
            }

            if (extensionPath != null && maleExtensionIterator == null && femaleExtensionIterator == null && sexState != SexState.BOTH) {
                // For the NEITHER case, we can safely assume that the other branch (which must exist!!)
                // will generate at least one Path, so we deduct one from the maximumPaths value when we construct
                // its iterator.  That makes it no different from FEMALEONLY and MALEONLY extensions.
                if (sexState == SexState.FEMALEONLY || sexState == SexState.NEITHER) {
                    // Create a male-side extension.
                    maleExtensionIterator = new MatchState(extensionPath,
                                                           currentDepth + 1,  // One level more in depth
                                                           lowestLevelMatch,
                                                           maxDepth,
                                                           maximumPaths - 1,  // One less assignment to generate
                                                           true);
                }
                if (sexState == SexState.MALEONLY || sexState == SexState.NEITHER) {
                    // Create a female-side extension
                    femaleExtensionIterator = new MatchState(extensionPath,
                                                             currentDepth + 1,  // One level more in depth
                                                             lowestLevelMatch,
                                                             maxDepth,
                                                             maximumPaths - 1,  // One less assignment to generate
                                                             false);
                }
            }

            // The multiState state only applies if the SexState is FEMALEONLY or MALEONLY.  In that case
            // we have the choice of generating just the Path described, OR the Path plus whatever the extension
            // generates.  For the NEITHER case we always generate both sides (makes no sense otherwise), and for
            // the BOTH case we always generate just the Path, nothing else.
            
            // Now, generate return value!

            // Every MatchIterator generates at least ONE assignment.
            
            final List<Path> rval = new ArrayList<>();

            // Always add the path we created, if we have it, and if we are allowed.
            if (extensionPath != null && sexState != SexState.NEITHER) {
                rval.add(extensionPath);
            }

            // If the sexState is FEMALEONLY or MALEONLY, we check the multiState to decide whether to generate any downstream Paths.
            if (sexState == SexState.BOTH ||
                ((sexState == SexState.FEMALEONLY || sexState == SexState.MALEONLY) && multiState == MultiState.PATHONLY)) {
                // We're done, so return.
                return rval;
            }

            // Apply the iterators
            if (maleExtensionIterator != null) {
                final Collection<? extends Path> paths = maleExtensionIterator.getCurrentPathSet();
                rval.addAll(paths);
            }
            if (femaleExtensionIterator != null) {
                final Collection<? extends Path> paths = femaleExtensionIterator.getCurrentPathSet();
                rval.addAll(paths);
            }
            return rval;
        }

        /**
         * Advance to the next state.
         */
        public void advance() {
            // Advancement logic: increment the next levels

            // First priority: increment the extension iterators.
            if (maleExtensionIterator != null) {
                maleExtensionIterator.advance();
                if (maleExtensionIterator.atEnd()) {
                    maleExtensionIterator = null;
                } else {
                    return;
                }
            }
            if (femaleExtensionIterator != null) {
                femaleExtensionIterator.advance();
                if (femaleExtensionIterator.atEnd()) {
                    femaleExtensionIterator = null;
                } else {
                    return;
                }
            }

            // Next, do the multiState
            if (sexState == SexState.FEMALEONLY || sexState == SexState.MALEONLY) {
                if (multiState == MultiState.PATHONLY) {
                    multiState = MultiState.INCLUDEEXTENSION;
                    return;
                } else {
                    multiState = null;
                }
            }

            // Now, the sexState
            if (multiState == null) {
                multiState = MultiState.PATHONLY;
                switch (sexState) {
                case NEITHER:
                    sexState = SexState.FEMALEONLY;
                    break;
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
            }
            
            // Now, the depth
            if (sexState == null) {
                sexState = SexState.NEITHER;
                currentDepth++;
            }
        }
        
        /**
         * Given the current state, move to the first legal state at or after this one.
         */
        private void moveToFirst() {
            while (true) {
                // Basically, this method checks for illegal conditions, and if it finds them,
                // it modifies the current state to move past these, until it finds a legal state.
                
                if (currentDepth >= maxDepth) {
                    break;
                }
                if (currentDepth < lowestLevelMatch && sexState != SexState.NEITHER) {
                    currentDepth++;
                    sexState = SexState.NEITHER;
                    extensionPath = null;
                    maleExtensionIterator = null;
                    femaleExtensionIterator = null;
                    continue;
                }
                if (currentDepth >= maxDepth - 1 && sexState == SexState.NEITHER) {
                    sexState = SexState.FEMALEONLY;
                    multiState = MultiState.PATHONLY;
                    extensionPath = null;
                    maleExtensionIterator = null;
                    femaleExtensionIterator = null;
                    continue;
                }
                if (maximumPaths < 2 && sexState == SexState.NEITHER) {
                    sexState = SexState.FEMALEONLY;
                    multiState = MultiState.PATHONLY;
                    extensionPath = null;
                    maleExtensionIterator = null;
                    femaleExtensionIterator = null;
                    continue;
                }
                // Compute results for the current state, and skip if they exceed the maximum
                final Collection<? extends Path> results = computeResults();
                if (results.size() > maximumPaths) {
                    advance();
                    continue;
                }
                break;
            }
        }
    }


    
}

