package com.smarttrader.v2.model;

/**
 * Opportunity severity, per V2_TECH_SPEC_v2.5.md section 7. Drives which notification
 * channels NotificationFacadeService fans out to.
 */
public enum Severity {
    INFO,
    HIGH,
    CRITICAL
}
