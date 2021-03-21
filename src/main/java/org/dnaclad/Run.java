package org.dnaclad;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.BufferedReader;

public class Run {


    /*
    private static List<MatchProfile> convertMatches(final Map<String, List<ChromosomeMatch>> keyedSet) throws Exception {    
        // Convert to set of MatchProfiles
        final List<MatchProfile> profiles = new ArrayList<>();
        for (final String matchID : keyedSet.keySet()) {
            final List<ChromosomeMatch> cm = keyedSet.get(matchID);
            // Match depth set to 2 for now
            // Sex is male for now
            profiles.add(new MatchProfile(matchID, true, 2, cm));
        }

        return profiles;
    }
    */
    
    public static void main(final String[] argv) throws Exception {
        if (argv.length != 2) {
            System.err.println("Usage: Run <chromosome_csv_file> <groups_csv_file>");
            System.exit(-1);
        }

        final MatchesData matchesData = new MatchesData(new File[]{new File(argv[0])});
        final GroupsData groupsData = new GroupsData(new File(argv[1]));
        
        /*
        final Collection<MatchProfile> matches = convertMatches(matchFileData);
        final TreeIterator treeIterator = new TreeIterator(matches, 10, 2);
        while (true) {
            final ReducedTree rt = treeIterator.generateNext();
            if (rt == null) {
                break;
            }
        }
        */
        
        // Take the matches and group them.
        // The groups that we come up with will be overlapping, for now.
        // We want a hierarchy such that:
        // (1) The maximum extent of the group is determined by the entire set of overlapping chromosome parts;
        // (2) All the specific chromosome segments are associated with this.
        // Each individual segment is treated as having a potential parent couple associated with it.  But we want to do the work of grouping,
        // for both clarity and generational distance.  The result should be a set of linked groups, where the groups are hierarchical.  The base
        // group includes all the overlapping matches.  The groups on the next level(s) should be defined so that:
        // - they are not overlapping among themselves
        // - they are clearly a generation higher, or more.
        // One way to decompose the group might be to sequentially remove matches and see what is left.  Taking away the longest matches
        // first, one at a time, will quickly cause the remaining matches to de-group.  This forms new groups which can then be broken down further, etc.
        // We get a new level when either there are two or more distinct groups left, OR there is still one group but it is entirely contained within the original group
        // AND it is about 1/2 the size.
        
        // Groups consist of all the member matches taken together.  We want to be able to label each match persistently, but in a different file, so we include the
        // match information along with added match description.  Attaching the description to the entire group is tricky because, as more matches come in,
        // the definition and boundaries of a group may change, and we do not want to lose the important information recorded within.
        // How can we do this association so that it is persistent?
        //
        // The best way is to apply the group label to every match that is part of it.  The match has a reference to multiple group entries it belongs to.  The
        // code can sort out which group is meant by which members there are of that group.
        //
        // Converting the chromosome matches to groups is a bulk process that reads the matches file and outputs a file that links matches to groups.
        // It also takes a group description file which labels certain specific matches as being descriptive of specific groups.
        // The group description file is edited by hand and contains multiple rows having the following information:
        // match_chromosome, match_start, match_end, match_user, group_id, group_description
        //
        // This file, plus the matches file, will be assembled into an output file which contains a list of groups arranged in chromosome, position, and size order:
        // smaller groups first.  Each group will have in it all the matches in that group, before moving on to the next group.
        // chromosome_#, group_id, group_start, group_end, group_description, match_start, match_end, match_user 
        // Order: generally from left to right for every chromosome, EXCEPT that nested groups go smaller to larger, and the smaller groups precede the bigger ones
        // that contain them.  Obviously, matches are repeated because they can be part of multiple hierarchical groups.
    }
}

