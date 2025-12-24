# Colonel Clustered

**A Burp Suite extension for clustering HTTP responses to find outliers.**

## The Problem

When running automated attacks with tools like Burp Intruder, you often get thousands of results. Sifting through these results can be a "needle in a haystack" problem. Traditional sorting by response length, status code, or timing can help, but it often fails to identify subtle but significant differences in the *content* of the responses. You might have hundreds of responses that are the exact same size but contain slightly different error messages, tokens, or data, which are critical to discover.

Colonel Clustered solves this by analyzing the entire content of every response and grouping similar ones together, allowing you to instantly spot the outliers.

## Features

- **Content-Based Clustering**: Uses a TF-IDF -> PCA -> DBSCAN pipeline to cluster responses based on their content, not just metadata.
- **Outlier Detection**: The primary goal is to isolate unique or rare responses, which often represent interesting application behavior.
- **Automatic Parameter Tuning**: Automatically determines the optimal parameters for the DBSCAN algorithm based on your specific dataset:
    - **Adaptive `minPts`**: The `minPts` parameter (the minimum number of points to form a dense region) is calculated as the natural logarithm of your dataset size. This makes the clustering robust for both small and very large sets of requests.
    - **Optimal `epsilon`**: The `epsilon` parameter (the maximum distance between two samples for one to be considered as in the neighborhood of the other) is found by analyzing the "knee" of the k-distance graph. This eliminates the need for manual, trial-and-error tuning.
- **Universal Integration**: Works from the context menu of any Burp tool that handles HTTP requests/responses, including Proxy history, Repeater, and Intruder (including Turbo Intruder).
- **Interactive & Scalable UI**:
    - Displays clusters and their member counts in a hierarchical tree.
    - **Collapsible Groups**: Cluster groups are collapsed by default, making it easy to navigate even with thousands of requests.
    - **Visually Nested Structure**: Parent cluster nodes are larger and styled differently to clearly distinguish them from the child request nodes, creating an intuitive nested appearance.
    - Integrates Burp's native request/response viewers for familiar analysis.
    - Click any request in the tree to see its full request and response.

## How to Use

1.  **Load the Extension**:
    - Go to the **Extensions** tab in Burp Suite.
    - Click **Add** and select the `ColonelClustered.jar` file.
    - A new tab named "Colonel Clustered" should appear.

2.  **Send Responses for Analysis**:
    - Go to any tool in Burp, such as Intruder results or Proxy history.
    - Select one or more request/response items.
    - Right-click and select **"Send to Colonel Clustered"**.

3.  **Analyze the Results**:
    - The "Colonel Clustered" tab will automatically start processing.
    - The UI will display the clusters in a tree on the left. Groups with the fewest members (especially "Outliers") are often the most interesting.
    - Click on any individual request in the tree to view its full request and response in the viewers on the right.

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
-   **Consulting**: Professional security services provided by [TrustedSec](https://www.trustedsec.com).

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.
