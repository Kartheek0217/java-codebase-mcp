package com.mcp.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.mcp.entity.FileMetadata;
import com.mcp.entity.Project;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SymbolRepository;

@Service
public class TopologyService {

	// Fix O: import prefixes that add zero signal for AI tools
	private static final Set<String> NOISE_IMPORT_PREFIXES = Set.of(
			"java.", "javax.", "jakarta.", "org.springframework.", "com.fasterxml.",
			"org.hibernate.", "org.slf4j.", "org.apache.", "io.swagger.");

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

				// Fix O: filter JDK/Spring/framework imports — they are noise for AI tools
				List<String> filteredImports = importList.stream()
						.filter(imp -> NOISE_IMPORT_PREFIXES.stream().noneMatch(imp::startsWith))
						.collect(Collectors.toList());

				if (!filteredImports.isEmpty()) {
					dependencies.put(relativePath, filteredImports);
				}

				if (relativePath.contains("Controller") || relativePath.contains("Main")
						|| relativePath.contains("Application")) {
					entryPoints.add(relativePath);
				}
			}
		}

		// Fix K: aggregate top symbols in DB with GROUP BY instead of loading all
		// symbols into JVM heap
		List<Object[]> topSymbolRows = symbolRepository.findTopSymbolNames(projectId, PageRequest.of(0, 20));
		List<Map<String, Object>> topSymbols = topSymbolRows.stream().map(row -> {
			Map<String, Object> entry = new HashMap<>();
			entry.put("name", row[0]);
			entry.put("count", row[1]);
			return entry;
		}).collect(Collectors.toList());

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
