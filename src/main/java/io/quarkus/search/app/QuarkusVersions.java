package io.quarkus.search.app;

import java.util.Comparator;

public final class QuarkusVersions {
    private QuarkusVersions() {
    }

    public static final String LATEST = "latest";
    public static final String MAIN = "main";
    public static final String V3_2 = "3.2";

    public static final Comparator<String> COMPARATOR = new Comparator<String>() {
        @Override
        public int compare(String left, String right) {
            if (left.equals(right)) {
                return 0;
            } else if (left.equals(MAIN)) {
                return 1;
            } else if (right.equals(MAIN)) {
                return -1;
            } else if (left.equals(LATEST)) {
                // "latest" actually means "latest non-snapshot", so it's older than main.
                return 1;
            } else if (right.equals(LATEST)) {
                return -1;
            } else {
                return left.compareTo(right);
            }
        }
    };

}
