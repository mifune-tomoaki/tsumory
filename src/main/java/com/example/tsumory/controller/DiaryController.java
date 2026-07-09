package com.example.tsumory.controller;

import com.example.tsumory.domain.Diary;
import com.example.tsumory.domain.Post;
import com.example.tsumory.security.TsumoryUserDetails;
import com.example.tsumory.service.DiaryService;
import com.example.tsumory.service.DiaryWriter;
import com.example.tsumory.service.PostService;
import com.example.tsumory.service.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DiaryController {

  private static final DateTimeFormatter GENERATED_AT_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final DiaryService diaryService;
  private final PostService postService;
  private final DiaryWriter diaryWriter;
  private final Clock clock;

  @GetMapping("/diary/{date}")
  public String show(
      @PathVariable LocalDate date,
      @AuthenticationPrincipal TsumoryUserDetails principal,
      Model model) {
    if (!date.equals(LocalDate.now(clock))) {
      throw new ResourceNotFoundException();
    }
    Diary diary =
        diaryService
            .findByDate(principal.getId(), date)
            .orElseThrow(ResourceNotFoundException::new);
    model.addAttribute("diary", toView(diary));
    return "diary/show";
  }

  @PostMapping("/diaries/generate")
  public String generate(
      @AuthenticationPrincipal TsumoryUserDetails principal,
      RedirectAttributes redirectAttributes) {
    Long userId = principal.getId();
    LocalDate today = LocalDate.now(clock);
    List<Post> posts = postService.findTodayPosts(userId);
    if (posts.isEmpty()) {
      redirectAttributes.addFlashAttribute("errorMessage", "今日はまだつぶやきがありません");
      return "redirect:/posts";
    }
    boolean diaryExisted = diaryService.findByDate(userId, today).isPresent();
    try {
      String body = diaryWriter.write(posts);
      diaryService.upsertToday(userId, body);
    } catch (Exception e) {
      log.warn("Diary generation failed for userId={}", userId, e);
      redirectAttributes.addFlashAttribute("errorMessage", "日記の生成に失敗しました。時間をおいて試してください");
      return diaryExisted ? "redirect:/diary/" + today : "redirect:/posts";
    }
    return "redirect:/diary/" + today;
  }

  private DiaryView toView(Diary diary) {
    String generatedAt =
        GENERATED_AT_FORMAT.withZone(clock.getZone()).format(diary.getGeneratedAt());
    return new DiaryView(diary.getBody(), generatedAt);
  }
}
