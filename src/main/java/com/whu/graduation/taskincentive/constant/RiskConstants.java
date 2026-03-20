package com.whu.graduation.taskincentive.constant;

/**
 * 风控常量定义
 */
public final class RiskConstants {

    private RiskConstants() {}

    public static final String REASON_PASS = "PASS";
    public static final String REASON_REPLAY = "REPLAY";
    public static final String REASON_BLACKLIST = "BLACKLIST";
    public static final String REASON_WHITELIST = "WHITELIST";
    public static final String REASON_QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
    public static final String REASON_RULE_HIT = "RULE_HIT";
    public static final String REASON_DEFAULT = "DEFAULT";

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 2;

    public static final String CACHE_RULES_KEY = "risk:rules:active";
    public static final String CACHE_BLACKLIST_KEY = "risk:blacklist";
    public static final String CACHE_WHITELIST_KEY = "risk:whitelist";
    public static final String CACHE_QUOTA_KEY = "risk:quota";
    public static final String CACHE_DECISION_DEDUP_PREFIX = "risk:decision:";
}
