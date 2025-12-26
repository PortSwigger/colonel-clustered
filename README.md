# Colonel Clustered

**A Burp Suite extension for clustering HTTP responses to find outliers.**

## The Problem

When running automated attacks with tools like Burp Intruder, you often get thousands of results. Sifting through these results can be a "needle in a haystack" problem. Traditional sorting by response length, status code, or timing can help, but it often fails to identify subtle but significant differences in the *content* of the responses. You might have hundreds of responses that are the exact same size but contain slightly different error messages, tokens, or data, which are critical to discover.

Colonel Clustered solves this by analyzing the entire content of every response and grouping similar ones together, allowing you to instantly spot the outliers.

## How It Works

Colonel Clustered uses a high-performance, multi-stage hybrid algorithm to provide fast and accurate clustering without requiring any manual tuning.

1.  **Content-Aware Tokenization**: The extension first inspects the `Content-Type` header of each response to apply the most intelligent tokenization strategy:
    -   **HTML**: Strips all tags and scripts, then tokenizes the visible text content.
    -   **JSON**: Extracts all string and numeric values as tokens.
    -   **Text**: Performs a generic split on non-alphanumeric characters (whitespace, punctuation).
    -   **Binary/Other**: If the content is not text-based, it generates a set of 5-byte n-grams to find similarities in the binary data.

2.  **High-Performance Pre-Grouping**: To remain fast even with thousands of responses, the extension performs a single pass to group all *perfectly identical* responses. It calculates a hash of each response's token set and groups all items that share the same hash. This means the expensive clustering algorithm only has to run on the much smaller set of *unique* response bodies.

3.  **Self-Tuning Hierarchical Clustering**: The set of unique responses is then clustered using a custom agglomerative hierarchical algorithm:
    -   First, a similarity matrix is built by calculating the Jaccard distance between every pair of unique responses.
    -   The algorithm then iteratively merges the most similar clusters. It records the distance at which each merge occurs.
    -   **Automatic Threshold Detection**: To find the "best" number of clusters, the algorithm analyzes the list of merge distances and finds the largest "jump" or "elbow." This point represents the most natural place to stop merging, automatically adapting the clustering granularity to the dataset.
    -   **Needle-in-a-Haystack Detection**: A special rule handles cases with only two highly dissimilar unique responses, ensuring that clear outliers are always separated.

4.  **Outlier Consolidation**: After the best clustering is determined, any resulting cluster containing only a single unique member is considered an outlier. All such outliers are then consolidated into a single, convenient "Outliers" group in the UI.

This hybrid approach provides the best of all worlds: the speed of a single-pass algorithm, the intelligence of content-aware analysis, and the power of a self-tuning hierarchical clustering model to find a natural and useful balance of clusters for any given dataset.

## How to Use

1.  **Load the Extension**:
    - Go to the **Extensions** tab in Burp Suite.
    - Click **Add** and select the `ColonelClustered.jar` file.
    - A new tab named "Col. Clustered" should appear.

2.  **Send Responses for Analysis**:
    - Go to any tool in Burp, such as Intruder results or Proxy history.
    - Select one or more request/response items.
    - Right-click and select **"Send to Colonel Clustered"**.

3.  **Analyze the Results in the Quad-Pane UI**:
    - The "Colonel Clustered" tab uses a powerful four-pane layout to help you quickly navigate results.
    - **Top-Left (Clusters)**: This pane shows a high-level list of all clusters found, including a special "Outliers" group. Each entry shows the number of items in that cluster.
    - **Bottom-Left (Cluster Contents)**: Click on a cluster in the pane above to see all of its members displayed in this table. The table features several columns:
        - `Request/Response Pair`: The original index of the item.
        - `Status Code`: The HTTP response status code.
        - `Length`: The length of the response body in bytes.
        - `Content-Type`: The `Content-Type` header of the response.
    - **Sorting**: Click on any column header in the table to sort the items within that cluster, allowing you to easily find the largest/smallest responses, or group by status code.
    - **Top-Right & Bottom-Right (Viewers)**: Select any row in the table to view its full request and response in the viewers on the right.

## Screenshots

*(Note: The screenshots below are from an older version and do not reflect the current quad-pane UI.)*
<img width="1242" height="725" alt="Screenshot 2025-12-24 8 14 13 PM" src="https://github.com/user-attachments/assets/4aaae005-2c12-4166-b186-6da76a49a3b4" />


<img width="1172" height="879" alt="Screenshot 2025-12-24 8 11 49 PM" src="https://github.com/user-attachments/assets/54d8fda2-bc23-4956-86d2-62565c4132f9" />

<img width="1168" height="871" alt="Screenshot 2025-12-24 8 12 55 PM" src="https://github.com/user-attachments/assets/04919421-f519-4a56-ac57-4cf8b87bac7c" />


## Building from Source

This project uses Gradle.

1.  Clone the repository:
    ```bash
    git clone <repository-url>
    cd ColonelClustered
    ```
2.  Build the fat JAR:
    ```bash
    ./gradlew shadowJar
    ```
3.  The compiled JAR will be located at `build/libs/ColonelClustered.jar`.

## Author & Credits

-   **Author**: Drew Kirkpatrick
    -   **Twitter**: @hoodoer
    -   **Email**: hoodoer@bitwisemunitions.dev

## License

This project is released into the public domain under the Unlicense. See the `LICENSE` file for details.
