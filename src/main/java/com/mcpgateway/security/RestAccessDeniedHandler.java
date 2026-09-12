package com.mcpgateway.security;

import tools.jackson.databind.ObjectMapper;
import com.mcpgateway.common.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Answers authenticated but unauthorised requests in the shared JSON error shape. */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiError body = ApiError.of(
                HttpStatus.FORBIDDEN.value(),
                "ACCESS_DENIED",
                "You are not allowed to perform this action",
                request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
