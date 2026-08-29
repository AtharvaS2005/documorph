package com.documorph.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultResponse {
	private String text;
	private double score;
	private String docId;
	private int chunkIndex;
	private String sessionId;
	private String fileName;
}