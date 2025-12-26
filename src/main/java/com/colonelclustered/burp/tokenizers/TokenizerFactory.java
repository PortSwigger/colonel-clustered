package com.colonelclustered.burp.tokenizers;

import burp.api.montoya.http.message.responses.HttpResponse;

/**
 * Factory class to provide the appropriate tokenizer based on the Content-Type header.
 */
public class TokenizerFactory {
    private final Tokenizer textTokenizer = new TextTokenizer();
    private final Tokenizer jsonTokenizer = new JsonTokenizer();
    private final Tokenizer htmlTokenizer = new HtmlTokenizer();
    private final Tokenizer binaryTokenizer = new BinaryTokenizer(5); // 5-byte n-grams

    public Tokenizer getTokenizer(HttpResponse response) {
        String contentType = null;
        for (burp.api.montoya.http.message.HttpHeader header : response.headers()) {
            if ("Content-Type".equalsIgnoreCase(header.name())) {
                contentType = header.value();
                break;
            }
        }

        if (contentType == null) {
            return textTokenizer; // Default to text if header is missing
        }

        String lowerContentType = contentType.toLowerCase();

        if (lowerContentType.contains("html")) {
            return htmlTokenizer;
        } else if (lowerContentType.contains("json")) {
            return jsonTokenizer;
        } else if (lowerContentType.startsWith("text/")) {
            return textTokenizer;
        } else {
            // Fallback for everything else (e.g., images, applications, etc.)
            return binaryTokenizer;
        }
    }
}
