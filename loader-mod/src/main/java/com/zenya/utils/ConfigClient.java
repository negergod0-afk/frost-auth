package com.zenya.utils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ConfigClient {

    public record Entry(String name, long size, long updatedAt) {}

    private ConfigClient() {}

    public static boolean upload(String name, String data) {
        return false;
    }

    public static List<Entry> list() {
        return new ArrayList<>();
    }

    public static String download(String name) {
        return null;
    }

    public static boolean delete(String name) {
        return false;
    }
}
