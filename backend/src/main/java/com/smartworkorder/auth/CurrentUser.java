package com.smartworkorder.auth;

import com.smartworkorder.common.ApiException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    private final UserRepository users;
    public CurrentUser(UserRepository users) { this.users = users; }
    public User require(Jwt jwt) {
        try { return users.findById(Long.parseLong(jwt.getSubject()))
                .orElseThrow(() -> new ApiException(401, "账号不存在，请重新登录")); }
        catch (NumberFormatException e) { throw new ApiException(401, "登录信息无效"); }
    }
    public User admin(Jwt jwt) {
        User user = require(jwt);
        if (!user.isAdmin()) throw new ApiException(403, "仅管理员可以执行此操作");
        return user;
    }
}
