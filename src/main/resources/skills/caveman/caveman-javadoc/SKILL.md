---
name: caveman-javadoc
description: >
  Ultra-compressed Javadoc generator. Creates concise method-level Javadocs matching exact
  project templates for service, serviceImpl, repository, and resource packages. Cuts Javadoc
  bloat while preserving exact tags (@implNote, @param, @return, @exception, @author).
  Use when user says "generate javadocs", "add javadoc", "/javadoc", or invokes /caveman-javadoc.
---

Generate method-level Javadocs terse and exact. Use strict project templates based on package. No fluff.

## Rules

**Package matching & templates:**

1. **service, service.impl, repository packages:**
```java
/**
 * @implNote <terse explanation of what method does>
 * @param <param1>, <param2>
 * @return <ReturnType>
 * @exception <ExceptionType>
 * @author <authorName>
 */
```
- `@implNote`: 1-line exact summary of method action (e.g. "find ROI for overdraft application").
- `@param`: comma-separated parameter names on single line if multiple.
- `@return`: exact return type name or terse description.
- `@exception`: exact exception class name if thrown.
- `@author`: author identifier (e.g., karthik.j or as specified in project).

2. **resource package:**
```java
/**
 * {@code <HTTP_METHOD> <endpoint>} : <Terse summary of endpoint>.
 * 
 * @implNote: <terse implementation note or return description>
 * @param <param1>, <param2>
 * @return <ReturnType or ResponseObject>
 * @author <authorName>
 */
```
- First line: HTTP method and route inside `{@code ...}` followed by terse summary.
- `@implNote:`: concise note on what response object is saved or returned.
- `@param`: comma-separated list of request parameters/headers/DTOs.
- `@return`: return object description.
- `@author`: author identifier.

**What NEVER goes in:**
- Multi-line paragraphs of generic filler text.
- HTML formatting tags like `<p>` or `<ul>` unless strictly needed.
- AI attribution or auto-generated disclaimers.

## Examples

**Service / ServiceImpl / Repository Method:**
```java
/**
 * @implNote calculate penalty interest for overdue loan
 * @param loanId, daysOverdue
 * @return PenaltyCalculationDTO
 * @exception OverDraftBusinessException
 * @author karthik.j
 */
public PenaltyCalculationDTO calculatePenalty(Long loanId, int daysOverdue) throws OverDraftBusinessException { ... }
```

**Resource Endpoint Method:**
```java
/**
 * {@code POST  /apply} : Submit a new OverdraftApplicationDTO.
 * 
 * @implNote: save the response object by
 *            "OverdraftApplicationDTO".
 * @param userId, authToken, OverdraftApplicationDTO.
 * @return the ResponseObject
 * @author karthik.j
 */
@PostMapping("/apply")
public ResponseEntity<ResponseObject> applyOverdraft(@RequestHeader("userId") String userId, ...) { ... }
```

## Boundaries

Output the generated Javadoc block ready to paste above target methods. Does not modify files unless user asks to apply directly. "stop caveman-javadoc" or "normal mode": revert to standard Javadoc style.
