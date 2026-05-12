package com.minisql.region.storage;

import com.minisql.common.TableSchema;
import com.minisql.region.model.RowRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local storage engine used by Region Server.
 *
 * <p>Writes are appended to WAL before mutating MemStore. Flush writes full
 * table snapshots to disk and truncates WAL.</p>
 */
public class RegionStorageEngine implements AutoCloseable {
    private final MemStore memStore = new MemStore();
    private final DiskStore diskStore;
    private final WalLog walLog;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public RegionStorageEngine(Path storageRoot) {
        this.diskStore = new DiskStore(storageRoot);
        this.walLog = new WalLog(storageRoot);
    }

    public void start() throws IOException {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        diskStore.initialize();
        walLog.initialize();
        loadSnapshots();
        replayWal();
    }

    public synchronized void createTable(TableSchema schema) throws IOException {
        ensureStarted();
        WalEntry entry = WalEntry.createTable(schema);
        walLog.append(entry);
        apply(entry);
        diskStore.writeSchema(schema);
    }

    public synchronized void dropTable(String tableName) throws IOException {
        ensureStarted();
        WalEntry entry = WalEntry.dropTable(tableName);
        walLog.append(entry);
        apply(entry);
        diskStore.deleteTable(tableName);
    }

    public synchronized RowRecord insert(String tableName, Map<String, String> values) throws IOException {
        ensureStarted();
        RowRecord row = memStore.buildInsertRow(tableName, values);
        walLog.append(WalEntry.upsert(tableName, row));
        memStore.upsert(tableName, row);
        return row;
    }

    public synchronized RowRecord update(String tableName, String rowKey, Map<String, String> updates) throws IOException {
        ensureStarted();
        RowRecord row = memStore.buildUpdatedRow(tableName, rowKey, updates);
        walLog.append(WalEntry.upsert(tableName, row));
        memStore.upsert(tableName, row);
        return row;
    }

    public synchronized RowRecord delete(String tableName, String rowKey) throws IOException {
        ensureStarted();
        RowRecord deleted = memStore.get(tableName, rowKey)
                .orElseThrow(() -> new IllegalArgumentException("Row does not exist: " + rowKey));
        walLog.append(WalEntry.deleteRow(tableName, rowKey));
        memStore.delete(tableName, rowKey);
        return deleted;
    }

    public synchronized Optional<RowRecord> get(String tableName, String rowKey) {
        ensureStarted();
        return memStore.get(tableName, rowKey);
    }

    public synchronized List<RowRecord> scan(String tableName) {
        ensureStarted();
        return memStore.scan(tableName);
    }

    public synchronized Optional<TableSchema> getSchema(String tableName) {
        ensureStarted();
        return memStore.getSchema(tableName);
    }

    public synchronized Set<String> tableNames() {
        ensureStarted();
        return memStore.tableNames();
    }

    public synchronized void flush() throws IOException {
        ensureStarted();
        for (TableStore tableStore : memStore.snapshotTables()) {
            diskStore.writeSchema(tableStore.getSchema());
            diskStore.writeRows(tableStore.getTableName(), tableStore.scan());
        }
        walLog.truncate();
    }

    public DiskStore getDiskStore() {
        return diskStore;
    }

    public WalLog getWalLog() {
        return walLog;
    }

    @Override
    public void close() throws IOException {
        if (started.get()) {
            flush();
            started.set(false);
        }
    }

    private void loadSnapshots() throws IOException {
        for (String tableName : diskStore.listTables()) {
            TableSchema schema = diskStore.readSchema(tableName);
            TableStore tableStore = new TableStore(schema);
            for (RowRecord row : diskStore.readRows(tableName)) {
                tableStore.upsert(row);
            }
            memStore.putTable(tableStore);
        }
    }

    private void replayWal() throws IOException {
        List<WalEntry> entries = new ArrayList<>(walLog.readAll());
        for (WalEntry entry : entries) {
            apply(entry);
        }
    }

    private void apply(WalEntry entry) throws IOException {
        switch (entry.getOperation()) {
            case CREATE_TABLE:
                if (!memStore.containsTable(entry.getTableName())) {
                    memStore.createTable(entry.getSchema());
                    diskStore.writeSchema(entry.getSchema());
                }
                break;
            case DROP_TABLE:
                if (memStore.containsTable(entry.getTableName())) {
                    memStore.dropTable(entry.getTableName());
                }
                diskStore.deleteTable(entry.getTableName());
                break;
            case UPSERT_ROW:
                memStore.upsert(entry.getTableName(), entry.getRow());
                break;
            case DELETE_ROW:
                if (memStore.get(entry.getTableName(), entry.getRowKey()).isPresent()) {
                    memStore.delete(entry.getTableName(), entry.getRowKey());
                }
                break;
            default:
                throw new IOException("Unsupported WAL operation: " + entry.getOperation());
        }
    }

    private void ensureStarted() {
        if (!started.get()) {
            throw new IllegalStateException("RegionStorageEngine is not started");
        }
    }
}
