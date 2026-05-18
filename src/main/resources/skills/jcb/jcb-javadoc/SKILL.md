---
name: jcb-javadoc
description: >
  Ultra-compressed Javadoc generator for Spring projects. Creates concise method-level Javadocs matching exact project templates for service, repository, and resource packages.
---

# JCB Javadoc Generator

Generate terse, method-level Javadocs matching strict package templates. Eliminate prose bloat.

## Templates

### 1. Service / Repository Packages
```java
/**
 * @implNote <terse 1-line explanation of method action>
 * @param <param1>, <param2>
 * @return <ReturnType>
 * @exception <ExceptionType>
 * @author <authorName>
 */
```

### 2. Resource (Controller) Package
```java
/**
 * {@code <HTTP_METHOD> <endpoint>} : <Terse endpoint summary>.
 * 
 * @implNote <concise note on saved/returned entity>
 * @param <param1>, <param2>
 * @return <ResponseObject description>
 * @author <authorName>
 */
```

## JCB Tools Integration
- Locate targets: `mcp_jcb_search-symbols`, `mcp_jcb_search-files`.
- Inspect context: `mcp_jcb_get-file-context`.
- Stage updates: `mcp_jcb_stage-files`.

## Boundaries
- Output Javadoc block ready to paste.
- Never include multi-line generic paragraphs, HTML tags (`<p>`), or AI disclaimers.
- Revert to standard Javadoc if user says `stop jcb-javadoc`.
