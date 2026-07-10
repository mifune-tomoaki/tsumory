package com.example.tsumory.support;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** service層のユニットテストで使い回す固定クロックとサンプルデータの生成ヘルパー。 */
public final class TestFixtures {

  public static final ZoneOffset ZONE = ZoneOffset.UTC;
  public static final Instant NOW = Instant.parse("2026-07-10T03:00:00Z");

  /** つぶやきのサンプル本文(投稿時点)。 */
  public static final String POST_BODY_MORNING = "通勤中に近所の公園で桜が咲き始めているのを見つけた。今年も見られてよかった。";

  /** つぶやきのサンプル本文(別の投稿)。 */
  public static final String POST_BODY_LUNCH = "お昼にラーメンを大盛りで頼んだら食べすぎて、午後はずっと眠気と戦っていた。";

  /** つぶやきのサンプル本文(編集後)。 */
  public static final String POST_BODY_EDITED = "夜は資格試験の勉強を2時間だけ頑張った。少しずつでも続けていきたい。";

  /** 日記のサンプル本文(生成直後)。 */
  public static final String DIARY_BODY_DRAFT =
      """
      今日は朝から天気が良く、通勤中に近所の公園で桜が咲き始めているのに気づいた。
      お昼はラーメンを食べすぎて午後は少し眠くなってしまったが、
      夜は資格試験の勉強を2時間続けることができた。
      """;

  /** 日記のサンプル本文(再生成後)。 */
  public static final String DIARY_BODY_REGENERATED =
      """
      今日は思っていたよりも充実した一日だった。
      桜を見て気分転換ができ、勉強も予定通り進められた。
      """;

  private TestFixtures() {}

  public static Clock fixedClock() {
    return Clock.fixed(NOW, ZONE);
  }

  public static User user() {
    return new User("hanako.tanaka@example.com", "$2a$10$dummyBcryptHashForTesting", NOW);
  }

  /** {@link #user()}が投稿した体で本文だけ差し替えたPostを組み立てる。 */
  public static Post post(String body) {
    return new Post(user(), body, Instant.now());
  }
}
