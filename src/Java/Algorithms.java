import java.util.*;

/**
 * Implement the six methods and add your route-reconstruction helper.
 * Input extraction is provided; all algorithm state belongs to the current request.
 * See ../../README.md for contracts and Java guidance.
 */
public final class Algorithms {
    /** DSU with path compression and union by size/rank; return states. */
    private static int find(int n, int[] parent) {
        int root = n;

        while (parent[root] != root) {
            root = parent[root];
        }

        while (parent[n] != root) {
            int next = parent[n];
            parent[n] = root;
            n = next;
        }
        return root;
    }

    private static boolean union(int x, int y, int[] parent, int[] size) {
        int rootX = find(x, parent);
        int rootY = find(y, parent);
        if (rootX == rootY) {
            return false;
        }

        if(size[rootX] < size[rootY]){
            int tmp = rootX;
            rootX = rootY;
            rootY = tmp;
        }
        parent[rootX] = rootX;
        size[rootX] += size[rootY];
        return true;
    }

    static Map<String, Object> dsu(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] operations = Input.operations(input); //union [[0,1],[1,2],[2,3],[0,2],[4,4]]
        int[] parent = new int[n];
        //int[] rank = new int[n];
        int[] size = new int[n];

        int[][] states = new int[operations.length][];
        int components = n;
        int largest = 1;

        //dsu check
        for(int i = 0; i < n; i++) {
            parent[i] = i; // x = 0 1 2 3 4 parents[x] = 0 1 2 3 4
            size[i] = 1;
        }

        for (int i = 0; i < operations.length; i++) {
            int x = operations[i][0];
            int y = operations[i][1];
            if (union(x ,y, parent, size)) {
                components--;
                int root = find(x, parent);
                if (size[root] > largest) {
                    largest = size[root];
                }
            }
            states[i] = new int[] {components, largest};
        }
        Map<String, Object> result = new HashMap<>();
        result.put("states:", states);
        return result;
    }

    /** Separate Kruskal implementation; return cost, edge_ids, components. */
    static Map<String, Object> kruskal(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);
        int m = edges.length;

        int cost;
        int[] edge_ids;
        int components;

        int t = 0;

        for (int[] edge : edges) {
            edges[t++] = edge;
        }



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
