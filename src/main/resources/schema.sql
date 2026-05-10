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
-- Project Rules (similar to Skills but for constraints)
CREATE TABLE IF NOT EXISTS project_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    rule_value VARCHAR(1000) NOT NULL,
    category VARCHAR(100),
    description VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- Task Management with steps
CREATE TABLE IF NOT EXISTS project_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'TODO',
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS task_steps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    step_number INT NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'TODO',
    completed_at TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES project_tasks(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_rules_project ON project_rules(project_id);
CREATE INDEX IF NOT EXISTS idx_rules_category ON project_rules(project_id, category);
CREATE INDEX IF NOT EXISTS idx_tasks_project ON project_tasks(project_id, status);
CREATE INDEX IF NOT EXISTS idx_steps_task ON task_steps(task_id, step_number);

-- Symbol Calls (Hierarchy)
CREATE TABLE IF NOT EXISTS symbol_calls (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    caller_id BIGINT NOT NULL,
    caller_file_path VARCHAR(512),
    callee_name VARCHAR(255) NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (caller_id) REFERENCES symbols(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_symbol_calls_caller ON symbol_calls(caller_id);
CREATE INDEX IF NOT EXISTS idx_symbol_calls_callee ON symbol_calls(project_id, callee_name);
CREATE INDEX IF NOT EXISTS idx_symbol_calls_file ON symbol_calls(project_id, caller_file_path);

-- Browser Session tracking
CREATE TABLE IF NOT EXISTS browser_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL UNIQUE,
    project_id BIGINT,
    browser_type VARCHAR(20) DEFAULT 'chromium',
    headless BOOLEAN DEFAULT TRUE,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    current_url VARCHAR(2048),
    viewport_width INT DEFAULT 1280,
    viewport_height INT DEFAULT 720,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_active TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_browser_session_id ON browser_session(session_id);
CREATE INDEX IF NOT EXISTS idx_browser_session_status ON browser_session(status);
