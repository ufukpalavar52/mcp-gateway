package com.mcpgateway.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading the rows out of an endpoint's answer.
 *
 * <p>A search for eleven users came back as a paragraph of JSON, because a REST body is a
 * table wearing a different coat and nothing was taking the coat off.
 *
 * <p>Three shapes are recognised and everything else is left as text. A wrong table is
 * worse than honest JSON: it quietly omits half the answer and looks complete.
 */
class JsonRowsTest {

    private final JsonRows rows = new JsonRows();

    @Test
    void anArrayOfObjectsIsTheRows() {
        var found = rows.of("[{\"id\": 1, \"name\": \"ali\"}, {\"id\": 2, \"name\": \"veli\"}]");

        assertThat(found).hasSize(2);
        assertThat(found.getFirst()).containsEntry("name", "ali");
    }

    @Test
    void anEnvelopeIsOpened() {
        // The commonest listing shape: the rows under a name, with a count beside them.
        var found = rows.of("{\"count\": 1, \"data\": [{\"id\": 102, \"first_name\": \"Mehmet\"}]}");

        assertThat(found).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("first_name", "Mehmet"));
    }

    @Test
    void aSingleRecordIsOneRow() {
        // Which is how a GET by id reads.
        var found = rows.of("{\"id\": 102, \"first_name\": \"Mehmet\"}");

        assertThat(found).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("id", 102));
    }

    @Test
    void anEmptyListIsAnAnswerRatherThanNothing() {
        // "No rows" is what the endpoint said, and the panel has a sentence for it.
        assertThat(rows.of("{\"count\": 0, \"data\": []}")).isEmpty();
    }

    @Test
    void aListOfValuesIsNotATable() {
        // It is one value that happens to have several parts, not several records.
        assertThat(rows.of("[1, 2, 3]")).isNull();
    }

    @Test
    void anObjectIsOneRowEvenWhenAFieldHoldsAList() {
        /*
         * The rule is "a single object is one record", and it does not look inside to
         * change its mind. A field that holds a list is a column whose value is a list —
         * unusual to read, but it is what the endpoint said, and inventing a different
         * shape for it would be this service editing the answer.
         */
        assertThat(rows.of("{\"tags\": [\"a\", \"b\"]}")).singleElement()
                .satisfies(row -> assertThat(row).containsKey("tags"));
    }

    @Test
    void twoArraysAreNotChosenBetween() {
        /*
         * Which of them did the reader mean? Answering that would be this service deciding
         * what somebody's API meant, and being wrong half the time.
         */
        assertThat(rows.of("{\"data\": [{\"id\": 1}], \"errors\": [{\"code\": \"x\"}]}")).isNull();
    }

    @Test
    void anArrayUnderAnUnfamiliarNameIsLeftAlone() {
        // Only the names an envelope usually uses. Anything else may be a field that
        // happens to be a list rather than the answer itself.
        assertThat(rows.of("{\"attachments\": [{\"id\": 1}]}")).isNull();
    }

    @Test
    void aBodyThatIsNotJsonIsLeftAsText() {
        assertThat(rows.of("user deleted")).isNull();
        assertThat(rows.of("")).isNull();
        assertThat(rows.of(null)).isNull();
    }

    @Test
    void anEnormousAnswerIsCapped() {
        String many = "[" + "{\"id\": 1},".repeat(999) + "{\"id\": 1}]";

        assertThat(rows.of(many)).hasSize(500);
    }
}
