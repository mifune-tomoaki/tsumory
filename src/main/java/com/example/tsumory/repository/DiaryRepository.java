package com.example.tsumory.repository;

import com.example.tsumory.domain.Diary;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DiaryRepository
    extends JpaRepository<Diary, Long>, JpaSpecificationExecutor<Diary> {

  Optional<Diary> findByUserIdAndDiaryOn(Long userId, LocalDate diaryOn);
}
