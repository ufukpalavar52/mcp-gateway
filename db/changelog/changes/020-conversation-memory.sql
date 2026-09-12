--liquibase formatted sql

--changeset mcp-panel:020-conversation-memory dbms:postgresql
--comment: summaries for long conversations, and answers that needed no tool

-- A question can lean on the ones before it now — but the window slides. After fifty
-- turns the beginning of the conversation — the question that established what everyone
-- has been talking about — fell outside it, as though it had never been asked.
--
-- The summary grows by folding in the turns that leave: re-reading the whole
-- conversation each time would cost twenty times as much at the thousandth question as
-- at the fiftieth. summarised_through_id records how far the folding reached, so no
-- turn enters the summary twice.
ALTER TABLE conversations ADD COLUMN summary               text;
ALTER TABLE conversations ADD COLUMN summarised_through_id bigint;

-- Not everything typed into the console is a question about data: "what did I just run",
-- "what does this query do", "why did it come back empty" — none of them needs a tool
-- called, and all of them can be answered from the conversation. Answering those with
-- "no tool matches" made the console useless for exactly the questions people ask it.
--
-- A separate column from `statement`, because it is a separate thing: one is what a
-- system ran, the other is what a model said. Keeping them together would make a guess
-- hale getirirdi.
ALTER TABLE conversation_turns ADD COLUMN answer text;

--rollback ALTER TABLE conversation_turns DROP COLUMN IF EXISTS answer;
--rollback ALTER TABLE conversations DROP COLUMN IF EXISTS summarised_through_id;
--rollback ALTER TABLE conversations DROP COLUMN IF EXISTS summary;
