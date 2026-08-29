package com.documorph.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.documorph.dto.ForumPostRequest;
import com.documorph.dto.ForumPostResponse;
import com.documorph.dto.ForumReplyRequest;
import com.documorph.dto.ForumReplyResponse;
import com.documorph.exception.ForbiddenException;
import com.documorph.exception.ResourceNotFoundException;
import com.documorph.model.ForumPost;
import com.documorph.model.ForumReply;
import com.documorph.model.Organization;
import com.documorph.model.RecordSet;
import com.documorph.model.UploadSession;
import com.documorph.model.User;
import com.documorph.model.Workspace;
import com.documorph.repository.ForumPostRepository;
import com.documorph.repository.ForumReplyRepository;
import com.documorph.repository.RecordSetRepository;
import com.documorph.repository.UploadSessionRepository;
import com.documorph.repository.UserRepository;
import com.documorph.repository.WorkspaceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ForumService {

	private final UserRepository userRepository;
	private final WorkspaceRepository workspaceRepository;
	private final ForumPostRepository forumPostRepository;
	private final ForumReplyRepository forumReplyRepository;
	private final UploadSessionRepository uploadSessionRepository;
	private final RecordSetRepository recordSetRepository;

	@Transactional
	public ForumPostResponse createPost(ForumPostRequest request, String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		Organization organization = workspace.getOrganization();
		if (organization == null) {
			throw new ForbiddenException("Only organization members can post to the forum.");
		}

		UploadSession session = null;
		if (request.getSessionId() != null) {
			session = uploadSessionRepository.findById(request.getSessionId())
					.orElseThrow(() -> new ResourceNotFoundException("Session not found."));
		}

		RecordSet recordSet = null;
		if (request.getRecordSetId() != null) {
			recordSet = recordSetRepository.findById(request.getRecordSetId())
					.orElseThrow(() -> new ResourceNotFoundException("Record set not found."));
		}

		ForumPost post = ForumPost.builder().organization(organization).author(user).session(session)
				.recordSet(recordSet).title(request.getTitle()).message(request.getMessage()).build();
		post = forumPostRepository.save(post);

		return toPostResponse(post, 0L);
	}

	@Transactional(readOnly = true)
	public List<ForumPostResponse> getPosts(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		Organization organization = workspace.getOrganization();
		if (organization == null) {
			throw new ForbiddenException("Only organization members can access the forum.");
		}

		return forumPostRepository.findByOrganizationIdOrderByCreatedAtDesc(organization.getId()).stream()
				.map(post -> toPostResponse(post, forumReplyRepository.countByPostId(post.getId())))
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public ForumPostResponse getPost(UUID postId, String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		Organization organization = workspace.getOrganization();
		if (organization == null) {
			throw new ForbiddenException("Only organization members can access the forum.");
		}

		if (!forumPostRepository.existsByIdAndOrganizationId(postId, organization.getId())) {
			throw new ResourceNotFoundException("Post not found.");
		}

		ForumPost post = forumPostRepository.findById(postId)
				.orElseThrow(() -> new ResourceNotFoundException("Post not found."));

		return toPostResponse(post, forumReplyRepository.countByPostId(postId));
	}

	@Transactional
	public ForumReplyResponse createReply(UUID postId, ForumReplyRequest request, String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		Organization organization = workspace.getOrganization();
		if (organization == null) {
			throw new ForbiddenException("Only organization members can reply to forum posts.");
		}

		if (!forumPostRepository.existsByIdAndOrganizationId(postId, organization.getId())) {
			throw new ResourceNotFoundException("Post not found.");
		}

		ForumPost post = forumPostRepository.findById(postId)
				.orElseThrow(() -> new ResourceNotFoundException("Post not found."));

		ForumReply reply = ForumReply.builder().post(post).author(user).message(request.getMessage()).build();
		reply = forumReplyRepository.save(reply);

		return toReplyResponse(reply);
	}

	@Transactional(readOnly = true)
	public List<ForumReplyResponse> getReplies(UUID postId, String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		Organization organization = workspace.getOrganization();
		if (organization == null) {
			throw new ForbiddenException("Only organization members can access the forum.");
		}

		if (!forumPostRepository.existsByIdAndOrganizationId(postId, organization.getId())) {
			throw new ResourceNotFoundException("Post not found.");
		}

		return forumReplyRepository.findByPostIdOrderByCreatedAtAsc(postId).stream().map(this::toReplyResponse)
				.collect(Collectors.toList());
	}

	private ForumPostResponse toPostResponse(ForumPost post, long replyCount) {
		return ForumPostResponse.builder().id(post.getId()).organizationId(post.getOrganization().getId())
				.authorId(post.getAuthor().getId()).authorEmail(post.getAuthor().getEmail()).title(post.getTitle())
				.message(post.getMessage()).sessionId(post.getSession() != null ? post.getSession().getId() : null)
				.recordSetId(post.getRecordSet() != null ? post.getRecordSet().getId() : null).replyCount(replyCount)
				.createdAt(post.getCreatedAt()).build();
	}

	private ForumReplyResponse toReplyResponse(ForumReply reply) {
		return ForumReplyResponse.builder().id(reply.getId()).postId(reply.getPost().getId())
				.authorId(reply.getAuthor().getId()).authorEmail(reply.getAuthor().getEmail())
				.message(reply.getMessage()).createdAt(reply.getCreatedAt()).build();
	}
}