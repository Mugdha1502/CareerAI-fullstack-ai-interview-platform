package com.careerai.backend.practice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compares a submitted result with the reference result by VALUE (never by SQL text).
 * Columns are compared by position (aliases may differ); numbers compare with a small tolerance
 * (so 91500 equals 91500.0); dates compare as ISO text.
 */
public final class SqlResultComparator {

    private SqlResultComparator() {
    }

    public static boolean matches(SqlQuestion.EvaluationMode mode, SqlSandbox.ResultTable actual, SqlSandbox.ResultTable expected) {
        return switch (mode) {
            case ROW_COUNT -> actual.rows().size() == expected.rows().size();
            case SCALAR -> actual.rows().size() == 1 && expected.rows().size() == 1
                && actual.columns().size() == 1 && expected.columns().size() == 1
                && sameCell(actual.rows().getFirst().getFirst(), expected.rows().getFirst().getFirst());
            case ORDERED -> sameShape(actual, expected) && sameRowsInOrder(normalize(actual.rows()), normalize(expected.rows()));
            case UNORDERED -> sameShape(actual, expected) && sameRowsInOrder(sorted(normalize(actual.rows())), sorted(normalize(expected.rows())));
        };
    }

    private static boolean sameShape(SqlSandbox.ResultTable actual, SqlSandbox.ResultTable expected) {
        return actual.columns().size() == expected.columns().size() && actual.rows().size() == expected.rows().size();
    }

    private static boolean sameRowsInOrder(List<List<String>> actual, List<List<String>> expected) {
        return actual.equals(expected);
    }

    private static boolean sameCell(Object a, Object b) {
        return Objects.equals(normalizeCell(a), normalizeCell(b));
    }

    private static List<List<String>> normalize(List<List<Object>> rows) {
        List<List<String>> result = new ArrayList<>();
        for (List<Object> row : rows) {
            List<String> cells = new ArrayList<>();
            for (Object cell : row) cells.add(normalizeCell(cell));
            result.add(cells);
        }
        return result;
    }

    private static List<List<String>> sorted(List<List<String>> rows) {
        List<List<String>> copy = new ArrayList<>(rows);
        copy.sort((a, b) -> String.join("\u0000", a.stream().map(String::valueOf).toList())
            .compareTo(String.join("\u0000", b.stream().map(String::valueOf).toList())));
        return copy;
    }

    /** Numbers → canonical decimal rounded to 6 places; everything else → its text. */
    static String normalizeCell(Object cell) {
        if (cell == null) return null;
        if (cell instanceof Number number) {
            try {
                BigDecimal value = new BigDecimal(number.toString()).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
                return "n:" + value.toPlainString();
            } catch (NumberFormatException ex) {
                return "n:" + number;
            }
        }
        String text = String.valueOf(cell);
        try {
            // Numeric text (e.g. a CAST to VARCHAR) still matches the same number.
            return "n:" + new BigDecimal(text.trim()).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            return "s:" + text;
        }
    }
}
