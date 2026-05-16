package com.minisql.client.sql;

import com.minisql.common.ColumnInfo;
import com.minisql.common.ColumnType;
import com.minisql.common.TableSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlStatementParser {
    private static final String TABLE_NAME = "([A-Za-z_][A-Za-z0-9_]*)";
    private static final Pattern CREATE_TABLE = Pattern.compile("^CREATE\\s+TABLE\\s+" + TABLE_NAME + "\\s*\\((.+)\\)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DROP_TABLE = Pattern.compile("^DROP\\s+TABLE\\s+" + TABLE_NAME + "\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INSERT = Pattern.compile("^INSERT\\s+INTO\\s+" + TABLE_NAME + "\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SELECT = Pattern.compile("^SELECT\\s+.+?\\s+FROM\\s+" + TABLE_NAME + "\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern UPDATE = Pattern.compile("^UPDATE\\s+" + TABLE_NAME + "\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DELETE = Pattern.compile("^DELETE\\s+FROM\\s+" + TABLE_NAME + "\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public SqlStatement parse(String sql) {
        String normalized = normalize(sql);
        MatchResult result = match(CREATE_TABLE, normalized, SqlOperation.CREATE_TABLE);
        if (result != null) {
            Matcher matcher = CREATE_TABLE.matcher(normalized);
            matcher.matches();
            return new SqlStatement(
                    SqlOperation.CREATE_TABLE,
                    matcher.group(1),
                    normalized,
                    parseCreateTableSchema(matcher.group(1), matcher.group(2)));
        }
        result = match(DROP_TABLE, normalized, SqlOperation.DROP_TABLE);
        if (result != null) {
            return result.toStatement(normalized);
        }
        result = match(INSERT, normalized, SqlOperation.INSERT);
        if (result != null) {
            return result.toStatement(normalized);
        }
        result = match(SELECT, normalized, SqlOperation.SELECT);
        if (result != null) {
            return result.toStatement(normalized);
        }
        result = match(UPDATE, normalized, SqlOperation.UPDATE);
        if (result != null) {
            return result.toStatement(normalized);
        }
        result = match(DELETE, normalized, SqlOperation.DELETE);
        if (result != null) {
            return result.toStatement(normalized);
        }
        return new SqlStatement(SqlOperation.UNKNOWN, null, normalized);
    }

    private String normalize(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL must not be blank");
        }
        String normalized = sql.trim();
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private MatchResult match(Pattern pattern, String sql, SqlOperation operation) {
        Matcher matcher = pattern.matcher(sql);
        if (!matcher.matches()) {
            return null;
        }
        return new MatchResult(operation, matcher.group(1));
    }

    private TableSchema parseCreateTableSchema(String tableName, String columnDefinitions) {
        List<ColumnInfo> columns = new ArrayList<>();
        for (String definition : splitCommaAware(columnDefinitions)) {
            columns.add(parseColumn(definition));
        }
        return new TableSchema(tableName, columns);
    }

    private ColumnInfo parseColumn(String definition) {
        String[] tokens = definition.trim().split("\\s+");
        if (tokens.length < 2) {
            throw new IllegalArgumentException("Invalid column definition: " + definition);
        }

        String columnName = tokens[0];
        String typeToken = tokens[1].toUpperCase(Locale.ROOT);
        int length = 0;
        Matcher lengthMatcher = Pattern.compile("^(STRING|CHAR)\\((\\d+)\\)$").matcher(typeToken);
        if (lengthMatcher.matches()) {
            typeToken = lengthMatcher.group(1);
            length = Integer.parseInt(lengthMatcher.group(2));
        }

        ColumnType type = ColumnType.valueOf(typeToken);
        boolean primaryKey = definition.toUpperCase(Locale.ROOT).contains("PRIMARY KEY");
        return new ColumnInfo(columnName, type, length, primaryKey);
    }

    private List<String> splitCommaAware(String input) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int parentheses = 0;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (ch == '(' && !inSingleQuote && !inDoubleQuote) {
                parentheses++;
            } else if (ch == ')' && !inSingleQuote && !inDoubleQuote) {
                parentheses--;
            }
            if (ch == ',' && !inSingleQuote && !inDoubleQuote && parentheses == 0) {
                parts.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        parts.add(current.toString().trim());
        return parts;
    }

    private static class MatchResult {
        private final SqlOperation operation;
        private final String tableName;

        private MatchResult(SqlOperation operation, String tableName) {
            this.operation = operation;
            this.tableName = tableName;
        }

        private SqlStatement toStatement(String normalizedSql) {
            return new SqlStatement(operation, tableName, normalizedSql);
        }
    }
}
