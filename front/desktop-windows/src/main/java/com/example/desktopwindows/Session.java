package com.example.desktopwindows;

public class Session {
    private static String token;

    public static void setToken(String token){
        Session.token =token;
    }

    public static String getToken(){
        return token;
    }
}
