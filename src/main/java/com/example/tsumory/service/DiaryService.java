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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiaryService {

  private static final int PAGE_SIZE = 20;

  private final DiaryRepository diaryRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  public Optional<Diary> findByDate(Long userId, LocalDate date) {
    Optional<Diary> diary = diaryRepository.findByUserIdAndDiaryOn(userId, date);
    log.debug("Diary lookup for userId={} date={} found={}", userId, date, diary.isPresent());
    return diary;
  }

  /** 過去の日記一覧・検索。{@code q}が空/nullなら全件、指定があれば本文の部分一致で絞り込む。 */
  public Page<Diary> findPage(Long userId, String q, int page) {
    PageRequest pageRequest = PageRequest.of(page, PAGE_SIZE);
    Page<Diary> result =
        (q == null || q.isBlank())
            ? diaryRepository.findByUserIdOrderByDiaryOnDesc(userId, pageRequest)
            : diaryRepository.findByUserIdAndBodyContainingIgnoreCaseOrderByDiaryOnDesc(
                userId, q, pageRequest);
    log.debug(
        "Diary list for userId={} q={} page={} totalElements={}",
        userId,
        q,
        page,
        result.getTotalElements());
    return result;
  }

  @Transactional
  public Diary upsert(Long userId, LocalDate date, String body) {
    Optional<Diary> existing = diaryRepository.findByUserIdAndDiaryOn(userId, date);
    // 本文には個人的な内容が含まれるためログには文字数のみ出力する
    if (existing.isPresent()) {
      Diary diary = existing.get();
      diary.regenerate(body, clock.instant());
      log.info(
          "Regenerated diary id={} for userId={} date={} (length={})",
          diary.getId(),
          userId,
          date,
          body.length());
      return diary;
    }
    User user = userRepository.getReferenceById(userId);
    Diary diary = diaryRepository.save(new Diary(user, date, body, clock.instant()));
    log.info(
        "Created diary id={} for userId={} date={} (length={})",
        diary.getId(),
        userId,
        date,
        body.length());
    return diary;
  }
}
