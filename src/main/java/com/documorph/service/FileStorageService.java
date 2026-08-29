package com.documorph.service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.documorph.exception.ResourceNotFoundException;

@Service
public class FileStorageService {

	private final Path rootDir;

	public FileStorageService(@Value("${app.upload.dir}") String uploadDir) {
		this.rootDir = Paths.get(uploadDir).toAbsolutePath().normalize();
		try {
			Files.createDirectories(rootDir);
		} catch (IOException e) {
			throw new RuntimeException("Could not create upload directory: " + rootDir, e);
		}
	}

	public String store(UUID sessionId, MultipartFile file) {
		try {
			Path sessionDir = rootDir.resolve(sessionId.toString());
			Files.createDirectories(sessionDir);

			String safeOriginalName = file.getOriginalFilename() != null
					? file.getOriginalFilename().replaceAll("[^A-Za-z0-9._-]", "_")
					: "upload";
			Path target = sessionDir.resolve(UUID.randomUUID() + "_" + safeOriginalName);

			file.transferTo(target);
			return target.toString();
		} catch (IOException e) {
			throw new RuntimeException("Failed to store file: " + file.getOriginalFilename(), e);
		}
	}

	public Resource loadAsResource(String filePath) {
		try {
			Path target = Paths.get(filePath).toAbsolutePath().normalize();
			// defence in depth: paths come from our own DB, but never read
			// outside the configured upload root
			if (!target.startsWith(rootDir)) {
				throw new ResourceNotFoundException("File not found.");
			}
			Resource resource = new UrlResource(target.toUri());
			if (!resource.exists() || !resource.isReadable()) {
				throw new ResourceNotFoundException("File is missing from storage.");
			}
			return resource;
		} catch (MalformedURLException e) {
			throw new ResourceNotFoundException("File could not be read.");
		}
	}

	/** Best-effort removal of a session's upload folder and its contents. */
	public void deleteSessionDirectory(UUID sessionId) {
		Path dir = rootDir.resolve(sessionId.toString()).normalize();
		if (!dir.startsWith(rootDir) || !Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(dir)) {
			paths.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException ignored) {
					// leave anything we cannot remove
				}
			});
		} catch (IOException ignored) {
			// best-effort
		}
	}
}