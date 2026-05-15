package com.minisql.region.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minisql.common.TableSchema;
import com.minisql.region.model.RowRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DiskStore {
    public static final String TABLES_DIR = "tables";
    public static final String SCHEMA_FILE = "schema.json";
    public static final String DATA_FILE = "data.jsonl";

    private final Path root;
    private final ObjectMapper mapper;

    public DiskStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.mapper = new ObjectMapper();
    }

    public void initialize() throws IOException {
        Files.createDirectories(tablesRoot());
    }

    public List<String> listTables() throws IOException {
        Path tablesRoot = tablesRoot();
        if (!Files.exists(tablesRoot)) {
            return Collections.emptyList();
        }
        List<String> tableNames = new ArrayList<>();
        try (var stream = Files.list(tablesRoot)) {
            stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .forEach(tableNames::add);
        }
        return tableNames;
    }

    public void writeSchema(TableSchema schema) throws IOException {
        Path tableDir = tableDir(schema.getTableName());
        Files.createDirectories(tableDir);
        mapper.writeValue(tableDir.resolve(SCHEMA_FILE).toFile(), schema);
    }

    public TableSchema readSchema(String tableName) throws IOException {
        return mapper.readValue(tableDir(tableName).resolve(SCHEMA_FILE).toFile(), TableSchema.class);
    }

    public void writeRows(String tableName, List<RowRecord> rows) throws IOException {
        Path tableDir = tableDir(tableName);
        Files.createDirectories(tableDir);
        Path dataFile = tableDir.resolve(DATA_FILE);
        try (BufferedWriter writer = Files.newBufferedWriter(dataFile, StandardCharsets.UTF_8)) {
            for (RowRecord row : rows) {
                writer.write(mapper.writeValueAsString(row));
                writer.newLine();
            }
        }
    }

    public List<RowRecord> readRows(String tableName) throws IOException {
        Path dataFile = tableDir(tableName).resolve(DATA_FILE);
        if (!Files.exists(dataFile)) {
            return Collections.emptyList();
        }
        List<RowRecord> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    rows.add(mapper.readValue(line, RowRecord.class));
                }
            }
        }
        return rows;
    }

    public void deleteTable(String tableName) throws IOException {
        Path tableDir = tableDir(tableName);
        if (!Files.exists(tableDir)) {
            return;
        }
        try (var walk = Files.walk(tableDir)) {
            List<Path> paths = new ArrayList<>();
            walk.forEach(paths::add);
            Collections.reverse(paths);
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    public Path schemaPath(String tableName) {
        return tableDir(tableName).resolve(SCHEMA_FILE);
    }

    public Path dataPath(String tableName) {
        return tableDir(tableName).resolve(DATA_FILE);
    }

    private Path tablesRoot() {
        return root.resolve(TABLES_DIR);
    }

    private Path tableDir(String tableName) {
        validateTableName(tableName);
        return tablesRoot().resolve(tableName);
    }

    private static void validateTableName(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
        if (tableName.contains("/") || tableName.contains("\\") || tableName.contains("..")) {
            throw new IllegalArgumentException("tableName contains illegal path characters: " + tableName);
        }
    }
}
