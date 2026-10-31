package com.example.expense.auth;

import java.util.List;

public record MeResponse(String username, List<String> roles) {
}
