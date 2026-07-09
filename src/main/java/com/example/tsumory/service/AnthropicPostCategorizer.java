package com.example.tsumory.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolChoiceTool;
import com.anthropic.models.messages.ToolUseBlock;
import com.example.tsumory.domain.PostCategory;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AnthropicPostCategorizer implements PostCategorizer {

  private static final String TOOL_NAME = "categorize_post";

  private final AnthropicClient client;
  private final String model;
  private final long maxTokens;
  private final Tool categorizeTool;

  public AnthropicPostCategorizer(
      AnthropicClient client,
      @Value("${tsumory.ai.categorize-model:claude-haiku-4-5}") String model,
      @Value("${tsumory.ai.categorize-max-tokens:64}") long maxTokens) {
    this.client = client;
    this.model = model;
    this.maxTokens = maxTokens;
    this.categorizeTool = buildCategorizeTool();
  }

  @Override
  public PostCategory categorize(String body) {
    MessageCreateParams params =
        MessageCreateParams.builder()
            .model(model)
            .maxTokens(maxTokens)
            .addUserMessage("以下のつぶやきに最も当てはまるカテゴリを1つ選んでください。\n\n%s".formatted(body))
            .addTool(categorizeTool)
            .toolChoice(ToolChoiceTool.builder().name(TOOL_NAME).build())
            .build();

    log.info("Anthropic categorize call starting model={}", model);
    long startNanos = System.nanoTime();
    Message response;
    try {
      response = client.messages().create(params);
    } catch (AnthropicException e) {
      long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
      log.warn(
          "Anthropic categorize call failed model={} elapsedMillis={} error={}",
          model,
          elapsedMillis,
          e.toString());
      throw e;
    }
    long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

    ToolUseBlock toolUse =
        response.content().stream()
            .flatMap(block -> block.toolUse().stream())
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Anthropic categorize response did not contain a tool_use block"));
    PostCategory category =
        PostCategory.valueOf(toolUse._input().convert(CategorizeInput.class).category());

    log.info(
        "Anthropic categorize call succeeded model={} elapsedMillis={} category={} inputTokens={}"
            + " outputTokens={} requestId={}",
        model,
        elapsedMillis,
        category,
        response.usage().inputTokens(),
        response.usage().outputTokens(),
        response.id());

    return category;
  }

  private static Tool buildCategorizeTool() {
    Tool.InputSchema inputSchema =
        Tool.InputSchema.builder()
            .properties(
                Tool.InputSchema.Properties.builder()
                    .putAdditionalProperty(
                        "category",
                        JsonValue.from(
                            Map.of(
                                "type",
                                "string",
                                "enum",
                                Arrays.stream(PostCategory.values()).map(Enum::name).toList(),
                                "description",
                                "つぶやきに最も当てはまるカテゴリ。判定に迷う場合はOTHERを選ぶこと。")))
                    .build())
            .required(List.of("category"))
            .build();

    return Tool.builder()
        .name(TOOL_NAME)
        .description("つぶやきの本文を読み、あらかじめ定義されたカテゴリのいずれか1つに分類する。")
        .inputSchema(inputSchema)
        .build();
  }

  private record CategorizeInput(String category) {}
}
