package com.mcpgateway.config;

import com.mcpgateway.domain.entity.User;
import com.mcpgateway.domain.enums.UserRole;
import com.mcpgateway.domain.enums.UserStatus;
import com.mcpgateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator, once, from the environment.
 *
 * <p>A freshly installed stack had no way in. Registering through the panel gives a
 * {@code VIEWER} — right for the second person and wrong for the first, who then has to
 * reach past the application into the database to grant themselves the role. A setup step
 * that ends in "now run this UPDATE" is a setup step that is not finished.
 *
 * <p><b>Only into an empty users table.</b> Not "if this email is missing" — that would let
 * anyone who can set an environment variable add an administrator to a running system, and
 * a compose file is easier to edit than a database. Once there is one account this does
 * nothing at all, whatever the variables say.
 *
 * <p>Silence is the normal case. An installation that manages its users elsewhere leaves
 * these unset and is never asked about them again.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirstAdmin {

    /** The same floor the registration form applies. A bootstrap is not a way around it. */
    private static final int MINIMUM_PASSWORD = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${mcp.admin.email:}")
    private String email;

    @Value("${mcp.admin.password:}")
    private String password;

    @Value("${mcp.admin.name:Administrator}")
    private String name;

    @EventListener(ApplicationReadyEvent.class)
    public void create() {
        // Before anything touches the repository. There is no @Transactional on this
        // method for the same reason: a transaction opens on entry, which means a
        // connection, which means every test that loads a context would need a database
        // to start an administrator it was never going to create.
        //
        // The two statements below do not need one between them anyway. Each is atomic,
        // and the window between them only matters if two gateways start against an empty
        // database at the same instant — in which case the unique index on email decides.
        if (email.isBlank() || password.isBlank()) {
            return;
        }

        if (userRepository.count() > 0) {
            // Said once, at INFO, because somebody who left the variables in their compose
            // file should be able to see that they are doing nothing rather than wonder
            // whether the password is being reapplied on every restart. It is not.
            log.info("An account already exists; the configured administrator was not created");
            return;
        }

        if (password.length() < MINIMUM_PASSWORD) {
            log.error("MCP_ADMIN_PASSWORD is shorter than {} characters; no administrator "
                    + "was created. Set a longer one and restart.", MINIMUM_PASSWORD);
            return;
        }

        User admin = userRepository.save(User.builder()
                .email(email.trim())
                .fullName(name)
                .passwordHash(passwordEncoder.encode(password))
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());

        // The address, never the password — this line goes to a file and a log store.
        log.info("Created the first administrator: {}", admin.getEmail());
    }
}
