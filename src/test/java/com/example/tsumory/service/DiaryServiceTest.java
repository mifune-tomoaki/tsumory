package com.example.tsumory.service;

import static com.example.tsumory.support.TestFixtures.DIARY_BODY_DRAFT;
import static com.example.tsumory.support.TestFixtures.DIARY_BODY_REGENERATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
  void findPage_usesPlainListingWhenQueryIsBlank() {
    Page<Diary> page = new PageImpl<>(List.of());
    when(diaryRepository.findByUserIdOrderByDiaryOnDesc(
            eq(USER_ID), eq(PageRequest.of(0, PAGE_SIZE))))
        .thenReturn(page);

    Page<Diary> result = diaryService.findPage(USER_ID, "  ", 0);

    assertThat(result).isSameAs(page);
    verify(diaryRepository, never())
        .findByUserIdAndBodyContainingIgnoreCaseOrderByDiaryOnDesc(any(), any(), any());
  }

  @Test
  void findPage_usesPlainListingWhenQueryIsNull() {
    Page<Diary> page = new PageImpl<>(List.of());
    when(diaryRepository.findByUserIdOrderByDiaryOnDesc(
            eq(USER_ID), eq(PageRequest.of(2, PAGE_SIZE))))
        .thenReturn(page);

    Page<Diary> result = diaryService.findPage(USER_ID, null, 2);

    assertThat(result).isSameAs(page);
  }

  @Test
  void findPage_searchesBodyWhenQueryIsGiven() {
    Page<Diary> page = new PageImpl<>(List.of());
    when(diaryRepository.findByUserIdAndBodyContainingIgnoreCaseOrderByDiaryOnDesc(
            eq(USER_ID), eq("花見"), eq(PageRequest.of(0, PAGE_SIZE))))
        .thenReturn(page);

    Page<Diary> result = diaryService.findPage(USER_ID, "花見", 0);

    assertThat(result).isSameAs(page);
    verify(diaryRepository, never()).findByUserIdOrderByDiaryOnDesc(any(), any());
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
