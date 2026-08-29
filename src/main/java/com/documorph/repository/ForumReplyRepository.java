package com.documorph.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.documorph.model.ForumReply;

@Repository
public interface ForumReplyRepository extends JpaRepository<ForumReply, UUID> {
	List<ForumReply> findByPostIdOrderByCreatedAtAsc(UUID postId);

	long countByPostId(UUID postId);
}