package com.iconcile.expense.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tuning knobs for anomaly detection. The requirement fixes the multiplier at 3x, but the
 * sample-size floor and the lookback window are judgement calls, so they are configuration
 * rather than constants.
 */
@ConfigurationProperties(prefix = "anomaly")
public class AnomalyProperties {

    /** An expense is flagged when it exceeds this multiple of its category average. */
    private BigDecimal multiplier = new BigDecimal("3.0");

    /**
     * How many <em>other</em> expenses a category needs before anything in it can be flagged.
     * With a floor of 1, a category holding two rows flags on pure noise.
     */
    private int minSampleSize = 3;

    /** Window used for the average. Null means all time. */
    private Integer lookbackDays;

    /** Sentinel used instead of a null date parameter, so native queries stay untyped-null free. */
    public static final LocalDate BEGINNING_OF_TIME = LocalDate.of(1900, 1, 1);

    public BigDecimal getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(BigDecimal multiplier) {
        this.multiplier = multiplier;
    }

    public int getMinSampleSize() {
        return minSampleSize;
    }

    public void setMinSampleSize(int minSampleSize) {
        this.minSampleSize = minSampleSize;
    }

    public Integer getLookbackDays() {
        return lookbackDays;
    }

    public void setLookbackDays(Integer lookbackDays) {
        this.lookbackDays = lookbackDays;
    }

    /** Resolves the lookback window to a concrete lower bound. */
    public LocalDate sinceDate(LocalDate today) {
        return lookbackDays == null ? BEGINNING_OF_TIME : today.minusDays(lookbackDays);
    }
}
