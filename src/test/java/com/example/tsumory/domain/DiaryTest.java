package com.example.tsumory.domain;

import static com.example.tsumory.support.TestFixtures.DIARY_BODY_DRAFT;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tsumory.support.TestFixtures;
import jakarta.validation.ConstraintViolation;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * AnthropicDiaryWriter.write()がテキストブロックを含まない応答から空文字列を返した場合でも、
 * 空の日記が気づかれずに保存されないよう、body(取得元を問わず)にNotBlankを課していることを確認する。
 */
class DiaryTest {

  @Test
  void bodyValidation_rejectsBlankBody() {
    Diary diary = TestFixtures.diary("");

    Set<ConstraintViolation<Diary>> violations = TestFixtures.validate(diary);

    assertThat(violations).isNotEmpty();
  }

  @Test
  void bodyValidation_acceptsNonBlankBody() {
    Diary diary = TestFixtures.diary(DIARY_BODY_DRAFT);

    Set<ConstraintViolation<Diary>> violations = TestFixtures.validate(diary);

    assertThat(violations).isEmpty();
  }
}
