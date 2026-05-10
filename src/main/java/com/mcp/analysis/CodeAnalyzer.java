package com.mcp.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.util.CharTokenizer;

/**
 * Custom analyzer for source code that preserves underscores and dots.
 */
public class CodeAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        final Tokenizer source = new CodeTokenizer();
        TokenStream result = new LowerCaseFilter(source);
        return new TokenStreamComponents(source, result);
    }

    private static class CodeTokenizer extends CharTokenizer {
        @Override
        protected boolean isTokenChar(int c) {
            // Keep alphanumeric, underscore, and dots to preserve identifiers
            return Character.isLetterOrDigit(c) || c == '_' || c == '.';
        }
    }
}
