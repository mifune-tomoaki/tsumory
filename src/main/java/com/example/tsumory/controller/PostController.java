package com.example.tsumory.controller;

import com.example.tsumory.domain.Post;
import com.example.tsumory.form.PostForm;
import com.example.tsumory.security.TsumoryUserDetails;
import com.example.tsumory.service.DiaryService;
import com.example.tsumory.service.PostService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class PostController {

  private static final DateTimeFormatter POSTED_AT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

  private final PostService postService;
  private final DiaryService diaryService;
  private final Clock clock;

  @GetMapping("/")
  public String root() {
    return "redirect:/posts";
  }

  @GetMapping("/posts")
  public String index(
      @RequestParam(name = "edit", required = false) Long edit,
      @AuthenticationPrincipal TsumoryUserDetails principal,
      Model model) {
    populateHomeModel(principal.getId(), edit, model);
    return "posts/index";
  }

  @PostMapping("/posts")
  public String create(
      @Valid @ModelAttribute("postForm") PostForm postForm,
      BindingResult bindingResult,
      @AuthenticationPrincipal TsumoryUserDetails principal,
      Model model) {
    if (bindingResult.hasErrors()) {
      populateHomeModel(principal.getId(), null, model);
      return "posts/index";
    }
    postService.create(principal.getId(), postForm.body());
    return "redirect:/posts";
  }

  @PostMapping("/posts/{id}")
  public String edit(
      @PathVariable Long id,
      @Valid @ModelAttribute("editForm") PostForm editForm,
      BindingResult bindingResult,
      @AuthenticationPrincipal TsumoryUserDetails principal,
      Model model) {
    if (bindingResult.hasErrors()) {
      postService.findOwnedPost(principal.getId(), id);
      populateHomeModel(principal.getId(), id, model);
      return "posts/index";
    }
    postService.edit(principal.getId(), id, editForm.body());
    return "redirect:/posts";
  }

  @PostMapping("/posts/{id}/delete")
  public String delete(
      @PathVariable Long id, @AuthenticationPrincipal TsumoryUserDetails principal) {
    postService.delete(principal.getId(), id);
    return "redirect:/posts";
  }

  private void populateHomeModel(Long userId, Long editingPostId, Model model) {
    List<PostView> posts = postService.findTodayPosts(userId).stream().map(this::toView).toList();
    model.addAttribute("posts", posts);
    if (!model.containsAttribute("postForm")) {
      model.addAttribute("postForm", new PostForm(""));
    }
    if (editingPostId != null) {
      model.addAttribute("editingPostId", editingPostId);
      if (!model.containsAttribute("editForm")) {
        Post editingPost = postService.findOwnedPost(userId, editingPostId);
        model.addAttribute("editForm", new PostForm(editingPost.getBody()));
      }
    }
    LocalDate today = LocalDate.now(clock);
    model.addAttribute("today", today);
    model.addAttribute("todayDiaryExists", diaryService.findByDate(userId, today).isPresent());
  }

  private PostView toView(Post post) {
    String postedAt = POSTED_AT_FORMAT.withZone(clock.getZone()).format(post.getPostedAt());
    return new PostView(post.getId(), post.getBody(), postedAt);
  }
}
