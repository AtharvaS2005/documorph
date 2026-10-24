package com.documorph.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.documorph.exception.TooManyRequestsException;

/**
 * Brute-force protection for sign-in and sign-up.
 *
 * The main rule is per account: after {@value #MAX_FAILURES} failed sign-ins
 * within 15 minutes, that email can't sign in until the oldest failure ages
 * out, whichever address the attempts come from. A looser per-address cap
 * limits how fast one client can spray guesses across many accounts or
 * create accounts; behind Docker's port forwarding every client may share
 * one address, so it is deliberately generous.
 *
 * State is in memory, which suits the single backend instance this project
 * runs; it resets on restart.
 */
@Component
public class AuthRateLimiter {

	static final int MAX_FAILURES = 5;
	static final long FAILURE_WINDOW_MS = 15 * 60_000;
	static final int MAX_LOGINS_PER_ADDRESS = 30;
	static final int MAX_SIGNUPS_PER_ADDRESS = 10;
	static final long ADDRESS_WINDOW_MS = 60_000;
	static final long SIGNUP_WINDOW_MS = 60 * 60_000;

	private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();
	private final Map<String, Deque<Long>> loginsByAddress = new ConcurrentHashMap<>();
	private final Map<String, Deque<Long>> signupsByAddress = new ConcurrentHashMap<>();

	/** Call before checking the password. Throws if this sign-in must not be attempted. */
	public void checkLogin(String email, String address) {
		long now = System.currentTimeMillis();
		Deque<Long> f = failures.computeIfAbsent(key(email), k -> new ArrayDeque<>());
		synchronized (f) {
			prune(f, now, FAILURE_WINDOW_MS);
			if (f.size() >= MAX_FAILURES) {
				long wait = retryAfter(f, now, FAILURE_WINDOW_MS);
				throw new TooManyRequestsException("Too many failed sign-in attempts for this account. Try again in "
						+ minutes(wait) + ".", wait);
			}
		}
		hit(loginsByAddress, address, now, ADDRESS_WINDOW_MS, MAX_LOGINS_PER_ADDRESS,
				"Too many sign-in attempts. Please wait a minute and try again.");
	}

	public void recordFailure(String email) {
		long now = System.currentTimeMillis();
		Deque<Long> f = failures.computeIfAbsent(key(email), k -> new ArrayDeque<>());
		synchronized (f) {
			prune(f, now, FAILURE_WINDOW_MS);
			f.addLast(now);
		}
	}

	public void recordSuccess(String email) {
		failures.remove(key(email));
	}

	public void checkSignup(String address) {
		hit(signupsByAddress, address, System.currentTimeMillis(), SIGNUP_WINDOW_MS, MAX_SIGNUPS_PER_ADDRESS,
				"Too many accounts created from this address. Please try again later.");
	}

	private void hit(Map<String, Deque<Long>> map, String address, long now, long window, int max, String message) {
		Deque<Long> times = map.computeIfAbsent(address == null ? "unknown" : address, k -> new ArrayDeque<>());
		synchronized (times) {
			prune(times, now, window);
			if (times.size() >= max) {
				throw new TooManyRequestsException(message, retryAfter(times, now, window));
			}
			times.addLast(now);
		}
	}

	private static void prune(Deque<Long> times, long now, long window) {
		while (!times.isEmpty() && now - times.peekFirst() >= window) {
			times.pollFirst();
		}
	}

	private static long retryAfter(Deque<Long> times, long now, long window) {
		return Math.max(1, (window - (now - times.peekFirst()) + 999) / 1000);
	}

	private static String minutes(long seconds) {
		long m = (seconds + 59) / 60;
		return m <= 1 ? "a minute" : m + " minutes";
	}

	private static String key(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}
}
