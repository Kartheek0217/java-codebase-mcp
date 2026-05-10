package com.mcp.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.entity.Project;
import com.mcp.entity.Symbol;
import com.mcp.entity.SymbolCall;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SymbolCallRepository;
import com.mcp.repository.SymbolRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ui")
@Tag(name = "UI Helpers", description = "Endpoints specifically designed to support the web UI.")
public class UIHelperController {

	private final ProjectRepository projectRepository;
	private final FileMetadataRepository fileMetadataRepository;
	private final SymbolRepository symbolRepository;
	private final SymbolCallRepository symbolCallRepository;

	public UIHelperController(ProjectRepository projectRepository, FileMetadataRepository fileMetadataRepository,
			SymbolRepository symbolRepository, SymbolCallRepository symbolCallRepository) {
		this.projectRepository = projectRepository;
		this.fileMetadataRepository = fileMetadataRepository;
		this.symbolRepository = symbolRepository;
		this.symbolCallRepository = symbolCallRepository;
	}

	/**
	 * Retrieves a summary of all projects for the UI.
	 *
	 * @return A list of maps containing project ID, name, path, and basic counts
	 */
	@GetMapping("/projects-summary")
	@Operation(summary = "Get a summary of all projects", description = "Returns IDs, names, and basic statistics for all projects to populate UI selectors.")
	public List<Map<String, Object>> getProjectsSummary() {
		List<Project> projects = projectRepository.findAll();
		List<Map<String, Object>> summary = new ArrayList<>();

		for (Project project : projects) {
			Map<String, Object> item = new HashMap<>();
			item.put("id", project.getId());
			item.put("name", project.getName());
			item.put("rootPath", project.getRootPath());
			item.put("fileCount", fileMetadataRepository.countByProjectId(project.getId()));
			item.put("symbolCount", symbolRepository.countByProjectId(project.getId()));
			summary.add(item);
		}
		return summary;
	}

	/**
	 * Retrieves detailed statistics for a specific project.
	 *
	 * @param projectId The ID of the project
	 * @return A map containing detailed file and symbol statistics
	 */
	@GetMapping("/project-stats")
	@Operation(summary = "Get detailed stats for a project by ID")
	public Map<String, Object> getProjectStats(Long projectId) {
		Map<String, Object> stats = new HashMap<>();
		stats.put("projectId", projectId);
		stats.put("fileCount", fileMetadataRepository.countByProjectId(projectId));
		stats.put("symbolCount", symbolRepository.countByProjectId(projectId));
		return stats;
	}

	/**
	 * Retrieves detailed information about a specific symbol by its ID.
	 *
	 * @param id The unique ID of the symbol
	 * @return The Symbol entity
	 */
	@GetMapping("/symbols/{id}")
	@Operation(summary = "Get symbol details by ID")
	public Symbol getSymbolById(@PathVariable Long id) {
		return symbolRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND));
	}

	/**
	 * Retrieves the call hierarchy for a specific symbol.
	 *
	 * @param id The ID of the symbol (caller)
	 * @return A map containing incoming and outgoing calls
	 */
	@GetMapping("/symbols/{id}/hierarchy")
	@Operation(summary = "Get call hierarchy for a symbol")
	public Map<String, Object> getCallHierarchy(@PathVariable Long id) {
		Symbol symbol = symbolRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

		Map<String, Object> result = new HashMap<>();
		result.put("symbol", symbol);

		// Outgoing calls (who does this symbol call?)
		List<SymbolCall> outgoing = symbolCallRepository.findByCallerId(id);
		result.put("outgoing", outgoing);

		// Incoming calls (who calls this symbol?)
		List<SymbolCall> incoming = symbolCallRepository.findByProjectIdAndCalleeName(symbol.getProjectId(),
				symbol.getName());

		// Enrich incoming calls with caller symbol details
		List<Map<String, Object>> incomingEnriched = incoming.stream().map(call -> {
			Map<String, Object> item = new HashMap<>();
			item.put("call", call);
			symbolRepository.findById(call.getCallerId()).ifPresent(caller -> item.put("caller", caller));
			return item;
		}).collect(Collectors.toList());

		result.put("incoming", incomingEnriched);

		return result;
	}

}
