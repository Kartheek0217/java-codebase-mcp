package com.mcp.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.mcp.entity.Project;
import com.mcp.repository.ProjectRepository;
import com.mcp.entity.FileMetadata;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.SymbolRepository;
import com.mcp.entity.Symbol;

@Service
public class TopologyService {
	private static final Logger logger = LoggerFactory.getLogger(TopologyService.class);

	private final ProjectRepository projectRepository;
	private final FileMetadataRepository fileMetadataRepository;
	private final SymbolRepository symbolRepository;

	public TopologyService(ProjectRepository projectRepository, FileMetadataRepository fileMetadataRepository,
			SymbolRepository symbolRepository) {
		this.projectRepository = projectRepository;
		this.fileMetadataRepository = fileMetadataRepository;
		this.symbolRepository = symbolRepository;
	}

	@Cacheable(value = "topology", key = "#projectId")
	public Map<String, Object> getProjectTopology(Long projectId) {
		Project project = projectRepository.findById(projectId)
				.orElseThrow(() -> new RuntimeException("Project not found"));

		List<FileMetadata> files = fileMetadataRepository.findByProjectId(projectId);

		Map<String, List<String>> dependencies = new HashMap<>();
		List<String> entryPoints = new ArrayList<>();

		for (FileMetadata file : files) {
			String path = file.getFilePath();
			String relativePath = path.replace(project.getRootPath(), "");
			if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
				relativePath = relativePath.substring(1);
			}

			if (path.endsWith(".java")) {
				try {
					List<String> imports = extractImports(path);
					dependencies.put(relativePath, imports);

					if (relativePath.contains("Controller") || relativePath.contains("Main")
							|| relativePath.contains("Application")) {
						entryPoints.add(relativePath);
					}
				} catch (Exception e) {
					logger.warn("Failed to extract imports from {}: {}", path, e.getMessage());
				}
			}
		}

		// Top symbols by frequency
		List<Symbol> allSymbols = symbolRepository.findByProjectId(projectId);
		Map<String, Long> symbolFrequency = allSymbols.stream()
				.collect(Collectors.groupingBy(Symbol::getName, Collectors.counting()));

		List<Map.Entry<String, Long>> topSymbols = symbolFrequency.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(20).collect(Collectors.toList());

		Map<String, Object> topology = new HashMap<>();
		topology.put("projectId", projectId);
		topology.put("projectName", project.getName());
		topology.put("fileCount", files.size());
		topology.put("entryPoints", entryPoints);
		topology.put("topSymbols", topSymbols);
		topology.put("dependencies", dependencies);

		return topology;
	}

	private List<String> extractImports(String filePath) throws IOException {
		try {
			String content = Files.readString(Paths.get(filePath));
			CompilationUnit cu = StaticJavaParser.parse(content);
			return cu.getImports().stream().map(ImportDeclaration::getNameAsString).collect(Collectors.toList());
		} catch (Exception e) {
			return Collections.emptyList();
		}
	}
}
