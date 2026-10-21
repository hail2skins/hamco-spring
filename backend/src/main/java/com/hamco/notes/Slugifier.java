package com.hamco.notes;

import java.util.Locale;

/**
 * Preserves the reference site's ASCII slug algorithm, including its known
 * limitations: accented letters are dropped and duplicate titles may share a
 * slug because note routes use numeric IDs. Validation limits the original
 * title to the database's 255-character column before we ever call this.
 */
final class Slugifier {
    private Slugifier() {
    }

    static String fromTitle(String title) {
        return title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
