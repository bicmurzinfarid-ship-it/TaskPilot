package com.example.desktopwindows;

public class Session {
    private static String token;
    private static Long userId;

    public static void setToken(String token) { Session.token = token; }
    public static String getToken() { return token; }

    public static void setUserId(Long userId) { Session.userId = userId; }
    public static Long getUserId() { return userId; }
}
