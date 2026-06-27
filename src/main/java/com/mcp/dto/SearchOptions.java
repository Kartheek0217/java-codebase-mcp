package com.mcp.dto;
import java.util.Set;

/**
 * Options for Lucene full-text search, replacing the 6 overloaded
 * {@code searchContent} variants in {@code LuceneIndexService}.
 *
 * <p>Build via the fluent builder:
 * <pre>{@code
 * SearchOptions opts = SearchOptions.builder()
 *     .query("foo")
 *     .type("java")
 *     .limit(20)
 *     .build();
 * }</pre>
 *
 * @param query     Required — Lucene query string.
 * @param type      Optional — filter by document type (e.g. {@code "code"}, {@code "web"}).
 * @param site      Optional — filter by domain / path fragment (web documents).
 * @param filePaths Optional — restrict search to this set of absolute file paths.
 * @param limit     Maximum number of results to return (default 10).
 * @param offset    Result offset for pagination (default 0).
 */
public record SearchOptions(
        String query,
        String type,
        String site,
        Set<String> filePaths,
        int limit,
        int offset) {

    /** Default limit when no limit is specified. */
    public static final int DEFAULT_LIMIT = 10;

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String query;
        private String type;
        private String site;
        private Set<String> filePaths;
        private int limit = DEFAULT_LIMIT;
        private int offset = 0;

        private Builder() {}

        public Builder query(String query)                        { this.query = query;         return this; }
        public Builder type(String type)                          { this.type = type;           return this; }
        public Builder site(String site)                          { this.site = site;           return this; }
        public Builder filePaths(Set<String> filePaths) { this.filePaths = filePaths; return this; }
        public Builder limit(int limit)                           { this.limit = limit;         return this; }
        public Builder offset(int offset)                         { this.offset = offset;       return this; }

        public SearchOptions build() {
            if (query == null || query.isBlank())
                throw new IllegalArgumentException("SearchOptions.query must not be blank");
            return new SearchOptions(query, type, site, filePaths, limit, offset);
        }
    }
}
