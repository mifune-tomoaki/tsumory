package com.example.tsumory.repository;

import com.example.tsumory.domain.Diary;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryRepository extends JpaRepository<Diary, Long> {

  Optional<Diary> findByUserIdAndDiaryOn(Long userId, LocalDate diaryOn);

  Page<Diary> findByUserIdOrderByDiaryOnDesc(Long userId, Pageable pageable);

  Page<Diary> findByUserIdAndBodyContainingIgnoreCaseOrderByDiaryOnDesc(
      Long userId, String q, Pageable pageable);
}
