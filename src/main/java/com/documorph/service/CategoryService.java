package com.documorph.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.documorph.dto.CategoryRequest;
import com.documorph.dto.CategoryResponse;
import com.documorph.exception.ResourceNotFoundException;
import com.documorph.model.Category;
import com.documorph.model.User;
import com.documorph.model.Workspace;
import com.documorph.repository.CategoryRepository;
import com.documorph.repository.UserRepository;
import com.documorph.repository.WorkspaceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

	private final CategoryRepository categoryRepository;
	private final WorkspaceRepository workspaceRepository;
	private final UserRepository userRepository;

	public List<CategoryResponse> getAllCategories(String email) {
		Workspace workspace = getWorkspaceByEmail(email);
		return categoryRepository.findByWorkspaceId(workspace.getId()).stream().map(this::toResponse)
				.collect(Collectors.toList());
	}

	public CategoryResponse getCategoryById(UUID categoryId, String email) {
		Category category = categoryRepository.findById(categoryId)
				.orElseThrow(() -> new ResourceNotFoundException("Category not found."));
		verifyOwnership(category, email);
		return toResponse(category);
	}

	@Transactional
	public CategoryResponse createCategory(CategoryRequest request, String email) {
		Workspace workspace = getWorkspaceByEmail(email);

		Category category = Category.builder().workspace(workspace).categoryName(request.getCategoryName())
				.categoryType(request.getCategoryType()).defaultExtractionMode(request.getDefaultExtractionMode())
				.summaryStyle(request.getSummaryStyle()).customPrompt(request.getCustomPrompt())
				.storageFormat(request.getStorageFormat()).description(request.getDescription()).build();

		return toResponse(categoryRepository.save(category));
	}

	@Transactional
	public CategoryResponse updateCategory(UUID categoryId, CategoryRequest request, String email) {
		Category category = categoryRepository.findById(categoryId)
				.orElseThrow(() -> new ResourceNotFoundException("Category not found."));
		verifyOwnership(category, email);

		category.setCategoryName(request.getCategoryName());
		category.setCategoryType(request.getCategoryType());
		category.setDefaultExtractionMode(request.getDefaultExtractionMode());
		category.setSummaryStyle(request.getSummaryStyle());
		category.setCustomPrompt(request.getCustomPrompt());
		category.setStorageFormat(request.getStorageFormat());
		category.setDescription(request.getDescription());

		return toResponse(categoryRepository.save(category));
	}

	@Transactional
	public void deleteCategory(UUID categoryId, String email) {
		Category category = categoryRepository.findById(categoryId)
				.orElseThrow(() -> new ResourceNotFoundException("Category not found."));
		verifyOwnership(category, email);
		categoryRepository.delete(category);
	}

	private Workspace getWorkspaceByEmail(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		return workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));
	}

	private void verifyOwnership(Category category, String email) {
		if (!category.getWorkspace().getUser().getEmail().equals(email)) {
			throw new ResourceNotFoundException("Category not found.");
		}
	}

	private CategoryResponse toResponse(Category category) {
		return CategoryResponse.builder().id(category.getId()).categoryName(category.getCategoryName())
				.categoryType(category.getCategoryType()).defaultExtractionMode(category.getDefaultExtractionMode())
				.summaryStyle(category.getSummaryStyle()).customPrompt(category.getCustomPrompt())
				.storageFormat(category.getStorageFormat()).description(category.getDescription())
				.createdAt(category.getCreatedAt()).updatedAt(category.getUpdatedAt()).build();
	}
}