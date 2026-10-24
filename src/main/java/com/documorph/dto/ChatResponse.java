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
	/** Written answer; may contain inline [n] markers pointing into passages. */
	private String answer;
	/** Passage numbers the answer actually relied on. */
	private List<Integer> sources;
	private String modelUsed;
	/** Every retrieved passage, numbered from 1 to match the citations. */
	private List<Passage> passages;

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
