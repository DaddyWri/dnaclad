package org.dnaclad;

import java.util.Collection;
import org.junit.Test;

public class TreeIteratorTest {

    @Test
    public void testMatchState() {
        final TreeIterator.MatchState matchState = new TreeIterator.MatchState(3,
                                                                               10,
                                                                               2,
                                                                               true);
        int matchStateCounter = 0;
        while (!matchState.atEnd()) {
            System.out.println("Have a result");
            matchStateCounter++;
            final Collection<? extends Path> results = matchState.getCurrentPathSet();
            System.out.println("Successfully fetched result");
            matchState.advance();
        }

        System.out.println("Total unique combinations: "+matchStateCounter);
    }
}
