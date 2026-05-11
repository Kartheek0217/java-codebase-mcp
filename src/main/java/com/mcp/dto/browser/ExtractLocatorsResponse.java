package com.mcp.dto.browser;

import java.util.List;

public record ExtractLocatorsResponse(
        List<LocatorInfo> locators) {
}
