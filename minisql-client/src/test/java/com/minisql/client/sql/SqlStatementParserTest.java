package com.minisql.client.sql;

import com.minisql.common.ColumnType;
import com.minisql.common.TableSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SqlStatementParserTest {
    private final SqlStatementParser parser = new SqlStatementParser();

    @Test
    void parsesCreateTableSchema() {
        SqlStatement statement = parser.parse("CREATE TABLE student (id INT PRIMARY KEY, name STRING(32));");

        assertEquals(SqlOperation.CREATE_TABLE, statement.getOperation());
        assertEquals("student", statement.getTableName());
        TableSchema schema = statement.getSchema();
        assertNotNull(schema);
        assertEquals("student", schema.getTableName());
        assertEquals("id", schema.getPrimaryKey());
        assertEquals(2, schema.getColumns().size());
        assertEquals(ColumnType.INT, schema.getColumns().get(0).getType());
        assertEquals(ColumnType.STRING, schema.getColumns().get(1).getType());
        assertEquals(32, schema.getColumns().get(1).getLength());
    }

    @Test
    void parsesTableNamesForDml() {
        assertEquals("student", parser.parse("INSERT INTO student (id) VALUES (1);").getTableName());
        assertEquals("student", parser.parse("SELECT id FROM student WHERE id = 1;").getTableName());
        assertEquals("student", parser.parse("UPDATE student SET name = 'Alice' WHERE id = 1;").getTableName());
        assertEquals("student", parser.parse("DELETE FROM student WHERE id = 1;").getTableName());
    }
}
