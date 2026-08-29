package com.documorph.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.documorph.dto.CategoryRequest;
import com.documorph.dto.CategoryResponse;
import com.documorph.service.CategoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

	private final CategoryService categoryService;

	@GetMapping
	public ResponseEntity<List<CategoryResponse>> getAllCategories(@AuthenticationPrincipal UserDetails userDetails) {
		return ResponseEntity.ok(categoryService.getAllCategories(userDetails.getUsername()));
	}

	@GetMapping("/{id}")
	public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable UUID id,
			@AuthenticationPrincipal UserDetails userDetails) {
		return ResponseEntity.ok(categoryService.getCategoryById(id, userDetails.getUsername()));
	}

	@PostMapping
	public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request,
			@AuthenticationPrincipal UserDetails userDetails) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(categoryService.createCategory(request, userDetails.getUsername()));
	}

	@PutMapping("/{id}")
	public ResponseEntity<CategoryResponse> updateCategory(@PathVariable UUID id,
			@Valid @RequestBody CategoryRequest request, @AuthenticationPrincipal UserDetails userDetails) {
		return ResponseEntity.ok(categoryService.updateCategory(id, request, userDetails.getUsername()));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteCategory(@PathVariable UUID id,
			@AuthenticationPrincipal UserDetails userDetails) {
		categoryService.deleteCategory(id, userDetails.getUsername());
		return ResponseEntity.noContent().build();
	}
}