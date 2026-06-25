package works.momens.server.auth.internal.jwt;

public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {}
