package com.colonelclustered.burp.tokenizers;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple tokenizer for JSON content that extracts string and number values.
 */
public class JsonTokenizer implements Tokenizer {
    // Regex to find string values (in quotes) and numeric values (not in quotes)
    private static final Pattern JSON_VALUES_PATTERN = Pattern.compile(": *(\"[^\"]*\"|\\d+(\\.\\d+)?)");

    @Override
    public Set<String> tokenize(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        Set<String> tokens = new HashSet<>();
        Matcher matcher = JSON_VALUES_PATTERN.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1).replace("\"", ""); // Remove quotes from strings
            tokens.add(value);
        }
        return tokens;
    }
}
