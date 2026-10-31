package com.example.expense.user;

import java.util.Set;

public record UserResponse(Long id, String username, String displayName, Set<Role> roles) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRoles());
    }
}
