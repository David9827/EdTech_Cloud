package com.java.edtech.domain.entity;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_token")
@Getter
@Setter
public class RefreshToken {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    @Column(name = "is_revoked", nullable = false)
    private boolean revoked = false;

    @PrePersist
    void prePersist() {
        if (this.expiryDate == null) {
            this.expiryDate = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }
}
