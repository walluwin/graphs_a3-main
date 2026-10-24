import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kruskal vs Prim benchmark on the SAME graphs.
 *
 * Measured: one call Algorithms.kruskal(request) / Algorithms.prim(request) on an already parsed
 * request (Input.edges conversion, algorithm-specific graph building, the algorithm itself and
 * building the result map are all inside the timed region; JSON parsing, JSON output, process
 * start and compilation are NOT).  Both methods get exactly the same boundary.
 * Timer: System.nanoTime().  Warm-up: 5 untimed calls of each method per size (JIT).
 * Measurements: 7 timed repetitions per size, Kruskal and Prim alternate inside every repetition.
 *
 * Run through:  python tests/bench.py
 */
public class Bench {
    static final long SEED = 20261024L;
    static final int WARMUP = 5;
    static final int REPS = 7;
    static final int[][] SIZES = {{1_000, 5_000}, {10_000, 50_000}, {100_000, 250_000}};

    static Map<String, Object> request(String task, int n, int[][] edges) {
        List<Object> rows = new ArrayList<>(edges.length);
        for (int[] e : edges) {
            List<Object> row = new ArrayList<>(3);
            for (int x : e) {
                row.add((long) x); // the JSON parser of the project stores integers as Long
            }
            rows.add(row);
        }
        Map<String, Object> request = new HashMap<>();
        request.put("task", task);
        request.put("n", (long) n);
        request.put("edges", rows);
        return request;
    }

    static int find(int[] p, int x) {
        while (p[x] != x) {
            p[x] = p[p[x]];
            x = p[x];
        }
        return x;
    }

    /** Independent validation: forest without cycles / loops, cost = sum, n-c edges, c from scratch. */
    @SuppressWarnings("unchecked")
    static void validate(String name, int n, int[][] edges, Map<String, Object> result) {
        long cost = (Long) result.get("cost");
        int components = (Integer) result.get("components");
        List<Integer> ids = (List<Integer>) result.get("edge_ids");

        int[] all = new int[n];
        for (int i = 0; i < n; i++) {
            all[i] = i;
        }
        int c = n;
        for (int[] e : edges) {
            int a = find(all, e[0]);
            int b = find(all, e[1]);
            if (a != b) {
                all[a] = b;
                c--;
            }
        }
        int[] forest = new int[n];
        for (int i = 0; i < n; i++) {
            forest[i] = i;
        }
        long sum = 0;
        boolean[] used = new boolean[edges.length];
        for (int id : ids) {
            if (used[id] || edges[id][0] == edges[id][1]) {
                throw new IllegalStateException(name + ": repeated edge id or loop selected");
            }
            used[id] = true;
            int a = find(forest, edges[id][0]);
            int b = find(forest, edges[id][1]);
            if (a == b) {
                throw new IllegalStateException(name + ": cycle in the chosen edges");
            }
            forest[a] = b;
            sum += edges[id][2];
        }
        if (sum != cost || components != c || ids.size() != n - c) {
            throw new IllegalStateException(name + ": cost/components/edge count mismatch");
        }
    }

    static long median(long[] values) {
        long[] copy = values.clone();
        java.util.Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    static String ms(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.1f", nanos / 1e6);
    }

    static String list(long[] nanos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nanos.length; i++) {
            sb.append(i == 0 ? "" : " / ").append(ms(nanos[i]));
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        java.util.Random rnd = new java.util.Random(SEED);
        System.out.println("| n | m | components | MST cost | Kruskal runs, ms | **Kruskal median, ms** | Prim runs, ms | **Prim median, ms** |");
        System.out.println("|---:|---:|---:|---:|---|---:|---|---:|");
        for (int[] size : SIZES) {
            int n = size[0];
            int m = size[1];
            int[][] edges = new int[m][3];
            for (int i = 0; i < m; i++) {
                edges[i][0] = rnd.nextInt(n);
                edges[i][1] = rnd.nextInt(n);
                edges[i][2] = rnd.nextInt(2_000_000_001) - 1_000_000_000;
            }
            Map<String, Object> kr = request("kruskal", n, edges);
            Map<String, Object> pr = request("prim", n, edges);

            for (int i = 0; i < WARMUP; i++) {
                Algorithms.kruskal(kr);
                Algorithms.prim(pr);
            }
            long[] tk = new long[REPS];
            long[] tp = new long[REPS];
            Map<String, Object> lastK = null;
            Map<String, Object> lastP = null;
            for (int r = 0; r < REPS; r++) {
                long t0 = System.nanoTime();
                lastK = Algorithms.kruskal(kr);
                tk[r] = System.nanoTime() - t0;
                t0 = System.nanoTime();
                lastP = Algorithms.prim(pr);
                tp[r] = System.nanoTime() - t0;
            }
            validate("kruskal n=" + n, n, edges, lastK);
            validate("prim n=" + n, n, edges, lastP);
            if (!lastK.get("cost").equals(lastP.get("cost"))
                    || !lastK.get("components").equals(lastP.get("components"))) {
                throw new IllegalStateException("Kruskal and Prim disagree for n=" + n);
            }
            System.out.println("| " + n + " | " + m + " | " + lastK.get("components") + " | " + lastK.get("cost")
                    + " | " + list(tk) + " | **" + ms(median(tk)) + "** | " + list(tp) + " | **" + ms(median(tp)) + "** |");
            System.gc();
        }
        System.out.println();
        System.out.println("CHECK: for every size both forests are valid, costs and component counts are identical.");
    }
}