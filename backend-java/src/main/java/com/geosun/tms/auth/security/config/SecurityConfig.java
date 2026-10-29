package com.geosun.tms.auth.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosun.tms.auth.config.AppEmailProperties;
import com.geosun.tms.auth.config.RateLimitProperties;
import com.geosun.tms.auth.repository.RefreshTokenRepository;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.auth.security.SecurityErrorWriter;
import com.geosun.tms.auth.security.jwt.JwtAuthenticationFilter;
import com.geosun.tms.auth.security.jwt.JwtService;
import com.geosun.tms.enterprise.repository.EnterpriseRepository;
import com.geosun.tms.enterprise.repository.PlatformOperatorRefreshTokenRepository;
import com.geosun.tms.enterprise.repository.PlatformOperatorRepository;
import com.geosun.tms.routes.config.CountryBreakdownProperties;
import com.geosun.tms.routes.config.HereProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT, публічні auth-маршрути; GET /admin/users — ADMIN/MANAGER; мутації users і інші admin — ADMIN.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({
  JwtProperties.class,
  SuperAdminProperties.class,
  AppEmailProperties.class,
  RateLimitProperties.class,
  HereProperties.class,
  CountryBreakdownProperties.class,
  CorsProperties.class
})
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper)
      throws Exception {
    http.csrf(csrf -> csrf.disable());
    http.cors(Customizer.withDefaults());
    http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    http.authorizeHttpRequests(
        auth ->
            auth.requestMatchers(
                    HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info")
                .permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs",
                    "/v3/api-docs/**")
                .permitAll()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/verify-email",
                    "/api/v1/auth/resend-verification",
                    "/api/v1/auth/forgot-password",
                    "/api/v1/auth/reset-password-info",
                    "/api/v1/auth/reset-password",
                    "/api/v1/auth/refresh",
                    "/api/v1/platform/auth/login",
                    "/api/v1/platform/auth/refresh",
                    "/api/v1/webhooks/telegram")
                .permitAll()
                // Оператор платформи; співробітник отримує 403 PLATFORM_SCOPE_REQUIRED у фільтрі
                // JWT
                .requestMatchers("/api/v1/platform/**")
                .hasRole("PLATFORM_OPERATOR")
                // Список і картка користувачів — ADMIN і MANAGER (лише читання контактів)
                .requestMatchers(HttpMethod.GET, "/api/v1/admin/users", "/api/v1/admin/users/*")
                .hasAnyRole("ADMIN", "MANAGER")
                // Мутації обліковок і PUT профілю — лише ADMIN
                .requestMatchers("/api/v1/admin/users", "/api/v1/admin/users/**")
                .hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/stored-files", "/api/v1/admin/stored-files/**")
                .hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/super-admin", "/api/v1/admin/super-admin/**")
                .hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/document-types", "/api/v1/admin/document-types/**")
                .hasRole("ADMIN")
                .requestMatchers(
                    HttpMethod.GET, "/api/v1/admin/chatbots", "/api/v1/admin/chatbots/**")
                .hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/users/*")
                .hasRole("ADMIN")
                .anyRequest()
                .authenticated());

    http.exceptionHandling(
        ex ->
            ex.authenticationEntryPoint(
                    (request, response, e) ->
                        SecurityErrorWriter.writeJson(
                            response,
                            objectMapper,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "Unauthorized",
                            "UNAUTHORIZED",
                            "Authentication required",
                            request.getRequestURI()))
                .accessDeniedHandler(
                    (request, response, e) ->
                        SecurityErrorWriter.writeJson(
                            response,
                            objectMapper,
                            HttpServletResponse.SC_FORBIDDEN,
                            "Forbidden",
                            "FORBIDDEN",
                            "Access denied",
                            request.getRequestURI())));

    http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public JwtAuthenticationFilter jwtAuthenticationFilter(
      JwtService jwtService,
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      EnterpriseRepository enterpriseRepository,
      PlatformOperatorRepository platformOperatorRepository,
      PlatformOperatorRefreshTokenRepository platformRefreshTokenRepository,
      ObjectMapper objectMapper) {
    return new JwtAuthenticationFilter(
        jwtService,
        userRepository,
        refreshTokenRepository,
        enterpriseRepository,
        platformOperatorRepository,
        platformRefreshTokenRepository,
        objectMapper);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(corsProperties.resolveAllowedOriginPatterns());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
