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
        parent[rootY] = rootX;
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
        int largest = n > 0 ? 1 : 0;

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
        result.put("states", states);
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
                edge_ids.add(id);
                cost += weight;

                union(u, v, parent, size);
                components--;
            }
        }


        Map<String, Object> result = new HashMap<>();
        result.put("cost", cost);
        result.put("edge_ids", edge_ids);
        result.put("components", components);
        return result;
    }

    /** Separate Prim implementation; restart for every unvisited component. */
    static Map<String, Object> prim(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);

        List<List<int[]>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
        }
        for (int i = 0; i < edges.length; i++) {
            int u = edges[i][0];
            int v = edges[i][1];
            int w = edges[i][2];
            if (u == v) {
                continue;
            }
            adj.get(u).add(new int[] {v, w, i});
            adj.get(v).add(new int[] {u, w, i});
        }

        boolean[] visited = new boolean[n];
        long cost = 0;
        int components = 0;
        List<Integer> edge_ids = new ArrayList<>();

        for (int start = 0; start < n; start++) {
            if (visited[start]) {
                continue;
            }
            components++;
            visited[start] = true;

            PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> Integer.compare(a[1], b[1]));
            pq.addAll(adj.get(start));

            while (!pq.isEmpty()) {
                int[] e = pq.poll();
                int v = e[0];
                if (visited[v]) {
                    continue;
                }
                visited[v] = true;
                cost += e[1];
                edge_ids.add(e[2]);
                for (int[] next : adj.get(v)) {
                    if (!visited[next[0]]) {
                        pq.add(next);
                    }
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("cost", cost);
        result.put("edge_ids", edge_ids);
        result.put("components", components);
        return result;
    }

    /** Kosaraju's two DFS passes; return component and exact condensation. */
    static Map<String, Object> scc(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);

        List<List<Integer>> adj = new ArrayList<>();
        List<List<Integer>> radj = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
            radj.add(new ArrayList<>());
        }
        for (int[] e : edges) {
            adj.get(e[0]).add(e[1]);
            radj.get(e[1]).add(e[0]);
        }

        boolean[] visited = new boolean[n];
        int[] next = new int[n];
        List<Integer> order = new ArrayList<>();
        for (int s = 0; s < n; s++) {
            if (visited[s]) {
                continue;
            }
            List<Integer> stack = new ArrayList<>();
            stack.add(s);
            visited[s] = true;
            while (!stack.isEmpty()) {
                int u = stack.get(stack.size() - 1);
                if (next[u] < adj.get(u).size()) {
                    int v = adj.get(u).get(next[u]++);
                    if (!visited[v]) {
                        visited[v] = true;
                        stack.add(v);
                    }
                } else {
                    order.add(u);
                    stack.remove(stack.size() - 1);
                }
            }
        }

        int[] component = new int[n];
        Arrays.fill(component, -1);
        int label = 0;
        for (int i = n - 1; i >= 0; i--) {
            int s = order.get(i);
            if (component[s] != -1) {
                continue;
            }
            List<Integer> stack = new ArrayList<>();
            stack.add(s);
            component[s] = label;
            while (!stack.isEmpty()) {
                int u = stack.remove(stack.size() - 1);
                for (int v : radj.get(u)) {
                    if (component[v] == -1) {
                        component[v] = label;
                        stack.add(v);
                    }
                }
            }
            label++;
        }

        Set<Long> seen = new HashSet<>();
        List<int[]> condensation = new ArrayList<>();
        for (int[] e : edges) {
            int a = component[e[0]];
            int b = component[e[1]];
            if (a != b && seen.add((long) a * label + b)) {
                condensation.add(new int[] {a, b});
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("component", component);
        result.put("condensation", condensation);
        return result;
    }

    private static List<Integer> topoOrder(int n, int[][] edges) {
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
        }
        for (int[] e : edges) {
            adj.get(e[0]).add(e[1]);
        }

        int[] state = new int[n];
        int[] next = new int[n];
        List<Integer> finished = new ArrayList<>();

        for (int s = 0; s < n; s++) {
            if (state[s] != 0) {
                continue;
            }
            List<Integer> stack = new ArrayList<>();
            stack.add(s);
            state[s] = 1;
            while (!stack.isEmpty()) {
                int u = stack.get(stack.size() - 1);
                if (next[u] < adj.get(u).size()) {
                    int v = adj.get(u).get(next[u]++);
                    if (state[v] == 1) {
                        return null;
                    }
                    if (state[v] == 0) {
                        state[v] = 1;
                        stack.add(v);
                    }
                } else {
                    state[u] = 2;
                    finished.add(u);
                    stack.remove(stack.size() - 1);
                }
            }
        }

        Collections.reverse(finished);
        return finished;
    }

    /** Three-state DFS and reverse finish order; return acyclic and order. */
    static Map<String, Object> topo(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);

        List<Integer> order = topoOrder(n, edges);

        Map<String, Object> result = new HashMap<>();
        if (order == null) {
            result.put("acyclic", false);
            result.put("order", new ArrayList<Integer>());
        } else {
            result.put("acyclic", true);
            result.put("order", order);
        }
        return result;
    }

    /** Whole-graph cycle check and DAG relaxation; return acyclic, distance, parent_edge. */
    static Map<String, Object> dag(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int source = Input.source(input);
        int[][] edges = Input.edges(input);

        Map<String, Object> result = new HashMap<>();
        List<Integer> order = topoOrder(n, edges);
        if (order == null) {
            result.put("acyclic", false);
            result.put("distance", new ArrayList<Long>());
            result.put("parent_edge", new ArrayList<Integer>());
            return result;
        }

        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
        }
        for (int i = 0; i < edges.length; i++) {
            adj.get(edges[i][0]).add(i);
        }

        Long[] distance = new Long[n];
        int[] parentEdge = new int[n];
        Arrays.fill(parentEdge, -1);
        distance[source] = 0L;

        for (int u : order) {
            if (distance[u] == null) {
                continue;
            }
            for (int id : adj.get(u)) {
                int v = edges[id][1];
                long candidate = distance[u] + edges[id][2];
                if (distance[v] == null || candidate < distance[v]) {
                    distance[v] = candidate;
                    parentEdge[v] = id;
                }
            }
        }

        result.put("acyclic", true);
        result.put("distance", distance);
        result.put("parent_edge", parentEdge);
        return result;
    }

    static List<Integer> route(int[][] edges, int[] parentEdge, int source, int target) {
        List<Integer> path = new ArrayList<>();
        if (target != source && parentEdge[target] == -1) {
            return path;
        }
        int v = target;
        path.add(v);
        while (v != source) {
            v = edges[parentEdge[v]][0];
            path.add(v);
        }
        Collections.reverse(path);
        return path;
    }
}