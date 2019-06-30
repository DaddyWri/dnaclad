package org.dnaclad;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

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

    final Map<MatchProfile, MatchState> matchIterators;
    final List<MatchState> matchStates;
    
    public TreeIterator(final Collection<? extends MatchProfile> matches,
                        final int maxDepth,
                        final int maxAssignmentsPerMatch) {
        matchIterators = new HashMap<>(matches.size());
        matchStates = new ArrayList<>(matches.size());
        for (final MatchProfile mp : matches) {
            final MatchState ms = new MatchState(mp, mp.minimumMatchDepth, maxDepth, maxAssignmentsPerMatch, mp.isMale);
            matchIterators.put(mp, ms);
            matchStates.add(ms);
        }
    }

    /**
     * Retrieve next ReducedTree, or null if there are no more.
     */
    public ReducedTree generateNext() {

        if (matchStatesAtEnd()) {
            return null;
        }

        // MHL
        ReducedTree rt = null;
        // MHL
        
        advanceMatchStates();

        return rt;
    }

    // Combinatorially create assignments given all the matchstates we have
    //
    // The basic idea here is to take all of the MatchStates and put together
    // a new set of Assignments.  But this has to be done in a way that allows
    // us to traverse all possibilities.
    //
    // Between any individual MatchState and the main profile, we have a list
    // of Path objects representing Assignments we want to have.  But several
    // Paths may all depend on the same root Path, which represents the lowest-
    // level node in common between them.  Our assignment building starts with
    // these nodes.  They are combinatorially paired with individual hierarchy
    // levels in the main profile, based on age ranges.  The pairings can work
    // as follows:
    //
    // (1) The pairing has no relationship with any other pairing on the same
    //     level so far made.  In this case there may still be a relationship
    //     between the pairing and another pairing, in that they both share a
    //     common node that is closer to the root of the main profile; all such
    //     possibilities must be considered.
    // (2) The pairing is exactly the same as another pairing we already have.
    //     in this case, we have to combinatorially permute all the different
    //     ways the downstream Path sections can be combined: independent, the
    //     same, or the same via some node in-between.
    // (3) Each level of the main profile has a specific limit for the number
    //     of nodes of specific kinds that it can accept, which means that
    //     independent assignment is not always possible given the history
    //     and given the field of assignments already made.
    //
    // The problem is that any order-dependent way of layering on MatchStates
    // to produce Assignments implies that we have to permute the ordering we do
    // this, as well.  That potentially produces duplicates and a lot of wasted
    // effort.  But turning the problem around has potential.  Instead of
    // looking to layer in MatchStates, we instead combinatorially attempt to
    // build partial trees, one level at a time, EXACTLY the way we built the
    // MatchStates themselves.  Whenever a choice is possible for a Path extension
    // we select from among the available MatchState Paths combinatorially to
    // use as matches.  (Once again, we need the root Paths to give us this set.)
    // In this way we can combinatorially select root Path assignments without
    // violating any tree constraints.  But we still have exactly the same
    // combinatoric generation problem for all Paths emanating from a common
    // node, so the algorithm would need to be recursive in much the same way
    // that the MatchState algorithm is recursive.
    //
    // The data structures we need for this include at least the following:
    // (a) An ordered set of root Paths, which we compute from the overall
    //     list of MatchStates;
    // (b) For each root Path, a set of extension Paths that go through it.

    
    private boolean matchStatesAtEnd() {
        return matchStates.get(matchStates.size()-1).atEnd();
    }

    private void advanceMatchStates() {
        int index = 0;
        while (true) {
            if (index == matchStates.size()) {
                break;
            }
            matchStates.get(index).advance();
            if (!matchStates.get(index).atEnd()) {
                return;
            }
            matchStates.get(index).reset();
            index++;
        }
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

        /** Match profile */
        private final MatchProfile matchProfile;
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

        // We cache the current results too
        private Collection<? extends Path> currentResults;
        
        public MatchState(final MatchProfile matchProfile,
                          final int lowestLevelMatch,
                          final int maxDepth,
                          final int maximumPaths,
                          final boolean rootProfileIsMale) {
            this(matchProfile, null, 1, lowestLevelMatch, maxDepth, maximumPaths, rootProfileIsMale);
        }
        
        public MatchState(final MatchProfile matchProfile,
                          final Path pathToExtend,
                          final int depth,
                          final int lowestLevelMatch,
                          final int maxDepth,
                          final int maximumPaths,
                          final boolean pathProfileIsMale) {
            if (maximumPaths > 4) {
                throw new IllegalArgumentException("Iterator can only work when maximum number of paths is 4 or less");
            }
            if (depth < 1) {
                throw new IllegalArgumentException("Attempt to create a MatchState with depth 0");
            }
            if (depth > maxDepth) {
                throw new IllegalArgumentException("Attempt to create a MatchState deeper than maxDepth");
            }
            if (maximumPaths < 1) {
                throw new IllegalArgumentException("Asking for no more than zero paths");
            }
            this.matchProfile = matchProfile;
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
            currentDepth = depth;
            sexState = SexState.NEITHER;
            multiState = MultiState.PATHONLY;
            extensionPath = null;
            maleExtensionIterator = null;
            femaleExtensionIterator = null;
            currentResults = null;
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
            if (currentDepth > maxDepth) {
                return true;
            }
            return false;
        }
        
        /**
         * Get the current Path collection.  An exception will be thrown if we're beyond the end of the sequence.
         * Use atEnd() to prevent that.
         */
        public Collection<? extends Path> getCurrentPathSet() {
            // This requires current results to be computed!!
            moveToFirst();
            return currentResults;
        }

        /**
         * Compute the results, without any advancement.  We are guaranteed to be at a legal state.
         */
        private void computeResults() {
            //System.out.println("in computeResults for "+System.identityHashCode(this));
            if (currentResults != null) {
                return;
            }
            
            if (extensionPath == null) {
                // Generate a Path for this state and increment.
                // The path we create goes from the current level back to the terminus of the pathToExtend.
                // For example, if the pathToExtend represents the immediate parents of the root profile,
                // its depth would have been 1, and thus if currentDepth is 2, we need to compute the number
                // of intervening generations as 2 - (1+1) = 0.
                switch (sexState) {
                case NEITHER:
                    extensionPath = new Path(matchProfile, false, false, currentDepth - (depth+1), isPathProfileMale, pathToExtend);
                    break;
                case FEMALEONLY:
                    extensionPath = new Path(matchProfile, false, true, currentDepth - (depth+1), isPathProfileMale, pathToExtend);
                    break;
                case MALEONLY:
                    extensionPath = new Path(matchProfile, true, false, currentDepth - (depth+1), isPathProfileMale, pathToExtend);
                    break;
                case BOTH:
                    extensionPath = new Path(matchProfile, true, true, currentDepth - (depth+1), isPathProfileMale, pathToExtend);
                    break;
                default:
                    break;
                }
            }

            if (extensionPath != null && maleExtensionIterator == null && femaleExtensionIterator == null && multiState == multiState.INCLUDEEXTENSION && sexState != SexState.BOTH) {
                // For the NEITHER case, we can safely assume that the other branch (which must exist!!)
                // will generate at least one Path, so we deduct one from the maximumPaths value when we construct
                // its iterator.  That makes it no different from FEMALEONLY and MALEONLY extensions.

                // The iterator we construct has a starting depth of one more than the depth of the Path we built,
                // because that is the lowest level of iteration.
                
                if (sexState == SexState.FEMALEONLY || sexState == SexState.NEITHER) {
                    // Create a male-side extension.
                    maleExtensionIterator = new MatchState(matchProfile,
                                                           extensionPath,
                                                           currentDepth + 1,  // One level more in depth
                                                           lowestLevelMatch,
                                                           maxDepth,
                                                           maximumPaths - 1,  // One less assignment to generate
                                                           true);
                    if (maleExtensionIterator.atEnd()) {
                        //System.out.println("Depth we created: "+(currentDepth+1));
                        throw new IllegalStateException("Created a male extension iterator that has no states; should not happen: state="+this);
                    }
                }
                if (sexState == SexState.MALEONLY || sexState == SexState.NEITHER) {
                    // Create a female-side extension
                    femaleExtensionIterator = new MatchState(matchProfile,
                                                             extensionPath,
                                                             currentDepth + 1,  // One level more in depth
                                                             lowestLevelMatch,
                                                             maxDepth,
                                                             maximumPaths - 1,  // One less assignment to generate
                                                             false);
                    if (femaleExtensionIterator.atEnd()) {
                        throw new IllegalStateException("Created a female extension iterator with no states; should not happen: state="+this);
                    }
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

            // The child iterators will not be created if they are not needed

            // Apply the iterators
            if (maleExtensionIterator != null) {
                final Collection<? extends Path> paths = maleExtensionIterator.getCurrentPathSet();
                rval.addAll(paths);
            }
            if (femaleExtensionIterator != null) {
                final Collection<? extends Path> paths = femaleExtensionIterator.getCurrentPathSet();
                rval.addAll(paths);
            }

            currentResults = rval;
        }

        /**
         * Advance to the next state.
         */
        public void advance() {
            // We call moveToFirst() in case we advance without getting results.  This should never be the case though
            // so no call for now.
            //moveToFirst();
            if (currentDepth > maxDepth) {
                throw new IllegalStateException("Asked to advance when already at end");
            }
            simpleAdvance();
        }

        private void simpleAdvance() {
            //System.out.println("in simpleAdvance() for "+System.identityHashCode(this)+":"+this.toString());
            // Advancement logic: increment the next levels
            currentResults = null;
            
            // First priority: increment the extension iterators.  These will not have been created at all
            // unless they are needed.
            if (maleExtensionIterator != null) {
                maleExtensionIterator.advance();
                if (maleExtensionIterator.atEnd()) {
                    maleExtensionIterator = null;
                } else {
                    //System.out.println(" ...advanced maleExtensionIterator for "+System.identityHashCode(this));
                    return;
                }
            }
            if (femaleExtensionIterator != null) {
                femaleExtensionIterator.advance();
                if (femaleExtensionIterator.atEnd()) {
                    femaleExtensionIterator = null;
                } else {
                    //System.out.println(" ...advanced femaleExtensionIterator for "+System.identityHashCode(this));
                    return;
                }
            }

            // Order of advancement:
            // (1) multiState
            // (2) sexState
            // (3) depth
            
            // If all done with those, advance the multiState
            switch (multiState) {
            case PATHONLY:
                multiState = MultiState.INCLUDEEXTENSION;
                //System.out.println(" ...advanced multiState to INCLUDEEXTENSION");
                return;
            case INCLUDEEXTENSION:
                multiState = null;
                break;
            default:
                break;
            }

            // Now, advance the sexState
            if (multiState == null) {
                multiState = MultiState.PATHONLY;
                extensionPath = null;
                switch (sexState) {
                case NEITHER:
                    sexState = SexState.FEMALEONLY;
                    //System.out.println(" ...advanced to FEMALEONLY for "+System.identityHashCode(this));
                    return;
                case FEMALEONLY:
                    sexState = SexState.MALEONLY;
                    //System.out.println(" ...advanced to MALEONLY for "+System.identityHashCode(this));
                    return;
                case MALEONLY:
                    sexState = SexState.BOTH;
                    //System.out.println(" ...advanced to BOTH for "+System.identityHashCode(this));
                    return;
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
                //System.out.println(" ...advanced to currentDepth "+currentDepth+" for "+System.identityHashCode(this));
                return;
            }
            
            throw new IllegalStateException("Should never get here!");
        }
        
        /**
         * Given the current state, move to the first legal state at or after this one.
         */
        private void moveToFirst() {
            // Order of advancement:
            // (1) multiState
            // (2) sexState
            // (3) depth
            //System.out.println("in moveToFirst for "+System.identityHashCode(this)+" currentDepth="+currentDepth);
            while (true) {
                // Basically, this method checks for illegal conditions, and if it finds them,
                // it modifies the current state to move past these, until it finds a legal state.
                
                if (currentDepth > maxDepth) {
                    break;
                }
                if (maximumPaths == 1 && sexState != SexState.BOTH && multiState == MultiState.INCLUDEEXTENSION) {
                    //System.out.println(" ...case1");
                    // We cannot include extensions when we've already allocated all the paths
                    multiState = MultiState.PATHONLY;
                    sexState = SexState.BOTH;
                    extensionPath = null;
                    maleExtensionIterator = null;
                    femaleExtensionIterator = null;
                    continue;
                }
                if (currentDepth < lowestLevelMatch && sexState != SexState.NEITHER) {
                    //System.out.println(" ...case2");
                    // If we're below lowest level where we can generate a match, and yet we are
                    // generating a match, go to the next level
                    currentDepth++;
                    sexState = SexState.NEITHER;
                    multiState = MultiState.INCLUDEEXTENSION;
                    extensionPath = null;
                    maleExtensionIterator = null;
                    femaleExtensionIterator = null;
                    continue;
                }
                if (currentDepth == maxDepth && sexState == SexState.NEITHER) {
                    //System.out.println(" ...case3");
                    // If we're on the last row, we cannot be in a state where extensions are
                    // required.
                    sexState = SexState.FEMALEONLY;
                    multiState = MultiState.PATHONLY;
                    extensionPath = null;
                    maleExtensionIterator = null;
                    femaleExtensionIterator = null;
                    continue;
                }
                if (sexState != SexState.BOTH && multiState == MultiState.INCLUDEEXTENSION && currentDepth == maxDepth) {
                    //System.out.println(" ...case4");
                    // If we're on the last row, and yet we're being told to include the extension, skip to where no extension
                    // is used.  Only the BOTH state makes sense there.
                    sexState = SexState.BOTH;
                    multiState = MultiState.PATHONLY;
                    extensionPath = null;
                    maleExtensionIterator = null;
                    femaleExtensionIterator = null;
                    continue;
                }
                if (maximumPaths < 2 && sexState == SexState.NEITHER) {
                    //System.out.println(" ...case5");
                    // If we're in the NEITHER sexState, and we have fewer than 2 maximum paths, we advance because
                    // we are otherwise unable to actually generate a tree.
                    sexState = SexState.FEMALEONLY;
                    multiState = MultiState.PATHONLY;
                    extensionPath = null;
                    maleExtensionIterator = null;
                    femaleExtensionIterator = null;
                    continue;
                }
                if (multiState == MultiState.PATHONLY && sexState == SexState.NEITHER) {
                    //System.out.println(" ...case6");
                    // NEITHER doesn't generate anything so it never gets along with PATHONLY
                    multiState = MultiState.INCLUDEEXTENSION;
                    continue;
                }
                if (multiState == MultiState.INCLUDEEXTENSION && sexState == SexState.BOTH) {
                    //System.out.println(" ...case7");
                    // INCLUDEEXTENSION requires something to extend, and BOTH prohibits that
                    sexState = SexState.NEITHER;
                    multiState = MultiState.INCLUDEEXTENSION;
                    currentDepth++;
                    extensionPath = null;
                    maleExtensionIterator = null;
                    femaleExtensionIterator = null;
                    continue;
                }
                
                // If everything else is OK, compute results for the current state, and skip if they exceed the maximum
                computeResults();
                if (currentResults.size() > maximumPaths) {
                    //System.out.println(" ....case8");
                    simpleAdvance();
                    continue;
                }

                // Everything is legal so terminate
                break;
            }
            //System.out.println("... done with moveToFirst for "+System.identityHashCode(this)+": "+this);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("sexState=").append(sexState.toString()).append(";");
            sb.append("multiState=").append(multiState.toString()).append(";");
            sb.append("currentDepth=").append(new Integer(currentDepth)).append(";");
            sb.append("extensionPath=").append((extensionPath==null)?"null":extensionPath.toString()).append(";");
            sb.append("maleExtensionIterator=").append((maleExtensionIterator==null)?"null":(new Integer(System.identityHashCode(maleExtensionIterator)).toString() + "(" + maleExtensionIterator + ")")).append(";");
            sb.append("femaleExtensionIterator=").append((femaleExtensionIterator==null)?"null":(new Integer(System.identityHashCode(femaleExtensionIterator)).toString() + "(" + femaleExtensionIterator + ")")).append(";");
            return sb.toString();
        }
        
    }

    static class MatchNodes {
        /** The set of Paths for the null Path */
        private final Set<Path> nullPathExtensions = new HashSet<>();
        /** The set of extensions for any given Path */
        private final Map<Path, Set<Path>> pathExtensions = new HashMap<>();

        public MatchNodes() {
        }

        public void addPaths(final Collection<? extends Path> assignmentPaths) {
            for (final Path assignment : assignmentPaths) {
                addPath(assignment);
            }
        }

        public void addPath(final Path assignment) {
            if (assignment.derivedFrom == null) {
                nullPathExtensions.add(assignment);
            } else {
                Set<Path> extensions = pathExtensions.get(assignment.derivedFrom);
                if (extensions == null) {
                    extensions = new HashSet<>();
                    pathExtensions.put(assignment.derivedFrom, extensions);
                }
                extensions.add(assignment);
                addPath(assignment.derivedFrom);
            }
        }

        public Collection<? extends Path> getPotentialAssignments(final Path rootPath) {
            if (rootPath == null) {
                return nullPathExtensions;
            } else {
                return pathExtensions.get(rootPath);
            }
        }
        
    }
    
}

