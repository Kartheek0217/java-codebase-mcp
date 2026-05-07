DROP TABLE IF EXISTS file_metadata;
DROP TABLE IF EXISTS symbols;
DROP TABLE IF EXISTS projects;

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
    last_scanned TIMESTAMP,
    PRIMARY KEY (project_id, file_path),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);
