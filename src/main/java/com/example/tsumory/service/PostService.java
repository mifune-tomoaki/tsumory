package com.example.tsumory.service;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.User;
import com.example.tsumory.repository.PostRepository;
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
  private final Clock clock;

  public List<Post> findTodayPosts(User user) {
    LocalDate today = LocalDate.now(clock);
    Instant startOfDay = today.atStartOfDay(clock.getZone()).toInstant();
    Instant startOfNextDay = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
    List<Post> posts =
        postRepository
            .findByUser_IdAndPostedAtGreaterThanEqualAndPostedAtLessThanOrderByPostedAtAsc(
                user.getId(), startOfDay, startOfNextDay);
    log.debug("Fetched {} posts for userId={} on {}", posts.size(), user.getId(), today);
    return posts;
  }

  @Transactional
  public Post create(User user, String body) {
    Post saved = postRepository.save(new Post(user, body, clock.instant()));
    // 本文には個人的な内容が含まれるためログには文字数のみ出力する
    log.info(
        "Created post id={} for userId={} (length={})", saved.getId(), user.getId(), body.length());
    return saved;
  }

  @Transactional
  public void edit(User user, Long postId, String body) {
    findOwnedPost(user, postId).edit(body);
    log.info("Edited post id={} for userId={} (length={})", postId, user.getId(), body.length());
  }

  @Transactional
  public void delete(User user, Long postId) {
    postRepository.delete(findOwnedPost(user, postId));
    log.info("Deleted post id={} for userId={}", postId, user.getId());
  }

  private Post findOwnedPost(User user, Long postId) {
    return postRepository
        .findByIdAndUserId(postId, user.getId())
        .orElseThrow(
            () -> {
              log.warn("Post id={} not found or not owned by userId={}", postId, user.getId());
              return new ResourceNotFoundException();
            });
  }
}
