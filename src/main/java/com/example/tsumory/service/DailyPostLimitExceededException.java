package com.example.tsumory.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 1日あたりの投稿上限に達している状態で投稿しようとした場合にスローする。 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class DailyPostLimitExceededException extends RuntimeException {

  public DailyPostLimitExceededException(int limit) {
    super("1日の投稿上限(%d件)に達しました".formatted(limit));
  }
}
