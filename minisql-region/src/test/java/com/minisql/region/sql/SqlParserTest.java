package com.minisql.region.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlParserTest {
    private final SqlParser parser = new SqlParser();

    @Test
    void parsesCreateTable() {
        SqlCommand command = parser.parse("CREATE TABLE student (id INT PRIMARY KEY, name STRING);");

        assertEquals(SqlCommandType.CREATE_TABLE, command.getType());
        assertEquals("student", command.getTableName());
        assertEquals("id", command.getSchema().getPrimaryKey());
        assertEquals(2, command.getSchema().getColumns().size());
    }

    @Test
    void parsesInsertWithQuotedComma() {
        SqlCommand command = parser.parse("INSERT INTO student (id, name) VALUES (1, 'Alice, A')");

        assertEquals(SqlCommandType.INSERT, command.getType());
        assertEquals("Alice, A", command.getValues().get(1));
    }

    @Test
    void rejectsUnsupportedSql() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("ALTER TABLE student ADD age INT"));
    }
}
