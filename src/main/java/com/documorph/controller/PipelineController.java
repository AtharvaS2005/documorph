package com.documorph.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.documorph.service.PythonPipelineClient;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PipelineController {

	private final PythonPipelineClient pythonPipelineClient;

	@GetMapping("/api/pipeline/health")
	public Map<String, Object> health() {
		return pythonPipelineClient.checkHealth();
	}
}
