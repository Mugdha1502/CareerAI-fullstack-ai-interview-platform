package com.careerai.backend.dto;

import java.util.List;

/** A bounded query result: rows are capped (truncated=true when more rows existed). */
public record SqlQueryResponse(
    List<String> columns,
    List<List<Object>> rows,
    int rowCount,
    boolean truncated,
    long executionTimeMs
) {
}
