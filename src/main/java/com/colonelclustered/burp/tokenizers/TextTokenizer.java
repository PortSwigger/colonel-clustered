package com.colonelclustered.burp.tokenizers;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A generic tokenizer for plain text content. Splits by non-alphanumeric characters.
 */
public class TextTokenizer implements Tokenizer {
    @Override
    public Set<String> tokenize(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8).toLowerCase();
        return Arrays.stream(text.split("\\W+"))
                     .filter(s -> !s.isEmpty())
                     .collect(Collectors.toSet());
    }
}
