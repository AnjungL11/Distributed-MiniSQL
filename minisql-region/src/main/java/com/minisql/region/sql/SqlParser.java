package com.minisql.region.sql;

import com.minisql.common.ColumnInfo;
import com.minisql.common.ColumnType;
import com.minisql.common.TableSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlParser {
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "^CREATE\\s+TABLE\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\((.+)\\)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DROP_TABLE = Pattern.compile(
            "^DROP\\s+TABLE\\s+([A-Za-z_][A-Za-z0-9_]*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERT = Pattern.compile(
            "^INSERT\\s+INTO\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s*\\(([^)]*)\\))?\\s+VALUES\\s*\\((.*)\\)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SELECT = Pattern.compile(
            "^SELECT\\s+(.+)\\s+FROM\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+WHERE\\s+(.+))?$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern UPDATE = Pattern.compile(
            "^UPDATE\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+SET\\s+(.+)\\s+WHERE\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DELETE = Pattern.compile(
            "^DELETE\\s+FROM\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+WHERE\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CONDITION = Pattern.compile(
            "^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public SqlCommand parse(String sql) {
        String normalized = normalizeSql(sql);
        Matcher matcher = CREATE_TABLE.matcher(normalized);
        if (matcher.matches()) {
            return parseCreateTable(matcher);
        }
        matcher = DROP_TABLE.matcher(normalized);
        if (matcher.matches()) {
            SqlCommand command = command(SqlCommandType.DROP_TABLE, matcher.group(1));
            return command;
        }
        matcher = INSERT.matcher(normalized);
        if (matcher.matches()) {
            return parseInsert(matcher);
        }
        matcher = SELECT.matcher(normalized);
        if (matcher.matches()) {
            return parseSelect(matcher);
        }
        matcher = UPDATE.matcher(normalized);
        if (matcher.matches()) {
            return parseUpdate(matcher);
        }
        matcher = DELETE.matcher(normalized);
        if (matcher.matches()) {
            SqlCommand command = command(SqlCommandType.DELETE, matcher.group(1));
            command.setCondition(parseCondition(matcher.group(2)));
            return command;
        }
        throw new IllegalArgumentException("Unsupported SQL: " + normalized);
    }

    private SqlCommand parseCreateTable(Matcher matcher) {
        String tableName = matcher.group(1);
        List<ColumnInfo> columns = new ArrayList<>();
        for (String definition : splitCommaAware(matcher.group(2))) {
            columns.add(parseColumn(definition));
        }
        SqlCommand command = command(SqlCommandType.CREATE_TABLE, tableName);
        command.setSchema(new TableSchema(tableName, columns));
        return command;
    }

    private SqlCommand parseInsert(Matcher matcher) {
        SqlCommand command = command(SqlCommandType.INSERT, matcher.group(1));
        if (matcher.group(2) != null && !matcher.group(2).trim().isEmpty()) {
            command.setColumns(trimmedList(splitCommaAware(matcher.group(2))));
        }
        command.setValues(unquoteList(splitCommaAware(matcher.group(3))));
        return command;
    }

    private SqlCommand parseSelect(Matcher matcher) {
        SqlCommand command = command(SqlCommandType.SELECT, matcher.group(2));
        String columnPart = matcher.group(1).trim();
        if (!"*".equals(columnPart)) {
            command.setColumns(trimmedList(splitCommaAware(columnPart)));
        }
        if (matcher.group(3) != null) {
            command.setCondition(parseCondition(matcher.group(3)));
        }
        return command;
    }

    private SqlCommand parseUpdate(Matcher matcher) {
        SqlCommand command = command(SqlCommandType.UPDATE, matcher.group(1));
        command.setAssignments(parseAssignments(matcher.group(2)));
        command.setCondition(parseCondition(matcher.group(3)));
        return command;
    }

    private Map<String, String> parseAssignments(String assignmentsSql) {
        Map<String, String> assignments = new LinkedHashMap<>();
        for (String assignment : splitCommaAware(assignmentsSql)) {
            Matcher matcher = CONDITION.matcher(assignment.trim());
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid assignment: " + assignment);
            }
            assignments.put(matcher.group(1), unquote(matcher.group(2).trim()));
        }
        return assignments;
    }

    private SqlCondition parseCondition(String conditionSql) {
        Matcher matcher = CONDITION.matcher(conditionSql.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Only equality WHERE conditions are supported: " + conditionSql);
        }
        return new SqlCondition(matcher.group(1), unquote(matcher.group(2).trim()));
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

    private SqlCommand command(SqlCommandType type, String tableName) {
        SqlCommand command = new SqlCommand();
        command.setType(type);
        command.setTableName(tableName);
        return command;
    }

    private String normalizeSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL must not be blank");
        }
        String normalized = sql.trim();
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private List<String> trimmedList(List<String> values) {
        List<String> trimmed = new ArrayList<>();
        for (String value : values) {
            trimmed.add(value.trim());
        }
        return trimmed;
    }

    private List<String> unquoteList(List<String> values) {
        List<String> unquoted = new ArrayList<>();
        for (String value : values) {
            unquoted.add(unquote(value.trim()));
        }
        return unquoted;
    }

    private String unquote(String value) {
        if ((value.startsWith("'") && value.endsWith("'"))
                || (value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
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
}
