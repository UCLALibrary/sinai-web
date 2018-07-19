package edu.ucla.library.sinai.util;

import java.util.Comparator;

import io.vertx.core.json.JsonObject;

/**
 * Used for sorting JsonObjects first by primary_language_s, then by author_s, then by work_s.
 */
public class UTOComparator implements Comparator<JsonObject> {

    @Override
    public int compare(JsonObject o1, JsonObject o2) {
        // https://sinai-lib.atlassian.net/browse/PAL-60?focusedCommentId=10191&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-10191
        final String language1, language2, author1, author2, work1, work2;
        final Integer languageComparison, authorComparison, workComparison;

        language1 = o1.getString("primary_language_s");
        language2 = o2.getString("primary_language_s");
        if (language1 != null && language2 != null) {
            languageComparison = language1.compareTo(language2);
            if (languageComparison != 0) {
                return languageComparison;
            }
        } else if (language1 != null && language2 == null) {
            return -1;
        } else if (language1 == null && language2 != null) {
            return 1;
        }

        author1 = o1.getString("author_s");
        author2 = o2.getString("author_s");
        if (author1 != null && author2 != null) {
            authorComparison = author1.compareTo(author2);
            if (authorComparison != 0) {
                return authorComparison;
            }
        } else if (author1 != null && author2 == null) {
            return -1;
        } else if (author1 == null && author2 != null) {
            return 1;
        }

        work1 = o1.getString("work_s");
        work2 = o2.getString("work_s");
        if (work1 != null && work2 != null) {
            // remove first pair of parenthesis, since this is the only possible other first character that is not alphanumeric
            if (work1.charAt(0) == '(') {
                work1.replaceFirst("\\(", "");
                work1.replaceFirst("\\)", "");
            }
            if (work2.charAt(0) == '(') {
                work2.replaceFirst("\\(", "");
                work2.replaceFirst("\\)", "");
            }
            workComparison = work1.compareTo(work2);
            if (workComparison != 0) {
                return workComparison;
            }
        // work should never be null, but just in case, non-null goes first
        } else if (work1 != null && work2 == null) {
            return -1;
        } else if (work1 == null && work2 != null) {
            return 1;
        }

        return 0;
    }
}
