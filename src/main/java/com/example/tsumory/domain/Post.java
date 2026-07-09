package com.example.tsumory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @NotBlank
  @Size(max = 100)
  @Column(nullable = false, length = 100)
  private String body;

  @Column(name = "posted_at", nullable = false, updatable = false)
  private Instant postedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "category")
  private PostCategory category;

  public Post(User user, String body, Instant postedAt) {
    this.user = user;
    this.body = body;
    this.postedAt = postedAt;
  }

  /** 本文を書き換える。{@code postedAt}は投稿時刻のまま変更しない。本文が変わるためカテゴリは再分類対象としてクリアする。 */
  public void edit(String body) {
    this.body = body;
    this.category = null;
  }

  /** AIによる分類結果を反映する。 */
  public void assignCategory(PostCategory category) {
    this.category = category;
  }
}
