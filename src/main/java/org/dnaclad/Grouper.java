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
            
            // We need to find the "most joiny" match and remove that.
            // We heuristically define "most joiny" as being the one that has the most overlapping other matches in the group.
            // We tried just using match length as a proxy for this, but it fails to do the right thing much of the time.
            
            // Create a local list we can work with.
            final List<ChromosomeMatch> scratchMatches = new ArrayList<>(matchesInGroup);

            // Recursive step: remove longest match and regroup with what's left.
            while (true) {
                if (scratchMatches.size() == 1) {
                    // No further decomposition possible.
                    return rval;
                }
                ChromosomeMatch longest = removeMostJoinyMatch(scratchMatches);
                System.out.println("For group ("+startCM+" - "+endCM+"), removing "+longest.matchID+" ("+longest.start+" - "+longest.end+")");
                // Build a new group with what is left
                PriorityQueue<Group> startingGroups = new PriorityQueue<>();
                for (ChromosomeMatch nextOne : scratchMatches) {
                    Group newGroup = new Group(nextOne);
                    startingGroups.add(newGroup);
                }
                if (startingGroups.size() == 0) {
                    return rval;
                }
                startingGroups = regroup(startingGroups);
                if (startingGroups.size() > 1) {
                    // Successfully broke up the group into two or more pieces!  Recursively build a return collection from this.
                    while (true) {
                        Group longestGroup = startingGroups.poll();
                        if (longestGroup == null) {
                            break;
                        }
                        // Recurse
                        rval.addAll(longestGroup.decompose());
                    }
                    return rval;
                }
                // Loop back around and throw away another one, because we didn't split yet.
            }
                
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