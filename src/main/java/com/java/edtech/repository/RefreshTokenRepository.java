package com.java.edtech.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.java.edtech.domain.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Transactional
    @Query("""
            update RefreshToken rt
            set rt.revoked = true
            where rt.user.id = :userId and rt.revoked = false
            """)
    int revokeAllByUserId(@Param("userId") UUID userId);
}
