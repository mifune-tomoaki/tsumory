package com.example.tsumory.domain;

import lombok.Getter;

@Getter
public enum PostCategory {
  WORK("仕事", "bg-primary"),
  PRIVATE("プライベート", "bg-success"),
  LEARNING("学び", "bg-warning text-dark"),
  OTHER("その他", "bg-light text-dark");

  private final String label;
  private final String badgeClass;

  PostCategory(String label, String badgeClass) {
    this.label = label;
    this.badgeClass = badgeClass;
  }
}
