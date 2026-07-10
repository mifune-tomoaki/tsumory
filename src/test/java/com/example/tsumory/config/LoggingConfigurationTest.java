package com.example.tsumory.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * application.yamlにSQLバインド値のトレースログ設定が紛れ込むと、
 * PostService/DiaryServiceが意図的にマスクしている本文がHibernate経由でログに出てしまう (docs/code-review-non-service.md
 * 指摘1)。再発を防ぐための設定回帰テスト。
 */
class LoggingConfigurationTest {

  private static final YamlPropertySourceLoader LOADER = new YamlPropertySourceLoader();

  @Test
  void baseConfigDoesNotEnableHibernateBindValueTraceLogging() throws IOException {
    Properties properties = loadYamlProperties("application.yaml");

    assertThat(properties.getProperty("logging.level.org.hibernate.orm.jdbc.bind")).isNull();
    assertThat(properties.getProperty("logging.level.org.hibernate.SQL")).isNull();
  }

  @Test
  void hibernateBindValueTraceLoggingIsScopedToDevProfile() throws IOException {
    Properties properties = loadYamlProperties("application-dev.yaml");

    assertThat(properties.getProperty("spring.config.activate.on-profile")).isEqualTo("dev");
    assertThat(properties.getProperty("logging.level.org.hibernate.orm.jdbc.bind"))
        .isEqualTo("trace");
    assertThat(properties.getProperty("logging.level.org.hibernate.SQL")).isEqualTo("debug");
  }

  private Properties loadYamlProperties(String fileName) throws IOException {
    Properties properties = new Properties();
    for (PropertySource<?> source : LOADER.load(fileName, new ClassPathResource(fileName))) {
      if (source.getSource() instanceof Map<?, ?> map) {
        map.forEach(
            (key, value) -> properties.setProperty(String.valueOf(key), String.valueOf(value)));
      }
    }
    return properties;
  }
}
