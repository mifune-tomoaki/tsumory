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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DiaryController {

  private static final DateTimeFormatter GENERATED_AT_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final int PREVIEW_LENGTH = 80;

  private final DiaryService diaryService;
  private final PostService postService;
  private final DiaryWriter diaryWriter;
  private final Clock clock;

  @GetMapping("/diary/{date}")
  public String show(
      @PathVariable LocalDate date,
      @AuthenticationPrincipal TsumoryUserDetails principal,
      Model model) {
    if (date.isAfter(LocalDate.now(clock))) {
      throw new ResourceNotFoundException();
    }
    Long userId = principal.getId();
    Optional<Diary> diary = diaryService.findByDate(userId, date);
    if (diary.isEmpty() && postService.findPostsOn(userId, date).isEmpty()) {
      throw new ResourceNotFoundException();
    }
    model.addAttribute("diary", diary.map(this::toView).orElse(null));
    model.addAttribute("date", date);
    return "diary/show";
  }

  @GetMapping("/diaries")
  public String list(
      @RequestParam(name = "q", required = false) String q,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @AuthenticationPrincipal TsumoryUserDetails principal,
      Model model) {
    Page<Diary> diaries = diaryService.findPage(principal.getId(), q, page);
    model.addAttribute("diaries", diaries.map(this::toSummaryView).getContent());
    model.addAttribute("q", q);
    model.addAttribute("page", page);
    model.addAttribute("hasPrevious", diaries.hasPrevious());
    model.addAttribute("hasNext", diaries.hasNext());
    model.addAttribute("today", LocalDate.now(clock));
    return "diaries/index";
  }

  @PostMapping("/diaries/generate")
  public String generate(
      @RequestParam(name = "date", required = false) LocalDate date,
      @AuthenticationPrincipal TsumoryUserDetails principal,
      RedirectAttributes redirectAttributes) {
    Long userId = principal.getId();
    LocalDate today = LocalDate.now(clock);
    LocalDate targetDate = date != null ? date : today;
    boolean isToday = targetDate.equals(today);

    if (targetDate.isAfter(today)) {
      redirectAttributes.addFlashAttribute("errorMessage", "未来の日付の日記は作成できません");
      return "redirect:/diaries";
    }

    List<Post> posts = postService.findPostsOn(userId, targetDate);
    if (posts.isEmpty()) {
      redirectAttributes.addFlashAttribute(
          "errorMessage", isToday ? "今日はまだつぶやきがありません" : "その日のつぶやきがありません");
      return isToday ? "redirect:/posts" : "redirect:/diaries";
    }

    boolean diaryExisted = diaryService.findByDate(userId, targetDate).isPresent();
    try {
      String body = diaryWriter.write(posts);
      diaryService.upsert(userId, targetDate, body);
    } catch (Exception e) {
      log.warn("Diary generation failed for userId={}", userId, e);
      redirectAttributes.addFlashAttribute("errorMessage", "日記の生成に失敗しました。時間をおいて試してください");
      if (diaryExisted) {
        return "redirect:/diary/" + targetDate;
      }
      return isToday ? "redirect:/posts" : "redirect:/diaries";
    }
    return "redirect:/diary/" + targetDate;
  }

  private DiaryView toView(Diary diary) {
    String generatedAt =
        GENERATED_AT_FORMAT.withZone(clock.getZone()).format(diary.getGeneratedAt());
    return new DiaryView(diary.getBody(), generatedAt);
  }

  private DiarySummaryView toSummaryView(Diary diary) {
    String body = diary.getBody();
    String preview =
        body.length() > PREVIEW_LENGTH ? body.substring(0, PREVIEW_LENGTH) + "…" : body;
    return new DiarySummaryView(diary.getDiaryOn(), preview);
  }
}
