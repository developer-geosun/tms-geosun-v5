package com.geosun.tms.auth.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Маршрути без access JWT (публічні) згідно ТЗ та actuator/swagger.
 */
public final class SecurityPathRules {

  private SecurityPathRules() {}

  public static boolean isPublic(HttpServletRequest request) {
    String path = normalizePath(request);
    String method = request.getMethod();
    if ("GET".equalsIgnoreCase(method)) {
      if ("/actuator/health".equals(path)
          || path.startsWith("/actuator/health/")
          || "/actuator/info".equals(path)) {
        return true;
      }
      if ("/swagger-ui.html".equals(path)
          || path.startsWith("/swagger-ui/")
          || path.startsWith("/v3/api-docs")) {
        return true;
      }
    }
    if ("POST".equalsIgnoreCase(method)) {
      return switch (path) {
        case "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password-info",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/refresh",
            "/api/v1/platform/auth/login",
            "/api/v1/platform/auth/refresh",
            "/api/v1/webhooks/telegram" ->
            true;
        default -> false;
      };
    }
    return false;
  }

  /** Маршрути оператора платформи: доступні лише з токеном kind=operator. */
  public static boolean isPlatformPath(HttpServletRequest request) {
    String path = normalizePath(request);
    return "/api/v1/platform".equals(path) || path.startsWith("/api/v1/platform/");
  }

  private static String normalizePath(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String context = request.getContextPath();
    if (context != null && !context.isEmpty() && uri.startsWith(context)) {
      uri = uri.substring(context.length());
    }
    if (uri.isEmpty()) {
      return "/";
    }
    return uri;
  }
}
