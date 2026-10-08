package com.bracits.transactionservice.api.security;

import com.bracits.transactionservice.api.constant.ApiConstants;
import com.bracits.transactionservice.api.constant.ApiMessages;
import com.bracits.transactionservice.api.enums.ApiErrorCode;
import com.bracits.transactionservice.api.mapper.ProblemMapper;
import com.bracits.transactionservice.config.properties.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Static API key on the public API (NFR-09): every {@code /api/**} request must carry
 * {@code X-API-Key}. Actuator, {@code /openapi.yaml} and Swagger UI are not filtered. Constant-time
 * comparison.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ApiKeyFilter extends OncePerRequestFilter {

  private final byte[] expectedKey;
  private final ProblemMapper problems;
  private final JsonMapper jsonMapper;

  public ApiKeyFilter(SecurityProperties properties, ProblemMapper problems,
      JsonMapper jsonMapper) {
    this.expectedKey = properties.apiKey().getBytes(StandardCharsets.UTF_8);
    this.problems = problems;
    this.jsonMapper = jsonMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return !path.startsWith(ApiConstants.API_PREFIX);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain)
      throws ServletException, IOException {
    String presented = request.getHeader(ApiConstants.HEADER_API_KEY);
    if (presented != null && MessageDigest.isEqual(expectedKey,
        presented.getBytes(StandardCharsets.UTF_8))) {
      chain.doFilter(request, response);
      return;
    }

    response.setStatus(ApiErrorCode.UNAUTHORIZED.httpStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    jsonMapper.writeValue(response.getOutputStream(),
        problems.toMap(problems.toProblem(ApiErrorCode.UNAUTHORIZED, ApiMessages.UNAUTHORIZED)));
  }
}
