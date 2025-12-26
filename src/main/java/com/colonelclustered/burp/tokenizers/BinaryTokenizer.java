package com.colonelclustered.burp.tokenizers;

import java.util.HashSet;
import java.util.Set;

/**
 * A tokenizer for binary content that creates n-grams from the byte values.
 */
public class BinaryTokenizer implements Tokenizer {
    private final int n; // n-gram size

    public BinaryTokenizer(int n) {
        this.n = n;
    }

    @Override
    public Set<String> tokenize(byte[] content) {
        Set<String> ngrams = new HashSet<>();
        if (content.length < n) {
            return ngrams;
        }
        for (int i = 0; i < content.length - n + 1; i++) {
            StringBuilder ngram = new StringBuilder();
            for (int j = 0; j < n; j++) {
                ngram.append(String.format("%02X", content[i+j])); // Hex representation of byte
            }
            ngrams.add(ngram.toString());
        }
        return ngrams;
    }
}
