package com.example.tsumory.service;

import static com.example.tsumory.support.TestFixtures.DIARY_BODY_DRAFT;
import static com.example.tsumory.support.TestFixtures.DIARY_BODY_REGENERATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tsumory.domain.Diary;
import com.example.tsumory.domain.User;
import com.example.tsumory.repository.DiaryRepository;
import com.example.tsumory.repository.UserRepository;
import com.example.tsumory.support.TestFixtures;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

  private static final Long USER_ID = 1L;
  private static final int PAGE_SIZE = 20;

  @Mock private DiaryRepository diaryRepository;
  @Mock private UserRepository userRepository;

  private Clock clock;
  private DiaryService diaryService;

  @BeforeEach
  void setUp() {
    clock = TestFixtures.fixedClock();
    diaryService = new DiaryService(diaryRepository, userRepository, clock);
  }

  @Test
  void findByDate_delegatesToRepository() {
    LocalDate date = LocalDate.of(2026, 7, 10);
    Diary diary = new Diary(TestFixtures.user(), date, DIARY_BODY_DRAFT, clock.instant());
    when(diaryRepository.findByUserIdAndDiaryOn(USER_ID, date)).thenReturn(Optional.of(diary));

    Optional<Diary> result = diaryService.findByDate(USER_ID, date);

    assertThat(result).contains(diary);
  }

  @Test
  void findByDate_returnsEmptyWhenNotFound() {
    LocalDate date = LocalDate.of(2026, 7, 10);
    when(diaryRepository.findByUserIdAndDiaryOn(USER_ID, date)).thenReturn(Optional.empty());

    Optional<Diary> result = diaryService.findByDate(USER_ID, date);

    assertThat(result).isEmpty();
  }

  @Test
  void isFutureDate_returnsTrueForDateAfterToday() {
    LocalDate tomorrow = LocalDate.now(clock).plusDays(1);

    assertThat(diaryService.isFutureDate(tomorrow)).isTrue();
  }

  @Test
  void isFutureDate_returnsFalseForToday() {
    LocalDate today = LocalDate.now(clock);

    assertThat(diaryService.isFutureDate(today)).isFalse();
  }

  @Test
  void isFutureDate_returnsFalseForPastDate() {
    LocalDate yesterday = LocalDate.now(clock).minusDays(1);

    assertThat(diaryService.isFutureDate(yesterday)).isFalse();
  }

  // Specificationのラムダ内容そのものはMockitoでは検証できないため、ここではpage/size/sortの委譲のみを
  // 確認する。q/from/toの絞り込み条件がnullの軸をSQLへ一切送らないことは、実際に起動したアプリに対する
  // 手動検証(PostgreSQLはnullバインドパラメータの型を推論できずエラーになるため、
  // JPQLの`:param IS NULL`分岐ではなくCriteria APIで条件自体を組み立てから除外する必要があった)で確認済み。
  @Test
  void findPage_delegatesToRepositoryWithPageSizeAndDescendingDiaryOnSort() {
    Page<Diary> page = new PageImpl<>(List.of());
    when(diaryRepository.findAll(ArgumentMatchers.<Specification<Diary>>any(), any(Pageable.class)))
        .thenReturn(page);

    Page<Diary> result =
        diaryService.findPage(
            USER_ID, "花見", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10), 2);

    assertThat(result).isSameAs(page);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(diaryRepository)
        .findAll(ArgumentMatchers.<Specification<Diary>>any(), pageableCaptor.capture());
    Pageable pageable = pageableCaptor.getValue();
    assertThat(pageable.getPageNumber()).isEqualTo(2);
    assertThat(pageable.getPageSize()).isEqualTo(PAGE_SIZE);
    assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "diaryOn"));
  }

  @Test
  void upsert_regeneratesExistingDiaryWithoutCreatingNewOne() {
    LocalDate date = LocalDate.of(2026, 7, 10);
    Diary existing =
        new Diary(
            TestFixtures.user(),
            date,
            DIARY_BODY_DRAFT,
            TestFixtures.NOW.minus(Duration.ofDays(1)));
    when(diaryRepository.findByUserIdAndDiaryOn(USER_ID, date)).thenReturn(Optional.of(existing));

    Diary result = diaryService.upsert(USER_ID, date, DIARY_BODY_REGENERATED);

    assertThat(result).isSameAs(existing);
    assertThat(result.getBody()).isEqualTo(DIARY_BODY_REGENERATED);
    assertThat(result.getGeneratedAt()).isEqualTo(clock.instant());
    verify(diaryRepository, never()).save(any());
    verify(userRepository, never()).getReferenceById(any());
  }

  @Test
  void upsert_createsNewDiaryWhenNoneExistsForDate() {
    LocalDate date = LocalDate.of(2026, 7, 10);
    when(diaryRepository.findByUserIdAndDiaryOn(USER_ID, date)).thenReturn(Optional.empty());
    User user = TestFixtures.user();
    when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
    when(diaryRepository.save(any(Diary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Diary result = diaryService.upsert(USER_ID, date, DIARY_BODY_DRAFT);

    ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
    verify(diaryRepository).save(captor.capture());
    Diary saved = captor.getValue();
    assertThat(saved.getUser()).isEqualTo(user);
    assertThat(saved.getDiaryOn()).isEqualTo(date);
    assertThat(saved.getBody()).isEqualTo(DIARY_BODY_DRAFT);
    assertThat(saved.getGeneratedAt()).isEqualTo(clock.instant());
    assertThat(result).isSameAs(saved);
  }
}
