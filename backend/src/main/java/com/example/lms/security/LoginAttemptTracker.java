package com.example.lms.security;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptTracker {

  public static final int MAX_ATTEMPTS = 5;
  public static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000;

  private final ConcurrentHashMap<String, AttemptInfo> attempts = new ConcurrentHashMap<>();

  public boolean isLocked(String username) {
    var info = attempts.get(username);
    if (info == null) return false;
    if (System.currentTimeMillis() - info.lastAttemptTime > LOCKOUT_DURATION_MS) {
      attempts.remove(username);
      return false;
    }
    return info.count >= MAX_ATTEMPTS;
  }

  public void recordFailure(String username) {
    attempts.compute(
        username,
        (key, existing) -> {
          if (existing == null
              || System.currentTimeMillis() - existing.lastAttemptTime > LOCKOUT_DURATION_MS) {
            var info = new AttemptInfo();
            info.count = 1;
            info.lastAttemptTime = System.currentTimeMillis();
            return info;
          }
          existing.count++;
          existing.lastAttemptTime = System.currentTimeMillis();
          return existing;
        });
  }

  public void recordSuccess(String username) {
    attempts.remove(username);
  }

  public int getRemainingAttempts(String username) {
    var info = attempts.get(username);
    if (info == null || System.currentTimeMillis() - info.lastAttemptTime > LOCKOUT_DURATION_MS) {
      return MAX_ATTEMPTS;
    }
    return Math.max(0, MAX_ATTEMPTS - info.count);
  }

  public static class AttemptInfo {
    public int count;
    public long lastAttemptTime;
  }
}
