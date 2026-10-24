package com.documorph.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.documorph.dto.ChatLogEntry;
import com.documorph.dto.ChatRequest;
import com.documorph.dto.ChatResponse;
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

	@PostMapping("/api/search/chat")
	public ChatResponse chat(@Valid @RequestBody ChatRequest request,
			@AuthenticationPrincipal UserDetails userDetails) {
		return searchService.chat(request.getQuestion(), userDetails.getUsername());
	}

	@GetMapping("/api/search/chat/log")
	public List<ChatLogEntry> chatLog(@AuthenticationPrincipal UserDetails userDetails) {
		return searchService.chatLog(userDetails.getUsername());
	}

	@GetMapping("/api/search")
	public List<SearchResultResponse> search(@RequestParam String query, @RequestParam(defaultValue = "5") int topK,
			@AuthenticationPrincipal UserDetails userDetails) {
		return searchService.search(query, topK, userDetails.getUsername());
	}
}