package com.example.desktopwindows;

public class Session {
    private static String token;
    private static Long userId;

    /**
     * Базовый URL бэкенда. Можно переопределить через JVM-параметр:
     *   -Dapi.url=http://192.168.1.42:8080
     */
    public static final String API_BASE =
            System.getProperty("api.url", "http://localhost:8080");

    public static void setToken(String token) { Session.token = token; }
    public static String getToken() { return token; }

    public static void setUserId(Long userId) { Session.userId = userId; }
    public static Long getUserId() { return userId; }
}
