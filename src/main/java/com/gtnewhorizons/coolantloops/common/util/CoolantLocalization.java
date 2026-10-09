package com.gtnewhorizons.coolantloops.common.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.StatCollector;

public final class CoolantLocalization {

    private static final Map<String, String> FALLBACK_MAP = new HashMap<>();

    static {
        loadLangFile("/assets/coolantloops/lang/en_US.lang");
        injectIntoMinecraftStringTranslate();
    }

    private CoolantLocalization() {}

    public static void loadLangFile(String resourcePath) {
        try (InputStream is = CoolantLocalization.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int eq = line.indexOf('=');
                        if (eq > 0) {
                            String key = line.substring(0, eq)
                                .trim();
                            String val = line.substring(eq + 1)
                                .trim();
                            FALLBACK_MAP.put(key, val);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static void injectIntoMinecraftStringTranslate() {
        try {
            Field fName = StatCollector.class.getDeclaredField("localizedName");
            fName.setAccessible(true);
            Object st = fName.get(null);
            if (st != null) {
                Field f = st.getClass()
                    .getDeclaredField("languageList");
                f.setAccessible(true);
                Map<String, String> list = (Map<String, String>) f.get(st);
                if (list != null) {
                    list.putAll(FALLBACK_MAP);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static String get(String key) {
        if (StatCollector.canTranslate(key)) {
            return StatCollector.translateToLocal(key);
        }
        return FALLBACK_MAP.getOrDefault(key, key);
    }

    public static String get(String key, String fallback) {
        if (StatCollector.canTranslate(key)) {
            return StatCollector.translateToLocal(key);
        }
        return FALLBACK_MAP.getOrDefault(key, fallback);
    }

    public static String format(String key, Object... args) {
        if (StatCollector.canTranslate(key)) {
            return StatCollector.translateToLocalFormatted(key, args);
        }
        String pattern = FALLBACK_MAP.get(key);
        if (pattern != null) {
            try {
                return String.format(pattern, args);
            } catch (Exception e) {
                return pattern;
            }
        }
        return String.format(key, args);
    }
}
