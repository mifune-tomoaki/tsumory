package com.example.tsumory.service;

import com.example.tsumory.domain.PostCategory;

public interface PostCategorizer {

  PostCategory categorize(String body);
}
