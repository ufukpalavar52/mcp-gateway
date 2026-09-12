package com.mcpgateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Finds the rows in an endpoint's answer.
 *
 * <p>A database action returns rows and the panel draws a table from them. A REST action
 * returns a body, and the body of a listing endpoint is a table wearing a different coat —
 * but it arrived as text, so it was shown as text and a search for eleven users read as a
 * paragraph of JSON.
 *
 * <p>Derived when somebody asks rather than when the run is recorded. What is stored stays
 * exactly what the endpoint said, which is the thing worth having a year later; the shape
 * is this service's reading of it, and a reading can be improved without rewriting
 * history.
 *
 * <p>Deliberately unclever. Three shapes are recognised and everything else is left as
 * text: guessing at a fourth would sometimes produce a table that quietly omits half the
 * answer, and a wrong table is worse than honest JSON.
 */
@Slf4j
@Component
public class JsonRows {

    /** Where a listing endpoint usually keeps its rows. */
    private static final List<String> ENVELOPES = List.of("data", "items", "results", "records", "content");

    /** A ceiling, so one enormous response cannot be turned into an enormous response. */
    private static final int LIMIT = 500;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The rows in this body, or {@code null} when it is not a shape worth tabulating.
     *
     * <p>Recognised:
     * <ul>
     *   <li>an array of objects — the rows themselves;
     *   <li>an object holding exactly one array of objects — the usual envelope, where the
     *       rest of the fields are a count and some paging;
     *   <li>a single object — one record, which is one row.
     * </ul>
     */
    public List<Map<String, Object>> of(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            Object parsed = objectMapper.readValue(body, Object.class);
            return rowsOf(parsed);
        } catch (RuntimeException notJson) {
            // Most bodies are not JSON, and that is not a failure of anything.
            log.trace("A response body was not read as rows: {}", notJson.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> rowsOf(Object parsed) {
        if (parsed instanceof List<?> list) {
            return asRows(list);
        }

        if (!(parsed instanceof Map<?, ?> document)) {
            return null;
        }

        List<List<Map<String, Object>>> found = document.values().stream()
                .filter(List.class::isInstance)
                .map(value -> asRows((List<?>) value))
                .filter(rows -> rows != null)
                .toList();

        // Exactly one, and only when the field is one an envelope usually uses. Two arrays
        // means this service would be choosing which of them the reader meant.
        if (found.size() == 1 && namesAnEnvelope(document)) {
            return found.getFirst();
        }

        // A single record is a table of one row, which is how a GET by id reads.
        return found.isEmpty() && !document.isEmpty() ? List.of(asRow(document)) : null;
    }

    private static boolean namesAnEnvelope(Map<?, ?> document) {
        return document.keySet().stream()
                .anyMatch(key -> ENVELOPES.contains(String.valueOf(key).toLowerCase()));
    }

    private static List<Map<String, Object>> asRows(List<?> list) {
        if (list.isEmpty()) {
            // An empty list is an answer — "no rows" — and the panel says so.
            return List.of();
        }

        if (!list.stream().allMatch(Map.class::isInstance)) {
            // A list of numbers or strings is not a table; it is a value that happens to
            // have several parts.
            return null;
        }

        return list.stream()
                .limit(LIMIT)
                .map(item -> asRow((Map<?, ?>) item))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asRow(Map<?, ?> item) {
        return (Map<String, Object>) item;
    }
}
