package com.minisql.client.cli;

import com.minisql.client.model.SqlResult;

import java.util.ArrayList;
import java.util.List;

public class ResultPrinter {
    public void print(SqlResult result) {
        if (result == null) {
            System.out.println("Execution failed: empty result");
            return;
        }
        if (!result.isSuccess()) {
            System.out.println("Execution failed: " + safeMessage(result));
            return;
        }
        if (result.getColumns() != null && !result.getColumns().isEmpty()) {
            printRows(result.getColumns(), result.getRows());
            return;
        }
        String message = safeMessage(result);
        if (!message.isEmpty()) {
            System.out.println(message);
        } else {
            System.out.println("OK, affected rows: " + result.getAffectedRows());
        }
    }

    private String safeMessage(SqlResult result) {
        return result.getMessage() == null ? "" : result.getMessage();
    }

    private void printRows(List<String> columns, List<List<String>> rows) {
        List<Integer> widths = new ArrayList<>();
        for (String column : columns) {
            widths.add(displayWidth(column));
        }
        if (rows != null) {
            for (List<String> row : rows) {
                for (int i = 0; i < columns.size() && i < row.size(); i++) {
                    widths.set(i, Math.max(widths.get(i), displayWidth(row.get(i))));
                }
            }
        }

        printDivider(widths);
        printLine(columns, widths);
        printDivider(widths);
        if (rows != null) {
            for (List<String> row : rows) {
                printLine(row, widths);
            }
        }
        printDivider(widths);
        int count = rows == null ? 0 : rows.size();
        System.out.println(count + " row(s)");
    }

    private void printDivider(List<Integer> widths) {
        StringBuilder builder = new StringBuilder("+");
        for (Integer width : widths) {
            builder.append(repeat("-", width + 2)).append("+");
        }
        System.out.println(builder);
    }

    private void printLine(List<String> values, List<Integer> widths) {
        StringBuilder builder = new StringBuilder("|");
        for (int i = 0; i < widths.size(); i++) {
            String value = i < values.size() && values.get(i) != null ? values.get(i) : "";
            builder.append(' ').append(padRight(value, widths.get(i))).append(" |");
        }
        System.out.println(builder);
    }

    private String padRight(String value, int width) {
        int padding = Math.max(0, width - displayWidth(value));
        return value + repeat(" ", padding);
    }

    private int displayWidth(String value) {
        return value == null ? 0 : value.length();
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
