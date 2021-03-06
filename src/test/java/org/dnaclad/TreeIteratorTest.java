package org.dnaclad;

import java.util.Collection;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class TreeIteratorTest {

    @Test
    public void testMatchState() {
        TreeIterator.MatchState matchState = new TreeIterator.MatchState(null,
                                                                         3,
                                                                         10,
                                                                         2,
                                                                         true);
        int matchStateCounter = 0;
        while (!matchState.atEnd()) {
            //System.out.println("Have a result");
            matchStateCounter++;
            final Collection<? extends Path> results = matchState.getCurrentPathSet();
            //System.out.println("Successfully fetched result");
            matchState.advance();
        }

        assertEquals(188, matchStateCounter);
        //System.out.println("Total unique combinations: "+matchStateCounter);

        matchStateCounter = 0;
        matchState = new TreeIterator.MatchState(null,
                                                 5,
                                                 9,
                                                 1,
                                                 true);
        while (!matchState.atEnd()) {
            //System.out.println("Have a result");
            matchStateCounter++;
            final Collection<? extends Path> results = matchState.getCurrentPathSet();
            //System.out.println("Successfully fetched result");
            matchState.advance();
        }

        assertEquals(5, matchStateCounter);
    }
}
