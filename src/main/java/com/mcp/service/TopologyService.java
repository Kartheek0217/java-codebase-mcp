package com.mcp.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.mcp.entity.FileMetadata;
import com.mcp.entity.Project;
import com.mcp.entity.Symbol;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SymbolRepository;

@Service
public class TopologyService {

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
				String deps = file.getDependencies();
				List<String> importList = (deps != null && !deps.isEmpty())
						? Arrays.asList(deps.split(","))
						: Collections.emptyList();
				dependencies.put(relativePath, importList);

				if (relativePath.contains("Controller") || relativePath.contains("Main")
						|| relativePath.contains("Application")) {
					entryPoints.add(relativePath);
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

}
