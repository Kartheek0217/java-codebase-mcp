package com.mcp.indexing.sprint0;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class LuceneMMapSpikeMain {
    public static void main(String[] args) throws Exception {
        CliArgs cli = CliArgs.parse(args);
        int fileSizeMb = cli.getInt("fileSizeMb", 256);
        int chunkSizeMb = cli.getInt("chunkSizeMb", 64);

        Path workDir = Paths.get("target", "sprint0", "lucene-mmap-" + UUID.randomUUID());
        Files.createDirectories(workDir);
        Path dataFile = workDir.resolve("mmap-spike.bin");

        long fileBytes = Math.max(0, (long) fileSizeMb * 1024 * 1024);
        int chunkBytes = Math.max(1, chunkSizeMb) * 1024 * 1024;
        int chunkCount = (int) Math.max(1, (fileBytes + chunkBytes - 1) / chunkBytes);

        boolean mapped = false;
        String mapError = null;
        boolean deleteWhileMappedSuccess = false;
        String deleteWhileMappedError = null;
        boolean deleteAfterCloseSuccess = false;
        String deleteAfterCloseError = null;
        boolean unmapAttempted = false;
        boolean unmapSuccess = false;
        boolean deleteAfterUnmapSuccess = false;
        String deleteAfterUnmapError = null;

        List<MappedByteBuffer> buffers = new ArrayList<>(chunkCount);
        try (FileChannel channel = FileChannel.open(
                dataFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            if (fileBytes > 0) {
                channel.position(fileBytes - 1);
                channel.write(ByteBuffer.wrap(new byte[]{0}));
                channel.force(true);
            }

            for (int i = 0; i < chunkCount; i++) {
                long offset = (long) i * chunkBytes;
                long size = Math.min(chunkBytes, Math.max(0, fileBytes - offset));
                if (size <= 0) {
                    break;
                }
                MappedByteBuffer buf = channel.map(FileChannel.MapMode.READ_WRITE, offset, size);
                buf.put(0, (byte) (i & 0x7F));
                buffers.add(buf);
            }

            mapped = true;

            try {
                Files.deleteIfExists(dataFile);
                deleteWhileMappedSuccess = true;
            } catch (Throwable t) {
                deleteWhileMappedError = t.getClass().getName() + ": " + t.getMessage();
            }
        } catch (Throwable t) {
            mapError = t.getClass().getName() + ": " + t.getMessage();
        }

        try {
            Files.deleteIfExists(dataFile);
            deleteAfterCloseSuccess = true;
        } catch (Throwable t) {
            deleteAfterCloseError = t.getClass().getName() + ": " + t.getMessage();
        }

        unmapAttempted = true;
        unmapSuccess = tryUnmap(buffers);

        try {
            Files.deleteIfExists(dataFile);
            deleteAfterUnmapSuccess = true;
        } catch (Throwable t) {
            deleteAfterUnmapError = t.getClass().getName() + ": " + t.getMessage();
        }

        try {
            deleteRecursively(workDir);
        } catch (Throwable ignored) {
        }

        LuceneMmapReport report = new LuceneMmapReport(
                Sprint0ReportWriter.metadata(),
                workDir.toAbsolutePath().toString(),
                dataFile.toAbsolutePath().toString(),
                fileSizeMb,
                chunkSizeMb,
                chunkCount,
                mapped,
                mapError,
                deleteWhileMappedSuccess,
                deleteWhileMappedError,
                deleteAfterCloseSuccess,
                deleteAfterCloseError,
                unmapAttempted,
                unmapSuccess,
                deleteAfterUnmapSuccess,
                deleteAfterUnmapError
        );

        Sprint0ReportWriter writer = new Sprint0ReportWriter();
        Path out = writer.writeJson(Paths.get("target", "sprint0", "lucene-mmap-spike.json"), report);
        System.out.println("Wrote: " + out.toAbsolutePath());
        System.out.println("mapped=" + mapped + " deleteAfterUnmapSuccess=" + deleteAfterUnmapSuccess);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static boolean tryUnmap(List<MappedByteBuffer> buffers) {
        boolean ok = true;
        for (MappedByteBuffer b : buffers) {
            ok &= tryUnmapOne(b);
        }
        return ok;
    }

    private static boolean tryUnmapOne(MappedByteBuffer buffer) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            Method invokeCleaner = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
            invokeCleaner.invoke(unsafe, buffer);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    record LuceneMmapReport(
            Sprint0ReportWriter.Sprint0Metadata metadata,
            String workDir,
            String dataFile,
            int fileSizeMb,
            int chunkSizeMb,
            int chunkCount,
            boolean mapped,
            String mapError,
            boolean deleteWhileMappedSuccess,
            String deleteWhileMappedError,
            boolean deleteAfterCloseSuccess,
            String deleteAfterCloseError,
            boolean unmapAttempted,
            boolean unmapSuccess,
            boolean deleteAfterUnmapSuccess,
            String deleteAfterUnmapError
    ) {
    }
}

