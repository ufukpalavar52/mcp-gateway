package com.mcpgateway.domain.entity;

import com.mcpgateway.domain.enums.SecretKind;
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

import java.time.Instant;

/**
 * An encrypted secret. The gateway never decrypts: it stores ciphertext and the id
 * of the KMS key, and the dedicated crypto service performs the actual unwrap.
 */
@Entity
@Table(name = "secrets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Secret extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private SecretKind kind = SecretKind.OTHER;

    /** Never plaintext. */
    @Column(nullable = false)
    private byte[] ciphertext;

    @Column(name = "key_id", nullable = false)
    private String keyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
