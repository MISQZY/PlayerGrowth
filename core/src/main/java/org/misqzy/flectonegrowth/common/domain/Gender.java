package org.misqzy.flectonegrowth.common.domain;

/**
 * A configurable gender variant. Each gender has its own maximum growth
 * scale, so networks can give e.g. "female" a lower max height than "male"
 * (or add entirely custom, non-binary variants) purely through config.
 */
public final class Gender {

    public static final String DEFAULT_KEY = "male";

    private final String key;
    private final double maxScale;

    public Gender(String key, double maxScale) {
        this.key = key.toLowerCase();
        this.maxScale = maxScale;
    }

    public String key() {
        return key;
    }

    public double maxScale() {
        return maxScale;
    }

    @Override
    public String toString() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Gender g && key.equals(g.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }
}
