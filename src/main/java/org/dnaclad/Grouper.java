package org.dnaclad;

import java.util.Collection;
import java.util.PriorityQueue;
import java.util.List;
import java.util.ArrayList;

public class Grouper {
    
    private final int minCM;
    private PriorityQueue<Group> groups;
    
    public Grouper(int minCM) {
        this.minCM = minCM;
        this.groups = new PriorityQueue<>();
    }
    
    public void addMatch(final ChromosomeMatch match) {
        // Create a group and add it to the list
        final Group newGroup = new Group(match);
        groups.add(newGroup);
    }
    
    public void writeOutGroups(final GroupWriter out) {
        // First, assemble all the groups
        
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
        
        // Grouping complete.
        // Output organized by length.  Later we can rearrange this.
        while (true) {
            Group longest = groups.poll();
            if (longest == null) {
                break;
            }
            // Build a list of chromosome matches
            out.write(longest.chromosomeID, longest.startCM, longest.endCM, longest.getMatchesInGroup());
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
    
    /**
    * A group is a collection of matches that all overlap.
    */
    public static class Group implements Comparable<Group> {
        protected PriorityQueue<ChromosomeMatch> matchesInGroup = new PriorityQueue<>();
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
            final List<ChromosomeMatch> rval = new ArrayList<>();
            final PriorityQueue<ChromosomeMatch> newQueue = new PriorityQueue<>();
            while (true) {
                final ChromosomeMatch cm = matchesInGroup.poll();
                if (cm == null) {
                    break;
                }
                newQueue.add(cm);
                rval.add(cm);
            }
            matchesInGroup = newQueue;
            return rval;
        }
        
        public Collection<Group> decompose() {
            // Not yet implemented
            return new ArrayList<>(0);
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
            // Merge the priority queues
            while (true) {
                final ChromosomeMatch cm = group.matchesInGroup.poll();
                if (cm == null) {
                    return true;
                }
                matchesInGroup.add(cm);
            }
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