package org.dnaclad;

import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.Set;
import java.util.HashSet;

public class GroupsData {

    /** group ID to other data for the group, e.g. how to identify it */
    private final Map<String, GroupDescription> keyedGroupInfo = new HashMap<>();
    /** group starting CM to group description */
    private final Map<GroupKey, GroupDescription> startInfo = new HashMap<>();
    
    public GroupsData(final File file) throws Exception {
        readGroupsFile(file);
    }
    
    private void processGroupsLine(final String line) throws Exception {
        final String[] columns = line.split(",");
        if (columns.length != 5) {
            throw new Exception("Bad line: '"+line+"'");
        }
        final String groupID = columns[0];
        final String groupDescription = columns[1];
        final String chromosomeID = columns[2];
        final int startCM = Integer.parseInt(columns[3]);
        final int endCM = Integer.parseInt(columns[4]);

        final GroupDescription gd = new GroupDescription(groupID, groupDescription, chromosomeID, startCM, endCM);
        final GroupKey gk = new GroupKey(gd);
        keyedGroupInfo.put(groupID, gd);
        final GroupDescription oldGd = startInfo.get(gk);
        if (oldGd != null) {
            throw new Exception("Group information has two different descriptions; see group ID '"+groupID+"' and group ID '"+oldGd.groupID+"'");
        }
        startInfo.put(gk, gd);
    }
    
    private void readGroupsFile(final File file) throws Exception {
        final InputStream is = new FileInputStream(file);
        try {
            final Reader r = new InputStreamReader(is, "utf-8");
            final BufferedReader br = new BufferedReader(r);
            boolean firstLine = true;
            while (true) {
                final String line = br.readLine();
                if (line == null) {
                    break;
                }
                if (firstLine) {
                    firstLine = false;
                } else {
                    processGroupsLine(line.trim());
                }
            }
        } finally {
            is.close();
        }
    }

    public static class GroupKey {
        public final GroupDescription groupDescription;
        
        public GroupKey(final GroupDescription groupDescription) {
            this.groupDescription = groupDescription;
        }
        
        @Override
        public int hashCode() {
            return groupDescription.groupID.hashCode() + groupDescription.chromosomeID.hashCode() + Integer.hashCode(groupDescription.startCM) + Integer.hashCode(groupDescription.endCM);
        }
        
        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof GroupKey)) {
                return false;
            }
            final GroupKey other = (GroupKey)o;
            return groupDescription.groupID.equals(other.groupDescription.groupID) &&
                groupDescription.chromosomeID.equals(other.groupDescription.chromosomeID) &&
                groupDescription.startCM == other.groupDescription.startCM &&
                groupDescription.endCM == other.groupDescription.endCM;
        }
    }
        
    public static class GroupDescription {
        public final String groupID;
        public final String groupDescription;
        public final String chromosomeID;
        public final int startCM;
        public final int endCM;
        
        public GroupDescription(final String groupID,
            final String groupDescription,
            final String chromosomeID,
            final int startCM,
            final int endCM) {
            this.groupID = groupID;
            this.groupDescription = groupDescription;
            this.chromosomeID = chromosomeID;
            this.startCM = startCM;
            this.endCM = endCM;
        }
        
    }
}
