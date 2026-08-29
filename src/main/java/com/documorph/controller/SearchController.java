package com.documorph.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.documorph.dto.SearchResultResponse;
import com.documorph.service.SearchService;
import com.documorph.service.UploadSessionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SearchController {

	private final SearchService searchService;
	private final UploadSessionService uploadSessionService;

	@PostMapping("/api/search/reindex")
	public Map<String, Integer> reindex(@AuthenticationPrincipal UserDetails userDetails) {
		int indexed = uploadSessionService.reindexWorkspace(userDetails.getUsername());
		return Map.of("indexed", indexed);
	}

	@GetMapping("/api/search")
	public List<SearchResultResponse> search(@RequestParam String query, @RequestParam(defaultValue = "5") int topK,
			@AuthenticationPrincipal UserDetails userDetails) {
		return searchService.search(query, topK, userDetails.getUsername());
	}
}