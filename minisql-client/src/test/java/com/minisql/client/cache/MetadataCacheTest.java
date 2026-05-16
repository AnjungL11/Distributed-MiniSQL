package com.minisql.client.cache;

import com.minisql.client.model.Endpoint;
import com.minisql.common.ColumnInfo;
import com.minisql.common.ColumnType;
import com.minisql.common.TableSchema;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataCacheTest {
    @Test
    void cachesRoutesCaseInsensitively() {
        MetadataCache cache = new MetadataCache();
        Endpoint endpoint = new Endpoint("127.0.0.1", 9090);

        cache.updateTableRoute("Student", endpoint);

        assertEquals(endpoint, cache.getTableRoute("student").orElseThrow());
        cache.invalidateTableRoute("STUDENT");
        assertFalse(cache.getTableRoute("student").isPresent());
    }

    @Test
    void cachesTableSchemasCaseInsensitively() {
        MetadataCache cache = new MetadataCache();
        TableSchema schema = new TableSchema(
                "Student",
                Collections.singletonList(new ColumnInfo("id", ColumnType.INT, 0, true)));

        cache.updateTableSchema("Student", schema);

        assertTrue(cache.getTableSchema("student").isPresent());
        assertEquals("Student", cache.getTableSchema("STUDENT").orElseThrow().getTableName());
        cache.invalidateTableSchema("student");
        assertFalse(cache.getTableSchema("Student").isPresent());
    }
}
