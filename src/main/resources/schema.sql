CREATE TABLE IF NOT EXISTS projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    root_path VARCHAR(1024) NOT NULL
);

CREATE TABLE IF NOT EXISTS symbols (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50),
    file_path VARCHAR(1024),
    last_modified TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS file_metadata (
    project_id BIGINT NOT NULL,
    file_path VARCHAR(1024) NOT NULL,
    checksum VARCHAR(64),
    file_size BIGINT,
    last_scanned TIMESTAMP,
    PRIMARY KEY (project_id, file_path),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    content TEXT,
    source VARCHAR(1024),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_file_metadata_file_size ON file_metadata(file_size);
CREATE INDEX IF NOT EXISTS idx_file_metadata_project_path ON file_metadata(project_id, file_path);
CREATE INDEX IF NOT EXISTS idx_symbols_project_name ON symbols(project_id, name);
CREATE INDEX IF NOT EXISTS idx_skills_project_name ON skills(project_id, name);
