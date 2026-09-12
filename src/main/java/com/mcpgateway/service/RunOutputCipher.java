package com.mcpgateway.service;

import com.mcpgateway.client.CipherClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seals and opens what a run produced.
 *
 * <p>A query's answer is production data. Kept as it was, this database filled up with the
 * personal columns of whatever anyone had asked about — names, addresses, identifiers — in
 * a control plane that has no business holding them. It is sealed through mcp-cipher, whose
 * key lives outside this database, so a copy of the database is not a copy of the data.
 *
 * <p>Both representations go into one envelope. The rendered text and the rows are the same
 * answer in two shapes; sealing them apart would mean two round trips to read one result,
 * and one of them eventually being forgotten.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunOutputCipher {

    /**
     * Bound into the ciphertext, and it must be this exact string on both sides.
     *
     * <p>It also keeps a run's output from being opened as anything else: a value sealed
     * here cannot be read back as a model API key, even by a caller holding both.
     */
    private static final String CONTEXT = "run-output";

    private static final TypeReference<List<Map<String, Object>>> ROWS = new TypeReference<>() {
    };

    private final CipherClient cipherClient;
    private final ObjectMapper objectMapper;

    /** One target's output, in the two shapes the panel and a person each need. */
    public record Output(String text, List<Map<String, Object>> rows) {

        public boolean isEmpty() {
            return (text == null || text.isBlank()) && (rows == null || rows.isEmpty());
        }
    }

    /**
     * Seals an output, or fails.
     *
     * <p>There is no plaintext fallback on purpose. Writing the rows in the clear when the
     * cipher is unreachable would mean the protection quietly lapses exactly when something
     * is already wrong, and nobody would find out until they read the table.
     */
    public CipherClient.Sealed seal(Output output) {
        // A HashMap rather than Map.of, because null is a value here and Map.of refuses it.
        // It used to be turned into an empty list on the way in, which erased the one
        // distinction this field carries: no rows *at all*, which is what a command has,
        // against no rows *matched*, which is a query's answer. Everything came back as the
        // second, so the console drew "no rows" over the top of every command's output.
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("text", output.text() == null ? "" : output.text());
        envelope.put("rows", output.rows());

        return cipherClient.encrypt(objectMapper.writeValueAsString(envelope), CONTEXT);
    }

    /**
     * Opens a sealed output.
     *
     * <p>An unopenable value is reported as such rather than thrown: a history screen that
     * showed nothing at all because the cipher was down would look like a run that produced
     * nothing, which is a different and much more alarming thing.
     */
    public Output open(byte[] ciphertext, String keyId) {
        try {
            Map<String, Object> envelope = objectMapper.readValue(
                    cipherClient.decrypt(ciphertext, keyId, CONTEXT),
                    new TypeReference<Map<String, Object>>() {
                    });

            Object rows = envelope.get("rows");

            return new Output(
                    (String) envelope.get("text"),
                    rows == null ? null : objectMapper.convertValue(rows, ROWS));
        } catch (RuntimeException failure) {
            log.warn("A run's output could not be opened: {}", failure.getMessage());
            return new Output("(the output is sealed and mcp-cipher did not answer)", null);
        }
    }
}
