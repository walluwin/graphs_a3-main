import java.util.Map;

/**
 * Implement the six methods and add your route-reconstruction helper.
 * Input extraction is provided; all algorithm state belongs to the current request.
 * See ../../README.md for contracts and Java guidance.
 */
public final class Algorithms {
    /** DSU with path compression and union by size/rank; return states. */
    static Map<String, Object> dsu(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] operations = Input.operations(input); //union [[0,1],[1,2],[2,3],[0,2],[4,4]]
        int[] parent = new int[n];
        int[] rank = new int[n];

        //dsu check
        for(int i = 0; i < n; i++) {
            parent[i] = i; // x = 0 1 2 3 4 parents[x] = 0 1 2 3 4
        }

        for (int[] operation : operations) { // разделили каждую операцию
            int x = operation[0]; // [0,1] = 0
            int y = operation[1]; // [0,1] = 1

            // path compression
            int rootX = x;
            while (parent[x] != x) {
                int next = parent[x];
                parent[x] = rootX;
                x = next;
            }

            int rootY = y;
            while (parent[y] != y) {
                int next = parent[y];
                parent[y] = rootY;
                y = next;
            }

            //union
            if (rootX != rootY) {
                if (rank[rootX] < rank[rootY]) {
                    parent[rootX] = rootY;
                } else if (rank[rootX] > rank[rootY]) {
                    parent[rootY] = rootX;
                } else {
                    parent[rootY] = rootX;
                    rank[rootX]++;
                }
            }
        }
    }

    /** Separate Kruskal implementation; return cost, edge_ids, components. */
    static Map<String, Object> kruskal(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);

        throw new UnsupportedOperationException("TODO kruskal");
    }

    /** Separate Prim implementation; restart for every unvisited component. */
    static Map<String, Object> prim(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);

        throw new UnsupportedOperationException("TODO prim");
    }

    /** Kosaraju's two DFS passes; return component and exact condensation. */
    static Map<String, Object> scc(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);

        throw new UnsupportedOperationException("TODO scc");
    }

    /** Three-state DFS and reverse finish order; return acyclic and order. */
    static Map<String, Object> topo(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);

        throw new UnsupportedOperationException("TODO topo");
    }

    /** Whole-graph cycle check and DAG relaxation; return acyclic, distance, parent_edge. */
    static Map<String, Object> dag(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int source = Input.source(input);
        int[][] edges = Input.edges(input);

        // Add and test your own route-reconstruction helper.
        throw new UnsupportedOperationException("TODO dag");
    }
}
