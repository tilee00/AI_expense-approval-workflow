package com.example.expense.auth;

import java.util.List;

public record TokenResponse(String token, long expiresIn, List<String> roles) {
}
