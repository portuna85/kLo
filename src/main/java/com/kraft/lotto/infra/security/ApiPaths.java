package com.kraft.lotto.infra.security;

public final class ApiPaths {

    private ApiPaths() {
    }

    public static final String RECOMMEND_LEGACY = "/api/recommend";
    public static final String RECOMMEND_V1 = "/api/v1/recommend";

    public static final String COLLECT_REFRESH_LEGACY = "/api/winning-numbers/refresh";
    public static final String COLLECT_REFRESH_V1 = "/api/v1/winning-numbers/refresh";

    public static final String ADMIN_PREFIX = "/admin/";
    public static final String ADMIN_COLLECT_NEXT = "/admin/lotto/draws/collect-next";
    public static final String ADMIN_COLLECT_MISSING = "/admin/lotto/draws/collect-missing";
    public static final String ADMIN_BACKFILL = "/admin/lotto/draws/backfill";
}
