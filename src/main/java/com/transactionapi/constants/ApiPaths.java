package com.transactionapi.constants;

public final class ApiPaths {
    public static final String API_V1 = "/api/v1";
    public static final String HEALTH = API_V1 + "/health";
    public static final String TRADES = API_V1 + "/trades";
    public static final String ADMIN = API_V1 + "/admin";
    public static final String ADMIN_USERS = ADMIN + "/users";
    public static final String SHARES = API_V1 + "/shares";

    private ApiPaths() {
    }
}
