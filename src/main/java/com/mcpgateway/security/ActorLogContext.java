package com.mcpgateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Puts the acting user's id on every log line the request produces.
 *
 * <p>Without it a log could say what happened and not who asked for it: the identity lived
 * in the database, on the run row, and nowhere a log query could reach. "Show me everything
 * this person did" had no answer that did not involve SQL.
 *
 * <p><b>The id, not the email.</b> It answers the same question — one query per user, and
 * the name is one lookup away in {@code users} — and it keeps personal data out of six log
 * files and a log store that has no encryption and thirty days of retention. This stack
 * seals query output for exactly that reason; writing addresses into the logs beside it
 * would undo the arrangement one line at a time.
 *
 * <p>Runs after {@link JwtAuthenticationFilter}, because there is nobody to name until the
 * token has been read. An unauthenticated request logs {@code actor=-}, which is honest:
 * signing in is itself a request, and it has no actor until it succeeds.
 */
@Component
@Order(ActorLogContext.ORDER)
public class ActorLogContext extends OncePerRequestFilter {

    /**
     * After Spring Security's chain, which is where the token is read.
     *
     * <p>{@code LOWEST_PRECEDENCE} would do as well; a named constant says the ordering is
     * deliberate rather than whatever the container happened to choose.
     */
    static final int ORDER = Integer.MAX_VALUE - 100;

    /** The key the log pattern reads. */
    public static final String ACTOR = "actor";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        MDC.put(ACTOR, SecurityUtils.currentUserId().map(String::valueOf).orElse("-"));

        try {
            filterChain.doFilter(request, response);
        } finally {
            // In a finally, and this is the whole risk of MDC: it is thread-local, and a
            // container hands the same thread to the next request. Left behind, one
            // person's id would label another person's work — a log that is wrong about
            // who did something is worse than a log that does not say.
            MDC.remove(ACTOR);
        }
    }
}
