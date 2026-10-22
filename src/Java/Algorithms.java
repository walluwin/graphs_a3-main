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

        // sorting
        Integer[] order = new Integer[m];
        for (int i = 0; i < m; i++) {
            order[i] = i;
        }

        java.util.Arrays.sort(order, (a, b) -> {
            int weightA = edges[a][2];
            int weightB = edges[b][2];
            int comparison = Integer.compare(weightA, weightB);
            if (comparison == 0) {
                return Integer.compare(a, b);
            }
            return comparison;
        });

        int[] parent = new int[n];
        int[] size = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            size[i] = 1;
        }

        long cost = 0;
        int components = n;
        List<Integer> edge_ids = new ArrayList<>();
        // самое главное
        for (int id : order) {
            int u = edges[id][0];
            int v = edges[id][1];
            int weight = edges[id][2];

            // проверка на циклы
            if (find(u, parent) != find(v, parent)) {

                // 6. Add the edge to the spanning forest
                edge_ids.add(id);
                cost += weight;

                union(u, v, parent, size);
                components--;
            }
        }


        Map<String, Object> result = new HashMap<>();
        result.put("cost:", cost);
        result.put("edge_ids:", edge_ids);
        result.put("components:", components);
        return result;
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
