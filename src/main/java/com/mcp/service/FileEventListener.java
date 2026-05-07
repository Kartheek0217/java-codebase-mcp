package com.mcp.service;

import com.mcp.model.FileEvent;

public interface FileEventListener {
    void onFileEvent(FileEvent event);
}
