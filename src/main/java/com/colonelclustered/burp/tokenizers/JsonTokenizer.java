package com.colonelclustered.burp.tokenizers;

import burp.api.montoya.MontoyaApi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * A structure-aware tokenizer for JSON content that extracts keys and paths, ignoring values.
 */
public class JsonTokenizer implements Tokenizer {
    private MontoyaApi api;

    public JsonTokenizer(MontoyaApi api) {
        this.api = api;
    }

    @Override
    public Set<String> tokenize(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        Set<String> tokens = new HashSet<>();
        try {
            // Handle cases where the root is a JSONArray
            if (text.trim().startsWith("[")) {
                JSONArray jsonArray = new JSONArray(text);
                if (!jsonArray.isEmpty()) {
                    // Tokenize the structure of all elements in the array
                    for (int i = 0; i < jsonArray.length(); i++) {
                        Object element = jsonArray.get(i);
                        if (element instanceof JSONObject) {
                            tokenizeJsonObject(null, (JSONObject) element, tokens);
                        } else if (element instanceof JSONArray) {
                            tokenizeJsonArray(null, (JSONArray) element, tokens);
                        }
                    }
                }
            } else { // Handle cases where the root is a JSONObject
                JSONObject jsonObject = new JSONObject(text);
                tokenizeJsonObject(null, jsonObject, tokens);
            }
        } catch (JSONException e) {
            // If JSON parsing fails, fall back to a simple text tokenizer
            return new TextTokenizer().tokenize(content);
        }
        return tokens;
    }

    private void tokenizeJsonObject(String prefix, JSONObject jsonObject, Set<String> tokens) {
        for (String key : jsonObject.keySet()) {
            String currentPath = (prefix == null) ? key : prefix + "." + key;
            tokens.add(currentPath);

            Object value = jsonObject.get(key);
            if (value instanceof JSONObject) {
                tokenizeJsonObject(currentPath, (JSONObject) value, tokens);
            } else if (value instanceof JSONArray) {
                tokenizeJsonArray(currentPath, (JSONArray) value, tokens);
            }
        }
    }

    private void tokenizeJsonArray(String prefix, JSONArray jsonArray, Set<String> tokens) {
        for (int i = 0; i < jsonArray.length(); i++) {
            Object element = jsonArray.get(i);
            if (element instanceof JSONObject) {
                tokenizeJsonObject(prefix, (JSONObject) element, tokens);
            } else if (element instanceof JSONArray) {
                 tokenizeJsonArray(prefix, (JSONArray) element, tokens);
            }
        }
    }
}
