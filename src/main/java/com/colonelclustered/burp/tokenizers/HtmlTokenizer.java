package com.colonelclustered.burp.tokenizers;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A simple tokenizer for HTML that strips tags and tokenizes the remaining text.
 */
public class HtmlTokenizer implements Tokenizer {
    @Override
    public Set<String> tokenize(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8).toLowerCase();
        // 1. Remove script and style blocks
        text = text.replaceAll("<(script|style).*?</\\1>", " ");
        // 2. Remove all other HTML tags
        text = text.replaceAll("<[^>]*>", " ");
        // 3. Tokenize the remaining text
        return Arrays.stream(text.split("\\W+"))
                     .filter(s -> !s.trim().isEmpty())
                     .collect(Collectors.toSet());
    }
}
