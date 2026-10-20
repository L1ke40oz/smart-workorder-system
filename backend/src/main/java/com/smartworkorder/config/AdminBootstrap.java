package com.smartworkorder.config;

import com.smartworkorder.auth.User;
import com.smartworkorder.auth.UserRepository;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements CommandLineRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String username, password;
    public AdminBootstrap(UserRepository users, PasswordEncoder passwords,
            @Value("${app.bootstrap.admin-username}") String username,
            @Value("${app.bootstrap.admin-password}") String password) {
        this.users = users; this.passwords = passwords; this.username = username; this.password = password;
    }
    @Override public void run(String... args) {
        if (users.existsByRole("ADMIN")) return;
        if (!username.matches("[A-Za-z0-9_]{4,32}") || password.length() < 8 || password.length() > 64
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("首次启动请设置 ADMIN_USERNAME（4～32 位字母数字下划线）和 ADMIN_PASSWORD（8～64 位）");
        }
        if (users.findByUsername(username.toLowerCase(Locale.ROOT)).isPresent()) {
            throw new IllegalStateException("初始化管理员账号已被普通用户占用，请修改 ADMIN_USERNAME");
        }
        users.save(new User(username.toLowerCase(Locale.ROOT), "系统管理员", passwords.encode(password), "ADMIN"));
    }
}
