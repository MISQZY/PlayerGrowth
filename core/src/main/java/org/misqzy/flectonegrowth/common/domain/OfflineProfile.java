package org.misqzy.flectonegrowth.common.domain;

/**
 * A read-only, one-shot snapshot of a player's persisted growth state, for
 * callers that need this data without the player being online (e.g. a
 * PlaceholderAPI lookup for an offline leaderboard/hologram) - unlike
 * {@link PlayerProfile}, this isn't cached or kept in sync with anything;
 * it's built fresh from {@code Storage} for one read and then discarded.
 * See {@code GrowthEngine#loadOffline}.
 */
public record OfflineProfile(Gender gender, Double customScale, long targetGrowthSeconds, long playedSeconds) {

    public boolean hasCustomScale() {
        return customScale != null;
    }
}
