package com.mcp.service;

import com.mcp.entity.Project;
import com.mcp.entity.Skill;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SkillService {

    private static final Logger logger = LoggerFactory.getLogger(SkillService.class);
    private final SkillRepository skillRepository;
    private final ProjectRepository projectRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public SkillService(SkillRepository skillRepository, ProjectRepository projectRepository) {
        this.skillRepository = skillRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public void learnFromUrl(Long projectId, String url) {
        logger.info("Learning skill from URL: {}", url);
        String content = restTemplate.getForObject(url, String.class);
        if (content != null) {
            learnSkillFromMarkdown(projectId, content, url);
        }
    }

    @Transactional
    public void learnFromFile(Long projectId, String filePath) throws IOException {
        logger.info("Learning skill from file: {} for project: {}", filePath, projectId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        Path projectRoot = Paths.get(project.getRootPath()).toAbsolutePath().normalize();
        Path absoluteFilePath = projectRoot.resolve(filePath).toAbsolutePath().normalize();

        // Security check: ensure the file is within the project root
        if (!absoluteFilePath.startsWith(projectRoot)) {
            logger.error("Security violation: attempt to access file outside project root: {}", absoluteFilePath);
            throw new SecurityException("Access denied: File is outside project root");
        }

        if (!Files.exists(absoluteFilePath)) {
            logger.error("File not found: {}", absoluteFilePath);
            throw new IOException("File not found: " + filePath);
        }

        if (!Files.isRegularFile(absoluteFilePath)) {
            logger.error("Not a regular file: {}", absoluteFilePath);
            throw new IOException("Provided path is not a regular file: " + filePath);
        }

        String content = Files.readString(absoluteFilePath);
        learnSkillFromMarkdown(projectId, content, filePath);
    }

    @Transactional
    public void learnSkillFromMarkdown(Long projectId, String content, String source) {
        Map<String, String> metadata = parseFrontmatter(content);
        if (metadata.containsKey("name")) {
            String name = metadata.get("name");
            String description = metadata.getOrDefault("description", "");

            Project project = projectRepository.findById(projectId).orElseThrow();

            Skill skill = skillRepository.findByProjectIdAndName(projectId, name)
                    .orElse(new Skill());

            skill.setProject(project);
            skill.setName(name);
            skill.setDescription(description);
            skill.setContent(content);
            skill.setSource(source);

            skillRepository.save(skill);
            logger.info("Learned/Updated skill: {} from source: {}", name, source);
        } else {
            logger.debug("Markdown file at {} does not contain valid skill frontmatter.", source);
        }
    }

    @Transactional
    public void deleteSkillsByProject(Long projectId) {
        logger.info("Deleting all skills for project: {}", projectId);
        skillRepository.deleteByProjectId(projectId);
    }

    private Map<String, String> parseFrontmatter(String content) {
        Map<String, String> metadata = new HashMap<>();
        // Match frontmatter between --- and --- at the start of the file
        Pattern pattern = Pattern.compile("(?s)^---\\s*\\n(.*?)\\n---\\s*\\n");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String yaml = matcher.group(1);
            for (String line : yaml.split("\\n")) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                    metadata.put(key, value);
                }
            }
        }
        return metadata;
    }
}
