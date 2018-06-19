package edu.ucla.library.sinai.util;

import java.util.Comparator;

import io.vertx.core.json.JsonObject;

/**
 * Used for sorting two JsonObjects by comparing their "manuscript" property's "shelf_mark_s" property.
 */
public class SearchResultComparator implements Comparator<JsonObject> {

    @Override
    public int compare(JsonObject arg0, JsonObject arg1) {
        String shelfMark0 = arg0.getJsonObject("manuscript").getString("shelf_mark_s");
        String shelfMark1 = arg1.getJsonObject("manuscript").getString("shelf_mark_s");

        return new ShelfMarkComparator().compare(shelfMark0, shelfMark1);
    }
}
