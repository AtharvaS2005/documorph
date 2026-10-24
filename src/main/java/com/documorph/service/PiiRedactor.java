package com.documorph.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks high-risk identifiers before text is stored in the chat audit log.
 * Mirrors redact_pii in the pipeline's chat_guardrails.py, which already
 * masks answers and passages; this covers the user's own question.
 */
public final class PiiRedactor {

	private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]?){12,18}\\d\\b");
	private static final Pattern AADHAAR = Pattern.compile("\\b[2-9]\\d{3}[ -]?\\d{4}[ -]?\\d{4}\\b");
	private static final Pattern PAN = Pattern.compile("\\b[A-Z]{5}\\d{4}[A-Z]\\b");
	private static final Pattern SSN = Pattern.compile("\\b(?!000|666|9\\d\\d)\\d{3}-(?!00)\\d{2}-(?!0000)\\d{4}\\b");

	private PiiRedactor() {
	}

	public static String redact(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		Matcher m = CARD.matcher(text);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			String digits = m.group().replaceAll("\\D", "");
			boolean card = digits.length() >= 13 && digits.length() <= 19 && luhn(digits);
			m.appendReplacement(sb, Matcher.quoteReplacement(card ? "[card number hidden]" : m.group()));
		}
		m.appendTail(sb);
		String out = AADHAAR.matcher(sb.toString()).replaceAll("[Aadhaar number hidden]");
		out = PAN.matcher(out).replaceAll("[PAN hidden]");
		return SSN.matcher(out).replaceAll("[SSN hidden]");
	}

	private static boolean luhn(String digits) {
		int sum = 0;
		boolean alt = false;
		for (int i = digits.length() - 1; i >= 0; i--) {
			int d = digits.charAt(i) - '0';
			if (alt) {
				d *= 2;
				if (d > 9) {
					d -= 9;
				}
			}
			sum += d;
			alt = !alt;
		}
		return sum % 10 == 0;
	}
}
