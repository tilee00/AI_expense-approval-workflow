package com.example.expense.common;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;

public final class JsonErrors {

    private JsonErrors() {
    }

    public static void write(HttpServletResponse response, int status, String error,
                             String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"timestamp\":\"" + Instant.now()
                + "\",\"status\":" + status
                + ",\"error\":\"" + error
                + "\",\"message\":\"" + message
                + "\",\"path\":\"" + path.replace("\"", "'") + "\"}");
    }
}
