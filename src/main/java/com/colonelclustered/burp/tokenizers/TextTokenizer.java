package com.colonelclustered.burp.tokenizers;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * A generic tokenizer for plain text content. Uses character-based n-grams
 * to be resilient to small changes in otherwise similar responses.
 */
public class TextTokenizer implements Tokenizer {
    private static final int NGRAM_SIZE = 5;

    @Override
    public Set<String> tokenize(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8).toLowerCase();

        // Remove digits to handle template variations
        text = text.replaceAll("\\d+", " ");

        Set<String> ngrams = new HashSet<>();
        if (text.length() < NGRAM_SIZE) {
            ngrams.add(text);
            return ngrams;
        }

        for (int i = 0; i <= text.length() - NGRAM_SIZE; i++) {
            ngrams.add(text.substring(i, i + NGRAM_SIZE));
        }
        return ngrams;
    }
}