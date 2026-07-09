package com.example.tsumory.controller;

import com.example.tsumory.domain.PostCategory;

public record PostView(Long id, String body, String postedAt, PostCategory category) {}
