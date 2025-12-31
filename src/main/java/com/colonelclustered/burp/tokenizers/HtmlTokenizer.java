package com.colonelclustered.burp.tokenizers;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * A tokenizer for HTML. It first strips script/style/HTML tags, then
 * generates character-based n-grams on the remaining visible text. This makes
 * it resilient to small changes (like IDs) in otherwise similar content.
 */
public class HtmlTokenizer implements Tokenizer {
    private static final int NGRAM_SIZE = 5;

    @Override
    public Set<String> tokenize(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8).toLowerCase();
        
        // 1. Remove script and style blocks entirely
        text = text.replaceAll("<(script|style)[^>]*>.*?</\\1>", " ");
        
        // 2. Remove all other HTML tags, leaving their content
        text = text.replaceAll("<[^>]*>", " ");
        
        // 3. Remove digits to handle template variations
        text = text.replaceAll("\\d+", " ");
        
        // 4. Collapse whitespace and generate n-grams on the remaining text
        String visibleText = text.replaceAll("\\s+", " ").trim();
        
        Set<String> ngrams = new HashSet<>();
        if (visibleText.length() < NGRAM_SIZE) {
            if (!visibleText.isEmpty()) {
                ngrams.add(visibleText);
            }
            return ngrams;
        }

        for (int i = 0; i <= visibleText.length() - NGRAM_SIZE; i++) {
            ngrams.add(visibleText.substring(i, i + NGRAM_SIZE));
        }
        return ngrams;
    }
}