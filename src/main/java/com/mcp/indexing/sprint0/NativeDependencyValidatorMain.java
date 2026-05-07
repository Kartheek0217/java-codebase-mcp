package com.mcp.indexing.sprint0;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class NativeDependencyValidatorMain {
    public static void main(String[] args) throws Exception {
        CliArgs cli = CliArgs.parse(args);
        List<String> libs = resolveLibs(cli);
        List<Path> searchPaths = resolveSearchPaths(cli);

        List<NativeLibCheck> checks = new ArrayList<>();
        for (String lib : libs) {
            checks.add(check(lib, searchPaths, resolveExpectedClass(cli, lib)));
        }

        NativeValidationReport report = new NativeValidationReport(Sprint0ReportWriter.metadata(), checks);
        Sprint0ReportWriter writer = new Sprint0ReportWriter();
        Path out = writer.writeJson(Paths.get("target", "sprint0", "native-validation.json"), report);

        System.out.println("Wrote: " + out.toAbsolutePath());
        for (NativeLibCheck check : checks) {
            System.out.println(check.library() + " load=" + check.loadSuccess() + " class=" + check.classPresent());
        }
    }

    private static NativeLibCheck check(String library, List<Path> searchPaths, String expectedClass) {
        String loadStrategy = null;
        boolean loadSuccess = false;
        String loadError = null;

        try {
            String override = System.getProperty("sprint0.native." + library + ".file");
            if (override != null && !override.isBlank()) {
                loadStrategy = "System.load(file)";
                System.load(override);
            } else {
                Optional<Path> found = findLibraryFile(library, searchPaths);
                if (found.isPresent()) {
                    loadStrategy = "System.load(found-in-search-paths)";
                    System.load(found.get().toAbsolutePath().toString());
                } else {
                    loadStrategy = "System.loadLibrary(name)";
                    System.loadLibrary(library);
                }
            }
            loadSuccess = true;
        } catch (Throwable t) {
            loadError = t.getClass().getName() + ": " + t.getMessage();
        }

        boolean classPresent = false;
        String classError = null;
        if (expectedClass != null && !expectedClass.isBlank()) {
            try {
                Class.forName(expectedClass);
                classPresent = true;
            } catch (Throwable t) {
                classError = t.getClass().getName() + ": " + t.getMessage();
            }
        }

        return new NativeLibCheck(
                library,
                loadStrategy,
                loadSuccess,
                loadError,
                expectedClass,
                expectedClass == null || expectedClass.isBlank() ? null : classPresent,
                classError
        );
    }

    private static Optional<Path> findLibraryFile(String library, List<Path> searchPaths) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>();
        if (os.contains("win")) {
            candidates.add(library + ".dll");
        } else if (os.contains("mac")) {
            candidates.add("lib" + library + ".dylib");
        } else {
            candidates.add("lib" + library + ".so");
        }

        for (Path dir : searchPaths) {
            for (String name : candidates) {
                Path p = dir.resolve(name);
                if (Files.isRegularFile(p)) {
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    private static List<String> resolveLibs(CliArgs cli) {
        List<String> libs = new ArrayList<>();
        libs.addAll(cli.getAll("lib"));
        String raw = cli.getFirstOrNull("libs");
        if (raw != null) {
            Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(libs::add);
        }

        if (libs.isEmpty()) {
            libs.add("tree-sitter");
            libs.add("jnotify");
        }
        return libs;
    }

    private static List<Path> resolveSearchPaths(CliArgs cli) {
        List<Path> paths = new ArrayList<>();
        for (String p : cli.getAll("path")) {
            paths.add(Paths.get(p));
        }

        String raw = cli.getFirstOrNull("paths");
        if (raw != null) {
            Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(Paths::get)
                    .forEach(paths::add);
        }

        String env = System.getenv("SPRINT0_NATIVE_PATHS");
        if (env != null && !env.isBlank()) {
            Arrays.stream(env.split(System.getProperty("path.separator")))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(Paths::get)
                    .forEach(paths::add);
        }

        return paths;
    }

    private static String resolveExpectedClass(CliArgs cli, String library) {
        String override = System.getProperty("sprint0.native." + library + ".class");
        if (override != null && !override.isBlank()) {
            return override;
        }

        for (String mapping : cli.getAll("class")) {
            int idx = mapping.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String lib = mapping.substring(0, idx);
            if (lib.equals(library)) {
                return mapping.substring(idx + 1);
            }
        }

        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("tree-sitter", "org.treesitter.TSParser");
        defaults.put("jnotify", "net.contentobjects.jnotify.JNotify");
        return defaults.get(library);
    }

    record NativeValidationReport(Sprint0ReportWriter.Sprint0Metadata metadata, List<NativeLibCheck> checks) {
    }

    record NativeLibCheck(
            String library,
            String loadStrategy,
            boolean loadSuccess,
            String loadError,
            String expectedClass,
            Boolean classPresent,
            String classError
    ) {
    }
}
