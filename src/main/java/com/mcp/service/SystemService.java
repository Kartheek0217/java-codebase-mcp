package com.mcp.service;

import org.springframework.stereotype.Service;

import com.mcp.dto.SystemStatusDTO;
import com.mcp.properties.AgentProperties;
import com.mcp.repository.ProjectRepository;

@Service
public class SystemService {

    private final ProjectRepository projectRepository;
    private final GitInfoService gitInfoService;
    private final AgentClient agentClient;
    private final AgentProperties agentProperties;

    public SystemService(ProjectRepository projectRepository,
                         GitInfoService gitInfoService,
                         AgentClient agentClient,
                         AgentProperties agentProperties) {
        this.projectRepository = projectRepository;
        this.gitInfoService = gitInfoService;
        this.agentClient = agentClient;
        this.agentProperties = agentProperties;
    }

    public SystemStatusDTO getHealthStatus() {
        try {
            projectRepository.count();
            return new SystemStatusDTO("UP", "connected", null, null, null, null, null, null, null, null);
        } catch (Exception e) {
            return new SystemStatusDTO("DEGRADED", "disconnected", null, null, null, null, null, null, null, null);
        }
    }

    public SystemStatusDTO getInfoStatus() {
        return new SystemStatusDTO(null, null,
                gitInfoService.getCommitHash(),
                gitInfoService.getBranchName(),
                gitInfoService.isGitAvailable(),
                null, null, null, null, null);
    }

    public SystemStatusDTO getAgentStatus() {
        return new SystemStatusDTO(null, null, null, null, null,
                agentProperties.getBaseUrl(),
                agentProperties.getDefaultModel(),
                agentProperties.getTimeoutSeconds(),
                agentProperties.getMaxTokens(),
                agentClient.isReachable());
    }
}
