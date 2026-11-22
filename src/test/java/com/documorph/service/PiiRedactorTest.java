package com.documorph.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PiiRedactor}.
 *
 * <p>Deliberately a plain unit test rather than a {@code @SpringBootTest}: the
 * class is pure, so loading the application context would add a database and a
 * running pipeline as prerequisites for asserting on a regular expression.
 *
 * <p>The cases that matter are the ones where redaction must <em>not</em> fire.
 * Extracted documents are full of long digit strings that are not card numbers
 * -- invoice totals, chassis numbers, order references -- and redacting those
 * would quietly destroy the answers the product exists to give.
 */
class PiiRedactorTest {

	@Test
	@DisplayName("null and empty input are returned unchanged")
	void handlesEmptyInput() {
		assertNull(PiiRedactor.redact(null));
		assertEquals("", PiiRedactor.redact(""));
	}

	@Test
	@DisplayName("a Luhn-valid card number is hidden")
	void redactsValidCardNumber() {
		// A standard test card number; valid under Luhn, issued by nobody.
		String out = PiiRedactor.redact("Paid with 4111 1111 1111 1111 today.");
		assertEquals("Paid with [card number hidden] today.", out);
	}

	@Test
	@DisplayName("a digit string that fails Luhn is left alone")
	void keepsNonCardDigits() {
		// Card-shaped but Luhn-invalid: this is the false positive that
		// would otherwise eat real document content. The digits also avoid
		// the Aadhaar shape, which any twelve of them starting 2-9 would
		// otherwise trip (see the test below).
		String text = "Chassis MB8DP12PAR8647555, ref 1010 1010 1010 1011.";
		assertEquals(text, PiiRedactor.redact(text));
	}

	@Test
	@DisplayName("known limit: a 12-digit reference is redacted as an Aadhaar")
	void twelveDigitReferenceIsIndistinguishableFromAadhaar() {
		// Not a defect to fix here, but a property worth pinning down. An
		// Aadhaar number is only "twelve digits not starting 0 or 1", which
		// a purchase-order or consignment reference can match exactly. The
		// redactor cannot tell them apart, so it errs towards hiding. If
		// this ever starts failing, the pattern was loosened and real
		// reference numbers are now leaking into answers.
		String out = PiiRedactor.redact("Consignment ref 5678 9012 3456.");
		assertTrue(out.contains("[Aadhaar number hidden]"));
	}

	@Test
	@DisplayName("an invoice total is never mistaken for an identifier")
	void keepsMonetaryAmounts() {
		String text = "Total amount payable 445,868.00 due 18 April 2026.";
		assertEquals(text, PiiRedactor.redact(text));
	}

	@Test
	@DisplayName("an Indian PAN is hidden")
	void redactsPan() {
		String out = PiiRedactor.redact("PAN ABCDE1234F on file.");
		assertTrue(out.contains("[PAN hidden]"));
		assertFalse(out.contains("ABCDE1234F"));
	}

	@Test
	@DisplayName("an Aadhaar number is hidden")
	void redactsAadhaar() {
		String out = PiiRedactor.redact("Aadhaar 2234 5678 9012 verified.");
		assertFalse(out.contains("2234 5678 9012"));
	}

	@Test
	@DisplayName("a GSTIN is not redacted as a PAN")
	void keepsGstin() {
		// A GSTIN embeds a PAN but is a business identifier, printed openly
		// on every invoice; losing it would break supplier lookups.
		String text = "GSTIN 27AABCO1234F1Z5";
		assertEquals(text, PiiRedactor.redact(text));
	}

	@Test
	@DisplayName("several identifiers in one string are all handled")
	void redactsMultipleInOneString() {
		String out = PiiRedactor.redact("Card 4111111111111111, PAN ABCDE1234F.");
		assertTrue(out.contains("[card number hidden]"));
		assertTrue(out.contains("[PAN hidden]"));
	}
}
