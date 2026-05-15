package com.mcp.entity;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "projects")
public class Project {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "project_seq")
	@SequenceGenerator(name = "project_seq", sequenceName = "project_seq", allocationSize = 50)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(name = "root_path", nullable = false)
	private String rootPath;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private com.mcp.model.ProjectStatus status = com.mcp.model.ProjectStatus.INITIALIZING;

	public Project() {
	}

	public Project(String name, String rootPath) {
		this.name = name;
		this.rootPath = rootPath;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getRootPath() {
		return rootPath;
	}

	public void setRootPath(String rootPath) {
		this.rootPath = rootPath;
	}

	public com.mcp.model.ProjectStatus getStatus() {
		return status;
	}

	public void setStatus(com.mcp.model.ProjectStatus status) {
		this.status = status;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Project project = (Project) o;
		return Objects.equals(id, project.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
