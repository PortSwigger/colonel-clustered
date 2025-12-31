# Colonel Clustered

**A Burp Suite extension for clustering HTTP responses to find outliers.**

## The Problem

When running automated attacks with tools like Burp Intruder, you often get thousands of results. Sifting through these results can be a "needle in a haystack" problem. Traditional sorting by response length, status code, or timing can help, but it often fails to identify subtle but significant differences in the *content* of the responses. You might have hundreds of responses that are the exact same size but contain slightly different error messages, tokens, or data, which are critical to discover.

Colonel Clustered solves this by analyzing the entire content of every response and grouping similar ones together, allowing you to instantly spot the outliers.

## How It Works

Colonel Clustered now uses a high-performance, **dual-algorithm approach** to provide fast, accurate, and flexible clustering without requiring any manual tuning.

1.  **Content-Aware Tokenization**: The extension first inspects the `Content-Type` header of each response to apply the most intelligent tokenization strategy:
    -   **HTML**: Strips all tags and scripts, then generates character-based 5-grams on the visible text. This includes sanitizing digits to ensure resilience to minor variations (like IDs) in templated content.
    -   **JSON**: Extracts all string and numeric values as tokens.
    -   **Text**: Generates character-based 5-grams on plain text content, also sanitizing digits for template resilience.
    -   **Binary/Other**: If the content is not text-based, it generates a set of 5-byte n-grams to find similarities in the binary data.

2.  **High-Performance Pre-Grouping**: To remain fast even with thousands of responses, the extension performs a single pass to group all *perfectly identical* responses. It calculates a hash of each response's token set and groups all items that share the same hash. This means the expensive clustering algorithm only has to run on the much smaller set of *unique* response bodies.

3.  **Dual Clustering Algorithms**: Colonel Clustered offers two distinct clustering algorithms:

    *   **Fast Scan (Default)**: A high-performance `DBSCAN`-based algorithm runs automatically.
        -   **Automatic Epsilon Tuning**: It uses the Kneedle algorithm to automatically determine the optimal `epsilon` (density radius), adapting to the dataset's characteristics.
        -   **Outlier Detection (minPts=2)**: `minPts` is fixed at 2, making it highly effective for identifying responses that are unique or share similarity with only one other item, ensuring sensitive outlier detection.

    *   **Deep Analysis (Manual Trigger)**: The original, more computationally intensive hierarchical clustering algorithm is available via a "Deep Analysis" button. This option is designed for scenarios requiring a more granular and potentially different clustering perspective.
        -   It constructs a similarity matrix using Jaccard distance between unique responses.
        -   It iteratively merges the most similar clusters, recording merge distances to determine optimal thresholds.

4.  **Outlier Consolidation**: After clustering, any resulting group containing only a single unique member is considered an outlier. All such outliers are then consolidated into a single, convenient "Outliers" group in the UI.

This hybrid approach, with intelligent tokenization and dual clustering strategies, provides powerful and flexible outlier detection for various security testing scenarios.

## How to Use

1.  **Load the Extension**:
    - Go to the **Extensions** tab in Burp Suite.
    - Click **Add** and select the `ColonelClustered.jar` file.
    - A new tab named "Col. Clustered" should appear.

2.  **Send Responses for Analysis**:
    - Go to any tool in Burp, such as Intruder results or Proxy history.
    - Select one or more request/response items.
    - Right-click and select **"Send to Colonel Clustered"**.
    - By default, a **Fast Scan** (DBSCAN) will automatically run.

3.  **Perform Deep Analysis (Optional)**:
    - If a more detailed, hierarchical clustering is desired, click the **"Deep Analysis"** button within the "Col. Clustered" tab.
    - A progress bar will appear directly within the tab, allowing you to monitor the analysis without blocking the main Burp Suite UI.

4.  **Analyze the Results in the Quad-Pane UI**:
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

*(Note: The screenshots below are from an older version and do not reflect the current quad-pane UI. **New screenshots are needed!**)*
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
