package com.dailyproject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gera pelo menos {@code minLines} linhas de código Java válido a cada execução.
 */
public final class CodeGenerator {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Path outputDir;
    private final int minLines;

    public CodeGenerator(Path repoRoot, int minLines) {
        this.outputDir = repoRoot.resolve("src/main/java/com/dailyproject/generated");
        this.minLines = Math.max(100, minLines);
    }

    public GenerationResult generate() throws IOException {
        Files.createDirectories(outputDir);

        String className = "DailySnippet_" + LocalDateTime.now().format(STAMP);
        Path file = outputDir.resolve(className + ".java");

        List<String> lines = buildSource(className);
        Files.write(file, lines, StandardCharsets.UTF_8);

        return new GenerationResult(file, className, lines.size());
    }

    private List<String> buildSource(String className) {
        List<String> lines = new ArrayList<>();
        lines.add("package com.dailyproject.generated;");
        lines.add("");
        lines.add("import java.time.Instant;");
        lines.add("import java.util.ArrayList;");
        lines.add("import java.util.LinkedHashMap;");
        lines.add("import java.util.List;");
        lines.add("import java.util.Map;");
        lines.add("import java.util.Objects;");
        lines.add("");
        lines.add("/**");
        lines.add(" * Snippet gerado automaticamente em " + LocalDateTime.now() + ".");
        lines.add(" * Faz parte do fluxo diário de commits do DailyProject.");
        lines.add(" */");
        lines.add("public final class " + className + " {");
        lines.add("");
        lines.add("    private final String id;");
        lines.add("    private final Instant createdAt;");
        lines.add("    private final Map<String, Integer> metrics;");
        lines.add("");
        lines.add("    public " + className + "(String id) {");
        lines.add("        this.id = Objects.requireNonNull(id, \"id\");");
        lines.add("        this.createdAt = Instant.now();");
        lines.add("        this.metrics = new LinkedHashMap<>();");
        lines.add("        bootstrap();");
        lines.add("    }");
        lines.add("");
        lines.add("    private void bootstrap() {");

        int methodIndex = 0;
        while (lines.size() < minLines - 25) {
            methodIndex++;
            appendMetricBlock(lines, methodIndex);
            if (lines.size() >= minLines - 25) {
                break;
            }
            appendHelperMethodCall(lines, methodIndex);
        }

        lines.add("    }");
        lines.add("");

        for (int i = 1; i <= methodIndex; i++) {
            appendHelperMethod(lines, i);
        }

        lines.add("    public String getId() {");
        lines.add("        return id;");
        lines.add("    }");
        lines.add("");
        lines.add("    public Instant getCreatedAt() {");
        lines.add("        return createdAt;");
        lines.add("    }");
        lines.add("");
        lines.add("    public Map<String, Integer> snapshot() {");
        lines.add("        return Map.copyOf(metrics);");
        lines.add("    }");
        lines.add("");
        lines.add("    public int total() {");
        lines.add("        return metrics.values().stream().mapToInt(Integer::intValue).sum();");
        lines.add("    }");
        lines.add("");
        lines.add("    public List<String> keys() {");
        lines.add("        return new ArrayList<>(metrics.keySet());");
        lines.add("    }");
        lines.add("");
        lines.add("    @Override");
        lines.add("    public String toString() {");
        lines.add("        return \"" + className + "{id=\" + id + \", total=\" + total() + \"}\";");
        lines.add("    }");
        lines.add("}");

        // Garante mínimo absoluto se ainda faltar.
        while (lines.size() < minLines) {
            lines.add(lines.size() - 1, "    // padding line " + lines.size());
        }

        return lines;
    }

    private void appendMetricBlock(List<String> lines, int index) {
        int value = ThreadLocalRandom.current().nextInt(1, 500);
        lines.add("        metrics.put(\"metric_" + index + "\", " + value + ");");
        lines.add("        metrics.put(\"metric_" + index + "_sq\", " + (value * value) + ");");
        lines.add("        metrics.put(\"metric_" + index + "_dbl\", " + (value * 2) + ");");
    }

    private void appendHelperMethodCall(List<String> lines, int index) {
        lines.add("        applyStep" + index + "();");
    }

    private void appendHelperMethod(List<String> lines, int index) {
        lines.add("    private void applyStep" + index + "() {");
        lines.add("        int base = metrics.getOrDefault(\"metric_" + index + "\", 0);");
        lines.add("        metrics.put(\"step_" + index + "_a\", base + " + index + ");");
        lines.add("        metrics.put(\"step_" + index + "_b\", base * " + index + ");");
        lines.add("        metrics.put(\"step_" + index + "_c\", Math.abs(base - " + index + "));");
        lines.add("        if (base % 2 == 0) {");
        lines.add("            metrics.put(\"step_" + index + "_even\", 1);");
        lines.add("        } else {");
        lines.add("            metrics.put(\"step_" + index + "_odd\", 1);");
        lines.add("        }");
        lines.add("    }");
        lines.add("");
    }

    public record GenerationResult(Path file, String className, int lineCount) {
    }
}
