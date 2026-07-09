package com.example.tsumory.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostCategory;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AnthropicDiaryWriter implements DiaryWriter {

  // docs/mymanifesto.md の「AIのトーン」をそのまま反映する
  private static final String SYSTEM_PROMPT =
      """
      書き手ではなく、そばで話を聞いていた人のように書く。断定的に決めつけず、その日のつぶやきに寄り添いながら、\
      本人の言葉や温度感を尊重して物語に仕上げる。上から評価したり説教したりしない。押しつけがましくなく、\
      何度でも作り直せる前提だからこそ、気軽に肩の力を抜いたトーンでよい。

      出力は日記本文のみのプレーンテキストで返すこと。見出しや箇条書きにはせず、一人称の自然な文章で書くこと。""";

  private static final DateTimeFormatter POSTED_AT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

  private final AnthropicClient client;
  private final Clock clock;
  private final String model;
  private final long maxTokens;

  public AnthropicDiaryWriter(
      AnthropicClient client,
      Clock clock,
      @Value("${tsumory.ai.model:claude-haiku-4-5}") String model,
      @Value("${tsumory.ai.max-tokens:2048}") long maxTokens) {
    this.client = client;
    this.clock = clock;
    this.model = model;
    this.maxTokens = maxTokens;
  }

  @Override
  public String write(List<Post> posts) {
    MessageCreateParams params =
        MessageCreateParams.builder()
            .model(model)
            .maxTokens(maxTokens)
            .system(SYSTEM_PROMPT)
            .addUserMessage(buildUserMessage(posts))
            .build();

    log.info("Anthropic API call starting model={} postCount={}", model, posts.size());
    long startNanos = System.nanoTime();
    Message response;
    try {
      response = client.messages().create(params);
    } catch (AnthropicException e) {
      long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
      log.warn(
          "Anthropic API call failed model={} elapsedMillis={} error={}",
          model,
          elapsedMillis,
          e.toString());
      throw e;
    }
    long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
    log.info(
        "Anthropic API call succeeded model={} elapsedMillis={} inputTokens={} outputTokens={}"
            + " requestId={}",
        model,
        elapsedMillis,
        response.usage().inputTokens(),
        response.usage().outputTokens(),
        response.id());

    return response.content().stream()
        .flatMap(block -> block.text().stream())
        .map(text -> text.text())
        .collect(Collectors.joining());
  }

  private String buildUserMessage(List<Post> posts) {
    String timeline = posts.stream().map(this::formatPostLine).collect(Collectors.joining("\n"));
    return """
        以下は今日投稿されたつぶやきです。投稿時刻とともに時系列に並んでいます。
        角括弧はAIによる分類カテゴリの参考情報であり、つぶやき本文そのものではありません。\
        カテゴリ名を日記本文にそのまま書き写したり、カテゴリごとに整理して構成したりせず、\
        あくまで文脈を読み取るための補助情報として扱ってください(未分類のつぶやきにはカテゴリを付けていません)。
        これらのつぶやきを1本の日記としてまとめてください。

        %s"""
        .formatted(timeline);
  }

  private String formatPostLine(Post post) {
    String postedAt = POSTED_AT_FORMAT.withZone(clock.getZone()).format(post.getPostedAt());
    PostCategory category = post.getCategory();
    return category == null
        ? "%s %s".formatted(postedAt, post.getBody())
        : "%s [%s] %s".formatted(postedAt, category.getLabel(), post.getBody());
  }
}
