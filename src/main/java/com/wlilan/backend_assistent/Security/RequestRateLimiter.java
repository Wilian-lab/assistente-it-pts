package com.wlilan.backend_assistent.Security;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.exeptions.TooManyRequestsException;

@Service
public class RequestRateLimiter {

  private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> buckets = new ConcurrentHashMap<>();

  public void checkLimit(String scope, String subject, int maxAttempts, int windowSeconds, String message) {
    var safeScope = normalize(scope);
    var safeSubject = normalize(subject);
    var key = safeScope + "|" + safeSubject;
    var now = Instant.now().getEpochSecond();
    var cutoff = now - Math.max(windowSeconds, 1);
    var bucket = this.buckets.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());

    synchronized (bucket) {
      while (true) {
        var oldest = bucket.peekFirst();
        if (oldest == null || oldest >= cutoff) {
          break;
        }
        bucket.pollFirst();
      }

      if (bucket.size() >= Math.max(maxAttempts, 1)) {
        throw new TooManyRequestsException(message);
      }

      bucket.addLast(now);
    }
  }

  private String normalize(String value) {
    return Objects.toString(value, "").trim().toLowerCase();
  }
}
