package com.example.tsumory.repository;

import com.example.tsumory.domain.Post;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

  List<Post> findByUser_IdAndPostedAtGreaterThanEqualAndPostedAtLessThanOrderByPostedAtAsc(
      Long userId, Instant startInclusive, Instant endExclusive);

  long countByUser_IdAndPostedAtGreaterThanEqualAndPostedAtLessThan(
      Long userId, Instant startInclusive, Instant endExclusive);

  Optional<Post> findByIdAndUserId(Long id, Long userId);
}
