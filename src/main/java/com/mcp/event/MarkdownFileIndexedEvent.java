package com.mcp.event;

import org.springframework.context.ApplicationEvent;

/**
 * Published by {@link com.mcp.service.FileIndexerService} when a Markdown file
 * is successfully read during indexing.
 *
 * <p>{@link com.mcp.service.SkillService} listens to this event and extracts
 * skill definitions from the markdown content, decoupling the two services.
 */
public class MarkdownFileIndexedEvent extends ApplicationEvent {

    private final Long projectId;
    private final String content;
    private final String filePath;

    /**
     * @param source    The originating object (usually the FileIndexerService bean)
     * @param projectId Project the file belongs to
     * @param content   Raw markdown content
     * @param filePath  Absolute file path
     */
    public MarkdownFileIndexedEvent(Object source, Long projectId, String content, String filePath) {
        super(source);
        this.projectId = projectId;
        this.content = content;
        this.filePath = filePath;
    }

    public Long getProjectId() { return projectId; }
    public String getContent()  { return content; }
    public String getFilePath() { return filePath; }
}
