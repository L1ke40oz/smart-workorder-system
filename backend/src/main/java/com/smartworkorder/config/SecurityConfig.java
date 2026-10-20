package com.smartworkorder.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecretKeySpec signingKey(@Value("${app.security.secret}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET 必须至少包含 32 个字节，请通过环境变量配置");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKeySpec key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder jwtDecoder(SecretKeySpec key) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("smart-workorder"));
        return decoder;
    }
    @Bean SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper) throws Exception {
        return http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/api/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()).authenticationEntryPoint((req,res,e) -> {
                    res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
                    mapper.writeValue(res.getOutputStream(), Map.of("message", "登录已失效，请重新登录"));
                }))
                .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> {
                    res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
                    mapper.writeValue(res.getOutputStream(), Map.of("message", "请先登录"));
                }).accessDeniedHandler((req,res,ex) -> {
                    res.setStatus(403); res.setContentType("application/json;charset=UTF-8");
                    mapper.writeValue(res.getOutputStream(), Map.of("message", "没有操作权限"));
                })).build();
    }
}
