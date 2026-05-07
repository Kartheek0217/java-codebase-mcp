package com.mcp.indexing.sprint0;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public class VirtualThreadIoBenchmarkMain {
    public static void main(String[] args) throws Exception {
        CliArgs cli = CliArgs.parse(args);
        int fileCount = cli.getInt("fileCount", 2_000);
        int fileSizeKb = cli.getInt("fileSizeKb", 64);

        Path workDir = Paths.get("target", "sprint0", "vt-io-" + UUID.randomUUID());
        Files.createDirectories(workDir);

        Instant createStart = Instant.now();
        long generatedBytes = generateFiles(workDir, fileCount, fileSizeKb);
        Duration createDuration = Duration.between(createStart, Instant.now());

        Instant readStart = Instant.now();
        ReadResult readResult = readAllFilesVirtualThreads(workDir, fileCount);
        Duration readDuration = Duration.between(readStart, Instant.now());

        double mb = generatedBytes / (1024.0 * 1024.0);
        double seconds = Math.max(0.000_001, readDuration.toNanos() / 1_000_000_000.0);
        double mbPerSec = mb / seconds;

        IoBenchmarkReport report = new IoBenchmarkReport(
                Sprint0ReportWriter.metadata(),
                workDir.toAbsolutePath().toString(),
                fileCount,
                fileSizeKb,
                generatedBytes,
                createDuration.toMillis(),
                readResult.totalBytesRead(),
                readDuration.toMillis(),
                mbPerSec,
                readResult.failures()
        );

        Sprint0ReportWriter writer = new Sprint0ReportWriter();
        Path out = writer.writeJson(Paths.get("target", "sprint0", "virtual-thread-io-benchmark.json"), report);
        System.out.println("Wrote: " + out.toAbsolutePath());
        System.out.println("readBytes=" + readResult.totalBytesRead() + " failures=" + readResult.failures());
    }

    private static long generateFiles(Path workDir, int fileCount, int fileSizeKb) throws IOException {
        byte[] content = buildPayload(fileSizeKb);
        long total = 0;
        for (int i = 0; i < fileCount; i++) {
            Path p = workDir.resolve(String.format("file-%05d.txt", i));
            Files.write(p, content);
            total += content.length;
        }
        return total;
    }

    private static byte[] buildPayload(int fileSizeKb) {
        int bytes = Math.max(1, fileSizeKb) * 1024;
        String seed = "abcdefghijklmnopqrstuvwxyz0123456789\n";
        StringBuilder sb = new StringBuilder(bytes);
        while (sb.length() < bytes) {
            sb.append(seed);
        }
        return sb.substring(0, bytes).getBytes(StandardCharsets.UTF_8);
    }

    private static ReadResult readAllFilesVirtualThreads(Path workDir, int fileCount) throws Exception {
        List<Callable<Long>> tasks = new ArrayList<>(fileCount);
        for (int i = 0; i < fileCount; i++) {
            Path p = workDir.resolve(String.format("file-%05d.txt", i));
            tasks.add(() -> (long) Files.readAllBytes(p).length);
        }

        long totalBytes = 0;
        int failures = 0;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = executor.invokeAll(tasks);
            for (var f : futures) {
                try {
                    totalBytes += f.get();
                } catch (Throwable t) {
                    failures++;
                }
            }
        }
        return new ReadResult(totalBytes, failures);
    }

    record ReadResult(long totalBytesRead, int failures) {
    }

    record IoBenchmarkReport(
            Sprint0ReportWriter.Sprint0Metadata metadata,
            String workDir,
            int fileCount,
            int fileSizeKb,
            long generatedBytes,
            long createDurationMs,
            long readBytes,
            long readDurationMs,
            double throughputMbPerSec,
            int failures
    ) {
    }
}
