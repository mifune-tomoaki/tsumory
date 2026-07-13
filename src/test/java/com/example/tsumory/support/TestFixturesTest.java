package com.example.tsumory.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tsumory.domain.Diary;
import com.example.tsumory.domain.Post;
import org.junit.jupiter.api.Test;

class TestFixturesTest {

  @Test
  void post_buildsAPostWithTheGivenBodyOwnedByTheFixtureUser() {
    Post post = TestFixtures.post(TestFixtures.POST_BODY_MORNING);

    assertThat(post.getBody()).isEqualTo(TestFixtures.POST_BODY_MORNING);
    assertThat(post.getUser().getEmail()).isEqualTo(TestFixtures.user().getEmail());
  }

  @Test
  void diary_buildsADiaryWithTheGivenBodyOwnedByTheFixtureUserOnToday() {
    Diary diary = TestFixtures.diary(TestFixtures.DIARY_BODY_DRAFT);

    assertThat(diary.getBody()).isEqualTo(TestFixtures.DIARY_BODY_DRAFT);
    assertThat(diary.getUser().getEmail()).isEqualTo(TestFixtures.user().getEmail());
    assertThat(diary.getDiaryOn()).isEqualTo(TestFixtures.today());
  }
}
