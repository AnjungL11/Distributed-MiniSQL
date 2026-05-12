package com.minisql.region.storage;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class WalLog {
    public static final String WAL_FILE = "wal.log";

    private final Path walPath;
    private final ObjectMapper mapper = new ObjectMapper();

    public WalLog(Path storageRoot) {
        this.walPath = storageRoot.toAbsolutePath().normalize().resolve(WAL_FILE);
    }

    public void initialize() throws IOException {
        Files.createDirectories(walPath.getParent());
        if (!Files.exists(walPath)) {
            Files.createFile(walPath);
        }
    }

    public synchronized void append(WalEntry entry) throws IOException {
        initialize();
        try (BufferedWriter writer = Files.newBufferedWriter(
                walPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            writer.write(mapper.writeValueAsString(entry));
            writer.newLine();
        }
    }

    public synchronized List<WalEntry> readAll() throws IOException {
        initialize();
        List<WalEntry> entries = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(walPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    entries.add(mapper.readValue(line, WalEntry.class));
                }
            }
        }
        return entries;
    }

    public synchronized void truncate() throws IOException {
        initialize();
        try (BufferedWriter ignored = Files.newBufferedWriter(
                walPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            // Open with TRUNCATE_EXISTING to keep the WAL file but clear durable entries.
        }
    }

    public Path getWalPath() {
        return walPath;
    }
}
