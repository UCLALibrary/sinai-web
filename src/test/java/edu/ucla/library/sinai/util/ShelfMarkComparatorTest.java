package edu.ucla.library.sinai.util;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

public class ShelfMarkComparatorTest {
    private final Logger LOGGER = LoggerFactory.getLogger(ShelfMarkComparatorTest.class);

    @Test
    public void testShelfMarkComparator() {
        ShelfMarkComparator smc = new ShelfMarkComparator();

        List<String[]> testCases = new ArrayList<String[]>();

        // less than
        testCases.add(new String[]{"Arabic 518", "Arabic 588", "<"});
        testCases.add(new String[]{"Arabic 588", "Arabic NF 8", "<"});
        testCases.add(new String[]{"Arabic NF 8", "Arabic NF 28", "<"});
        testCases.add(new String[]{"CPA NF 5", "CPA NF 7", "<"});
        testCases.add(new String[]{"CPA NF 7", "Georgian 10", "<"});
        testCases.add(new String[]{"Georgian 10", "Georgian 34", "<"});
        testCases.add(new String[]{"Georgian 49", "Georgian NF 7", "<"});
        testCases.add(new String[]{"Georgian NF 13", "Georgian NF frg. 68a", "<"});
        testCases.add(new String[]{"Georgian NF frg. 68a", "Georgian NF frg. 72a", "<"});
        testCases.add(new String[]{"Greek 212", "Greek NF M 48", "<"});
        testCases.add(new String[]{"Greek NF MG 14", "Greek NF M 48", "<"});

        // greater than
        testCases.add(new String[]{"Arabic 588", "Arabic 518", ">"});
        testCases.add(new String[]{"Arabic NF 8", "Arabic 588", ">"});
        testCases.add(new String[]{"Arabic NF 28", "Arabic NF 8", ">"});
        testCases.add(new String[]{"CPA NF 7", "CPA NF 5", ">"});
        testCases.add(new String[]{"Georgian 10", "CPA NF 7", ">"});
        testCases.add(new String[]{"Georgian 34", "Georgian 10", ">"});
        testCases.add(new String[]{"Georgian NF 7", "Georgian 49", ">"});
        testCases.add(new String[]{"Georgian NF frg. 68a", "Georgian NF 13", ">"});
        testCases.add(new String[]{"Georgian NF frg. 72a", "Georgian NF frg. 68a", ">"});
        testCases.add(new String[]{"Greek NF M 48", "Greek 212", ">"});
        testCases.add(new String[]{"Greek NF M 48", "Greek NF MG 14", ">"});

        // equal to
        testCases.add(new String[]{"Arabic 518", "Arabic 518", "="});
        testCases.add(new String[]{"Arabic NF 8", "Arabic NF 8", "="});
        testCases.add(new String[]{"Georgian NF frg. 68a", "Georgian NF frg. 68a", "="});
        testCases.add(new String[]{"Greek NF MG 14", "Greek NF MG 14", "="});
        testCases.add(new String[]{"Greek NF M 48", "Greek NF M 48", "="});

        Iterator<String[]> it = testCases.iterator();

        while (it.hasNext()) {
            String[] testCase = it.next();

            switch (testCase[2]) {
            case "<":
                assertTrue(smc.compare(testCase[0], testCase[1]) < 0);
                break;
            case "=":
                assertTrue(smc.compare(testCase[0], testCase[1]) == 0);
                break;
            case ">":
            default:
                assertTrue(smc.compare(testCase[0], testCase[1]) > 0);
                break;
            }
        }

    }
}
