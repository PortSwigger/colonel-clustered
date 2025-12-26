package com.colonelclustered.burp.tokenizers;

import java.util.Set;

/**
 * Interface for tokenizing HTTP response bodies.
 */
public interface Tokenizer {
    Set<String> tokenize(byte[] content);
}
