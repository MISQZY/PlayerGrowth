package org.misqzy.flectonegrowth.common.config.migration;

/**
 * Compares dotted-numeric version strings (e.g. {@code "0.1.1"} vs
 * {@code "0.1.10"}) segment by segment, numerically rather than lexically -
 * a plain {@code String.compareTo} would rank {@code "0.1.10"} before
 * {@code "0.1.2"}. Needed now that {@code config.yml}'s {@code version} is
 * the plugin's real semver (see {@link ConfigMigrations}) rather than a
 * simple incrementing integer.
 *
 * <p>Missing trailing segments compare as {@code 0} (so {@code "0.1"} equals
 * {@code "0.1.0"}), and a non-numeric segment also compares as {@code 0}
 * rather than throwing - malformed input degrades gracefully instead of
 * crashing a plugin reload.</p>
 *
 * <p>Public (not package-private) because each platform module's
 * {@code ConfigMigrator} needs it too, for the same on-disk-vs-bundled
 * {@code version} comparison this class already does for
 * {@link ConfigMigrationStep} ranges.</p>
 */
public final class VersionComparator {

    private VersionComparator() {}

    public static int compare(String a, String b) {
        String[] partsA = a.split("\\.");
        String[] partsB = b.split("\\.");
        int length = Math.max(partsA.length, partsB.length);

        for (int i = 0; i < length; i++) {
            int segmentA = i < partsA.length ? parseSegment(partsA[i]) : 0;
            int segmentB = i < partsB.length ? parseSegment(partsB[i]) : 0;
            int cmp = Integer.compare(segmentA, segmentB);
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private static int parseSegment(String segment) {
        try {
            return Integer.parseInt(segment.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
