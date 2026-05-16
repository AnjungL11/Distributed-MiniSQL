package com.minisql.client;

import com.minisql.client.cli.ResultPrinter;
import com.minisql.client.model.SqlResult;

import java.util.Scanner;

public class ClientMain {
    public static void main(String[] args) {
        ResultPrinter printer = new ResultPrinter();
        try (MiniSqlClient client = new MiniSqlClient(); Scanner scanner = new Scanner(System.in)) {
            System.out.println("Distributed MiniSQL Client");
            System.out.println("Enter SQL ending with ';', or 'quit;' to exit.");
            while (true) {
                System.out.print("MiniSQL> ");
                String sql = readSql(scanner);
                if (sql == null || "quit;".equalsIgnoreCase(sql.trim()) || "exit;".equalsIgnoreCase(sql.trim())) {
                    break;
                }
                SqlResult result = client.execute(sql);
                printer.print(result);
            }
        }
    }

    private static String readSql(Scanner scanner) {
        StringBuilder sql = new StringBuilder();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (sql.length() > 0) {
                sql.append(' ');
            }
            sql.append(line);
            if (line.trim().endsWith(";")) {
                return sql.toString();
            }
            System.out.print("      > ");
        }
        return null;
    }
}
