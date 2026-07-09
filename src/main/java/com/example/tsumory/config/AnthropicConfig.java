package com.example.tsumory.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnthropicConfig {

  // AnthropicOkHttpClient.fromEnv()はSystem.getenv()を直接参照するため、
  // Springがspring.config.importで.envから読み込んだプロパティを拾えない。
  // そのためAPIキーはSpringの設定値として明示的に渡す。
  @Bean
  public AnthropicClient anthropicClient(@Value("${ANTHROPIC_API_KEY}") String apiKey) {
    return AnthropicOkHttpClient.builder().apiKey(apiKey).build();
  }
}
