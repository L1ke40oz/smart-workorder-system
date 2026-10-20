package com.smartworkorder.auth;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 32) private String username;
    @Column(nullable = false, length = 40) private String displayName;
    @Column(nullable = false, length = 100) private String passwordHash;
    @Column(nullable = false, length = 16) private String role;
    @Column(nullable = false) private Instant createdAt;
    protected User() {}
    public User(String username, String displayName, String passwordHash, String role) {
        this.username = username; this.displayName = displayName; this.passwordHash = passwordHash;
        this.role = role; this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public boolean isAdmin() { return "ADMIN".equals(role); }
}
