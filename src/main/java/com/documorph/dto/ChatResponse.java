package com.documorph.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
	/**
	 * ANSWERED, NOT_FOUND, BLOCKED (instructions in the question), REFUSED (not a
	 * question about the documents) or UNGROUNDED (answer not supported by its
	 * sources). Anything but ANSWERED comes with a fixed reply and no passages.
	 */
	private String outcome;
	/** Written answer; may contain inline [n] markers pointing into passages. */
	private String answer;
	/** Passage numbers the answer actually relied on. */
	private List<Integer> sources;
	private String modelUsed;
	/** Every retrieved passage, numbered from 1 to match the citations. */
	private List<Passage> passages;
	/** Retrieved passages that had planted instructions removed before the model saw them. */
	private int sanitizedPassages;
	/** Sensitive identifiers masked in the answer and passages. */
	private int redactions;

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Passage {
		private int number;
		private String text;
		private double score;
		private String docId;
		private String sessionId;
		private String fileName;
	}
}
