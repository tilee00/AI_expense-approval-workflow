package com.example.expense.user;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> listAll() {
        return users.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }
}
