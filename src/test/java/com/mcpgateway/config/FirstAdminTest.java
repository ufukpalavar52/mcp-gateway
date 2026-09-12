package com.mcpgateway.config;

import com.mcpgateway.domain.entity.User;
import com.mcpgateway.domain.enums.UserRole;
import com.mcpgateway.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A fresh installation had no way in.
 *
 * <p>Registering through the panel gives a VIEWER — right for the second person, wrong for
 * the first, who then has to reach past the application into the database to grant
 * themselves the role.
 */
class FirstAdminTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final FirstAdmin bootstrap = new FirstAdmin(users, encoder);

    private void configured(String email, String password) {
        ReflectionTestUtils.setField(bootstrap, "email", email);
        ReflectionTestUtils.setField(bootstrap, "password", password);
        ReflectionTestUtils.setField(bootstrap, "name", "Administrator");
    }

    @Nested
    @DisplayName("into an empty database")
    class IntoAnEmptyDatabase {

        @Test
        void anAdministratorIsCreated() {
            configured("admin@example.com", "correct-horse-battery");
            given(users.count()).willReturn(0L);
            given(users.save(any(User.class))).willAnswer(call -> call.getArgument(0));

            bootstrap.create();

            verify(users).save(argThat(saved -> {
                assertThat(saved.getEmail()).isEqualTo("admin@example.com");
                assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
                return true;
            }));
        }

        @Test
        void thePasswordIsHashed() {
            // It arrives from an environment variable; what reaches the column must not be
            // the same string.
            configured("admin@example.com", "correct-horse-battery");
            given(users.count()).willReturn(0L);
            given(users.save(any(User.class))).willAnswer(call -> call.getArgument(0));

            bootstrap.create();

            verify(users).save(argThat(saved -> {
                assertThat(saved.getPasswordHash()).isNotEqualTo("correct-horse-battery");
                assertThat(encoder.matches("correct-horse-battery", saved.getPasswordHash())).isTrue();
                return true;
            }));
        }

        @Test
        void aShortPasswordCreatesNothing() {
            // The same floor the registration form applies. A bootstrap is not a way round it.
            configured("admin@example.com", "short");
            given(users.count()).willReturn(0L);

            bootstrap.create();

            verify(users, never()).save(any());
        }
    }

    @Test
    void anExistingAccountIsNotJoinedByAnother() {
        /*
         * Not "if this email is missing" — that would let anyone who can set an environment
         * variable add an administrator to a running system, and a compose file is easier
         * to edit than a database.
         */
        configured("admin@example.com", "correct-horse-battery");
        given(users.count()).willReturn(3L);

        bootstrap.create();

        verify(users, never()).save(any());
    }

    @Test
    void nothingConfiguredAsksNothingOfTheDatabase() {
        // An installation that manages its users elsewhere leaves these unset.
        configured("", "");

        bootstrap.create();

        verify(users, never()).count();
        verify(users, never()).save(any());
    }

    private static org.mockito.ArgumentMatcher<User> argThatMatcher(
            java.util.function.Predicate<User> predicate) {
        return predicate::test;
    }

    private static User argThat(java.util.function.Predicate<User> predicate) {
        return org.mockito.ArgumentMatchers.argThat(argThatMatcher(predicate));
    }
}
