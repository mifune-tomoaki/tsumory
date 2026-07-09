package com.example.tsumory.service;

import com.example.tsumory.domain.Post;
import java.util.List;

public interface DiaryWriter {

  String write(List<Post> posts);
}
