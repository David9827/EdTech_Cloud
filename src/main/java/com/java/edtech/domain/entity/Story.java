package com.java.edtech.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.java.edtech.domain.enums.StoryStatus;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "story")
@Getter
@Setter
public class Story {
    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "topic_id")
    private UUID topicId;

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    @Column(name = "img_url", length = 500)
    private String imgUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "story_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private StoryStatus status = StoryStatus.DRAFT;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
