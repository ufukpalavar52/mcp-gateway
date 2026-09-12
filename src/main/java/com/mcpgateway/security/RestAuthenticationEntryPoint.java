package com.mcpgateway.security;

import com.mcpgateway.common.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Answers rejected requests in the shared JSON error shape.
 *
 * <p>The security chain runs before the DispatcherServlet, so without this class every
 * unknown path would be reported as {@code 401}: a typo in a URL looked exactly like a
 * missing token. The entry point therefore asks the handler mapping whether the request
 * would have reached a controller at all, and answers accordingly:
 *
 * <ul>
 *   <li>no mapping for the path → {@code 404}, the URL is simply wrong</li>
 *   <li>mapping exists but not for this verb → {@code 405}</li>
 *   <li>mapping exists → {@code 401}, the request really did need a token</li>
 * </ul>
 *
 * <p>The trade-off is deliberate: an anonymous caller can now tell which paths exist.
 * For an internal control plane whose API surface is documented anyway, correct status
 * codes are worth more than that much obscurity. Returning {@link HttpStatus#UNAUTHORIZED}
 * unconditionally is the one line change back.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    /**
     * Resolved lazily: the handler mapping is built by the MVC infrastructure, and
     * injecting it directly would tie the security chain into that bootstrap order.
     */
    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        Outcome outcome = resolveOutcome(request);

        response.setStatus(outcome.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiError body = ApiError.of(
                outcome.status().value(),
                outcome.errorCode(),
                outcome.message(),
                request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private Outcome resolveOutcome(HttpServletRequest request) {
        boolean anyMappingChecked = false;

        for (RequestMappingHandlerMapping mapping : handlerMappings) {
            anyMappingChecked = true;
            try {
                if (mapping.getHandler(request) != null) {
                    return Outcome.unauthorized();
                }
            } catch (HttpRequestMethodNotSupportedException ex) {
                // The path is mapped, the verb is not.
                return Outcome.methodNotAllowed(request.getMethod());
            } catch (Exception ex) {
                // Never let a lookup problem turn into a 500 on an unauthenticated path.
                return Outcome.unauthorized();
            }
        }

        // With no mapping to consult, assume the endpoint exists and demand a token.
        return anyMappingChecked ? Outcome.notFound() : Outcome.unauthorized();
    }

    private record Outcome(HttpStatus status, String errorCode, String message) {

        static Outcome unauthorized() {
            return new Outcome(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                    "A valid access token is required");
        }

        static Outcome notFound() {
            return new Outcome(HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "No endpoint matches this path");
        }

        static Outcome methodNotAllowed(String method) {
            return new Outcome(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                    "%s is not supported for this path".formatted(method));
        }
    }
}
