package com.example.tsumory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tsumory.support.TestFixtures;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * AnthropicDiaryWriter.write()がテキストブロックを含まない応答から空文字列を返した場合でも、
 * 空の日記が気づかれずに保存されないよう、body(取得元を問わず)にNotBlankを課していることを確認する。
 */
class DiaryTest {

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    validatorFactory.close();
  }

  @Test
  void rejectsBlankBody() {
    Diary diary = new Diary(TestFixtures.user(), LocalDate.now(), "", Instant.now());

    Set<ConstraintViolation<Diary>> violations = validator.validate(diary);

    assertThat(violations).isNotEmpty();
  }

  @Test
  void acceptsNonBlankBody() {
    Diary diary = new Diary(TestFixtures.user(), LocalDate.now(), "今日は良い一日だった", Instant.now());

    Set<ConstraintViolation<Diary>> violations = validator.validate(diary);

    assertThat(violations).isEmpty();
  }
}
