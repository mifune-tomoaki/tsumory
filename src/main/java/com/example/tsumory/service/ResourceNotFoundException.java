package com.example.tsumory.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 存在しないリソース、または本人以外が所有するリソースが指定された場合にスローする。404として扱う。 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {}
