package com.example.tsumory.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tsumory.domain.Post;
import org.junit.jupiter.api.Test;

class TestFixturesTest {

  @Test
  void post_buildsAPostWithTheGivenBodyOwnedByTheFixtureUser() {
    Post post = TestFixtures.post(TestFixtures.POST_BODY_MORNING);

    assertThat(post.getBody()).isEqualTo(TestFixtures.POST_BODY_MORNING);
    assertThat(post.getUser().getEmail()).isEqualTo(TestFixtures.user().getEmail());
  }
}
