package edu.ucla.library.sinai.util;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Used for sorting manuscript shelf marks.
 */
public class ShelfMarkComparator implements Comparator<String> {
    @Override
    public int compare(String arg0, String arg1) {

        Pattern shelfMarkPattern = Pattern.compile("(?<language>[a-zA-Z]+)(?: (?<newFind>NF(?: (?<newFindType>(?:[Ff]rg\\.?)|(?:MG?)))?))? (?<number>\\d+)(?<letter>[a-z])?");

        Matcher shelfMarkMatcher0 = shelfMarkPattern.matcher(arg0);
        Matcher shelfMarkMatcher1 = shelfMarkPattern.matcher(arg1);

        shelfMarkMatcher0.find();
        shelfMarkMatcher1.find();

        // Sort language part lexicographically
        Integer languageComparison = shelfMarkMatcher0.group("language").compareTo(shelfMarkMatcher1.group("language"));

        if (languageComparison != 0) {
            return languageComparison;
        }

        // Manuscripts before new finds
        String newFind0 = shelfMarkMatcher0.group("newFind");
        String newFind1 = shelfMarkMatcher1.group("newFind");

        if (newFind0 == null && newFind1 != null) {
            return -1;
        } else if (newFind0 != null && newFind1 == null) {
            return 1;
        } else if (newFind0 != null && newFind1 != null) {
            String newFindType0 = shelfMarkMatcher0.group("newFindType");
            String newFindType1 = shelfMarkMatcher1.group("newFindType");

            // If new find type is present, it will be "frg." or "frg" unless language is Greek
            if (newFindType0 == null && newFindType1 != null) {
                return -1;
            } else if (newFindType0 != null && newFindType1 == null) {
                return 1;
            } else if (newFindType0 != null && newFindType1 != null && !newFindType0.equals(newFindType1)) {
                if (shelfMarkMatcher0.group("language").equals("Greek")) {
                    // MG before M
                    if (newFindType0.equals("MG")) {
                        return -1;
                    } else if (newFindType0.equals("M")) {
                        return 1;
                    } else {
                        throw new Error("unrecognized new find type");
                    }
                } else {
                    throw new Error("only Greek has multiple new find types");
                }
            }
        }

        // Sort number part numerically
        Integer numberComparison = new Integer(shelfMarkMatcher0.group("number")).compareTo(new Integer(shelfMarkMatcher1.group("number")));
        if (numberComparison != 0) {
            return numberComparison;
        }

        // Check for optional letter part
        String letter0 = shelfMarkMatcher0.group("letter");
        String letter1 = shelfMarkMatcher1.group("letter");

        if (letter0 == null && letter1 != null) {
            return -1;
        } else if (letter0 != null && letter1 == null) {
            return 1;
        } else if (letter0 != null && letter1 != null) {
            return letter0.compareTo(letter1);
        }

        // The strings are the same
        return 0;
    }
}
