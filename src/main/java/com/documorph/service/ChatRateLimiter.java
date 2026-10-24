package com.documorph.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.documorph.exception.TooManyRequestsException;

/**
 * Per-user limit on chat questions. Each answer takes a GPU-bound model call
 * and the pipeline runs one at a time, so a single user sending many
 * questions would stall document processing for everyone.
 *
 * Two rules: at most {@value #MAX_PER_WINDOW} questions per rolling minute,
 * and one in flight at a time. State is in memory, which is enough for the
 * single backend instance this project runs.
 */
@Component
public class ChatRateLimiter {

	static final int MAX_PER_WINDOW = 5;
	static final long WINDOW_MS = 60_000;

	private final Map<String, Deque<Long>> recent = new ConcurrentHashMap<>();
	private final Map<String, Boolean> inFlight = new ConcurrentHashMap<>();

	/** Reserve a slot, or throw. Every successful call must be paired with {@link #release}. */
	public void acquire(String userKey) {
		long now = System.currentTimeMillis();
		Deque<Long> times = recent.computeIfAbsent(userKey, k -> new ArrayDeque<>());
		synchronized (times) {
			while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MS) {
				times.pollFirst();
			}
			if (times.size() >= MAX_PER_WINDOW) {
				long retry = Math.max(1, (WINDOW_MS - (now - times.peekFirst()) + 999) / 1000);
				throw new TooManyRequestsException("You've asked " + MAX_PER_WINDOW
						+ " questions in the last minute. Please wait " + retry + " seconds and try again.", retry);
			}
			if (inFlight.putIfAbsent(userKey, Boolean.TRUE) != null) {
				throw new TooManyRequestsException("Please wait for your current question to finish.", 5);
			}
			times.addLast(now);
		}
	}

	public void release(String userKey) {
		inFlight.remove(userKey);
	}
}
