package org.dnaclad;

import java.util.Set;
import java.util.HashSet;
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
            
            // Order the list by size of group, which is most easily done by sorting
            Group[] groupArray = new Group[subgroups.size()];
            int j = 0;
            for (Group g : subgroups) {
                groupArray[j++] = g;
            }
            java.util.Arrays.sort(groupArray);

            for (final Group subgroup : groupArray) {
                // Write the subgroup
                out.write(subgroup.chromosomeID, subgroup.startCM, subgroup.endCM, subgroup.getMatchesInGroup());
            }
        }
    }
    
    public static interface GroupWriter {
        public void write(String chromosomeID, int startCM, int endCM, List<ChromosomeMatch> matches);
    }

    private static List<Integer> findMostPopularArticulationPoints(List<ChromosomeMatch> matches, int excludeMe, int excludeMeToo) {
        // Loop through the matches and find the articulation point that seems to have the most representation in the matches (weighted by the length of the match - shorter is
        // less important)
        Map<Integer, Integer> articulations = new HashMap<>();
        // Look through chromosome matches to build the articulations table
        for (ChromosomeMatch match : matches) {
            if (match.start != excludeMe) {
                Integer startArticulation = new Integer(match.start);
                Integer startCount = articulations.get(startArticulation);
                if (startCount == null) {
                    startCount = new Integer(match.end-match.start);
                } else {
                    startCount = new Integer(Math.max(startCount, match.end-match.start));
                }
                articulations.put(startArticulation, startCount);
            }
            
            if (match.end + 1 != excludeMeToo) {
                Integer endArticulation = new Integer(match.end + 1);
                Integer endCount = articulations.get(endArticulation);
                if (endCount == null) {
                    endCount = new Integer(match.end-match.start);
                } else {
                    endCount = new Integer(Math.max(endCount, match.end-match.start));
                }
                articulations.put(endArticulation, endCount);
            }
        }

        if (articulations.size() == 0) {
            return new ArrayList<>(0);
        }
        
        // Now, find the most popular N
        // For this, we create a sorted list and use a cutoff based on how much lower than the best answer we are.
        Articulation[] array = new Articulation[articulations.size()];
        int i = 0;
        for (Integer articulation : articulations.keySet()) {
            array[i++] = new Articulation(articulation, articulations.get(articulation));
        }
        java.util.Arrays.sort(array);
        
        List<Integer> output = new ArrayList<>();
        int cutoffValue = (int)(((double)array[0].count) * 0.99);
        for (Articulation a : array) {
            //System.out.println("Looking at articulation point "+a.articulationPoint+" with score "+a.count+" for group ("+excludeMe+" - "+excludeMeToo+")");
            if (a.count >= cutoffValue) {
                //System.out.println("Considering articulation point "+a.articulationPoint+" with score "+a.count+" for group ("+excludeMe+" - "+excludeMeToo+")");
                output.add(a.articulationPoint);
            }
        }
        
        return output;
    }
    
    private static class Articulation implements Comparable<Articulation> {
        final public Integer articulationPoint;
        final public Integer count;
        
        public Articulation(final Integer articulationPoint, final Integer count) {
            this.articulationPoint = articulationPoint;
            this.count = count;
        }
        
        @Override
        public int compareTo(Articulation other) {
            return -count.compareTo(other.count);
        }
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
            Set<Group> rval = new HashSet<>();
            // Always add self
            rval.add(this);
            
            // Find the most popular articulation points, if any, provided it's not on the group boundary already
            List<Integer> articulationPoints = findMostPopularArticulationPoints(matchesInGroup, startCM, endCM + 1);
            if (articulationPoints.size() == 0) {
                return rval;
            }
            
            // It's very hard to know which single articulation point to pick.  The current strategy is therefore to generate alternate groupings based on
            // the top N points.  This means that we also have to do deduplication of the final groups we are going to be returning.
            for (Integer articulationPoint : articulationPoints) {
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

        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof Group)) {
                return false;
            }
            Group other = (Group)o;
            return chromosomeID.equals(other.chromosomeID) &&
                startCM == other.startCM &&
                endCM == other.endCM;
        }
        
        @Override
        public int hashCode() {
            return chromosomeID.hashCode() + startCM + endCM;
        }
        
    }
}