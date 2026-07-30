package org.misqzy.flectonegrowth.common.domain;

/**
 * One player's persisted playtime record - modeled on FlectonePulse's own
 * {@code fp_time} table ({@code first}/{@code last}/{@code total}/{@code sessions}),
 * decompiled from {@code net.flectone.pulse:core} to confirm the shape (see
 * {@code ARCHITECTURE.md} "Playtime tracking"). Unlike FlectonePulse's millisecond
 * fields, {@code first}/{@code last}/{@code total} here are all in seconds, matching
 * this project's existing convention ({@code growTimeSeconds()}, {@code targetGrowthSeconds()}).
 *
 * @param first    epoch second of the player's first-ever join
 * @param last     epoch second of the last persisted checkpoint (join or quit)
 * @param total    accumulated played seconds as of {@code last} - excludes whatever
 *                 elapsed since then if the player is still online
 * @param sessions number of times this player has joined
 */
public record PlayTime(long first, long last, long total, int sessions) {}
