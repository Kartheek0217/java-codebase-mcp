package com.mcp.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.entity.Project;
import com.mcp.entity.Skill;
import com.mcp.entity.Symbol;
import com.mcp.entity.SymbolCall;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SkillRepository;
import com.mcp.repository.SymbolCallRepository;
import com.mcp.repository.SymbolRepository;
import java.util.ArrayDeque;
import java.util.Deque;

@Service
public class EndpointAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(EndpointAnalysisService.class);
    private final ProjectRepository projectRepository;
    private final SymbolRepository symbolRepository;
    private final SymbolCallRepository symbolCallRepository;
    private final SkillRepository skillRepository;
    private final CodeSummarizerService codeSummarizerService;

    public EndpointAnalysisService(ProjectRepository projectRepository,
            SymbolRepository symbolRepository,
            SymbolCallRepository symbolCallRepository,
            SkillRepository skillRepository,
            CodeSummarizerService codeSummarizerService) {
        this.projectRepository = projectRepository;
        this.symbolRepository = symbolRepository;
        this.symbolCallRepository = symbolCallRepository;
        this.skillRepository = skillRepository;
        this.codeSummarizerService = codeSummarizerService;
    }

    @Transactional
    public Skill analyzeEndpoint(Long projectId, String controllerName, String methodName) throws IOException {
        if (controllerName == null || !controllerName.matches("^[a-zA-Z0-9_]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid controllerName");
        }
        if (methodName == null || !methodName.matches("^[a-zA-Z0-9_]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid methodName");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        // Find the controller method symbol
        List<Symbol> controllerSymbols = symbolRepository.findByProjectIdAndNameContainingIgnoreCase(projectId,
                methodName, Pageable.unpaged());
        Symbol entrySymbol = controllerSymbols.stream()
                .filter(s -> s.getFilePath() != null && s.getFilePath().contains(controllerName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "Endpoint method not found in controller: " + controllerName + "." + methodName));

        Map<String, String> componentCode = new LinkedHashMap<>();
        List<String> relationships = new ArrayList<>();
        StringBuilder treeReport = new StringBuilder();

        traceFlow(projectId, project.getRootPath(), entrySymbol, 5, componentCode, treeReport, relationships);

        StringBuilder report = new StringBuilder();
        report.append("# Endpoint Analysis: ").append(controllerName).append(".").append(methodName).append("\n\n");

        report.append("## 📊 Architectural Flow Diagram\n");
        report.append("```mermaid\ngraph TD\n");
        for (String rel : relationships) {
            report.append("    ").append(rel).append("\n");
        }
        report.append("```\n\n");

        report.append("## 🌲 Call Hierarchy (Text)\n");
        report.append(treeReport.toString());

        report.append("\n## 🔍 Component Implementation Details\n\n");
        for (Map.Entry<String, String> entry : componentCode.entrySet()) {
            String relPath = entry.getKey().replace(project.getRootPath(), "");
            report.append("### 📄 File: ").append(relPath).append("\n");
            report.append("```java\n").append(entry.getValue()).append("\n```\n\n");
        }

        String skillName = "Endpoint Analysis: " + controllerName + "." + methodName;
        Skill skill = skillRepository.findByProjectIdAndName(projectId, skillName).orElse(new Skill());

        skill.setProject(project);
        skill.setName(skillName);
        skill.setDescription("Deep analysis of the " + methodName + " endpoint in " + controllerName
                + ", tracing calls through Services, Repositories, and Mappers.");
        skill.setContent(report.toString());
        skill.setSource("Manual Analysis");

        return skillRepository.save(skill);
    }

    private void traceFlow(Long projectId, String projectRoot, Symbol startSymbol, int maxDepth,
            Map<String, String> componentCode, StringBuilder treeReport,
            List<String> relationships) {

        class Node {
            Symbol symbol;
            int depth;
            Set<Long> path;
            Node(Symbol symbol, int depth, Set<Long> path) {
                this.symbol = symbol; this.depth = depth; this.path = path;
            }
        }
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(new Node(startSymbol, 0, new HashSet<>()));

        while (!stack.isEmpty()) {
            Node current = stack.pop();
            Symbol symbol = current.symbol;
            int depth = current.depth;
            Set<Long> pathVisited = current.path;

            if (depth >= maxDepth || pathVisited.contains(symbol.getId())) {
                continue;
            }
            pathVisited.add(symbol.getId());

            String role = identifyRole(symbol.getFilePath());
            String displayName = role + ": " + symbol.getName();
            String safeId = "node_" + symbol.getId();

            treeReport.append("  ".repeat(depth)).append("- ").append(displayName).append("\n");

            if (symbol.getFilePath() != null && !componentCode.containsKey(symbol.getFilePath())) {
                try {
                    String content = Files.readString(Paths.get(symbol.getFilePath()));
                    String structure = codeSummarizerService.extractStructure(content);
                    componentCode.put(symbol.getFilePath(), structure);
                } catch (IOException e) {
                    logger.warn("Could not read file for symbol: {}", symbol.getFilePath());
                }
            }

            List<SymbolCall> calls = symbolCallRepository.findByCallerId(symbol.getId());
            // Reverse iteration to maintain DFS order
            for (int i = calls.size() - 1; i >= 0; i--) {
                SymbolCall call = calls.get(i);
                List<Symbol> callees = symbolRepository.findByProjectIdAndNameContainingIgnoreCase(projectId,
                        call.getCalleeName(), Pageable.unpaged());
                for (int j = callees.size() - 1; j >= 0; j--) {
                    Symbol callee = callees.get(j);
                    if (callee.getFilePath() != null && projectRoot != null && callee.getFilePath().startsWith(projectRoot)) {
                        String calleeRole = identifyRole(callee.getFilePath());
                        String calleeSafeId = "node_" + callee.getId();
                        relationships.add(safeId + "[\"" + displayName + "\"] --> " + calleeSafeId + "[\"" + calleeRole
                                + ": " + callee.getName() + "\"]");
                        stack.push(new Node(callee, depth + 1, new HashSet<>(pathVisited)));
                    }
                }
            }
        }
    }

    private String identifyRole(String filePath) {
        if (filePath == null)
            return "Unknown";
        if (filePath.contains("Controller"))
            return "Controller";
        if (filePath.contains("ServiceImpl"))
            return "Service Implementation";
        if (filePath.contains("Service"))
            return "Service Interface";
        if (filePath.contains("Repository"))
            return "Repository";
        if (filePath.contains("Mapper"))
            return "Mapper";
        if (filePath.contains("DTO"))
            return "DTO";
        if (filePath.contains("Entity") || filePath.contains("model"))
            return "Entity/Model";
        return "Component";
    }
}
