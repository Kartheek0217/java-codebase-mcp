package com.mcp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "symbols", indexes = {
    @Index(name = "idx_symbol_project_name", columnList = "project_id, name"),
    @Index(name = "idx_symbol_project_path", columnList = "project_id, file_path")
})
public class Symbol {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "symbol_seq")
	@SequenceGenerator(name = "symbol_seq", sequenceName = "symbol_seq", allocationSize = 50)
	private Long id;

	@Column(name = "project_id", nullable = false)
	private Long projectId;

	private String name;
	@Enumerated(EnumType.STRING)
	private SymbolType type;
	private String filePath;
	private LocalDateTime lastModified;

	// Fix E: Rich metadata fields — critical for AI tools to navigate without re-reading whole files
	private Integer lineNumber;

	@Column(length = 1024)
	private String signature;     // e.g. "public ResponseEntity<ContextDTO> getFileContext(Long, String, ...)"

	@Column(length = 256)
	private String returnType;    // e.g. "ResponseEntity<ContextDTO>"

	@Column(length = 128)
	private String modifiers;     // e.g. "public static final"

	// Fix F: Annotation metadata — captures @RestController, @Service, @GetMapping, etc.
	@Column(length = 512)
	private String annotations;   // e.g. "@GetMapping @Cacheable @Transactional"

	// Getters and Setters
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getProjectId() {
		return projectId;
	}

	public void setProjectId(Long projectId) {
		this.projectId = projectId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public SymbolType getType() {
		return type;
	}

	public void setType(SymbolType type) {
		this.type = type;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public LocalDateTime getLastModified() {
		return lastModified;
	}

	public void setLastModified(LocalDateTime lastModified) {
		this.lastModified = lastModified;
	}

	public Integer getLineNumber() {
		return lineNumber;
	}

	public void setLineNumber(Integer lineNumber) {
		this.lineNumber = lineNumber;
	}

	public String getSignature() {
		return signature;
	}

	public void setSignature(String signature) {
		this.signature = signature;
	}

	public String getReturnType() {
		return returnType;
	}

	public void setReturnType(String returnType) {
		this.returnType = returnType;
	}

	public String getModifiers() {
		return modifiers;
	}

	public void setModifiers(String modifiers) {
		this.modifiers = modifiers;
	}

	public String getAnnotations() {
		return annotations;
	}

	public void setAnnotations(String annotations) {
		this.annotations = annotations;
	}
}
