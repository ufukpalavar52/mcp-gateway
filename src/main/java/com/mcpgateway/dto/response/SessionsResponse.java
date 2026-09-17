package com.mcpgateway.dto.response;

/**
 * What is honestly known about somebody's sign-ins.
 *
 * <p>A count, and nothing else. The screen this replaces showed a device, a city and a
 * timestamp, all of them invented — the city was a translation string. None of that is
 * recorded anywhere, so none of it is reported here: a number that is true is worth more
 * than a list that is not.
 *
 * <p>Recording the rest is a separate decision with a separate cost — storing an address
 * against a person is not a thing to start doing by accident.
 */
public record SessionsResponse(int active) {
}
