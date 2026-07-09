package com.zenya.auth;

import com.zenya.ZenyaClient;

/**
 * Stub implementation of ZenyaAuth to allow compilation.
 */
public final class ZenyaAuth {
    private ZenyaAuth() {}

    public static boolean isAuthenticated() {
        return true;
    }

    public static String getAuthBaseUrl() {
        return "http://localhost";
    }

    public static String getJwt() {
        return "stub-token";
    }

    public static void quickTamperCheck() {
        // stub
    }
}
