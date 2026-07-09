package com.example.tsumory.service;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.domain.User;
import com.example.tsumory.repository.PostRepository;
import com.example.tsumory.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

  private static final int DAILY_POST_LIMIT = 50;

  private final PostRepository postRepository;
  private final UserRepository userRepository;
  private final PostCategorizer postCategorizer;
  private final Clock clock;

  private final ExecutorService categorizationExecutor =
      Executors.newVirtualThreadPerTaskExecutor();

  /** 指定した日付(その日の00:00〜翌日00:00)のつぶやきを投稿時刻昇順で取得する。 */
  public List<Post> findPostsOn(Long userId, LocalDate date) {
    List<Post> posts =
        postRepository
            .findByUser_IdAndPostedAtGreaterThanEqualAndPostedAtLessThanOrderByPostedAtAsc(
                userId, startOfDay(date), startOfDay(date.plusDays(1)));
    log.debug("Fetched {} posts for userId={} on {}", posts.size(), userId, date);
    return posts;
  }

  @Transactional
  public Post create(Long userId, String body) {
    LocalDate today = LocalDate.now(clock);
    long todayCount =
        postRepository.countByUser_IdAndPostedAtGreaterThanEqualAndPostedAtLessThan(
            userId, startOfDay(today), startOfDay(today.plusDays(1)));
    if (todayCount >= DAILY_POST_LIMIT) {
      log.warn("Daily post limit exceeded userId={} count={}", userId, todayCount);
      throw new DailyPostLimitExceededException(DAILY_POST_LIMIT);
    }

    User user = userRepository.getReferenceById(userId);
    Post saved = postRepository.save(new Post(user, body, clock.instant()));
    // 本文には個人的な内容が含まれるためログには文字数のみ出力する
    log.info("Created post id={} for userId={} (length={})", saved.getId(), userId, body.length());
    triggerCategorization(saved.getId(), body);
    return saved;
  }

  @Transactional
  public void edit(Long userId, Long postId, String body) {
    Post post = findOwnedPost(userId, postId);
    post.edit(body);
    log.info("Edited post id={} for userId={} (length={})", postId, userId, body.length());
    triggerCategorization(post.getId(), body);
  }

  /** AIによる分類結果を反映する。分類完了までに投稿が削除されている場合は何もしない。 */
  @Transactional
  public void applyCategory(Long postId, PostCategory category) {
    postRepository.findById(postId).ifPresent(post -> post.assignCategory(category));
  }

  /** ユーザーがAIの分類結果を手動で上書きする。以後の本文編集までは上書き後のカテゴリを維持する。 */
  @Transactional
  public void setCategory(Long userId, Long postId, PostCategory category) {
    Post post = findOwnedPost(userId, postId);
    post.assignCategory(category);
    log.info("Manually set category={} for post id={} userId={}", category, postId, userId);
  }

  @PreDestroy
  void shutdownCategorizationExecutor() {
    categorizationExecutor.shutdown();
  }

  private void triggerCategorization(Long postId, String body) {
    categorizationExecutor.execute(
        () -> {
          try {
            applyCategory(postId, postCategorizer.categorize(body));
          } catch (RuntimeException e) {
            log.warn("Post categorization failed postId={} error={}", postId, e.toString());
          }
        });
  }

  private Instant startOfDay(LocalDate date) {
    return date.atStartOfDay(clock.getZone()).toInstant();
  }

  @Transactional
  public void delete(Long userId, Long postId) {
    postRepository.delete(findOwnedPost(userId, postId));
    log.info("Deleted post id={} for userId={}", postId, userId);
  }

  public Post findOwnedPost(Long userId, Long postId) {
    return postRepository
        .findByIdAndUserId(postId, userId)
        .orElseThrow(
            () -> {
              log.warn("Post id={} not found or not owned by userId={}", postId, userId);
              return new ResourceNotFoundException();
            });
  }
}
