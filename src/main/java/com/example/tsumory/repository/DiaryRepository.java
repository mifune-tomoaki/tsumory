package com.example.tsumory.repository;

import com.example.tsumory.domain.Diary;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryRepository extends JpaRepository<Diary, Long> {

  Optional<Diary> findByUserIdAndDiaryOn(Long userId, LocalDate diaryOn);
}
