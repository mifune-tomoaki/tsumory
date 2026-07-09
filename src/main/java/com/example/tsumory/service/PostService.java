package com.example.tsumory.service;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.User;
import com.example.tsumory.repository.PostRepository;
import com.example.tsumory.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

  private final PostRepository postRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  public List<Post> findTodayPosts(Long userId) {
    LocalDate today = LocalDate.now(clock);
    Instant startOfDay = today.atStartOfDay(clock.getZone()).toInstant();
    Instant startOfNextDay = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
    List<Post> posts =
        postRepository
            .findByUser_IdAndPostedAtGreaterThanEqualAndPostedAtLessThanOrderByPostedAtAsc(
                userId, startOfDay, startOfNextDay);
    log.debug("Fetched {} posts for userId={} on {}", posts.size(), userId, today);
    return posts;
  }

  @Transactional
  public Post create(Long userId, String body) {
    User user = userRepository.getReferenceById(userId);
    Post saved = postRepository.save(new Post(user, body, clock.instant()));
    // 本文には個人的な内容が含まれるためログには文字数のみ出力する
    log.info("Created post id={} for userId={} (length={})", saved.getId(), userId, body.length());
    return saved;
  }

  @Transactional
  public void edit(Long userId, Long postId, String body) {
    findOwnedPost(userId, postId).edit(body);
    log.info("Edited post id={} for userId={} (length={})", postId, userId, body.length());
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
