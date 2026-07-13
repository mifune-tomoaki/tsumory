package com.example.tsumory.service;

import static com.example.tsumory.support.TestFixtures.POST_BODY_EDITED;
import static com.example.tsumory.support.TestFixtures.POST_BODY_LUNCH;
import static com.example.tsumory.support.TestFixtures.POST_BODY_MORNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.domain.User;
import com.example.tsumory.repository.PostRepository;
import com.example.tsumory.repository.UserRepository;
import com.example.tsumory.support.TestFixtures;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

  private static final Long USER_ID = 1L;

  @Mock private PostRepository postRepository;
  @Mock private UserRepository userRepository;
  @Mock private PostCategorizer postCategorizer;
  @Mock private PostService self;

  private Clock clock;
  private PostService postService;

  @BeforeEach
  void setUp() {
    clock = TestFixtures.fixedClock();
    postService = new PostService(postRepository, userRepository, postCategorizer, clock, self);
  }

  private Instant startOfDay(LocalDate date) {
    return date.atStartOfDay(TestFixtures.ZONE).toInstant();
  }

  @Test
  void findPostsOn_delegatesToRepositoryWithDayBoundaries() {
    LocalDate date = LocalDate.of(2026, 7, 10);
    Post post = new Post(TestFixtures.user(), POST_BODY_MORNING, clock.instant());
    when(postRepository
            .findByUser_IdAndPostedAtGreaterThanEqualAndPostedAtLessThanOrderByPostedAtAsc(
                USER_ID, startOfDay(date), startOfDay(date.plusDays(1))))
        .thenReturn(List.of(post));

    List<Post> result = postService.findPostsOn(USER_ID, date);

    assertThat(result).containsExactly(post);
  }

  @Test
  void create_savesPostWhenUnderDailyLimit() {
    when(postRepository.countByUser_IdAndPostedAtGreaterThanEqualAndPostedAtLessThan(
            eq(USER_ID), any(), any()))
        .thenReturn(49L);
    User user = TestFixtures.user();
    when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
    when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Post result = postService.create(USER_ID, POST_BODY_MORNING);

    assertThat(result.getUser()).isEqualTo(user);
    assertThat(result.getBody()).isEqualTo(POST_BODY_MORNING);
    assertThat(result.getPostedAt()).isEqualTo(clock.instant());
    verify(postRepository).save(any(Post.class));
    // 分類は非同期でトリガーされる
    verify(postCategorizer, timeout(2000))
        .categorize(argThat(post -> post.getBody().equals(POST_BODY_MORNING)));
  }

  @Test
  void create_throwsWhenDailyLimitAlreadyReached() {
    when(postRepository.countByUser_IdAndPostedAtGreaterThanEqualAndPostedAtLessThan(
            eq(USER_ID), any(), any()))
        .thenReturn(50L);

    assertThatThrownBy(() -> postService.create(USER_ID, POST_BODY_LUNCH))
        .isInstanceOf(DailyPostLimitExceededException.class);

    verify(postRepository, never()).save(any());
    verifyNoInteractions(userRepository, postCategorizer);
  }

  @Test
  void create_doesNotPropagateCategorizationFailure() {
    when(postRepository.countByUser_IdAndPostedAtGreaterThanEqualAndPostedAtLessThan(
            eq(USER_ID), any(), any()))
        .thenReturn(0L);
    when(userRepository.getReferenceById(USER_ID)).thenReturn(TestFixtures.user());
    when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(postCategorizer.categorize(any(Post.class))).thenThrow(new RuntimeException("AI error"));

    Post result = postService.create(USER_ID, POST_BODY_LUNCH);

    assertThat(result).isNotNull();
    verify(postCategorizer, timeout(2000))
        .categorize(argThat(post -> post.getBody().equals(POST_BODY_LUNCH)));
    // 分類失敗時はself.applyCategory(...)まで到達しない
    verify(self, after(500).never()).applyCategory(any(), any());
  }

  // applyCategory()はself(Springが管理するプロキシ経由の自己参照)を呼ぶ必要がある。this.applyCategory(...)という
  // 同一クラス内の自己呼び出しに戻すと@Transactionalが一切効かなくなり、AI呼び出しは成功しても分類結果が
  // デタッチ済みエンティティへの書き込みとして握りつぶされ、DBには反映されない(docs/code-review-service.md 指摘1)。
  @Test
  void create_appliesSuccessfulCategorizationThroughSelfProxy() {
    when(postRepository.countByUser_IdAndPostedAtGreaterThanEqualAndPostedAtLessThan(
            eq(USER_ID), any(), any()))
        .thenReturn(0L);
    when(userRepository.getReferenceById(USER_ID)).thenReturn(TestFixtures.user());
    when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(postCategorizer.categorize(any(Post.class))).thenReturn(PostCategory.WORK);

    Post result = postService.create(USER_ID, POST_BODY_MORNING);

    verify(self, timeout(2000)).applyCategory(result.getId(), PostCategory.WORK);
  }

  @Test
  void edit_updatesBodyAndClearsCategory() {
    Post post = new Post(TestFixtures.user(), POST_BODY_MORNING, clock.instant());
    post.assignCategory(PostCategory.WORK);
    when(postRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(post));

    postService.edit(USER_ID, 10L, POST_BODY_EDITED);

    assertThat(post.getBody()).isEqualTo(POST_BODY_EDITED);
    assertThat(post.getCategory()).isNull();
  }

  @Test
  void edit_throwsWhenPostNotOwnedByUser() {
    when(postRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> postService.edit(USER_ID, 10L, POST_BODY_EDITED))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void applyCategory_assignsCategoryToExistingPost() {
    Post post = new Post(TestFixtures.user(), POST_BODY_MORNING, clock.instant());
    when(postRepository.findById(10L)).thenReturn(Optional.of(post));

    postService.applyCategory(10L, PostCategory.LEARNING);

    assertThat(post.getCategory()).isEqualTo(PostCategory.LEARNING);
  }

  @Test
  void applyCategory_doesNothingWhenPostNoLongerExists() {
    when(postRepository.findById(10L)).thenReturn(Optional.empty());

    assertThatCode(() -> postService.applyCategory(10L, PostCategory.LEARNING))
        .doesNotThrowAnyException();
  }

  @Test
  void setCategory_overwritesCategoryForOwnedPost() {
    Post post = new Post(TestFixtures.user(), POST_BODY_MORNING, clock.instant());
    post.assignCategory(PostCategory.WORK);
    when(postRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(post));

    postService.setCategory(USER_ID, 10L, PostCategory.PRIVATE);

    assertThat(post.getCategory()).isEqualTo(PostCategory.PRIVATE);
  }

  @Test
  void setCategory_throwsWhenPostNotOwnedByUser() {
    when(postRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> postService.setCategory(USER_ID, 10L, PostCategory.PRIVATE))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void delete_removesOwnedPost() {
    Post post = new Post(TestFixtures.user(), POST_BODY_MORNING, clock.instant());
    when(postRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(post));

    postService.delete(USER_ID, 10L);

    verify(postRepository).delete(post);
  }

  @Test
  void delete_throwsWhenPostNotOwnedByUser() {
    when(postRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> postService.delete(USER_ID, 10L))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(postRepository, never()).delete(any());
  }

  @Test
  void findOwnedPost_returnsPostWhenOwnedByUser() {
    Post post = new Post(TestFixtures.user(), POST_BODY_MORNING, clock.instant());
    when(postRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(post));

    Post result = postService.findOwnedPost(USER_ID, 10L);

    assertThat(result).isEqualTo(post);
  }

  @Test
  void findOwnedPost_throwsWhenPostBelongsToAnotherUser() {
    when(postRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> postService.findOwnedPost(USER_ID, 10L))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
