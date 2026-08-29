package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * One published measurement.
 *
 * <p>A metric is either measured or explicitly {@link #unavailable(String, String, String)}. There is
 * no third state and no default value, because a metric silently defaulting to zero would be
 * indistinguishable from a real measurement of zero. Reporting {@code UNAVAILABLE} with a reason is
 * the only permitted way to publish a metric that could not be computed.
 *
 * @param metricId stable identifier
 * @param label human-readable name
 * @param value the measured value, or empty when unavailable
 * @param unit the unit of measurement
 * @param unavailableReason why the metric could not be measured, when it could not
 */
public record MetricResult(
        String metricId,
        String label,
        Optional<BigDecimal> value,
        Unit unit,
        String unavailableReason) {

    /**
     * The unit a metric is expressed in.
     */
    public enum Unit {
        /** A plain count. */
        COUNT,
        /** A percentage from 0 to 100. */
        PERCENT,
        /** Milliseconds of wall-clock time. */
        MILLISECONDS,
        /** Abstract burden units used by the touch-count model. */
        TOUCH_UNITS,
        /** A ratio from 0 to 1. */
        RATIO
    }

    public MetricResult {
        metricId = Identifiers.requireStable("metricId", metricId);
        label = Identifiers.requireText("label", label);
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(unit, "unit");
        unavailableReason = unavailableReason == null ? "" : unavailableReason.strip();

        if (value.isPresent() == !unavailableReason.isEmpty()) {
            throw new IllegalArgumentException(
                    "Metric " + metricId
                            + " must have exactly one of a value or an unavailable reason");
        }
        if (value.isPresent() && unit == Unit.PERCENT) {
            BigDecimal v = value.get();
            if (v.compareTo(BigDecimal.ZERO) < 0 || v.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException(
                        "Metric " + metricId + " is a percentage outside [0,100]: " + v);
            }
        }
    }

    public static MetricResult count(String metricId, String label, long count) {
        return new MetricResult(
                metricId, label, Optional.of(BigDecimal.valueOf(count)), Unit.COUNT, null);
    }

    /**
     * Creates a percentage metric, rounded to two decimal places with {@link RoundingMode#HALF_EVEN}.
     *
     * <p>Rounding is fixed here rather than at the presentation layer so that the canonical output —
     * and therefore the run hash — does not depend on how a report chooses to display the number.
     *
     * @param metricId stable identifier
     * @param label human-readable name
     * @param numerator the numerator
     * @param denominator the denominator; when zero the metric is reported unavailable
     * @return a measured percentage, or an unavailable metric when the denominator is zero
     */
    public static MetricResult percent(
            String metricId, String label, long numerator, long denominator) {
        if (denominator == 0) {
            return unavailable(
                    metricId, label, "The denominator is zero, so no percentage is defined.");
        }
        BigDecimal pct = BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_EVEN);
        return new MetricResult(metricId, label, Optional.of(pct), Unit.PERCENT, null);
    }

    public static MetricResult millis(String metricId, String label, long millis) {
        return new MetricResult(
                metricId, label, Optional.of(BigDecimal.valueOf(millis)), Unit.MILLISECONDS, null);
    }

    public static MetricResult touchUnits(String metricId, String label, long units) {
        return new MetricResult(
                metricId, label, Optional.of(BigDecimal.valueOf(units)), Unit.TOUCH_UNITS, null);
    }

    public static MetricResult unavailable(String metricId, String label, String reason) {
        return new MetricResult(
                metricId, label, Optional.empty(), Unit.COUNT, Identifiers.requireText("reason", reason));
    }

    public boolean measured() {
        return value.isPresent();
    }

    public String display() {
        return value.map(v -> switch (unit) {
                    case PERCENT -> v.toPlainString() + "%";
                    case MILLISECONDS -> v.toPlainString() + " ms";
                    case TOUCH_UNITS -> v.toPlainString() + " touch units";
                    case RATIO, COUNT -> v.toPlainString();
                })
                .orElse("UNAVAILABLE");
    }

    public Json toJson() {
        return Json.obj()
                .put("metricId", metricId)
                .put("label", label)
                .put("unit", unit)
                .put("value", value.map(Json::of).orElse(Json.NULL))
                .put("unavailableReason", unavailableReason)
                .build();
    }
}
