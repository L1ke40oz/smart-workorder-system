package com.smartworkorder.auth;

import com.smartworkorder.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    public record Registration(
            @NotNull @Pattern(regexp = "[A-Za-z0-9_]{4,32}", message = "账号需为 4～32 位字母、数字或下划线") String username,
            @NotBlank @Size(max = 40) String displayName,
            @NotBlank @Size(min = 8, max = 64) String password) {}
    public record Login(@NotBlank @Size(max=32) String username, @NotBlank @Size(max=64) String password) {}
    public record Profile(Long id, String username, String displayName, String role) {
        public static Profile of(User u) { return new Profile(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole()); }
    }
    public record Session(String token, Instant expiresAt, Profile user) {}
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder tokens;
    private final CurrentUser current;
    private final String dummyHash;
    public AuthController(UserRepository users, PasswordEncoder passwords, JwtEncoder tokens, CurrentUser current) {
        this.users = users; this.passwords = passwords; this.tokens = tokens; this.current = current;
        this.dummyHash = passwords.encode("unused-password-for-timing");
    }
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
    public Session register(@Valid @RequestBody Registration input) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) throw new ApiException(400, "密码的 UTF-8 编码长度不能超过 72 字节");
        String username = input.username().toLowerCase(Locale.ROOT);
        if (users.findByUsername(username).isPresent()) throw new ApiException(409, "账号已存在");
        return session(users.save(new User(username, input.displayName().trim(), passwords.encode(input.password()), "USER")));
    }
    @PostMapping("/auth/login")
    public Session login(@Valid @RequestBody Login input) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) throw new ApiException(401, "账号或密码不正确");
        User user = users.findByUsername(input.username().trim().toLowerCase(Locale.ROOT)).orElse(null);
        boolean matches = passwords.matches(input.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches) throw new ApiException(401, "账号或密码不正确");
        return session(user);
    }
    @GetMapping("/auth/me")
    public Profile me(@AuthenticationPrincipal Jwt jwt) { return Profile.of(current.require(jwt)); }
    @GetMapping("/health") public Map<String,String> health() { return Map.of("status", "UP"); }
    private Session session(User user) {
        Instant now = Instant.now(); Instant expires = now.plus(12, ChronoUnit.HOURS);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("smart-workorder").subject(user.getId().toString())
                .issuedAt(now).expiresAt(expires).build();
        String token = tokens.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Session(token, expires, Profile.of(user));
    }
}
