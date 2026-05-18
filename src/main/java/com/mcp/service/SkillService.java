package com.mcp.service;

import com.mcp.entity.Project;
import com.mcp.entity.Skill;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
	private final ResourcePatternResolver resourcePatternResolver;

	public SkillService(SkillRepository skillRepository, ProjectRepository projectRepository,
			ResourcePatternResolver resourcePatternResolver) {
		this.skillRepository = skillRepository;
		this.projectRepository = projectRepository;
		this.resourcePatternResolver = resourcePatternResolver;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void loadBuiltInSkills() {
		logger.info("Loading global built-in jcb skills...");
		try {
			Resource[] resources = resourcePatternResolver.getResources("classpath*:skills/jcb/**/SKILL.md");
			for (Resource resource : resources) {
				try (InputStream is = resource.getInputStream()) {
					String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
					String source = "built-in:" + resource.getFilename();
					try {
						String uri = resource.getURI().toString();
						int idx = uri.indexOf("skills/");
						if (idx != -1) {
							source = "built-in:" + uri.substring(idx);
						}
					} catch (Exception ex) {
						// Fallback to filename
					}
					learnGlobalSkillFromMarkdown(content, source);
				} catch (Exception e) {
					logger.error("Failed to load built-in skill from resource: {}", resource.getFilename(), e);
				}
			}
		} catch (Exception e) {
			logger.error("Failed to discover built-in skills on classpath", e);
		}
	}

	@Transactional
	public void learnGlobalSkillFromMarkdown(String content, String source) {
		Map<String, String> metadata = parseFrontmatter(content);
		if (metadata.containsKey("name")) {
			String name = metadata.get("name");
			String description = metadata.getOrDefault("description", "");

			Skill skill = skillRepository.findByProjectIdIsNullAndName(name).orElse(new Skill());

			skill.setProject(null); // Global skill
			skill.setName(name);
			skill.setDescription(description);
			skill.setContent(content);
			skill.setSource(source);

			skillRepository.save(skill);
			logger.info("Learned/Updated global skill: {} from source: {}", name, source);
		} else {
			logger.debug("Markdown file at {} does not contain valid skill frontmatter.", source);
		}
	}

	@Transactional
	public void learnFromUrl(Long projectId, String urlStr) throws IOException {
		logger.info("Learning skill from URL/path: {} for project: {}", urlStr, projectId);
		if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
			URI uri = URI.create(urlStr);
			HttpClient client = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
			try {
				HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() == 200) {
					learnSkillFromMarkdown(projectId, response.body(), urlStr);
				} else {
					throw new IOException("Failed to fetch skill from URL: HTTP " + response.statusCode());
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("Interrupted while fetching skill", e);
			}
		} else {
			learnFromFile(projectId, urlStr);
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

			Skill skill = skillRepository.findByProjectIdAndName(projectId, name).orElse(new Skill());

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
		Pattern pattern = Pattern.compile("(?s)^---\\s*\\n(.*?)\\n---\\s*(\\n|$)");
		Matcher matcher = pattern.matcher(content);
		if (matcher.find()) {
			String yaml = matcher.group(1);
			String currentKey = null;
			StringBuilder currentValue = new StringBuilder();

			for (String line : yaml.split("\\r?\\n")) {
				if (line.trim().isEmpty() || line.trim().startsWith("#"))
					continue;

				if (!line.startsWith(" ") && !line.startsWith("\t") && line.contains(":")) {
					if (currentKey != null) {
						metadata.put(currentKey, currentValue.toString().trim());
					}
					String[] parts = line.split(":", 2);
					currentKey = parts[0].trim();
					String val = parts[1].trim();
					if (val.equals(">") || val.equals("|")) {
						currentValue = new StringBuilder();
					} else {
						currentValue = new StringBuilder(val.replaceAll("^['\"]|['\"]$", ""));
					}
				} else if (currentKey != null) {
					if (!currentValue.isEmpty() && !currentValue.toString().endsWith("\n")) {
						currentValue.append(" ");
					}
					currentValue.append(line.trim());
				}
			}
			if (currentKey != null) {
				metadata.put(currentKey, currentValue.toString().trim());
			}
		}
		return metadata;
	}
}
