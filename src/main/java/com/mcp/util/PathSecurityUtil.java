package com.mcp.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathSecurityUtil {

    /**
     * Validates that the given target path resolves to a path within the root path.
     * Prevents directory traversal attacks, including symlink attacks.
     *
     * @param rootPath the allowed root directory
     * @param targetPath the target file path
     * @return the normalized absolute path of the target
     * @throws ResponseStatusException if the path is invalid or outside the root
     */
    public static Path validateAndNormalizePath(String rootPath, String targetPath) {
        if (rootPath == null || rootPath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid root path configuration");
        }
        if (targetPath == null || targetPath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File path is required");
        }

        try {
            Path root = Paths.get(rootPath).toAbsolutePath().normalize();
            if (root.toFile().exists()) {
                root = root.toRealPath();
            }
            Path target = root.resolve(targetPath).toAbsolutePath().normalize();
            
            if (target.toFile().exists()) {
                 target = target.toRealPath();
            }

            if (!target.startsWith(root)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path: path traversal detected");
            }
            
            return target;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path or IO error", e);
        }
    }
}
