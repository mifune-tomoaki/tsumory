package com.example.tsumory.service;

/**
 * ユーザー入力をAIへのプロンプトに埋め込む前に無害化する。 つぶやき本文に区切りタグ(&lt;tsubuyaki&gt;や&lt;posts&gt;など)と同じ見た目の文字列が
 * 含まれていても、区切りを偽装してプロンプトインジェクションの足がかりにされないようにする。
 */
final class PromptSanitizer {

  private PromptSanitizer() {}

  static String sanitize(String text) {
    return text.replace("<", "＜").replace(">", "＞");
  }
}
