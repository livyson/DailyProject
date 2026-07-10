package com.dailyproject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Carrega pares KEY=VALUE de um arquivo .env simples (sem export).
 */
final class EnvLoader {

    private EnvLoader() {
    }

    static Map<String, String> load(Path envFile) {
        Map<String, String> map = new HashMap<>();
        if (envFile == null || !Files.isRegularFile(envFile)) {
            return map;
        }
        try (Stream<String> lines = Files.lines(envFile, StandardCharsets.UTF_8)) {
            lines.map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .forEach(line -> {
                        int eq = line.indexOf('=');
                        if (eq <= 0) {
                            return;
                        }
                        String key = line.substring(0, eq).trim();
                        String value = line.substring(eq + 1).trim();
                        if ((value.startsWith("\"") && value.endsWith("\""))
                                || (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        map.put(key, value);
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + envFile + ": " + e.getMessage(), e);
        }
        return map;
    }
}
