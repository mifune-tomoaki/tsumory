package com.example.tsumory.config;

import com.example.tsumory.security.TsumoryUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * リクエストごとの相関ID(requestId)と認証済みユーザーID(userId)をMDCに積み、
 * すべてのログ行から(呼び出し元のクラスを問わず)障害調査時にリクエスト・ユーザーを特定できるようにする。
 * SecurityContextHolderFilterの直後(認証情報の復元後、認可判定より前)に差し込む。
 */
public class RequestLoggingContextFilter extends OncePerRequestFilter {

  static final String REQUEST_ID_HEADER = "X-Request-Id";
  static final String REQUEST_ID_MDC_KEY = "requestId";
  static final String USER_ID_MDC_KEY = "userId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString();
    MDC.put(REQUEST_ID_MDC_KEY, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof TsumoryUserDetails userDetails) {
      MDC.put(USER_ID_MDC_KEY, String.valueOf(userDetails.getId()));
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.clear();
    }
  }
}
