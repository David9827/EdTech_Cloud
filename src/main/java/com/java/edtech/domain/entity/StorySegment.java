package com.java.edtech.domain.entity;

import java.util.UUID;

import com.java.edtech.domain.enums.EmotionType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "story_segment")
@Getter
@Setter
public class StorySegment {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id")
    private Story story;

    @Column(name = "segment_order", nullable = false)
    private Integer segmentOrder;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "emotion_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EmotionType emotion;
}
