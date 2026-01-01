package com.colonelclustered.burp.tokenizers;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.responses.HttpResponse;

/**
 * Factory class to provide the appropriate tokenizer based on the Content-Type header.
 */
public class TokenizerFactory {
    private final MontoyaApi api;
    private final Tokenizer textTokenizer = new TextTokenizer();
    private final Tokenizer jsonTokenizer;
    private final Tokenizer htmlTokenizer = new HtmlTokenizer();
    private final Tokenizer binaryTokenizer = new BinaryTokenizer(5); // 5-byte n-grams

    public TokenizerFactory(MontoyaApi api) {
        this.api = api;
        this.jsonTokenizer = new JsonTokenizer(api);
    }

    public Tokenizer getTokenizer(HttpResponse response) {
        String contentType = null;
        for (burp.api.montoya.http.message.HttpHeader header : response.headers()) {
            if ("Content-Type".equalsIgnoreCase(header.name())) {
                contentType = header.value();
                break;
            }
        }

        Tokenizer selectedTokenizer;
        if (contentType == null) {
            selectedTokenizer = textTokenizer; // Default to text if header is missing
        } else {
            String lowerContentType = contentType.toLowerCase();

            if (lowerContentType.contains("html")) {
                selectedTokenizer = htmlTokenizer;
            } else if (lowerContentType.contains("json")) {
                selectedTokenizer = jsonTokenizer;
            } else if (lowerContentType.startsWith("text/")) {
                selectedTokenizer = textTokenizer;
            } else {
                // Fallback for everything else (e.g., images, applications, etc.)
                selectedTokenizer = binaryTokenizer;
            }
        }
        
        return selectedTokenizer;
    }
}
