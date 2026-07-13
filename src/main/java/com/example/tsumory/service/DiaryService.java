package com.example.tsumory.service;

import com.example.tsumory.domain.Diary;
import com.example.tsumory.domain.User;
import com.example.tsumory.repository.DiaryRepository;
import com.example.tsumory.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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

  /** 指定した日付が「今日」より未来かどうかを判定する。日記の閲覧・生成のどちらも未来日付は認めない。 */
  public boolean isFutureDate(LocalDate date) {
    return date.isAfter(LocalDate.now(clock));
  }

  /**
   * 過去の日記一覧・検索。{@code q}が空/nullなら本文条件なし、{@code from}/{@code to}はどちらもnull可で、
   * 指定した側だけ日付範囲の絞り込みに使われる(両方指定で期間検索、片方だけで以降/以前の絞り込みになる)。
   */
  public Page<Diary> findPage(Long userId, String q, LocalDate from, LocalDate to, int page) {
    PageRequest pageRequest =
        PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "diaryOn"));
    Page<Diary> result =
        diaryRepository.findAll(searchSpecification(userId, q, from, to), pageRequest);
    log.debug(
        "Diary list for userId={} q={} from={} to={} page={} totalElements={}",
        userId,
        q,
        from,
        to,
        page,
        result.getTotalElements());
    return result;
  }

  /**
   * q/from/toの各条件は指定されたものだけを絞り込みに使う。JPQLの{@code :param IS NULL}判定でnullを直接
   * バインドすると、PostgreSQLがパラメータの型を推論できずエラーになるため、Criteria APIでnullな軸の
   * 条件そのものを組み立てから除外する(nullをSQLへ一切送らない)方式にしている。
   */
  private static Specification<Diary> searchSpecification(
      Long userId, String q, LocalDate from, LocalDate to) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("user").get("id"), userId));
      if (q != null && !q.isBlank()) {
        predicates.add(cb.like(cb.lower(root.get("body")), "%" + q.toLowerCase() + "%"));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("diaryOn"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("diaryOn"), to));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
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
