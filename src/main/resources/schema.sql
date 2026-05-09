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

-- Crawl jobs tracking
CREATE TABLE IF NOT EXISTS crawl_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT,
    start_url VARCHAR(2048) NOT NULL,
    max_depth INT DEFAULT 3,
    max_pages INT DEFAULT 10,
    delay_ms INT DEFAULT 1000,
    respect_robots BOOLEAN DEFAULT TRUE,
    status VARCHAR(20) DEFAULT 'PENDING',
    pages_crawled INT DEFAULT 0,
    include_pattern VARCHAR(2048),
    exclude_pattern VARCHAR(2048),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- Crawled page metadata
CREATE TABLE IF NOT EXISTS crawled_page (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    crawl_job_id BIGINT,
    project_id BIGINT,
    url VARCHAR(2048) NOT NULL,
    title VARCHAR(500),
    mime_type VARCHAR(100),
    content_length BIGINT DEFAULT 0,
    status_code INT DEFAULT 200,
    checksum VARCHAR(64),
    raw_content CLOB,
    crawled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (crawl_job_id) REFERENCES crawl_job(id) ON DELETE CASCADE,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_crawl_job_status ON crawl_job(status);
CREATE INDEX IF NOT EXISTS idx_crawled_page_url ON crawled_page(url);
CREATE INDEX IF NOT EXISTS idx_crawled_page_project ON crawled_page(project_id);
