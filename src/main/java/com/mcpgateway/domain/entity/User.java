package com.mcpgateway.domain.entity;

import com.mcpgateway.domain.enums.UserRole;
import com.mcpgateway.domain.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** A panel account. Sessions live in Redis, so nothing session related is stored here. */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Column(nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    /** Null for accounts provisioned through SSO. */
    @Column(name = "password_hash")
    private String passwordHash;

    /**
     * Whether this password was chosen by somebody other than its owner.
     *
     * <p>Set when an administrator creates an account with a password they picked. Two
     * people know it and only one of them owns the account, so the panel locks to the
     * password screen until it has been changed — the window is not something to leave
     * open out of politeness.
     */
    @Builder.Default
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Builder.Default
    @Column(nullable = false)
    private UserRole role = UserRole.VIEWER;

    @Builder.Default
    @Column(nullable = false)
    private UserStatus status = UserStatus.INVITED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(name = "avatar_url")
    private String avatarUrl;

    /** Free-form UI preferences: locale, theme, notification toggles. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> preferences = new HashMap<>();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Only an active account may authenticate. */
    public boolean canAuthenticate() {
        return status == UserStatus.ACTIVE && passwordHash != null;
    }
}
