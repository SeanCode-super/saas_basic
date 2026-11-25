package com.saasbasics.platform.modules.tenant.service;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class TenantSnapshotArchive implements AutoCloseable {
    static final long MAX_BYTES = 500L * 1024 * 1024;

    /** Applied to uncompressed bytes, including data whose size is absent from the ZIP header. */
    static final class Budget {
        private long remaining = MAX_BYTES;

        OutputStream limit(OutputStream output) {
            return new FilterOutputStream(output) {
                @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                    consume(length);
                    out.write(bytes, offset, length);
                }

                @Override public void write(int value) throws IOException {
                    consume(1);
                    out.write(value);
                }
            };
        }

        private void consume(int length) {
            remaining -= length;
            if (remaining < 0) throw TenantBusinessSnapshot.invalid("归档展开后超过 500 MB");
        }
    }

    private final Path directory;
    final Map<String, Path> entries = new LinkedHashMap<>();

    TenantSnapshotArchive() throws IOException {
        directory = Files.createTempDirectory("tianshu-snapshot-");
    }

    void unpack(InputStream source) throws IOException {
        Budget budget = new Budget();
        try (ZipInputStream in = new ZipInputStream(source, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                if (entries.size() >= 4096 || entries.containsKey(name)
                        || !(name.equals("manifest.json") || name.matches("tables/[a-zA-Z_][a-zA-Z0-9_]{0,63}\\.json"))) {
                    throw TenantBusinessSnapshot.invalid("归档包含重复或无效条目");
                }
                Path path = directory.resolve(Integer.toString(entries.size()));
                entries.put(name, path);
                try (OutputStream out = budget.limit(Files.newOutputStream(path))) {
                    in.transferTo(out);
                }
            }
        }
    }

    static String sha256(InputStream source) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (var in = new java.security.DigestInputStream(source, hash)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(hash.digest());
    }

    @Override public void close() throws IOException {
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) Files.deleteIfExists(file);
        }
        Files.deleteIfExists(directory);
    }
}
