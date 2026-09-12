package com.mcpgateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates a request from its {@code Authorization: Bearer} header.
 *
 * <p>Three conditions must all hold, in this order:
 * <ol>
 *   <li>the signature verifies and the token has not expired</li>
 *   <li>the token is an access token, not a refresh token</li>
 *   <li>the token id is still registered in the {@link TokenStore}</li>
 * </ol>
 *
 * <p>The last check is what makes logout take effect immediately. A failure never
 * throws: the filter simply leaves the context empty and lets the entry point answer.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService tokenService;
    private final TokenStore tokenStore;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        extractBearerToken(request)
                .flatMap(tokenService::parse)
                .filter(claims -> claims.type() == TokenType.ACCESS)
                .filter(claims -> tokenStore.isActive(TokenType.ACCESS, claims.tokenId()))
                .ifPresent(claims -> authenticate(claims, request));

        filterChain.doFilter(request, response);
    }

    private void authenticate(TokenClaims claims, HttpServletRequest request) {
        AuthenticatedUser principal = new AuthenticatedUser(
                claims.userId(), claims.email(), claims.role(), claims.tokenId());

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(principal.authority())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
