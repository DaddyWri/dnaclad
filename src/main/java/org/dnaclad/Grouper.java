package org.dnaclad;

import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.PriorityQueue;
import java.util.List;
import java.util.ArrayList;

public class Grouper {
    
    private PriorityQueue<Group> groups;
    
    public Grouper() {
        this.groups = new PriorityQueue<>();
    }
    
    public void addMatch(final ChromosomeMatch match) {
        // Create a group and add it to the list
        final Group newGroup = new Group(match);
        groups.add(newGroup);
    }

    private static PriorityQueue<Group> regroup(PriorityQueue<Group> groups) {
        // We iterate until there are no changes.  A change involves merging two groups together.
        // Each time we do this we build a new PriorityQueue with agglomerated groups.
        while (true) {
            // The new list
            PriorityQueue<Group> newQueue = new PriorityQueue<Group>();
            boolean mergeHappened = false;
            while (true) {
                // Pop the longest one off the current remainder queue
                Group longest = groups.poll();
                if (longest == null) {
                    break;
                }
                PriorityQueue<Group> innerQueue = new PriorityQueue<Group>();
                // Iterate through the remaining items in groups and see if we intersect any of them
                while (true) {
                    Group nextLongest = groups.poll();
                    if (nextLongest == null) {
                        break;
                    }
                    if (!longest.mergeIfOverlaps(nextLongest)) {
                        // It didn't merge.  Keep it around for the next inner iteration.
                        innerQueue.add(nextLongest);
                    } else {
                        mergeHappened = true;
                    }
                }
                newQueue.add(longest);
                groups = innerQueue;
            }
            groups = newQueue;
            if (!mergeHappened) {
                break;
            }
        }
        return groups;
    }
    
    public void writeOutGroups(final GroupWriter out) {
        // First, assemble all the groups
        groups = regroup(groups);
        
        // Grouping complete.
        // Output organized by length.  Later we can rearrange this.
        while (true) {
            Group longest = groups.poll();
            if (longest == null) {
                break;
            }
            // Decompose the group.  Internally this is a recursive call so we need to only do it once here.
            final Collection<Group> subgroups = longest.decompose();
            for (final Group subgroup : subgroups) {
                // Write the subgroup
                out.write(subgroup.chromosomeID, subgroup.startCM, subgroup.endCM, subgroup.getMatchesInGroup());
            }
        }
    }
    
    public static interface GroupWriter {
        public void write(String chromosomeID, int startCM, int endCM, List<ChromosomeMatch> matches);
    }

    private static int findMostPopularArticulationPoint(List<ChromosomeMatch> matches, int excludeMe, int excludeMeToo) {
        // Loop through the matches and find the articulation point that seems to have the most representation in the matches
        Map<Integer, Integer> articulations = new HashMap<>();
        // Look through chromosome matches to build the articulations table
        for (ChromosomeMatch match : matches) {
            Integer startArticulation = new Integer(match.start);
            Integer endArticulation = new Integer(match.end + 1);
            Integer startCount = articulations.get(startArticulation);
            if (startCount == null) {
                startCount = new Integer(1);
            } else {
                startCount = new Integer(startCount + 1);
            }
            articulations.put(startArticulation, startCount);
            Integer endCount = articulations.get(endArticulation);
            if (endCount == null) {
                endCount = new Integer(1);
            } else {
                endCount = new Integer(endCount + 1);
            }
            articulations.put(endArticulation, endCount);
        }

        // Now, find the most popular
        int currentBest = -1;
        int bestValue = -1;
        for (Integer articulation : articulations.keySet()) {
            if (articulation != excludeMe && articulation != excludeMeToo) {
                if (articulations.get(articulation) > bestValue) {
                    bestValue = articulations.get(articulation);
                    currentBest = articulation;
                }
            }
        }
        
        return currentBest;
    }
    
    private static ChromosomeMatch removeMostJoinyMatch(List<ChromosomeMatch> matches) {
        // Iteration #4: try identifying articulations in the matches we have for the group.  These are places that seem to be
        // in common across more than one match.  Use these articulations to try to optimize which match to remove, as follows:
        // pick the match that crosses the most weighted articulations.  A weighted articulation includes the count of the number
        // of times it has been seen, so we preferentially remove matches that link obviously distinct areas of the chromosome
        // first.
        
        Map<Integer, Integer> articulations = new HashMap<>();
        // Look through chromosome matches to build the articulations table
        for (ChromosomeMatch match : matches) {
            Integer startArticulation = new Integer(match.start);
            Integer endArticulation = new Integer(match.end + 1);
            Integer startCount = articulations.get(startArticulation);
            if (startCount == null) {
                startCount = new Integer(1);
            } else {
                startCount = new Integer(startCount + 1);
            }
            articulations.put(startArticulation, startCount);
            Integer endCount = articulations.get(endArticulation);
            if (endCount == null) {
                endCount = new Integer(1);
            } else {
                endCount = new Integer(endCount + 1);
            }
            articulations.put(endArticulation, endCount);
        }
        
        // Now, find the match that crosses the most articulations
        double bestArticulationMetric = Double.NEGATIVE_INFINITY;
        ChromosomeMatch rval = null;
        for (ChromosomeMatch removalCandidate : matches) {
            // How many articulations does it cross?
            // By cross, I do not mean "align", I mean actually cross.
            int weightedCrossings = 0;
            int plainCrossings = 0;
            for (Integer articulation : articulations.keySet()) {
                // Does this one cross?
                if (removalCandidate.start < articulation && removalCandidate.end >= articulation) {
                    weightedCrossings += articulations.get(articulation);
                    plainCrossings += 1;
                }
            }
            double metric = ((double)weightedCrossings) / ((double)plainCrossings);
            //System.out.println("Match "+removalCandidate.matchID+" weighted crossings "+weightedCrossings+" plain crossings "+plainCrossings+" metric "+metric);
            if (metric > bestArticulationMetric) {
                bestArticulationMetric = metric;
                rval = removalCandidate;
            }
        }
        
        if (rval == null) {
            // No crossings found, so arbitrarily pick the first.
            rval = matches.get(0);
        }
        // Remove the match from the list
        matches.remove(rval);
        return rval;
    }
    
    /**
    * A group is a collection of matches that all overlap.
    */
    public static class Group implements Comparable<Group> {
        protected List<ChromosomeMatch> matchesInGroup = new ArrayList<>();
        public final String chromosomeID;
        protected int startCM;
        protected int endCM;
        
        public Group(final ChromosomeMatch startingMatch) {
            this.chromosomeID = startingMatch.chromosomeNumber;
            this.startCM = startingMatch.start;
            this.endCM = startingMatch.end;
            matchesInGroup.add(startingMatch);
        }
        
        public List<ChromosomeMatch> getMatchesInGroup() {
            // We must do this non-destructively, but we want to do it in length order.
            final PriorityQueue<ChromosomeMatch> orderedMatches = new PriorityQueue<>();
            orderedMatches.addAll(matchesInGroup);
            final List<ChromosomeMatch> rval = new ArrayList<>();
            while (true) {
                final ChromosomeMatch cm = orderedMatches.poll();
                if (cm == null) {
                    break;
                }
                rval.add(cm);
            }
            return rval;
        }
        
        public Collection<Group> decompose() {
            List<Group> rval = new ArrayList<>();
            // Always add self
            rval.add(this);
            
            // Find the most popular articulation point, if any, provided it's not on the group boundary already
            int articulationPoint = findMostPopularArticulationPoint(matchesInGroup, startCM, endCM + 1);
            if (articulationPoint == -1) {
                return rval;
            }
            
            // Split the group according to this articulation point.  One batch of matches goes one way, and the other goes the other.
            // Throw away any matches that straddle.
            PriorityQueue<Group> leftSideMatches = new PriorityQueue<>();
            PriorityQueue<Group> rightSideMatches = new PriorityQueue<>();
            
            for (ChromosomeMatch match : matchesInGroup) {
                if (match.end < articulationPoint) {
                    leftSideMatches.add(new Group(match));
                } else if (articulationPoint <= match.start) {
                    rightSideMatches.add(new Group(match));
                }
            }
            
            leftSideMatches = regroup(leftSideMatches);
            rightSideMatches = regroup(rightSideMatches);
            
            while (true) {
                Group g = leftSideMatches.poll();
                if (g == null) {
                    break;
                }
                rval.addAll(g.decompose());
            }
            
            while (true) {
                Group g = rightSideMatches.poll();
                if (g == null) {
                    break;
                }
                rval.addAll(g.decompose());
            }
            
            return rval;
        }
        
        /**
        * We always add in descending order of size, for efficiency.  But we repeat until all groups have
        * been tried against each other and nothing more overlaps.
        * @return false if there was no merge, true if there was (in which case the group mentioned in the argument is
        * merged into the current group).
        */
        public boolean mergeIfOverlaps(final Group group) {
            if (!group.chromosomeID.equals(chromosomeID)) {
                return false;
            }
            if (group.endCM < this.startCM || group.startCM > this.endCM) {
                return false;
            }
            if (group.startCM < this.startCM) {
                this.startCM = group.startCM;
            }
            if (group.endCM > this.endCM) {
                this.endCM = group.endCM;
            }
            // Merge the matches
            matchesInGroup.addAll(group.matchesInGroup);
            return true;
        }
        
        @Override
        public int compareTo(final Group other) {
            final int size = endCM-startCM;
            final int otherSize = other.endCM-other.startCM;
            if (size > otherSize) {
                return -1;
            }
            if (size < otherSize) {
                return 1;
            }
            return 0;
        }

    }
}