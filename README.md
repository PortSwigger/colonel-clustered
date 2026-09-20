# Colonel Clustered - Response Similarity Clustering (v1.0.1)

**A Burp Suite extension for clustering HTTP responses to find outliers.**

## Intro

This is a plugin I've been thinking about building for many years, simply because I'm moderately annoyed by how we analyze results of fuzzing attacks in Burp intruder. We're typically looking for changes in server responses in our fuzzing attacks. And we look for those differences by looking at status codes, response time, and response sizes. All of these are indirect measures of the content of the response. Of course as pentesters we don't have time to read the content of thousands of server to look for differences in the response content. But certainly we can have algorithms do this for us. 

That's where this plugin comes in. It uses text clustering techniques to analyze the content of the responses and put them into clusters based on their similarity. It gives you another way to view the results of your intruder fuzzing attacks, and quickly identifying those server responses that are different. 

## How It Works

Colonel Clustered provides two different algorithms to perform the response clustering. The default algorithm is relatively fast, with an optional deeper analysis algorithm that excels at spotting outliers, but doesn't scale well. Well, neither scales well, I would hesitate to throw 50k responses at this plugin. The faster/default algorithm is O(n^2) in complexity, whereas the deep analysis algorithm is O(n^3). So keep that in mind as you send intruder results to it to process. 

1.  **Content-Aware Tokenization**: The extension first inspects the `Content-Type` header of each response to apply the most intelligent tokenization strategy:
    -   **HTML**: Strips all tags and scripts, then generates character-based 5-grams on the visible text. This includes sanitizing digits to ensure resilience to minor variations (like IDs) in templated content.
    -   **JSON**: Extracts all keys and their nested paths as tokens, ignoring values. For arrays, it analyzes the structure of all contained objects.
    -   **Text**: Generates character-based 5-grams on plain text content, also sanitizing digits for template resilience.
    -   **Binary/Other**: If the content is not text-based, it generates a set of 5-byte n-grams to find similarities in the binary data.

2.  **Pre-Grouping**: To remain fast even with thousands of responses, the extension performs a single pass to group all perfectly identical responses. It calculates a hash of each response's token set and groups all items that share the same hash. This means the expensive clustering algorithm only has to run on the much smaller set of unique response bodies.

3.  **Dual Clustering Algorithms**: Colonel Clustered offers two distinct clustering algorithms:

    *   **Fast Scan (Default)**: A high-performance DBSCAN-based algorithm runs automatically when you send multiple request/responses to the extension. 
        -   **Automatic Epsilon Tuning**: It uses the Kneedle algorithm to automatically determine the optimal epsilon (density radius), adapting to the dataset's characteristics.
        -   **Outlier Detection (minPts=2)**: minPts is fixed at 2, making it highly effective for identifying responses that are unique or share similarity with only one other item, ensuring sensitive outlier detection.

    *   **Deep Analysis (Manual Trigger)**: The original, more computationally intensive hierarchical clustering algorithm is available via a "Deep Analysis" button. This option is designed for scenarios requiring a more granular and potentially different clustering perspective, utilizing Average Linkage for improved cluster cohesion.
        -   It constructs a similarity matrix using Jaccard distance between unique responses.
        -   It iteratively merges the most similar clusters, recording merge distances to determine optimal thresholds.

4.  **Outlier Consolidation**: After clustering, any resulting group containing only a single unique member is considered an outlier. All such outliers are then consolidated into a single, convenient "Outliers" group in the UI.

These two algorithms should provide an easy way to automatically identify server responses that differ in content, even when the response size is an unreliable measure of response differences. 

## How to Use

1.  **Load the Extension**:
    - Go to the **Extensions** tab in Burp Suite.
    - Click **Add** and select the ColonelClustered.jar file.
    - A new tab named "Col. Clustered" should appear.
    - Note: Hopefully I can get this into the BAPP store soon, I'll update README if it is accepted. 

2.  **Send Responses for Analysis**:
    - Go to any tool in Burp, such as Intruder results or Proxy history.
    - Select one or more request/response items.
    - Right-click and select **"Send to Colonel Clustered"**.
    - A default Fast Scan will automatically begin, and a progress bar will appear to monitor the analysis.

3.  **Perform Deep Analysis (Optional)**:
    - If a more detailed, hierarchical clustering is desired, click the **"Deep Analysis"** button.
    - **Note**: If you are analyzing a large number of items, a warning will appear about potential performance issues before the scan begins.
    - A progress bar will appear, allowing you to monitor the analysis.

4.  **Analyze the Results in the Quad-Pane UI**:
    - The "Colonel Clustered" tab uses a four-pane layout to help you quickly navigate results.
    - **Top-Left (Clusters)**: This pane shows the clusters found, including a special "Outliers" group. Each entry shows the number of items in that cluster.
    - **Bottom-Left (Cluster members)**: Select a cluster in the pane above to see all of its members displayed in this table. The table features several columns:
        - `Request/Response Pair`: The original index of the item.
        - `Status Code`: The HTTP response status code.
        - `Length`: The length of the response body in bytes.
        - `Content-Type`: The `Content-Type` header of the response.
    - **Sorting**: Click on any column header in the table to sort the items within that cluster, allowing you to easily find the largest/smallest responses, or group by status code.
    - **Top-Right & Bottom-Right (Viewers)**: Select any row in the table to view its full request and response in the viewers on the right.

## Screenshots
<img width="1920" height="1200" alt="image" src="https://github.com/user-attachments/assets/08873dc4-7fc3-4791-989e-ae9aaabe6598" />

<img width="1920" height="1200" alt="image" src="https://github.com/user-attachments/assets/6cfebc8a-91dd-4a0c-980f-1b2c7dc6bbc3" />


## Building from Source

This project uses Gradle. You need JDK version 17 installed to build the plugin. 

1.  Clone the repository:
    ```bash
    git clone <repository-url>
    cd ColonelClustered
    ```
2.  Build the fat JAR:
    ```bash
    ./gradlew build
    ```
3.  The compiled JAR will be located at `build/libs/ColonelClustered.jar`.

## Contact
Drew Kirkpatrick  
@hoodoer  
hoodoer@bitwisemunitions.dev

You can find me over at Blackthorne Consulting. 


## License

This project is released into the public domain under the Unlicense. See the LICENSE file for details.
