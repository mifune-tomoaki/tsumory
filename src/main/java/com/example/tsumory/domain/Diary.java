package com.example.tsumory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "diaries",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "diary_on"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diary {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "diary_on", nullable = false, updatable = false)
  private LocalDate diaryOn;

  @NotBlank
  @Column(nullable = false, columnDefinition = "text")
  private String body;

  @Column(name = "generated_at", nullable = false)
  private Instant generatedAt;

  public Diary(User user, LocalDate diaryOn, String body, Instant generatedAt) {
    this.user = user;
    this.diaryOn = diaryOn;
    this.body = body;
    this.generatedAt = generatedAt;
  }

  /** 生成・再生成の結果で本文と生成日時をまとめて上書きする(upsert)。 */
  public void regenerate(String body, Instant generatedAt) {
    this.body = body;
    this.generatedAt = generatedAt;
  }
}
