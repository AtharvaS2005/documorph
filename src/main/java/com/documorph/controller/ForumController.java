package com.documorph.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.documorph.dto.ForumPostRequest;
import com.documorph.dto.ForumPostResponse;
import com.documorph.dto.ForumReplyRequest;
import com.documorph.dto.ForumReplyResponse;
import com.documorph.service.ForumService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/forum/posts")
@RequiredArgsConstructor
public class ForumController {

	private final ForumService forumService;

	@PostMapping
	public ResponseEntity<ForumPostResponse> createPost(@RequestBody ForumPostRequest request,
			@AuthenticationPrincipal UserDetails userDetails) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(forumService.createPost(request, userDetails.getUsername()));
	}

	@GetMapping
	public ResponseEntity<List<ForumPostResponse>> getPosts(@AuthenticationPrincipal UserDetails userDetails) {
		return ResponseEntity.ok(forumService.getPosts(userDetails.getUsername()));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ForumPostResponse> getPost(@PathVariable("id") UUID id,
			@AuthenticationPrincipal UserDetails userDetails) {
		return ResponseEntity.ok(forumService.getPost(id, userDetails.getUsername()));
	}

	@PostMapping("/{id}/replies")
	public ResponseEntity<ForumReplyResponse> createReply(@PathVariable("id") UUID id,
			@RequestBody ForumReplyRequest request, @AuthenticationPrincipal UserDetails userDetails) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(forumService.createReply(id, request, userDetails.getUsername()));
	}

	@GetMapping("/{id}/replies")
	public ResponseEntity<List<ForumReplyResponse>> getReplies(@PathVariable("id") UUID id,
			@AuthenticationPrincipal UserDetails userDetails) {
		return ResponseEntity.ok(forumService.getReplies(id, userDetails.getUsername()));
	}
}