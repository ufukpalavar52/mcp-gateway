package com.mcpgateway.dto.request;

import java.util.List;
import java.util.Map;

/**
 * A direct tool invocation, and the approval it may be carrying.
 *
 * <p>The arguments used to be the whole body. They are wrapped now because a second thing
 * has to travel with them, and a command is not something to put in a query parameter:
 * they run to several lines — a heredoc writing a file is one value — and a URL is the
 * wrong shape for that as well as the wrong place, since query strings are what gets
 * written to access logs.
 *
 * @param arguments what the tool was called with
 * @param actionId  which of the tool's actions is wanted, for a definition with several
 * @param expect    the command a person was shown and agreed to, empty on a first ask
 * @param expectAll every command agreed to at once, when the plan showed more than one
 */
public record ToolExecuteRequest(Map<String, Object> arguments,
                                 Long actionId,
                                 String expect,
                                 List<String> expectAll) {

    public ToolExecuteRequest {
        // Absent and empty mean the same thing to everything downstream, and normalising
        // here keeps every reader from having to decide that for itself.
        arguments = arguments == null ? Map.of() : arguments;
        expect = expect == null ? "" : expect;
        expectAll = expectAll == null ? List.of() : expectAll;
    }
}
