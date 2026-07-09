package com.example.tsumory.service;

import com.example.tsumory.domain.Diary;
import com.example.tsumory.domain.User;
import com.example.tsumory.repository.DiaryRepository;
import com.example.tsumory.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiaryService {

  private final DiaryRepository diaryRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  public Optional<Diary> findByDate(Long userId, LocalDate date) {
    Optional<Diary> diary = diaryRepository.findByUserIdAndDiaryOn(userId, date);
    log.debug("Diary lookup for userId={} date={} found={}", userId, date, diary.isPresent());
    return diary;
  }

  @Transactional
  public Diary upsertToday(Long userId, String body) {
    LocalDate today = LocalDate.now(clock);
    Optional<Diary> existing = diaryRepository.findByUserIdAndDiaryOn(userId, today);
    // 本文には個人的な内容が含まれるためログには文字数のみ出力する
    if (existing.isPresent()) {
      Diary diary = existing.get();
      diary.regenerate(body, clock.instant());
      log.info(
          "Regenerated diary id={} for userId={} date={} (length={})",
          diary.getId(),
          userId,
          today,
          body.length());
      return diary;
    }
    User user = userRepository.getReferenceById(userId);
    Diary diary = diaryRepository.save(new Diary(user, today, body, clock.instant()));
    log.info(
        "Created diary id={} for userId={} date={} (length={})",
        diary.getId(),
        userId,
        today,
        body.length());
    return diary;
  }
}
