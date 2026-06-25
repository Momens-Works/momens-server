package works.momens.server.auth.internal;

public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {}
