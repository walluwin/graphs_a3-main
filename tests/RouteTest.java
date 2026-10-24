import java.util.List;
import java.util.Map;

/**
 * Test of the route-reconstruction helper Algorithms.route(...).
 * Run from the repository root:
 *   javac -d .build/routetest src/Java/*.java tests/RouteTest.java
 *   java -cp .build/routetest RouteTest
 */
public class RouteTest {
    static int failures = 0;

    static void check(String name, boolean ok, String details) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + "  " + details);
        if (!ok) {
            failures++;
        }
    }

    /** Sum of weights along the path, taking for each step an edge u->v with the parent id. */
    static long pathWeight(int[][] edges, int[] parent, List<Integer> path) {
        long sum = 0;
        for (int i = 1; i < path.size(); i++) {
            int v = path.get(i);
            int id = parent[v];
            if (edges[id][0] != path.get(i - 1) || edges[id][1] != v) {
                return Long.MIN_VALUE; // direction / edge mismatch
            }
            sum += edges[id][2];
        }
        return sum;
    }

    @SuppressWarnings("unchecked")
    static void run(String name, String json, int[][] edges, int source, int target,
                    List<Integer> expectedPath, Long expectedCost) {
        Map<String, Object> request = Json.parseRequest(json);
        Map<String, Object> result = Algorithms.dag(request);
        int[] parent = (int[]) result.get("parent_edge");
        Long[] distance = (Long[]) result.get("distance");

        List<Integer> path = Algorithms.route(edges, parent, source, target);
        boolean samePath = expectedPath == null || path.equals(expectedPath);
        check(name + " path", samePath, "route(" + source + "->" + target + ") = " + path
                + ", parent_edge = " + java.util.Arrays.toString(parent));

        if (expectedCost != null) {
            long weight = pathWeight(edges, parent, path);
            boolean ok = weight == expectedCost && distance[target] != null && distance[target] == weight;
            check(name + " cost", ok, "sum of weights along the path = " + weight
                    + ", distance[target] = " + distance[target] + ", expected " + expectedCost);
        }
    }

    public static void main(String[] args) {
        // 1) published example: reachable target 3, source 0, unreachable 4
        int[][] e1 = {{0, 1, 3}, {0, 2, 8}, {1, 2, -7}, {2, 3, 4}};
        String j1 = "{\"task\":\"dag\",\"n\":5,\"source\":0,\"edges\":[[0,1,3],[0,2,8],[1,2,-7],[2,3,4]]}";
        run("reachable target 3", j1, e1, 0, 3, List.of(0, 1, 2, 3), 0L);
        run("source itself", j1, e1, 0, 0, List.of(0), 0L);
        run("unreachable target 4", j1, e1, 0, 4, List.of(), null);

        // 2) parallel edges and two equal-cost paths to vertex 2 (cost 3 both)
        int[][] e2 = {{0, 1, 5}, {0, 1, 2}, {1, 2, 1}, {0, 2, 3}};
        String j2 = "{\"task\":\"dag\",\"n\":3,\"source\":0,\"edges\":[[0,1,5],[0,1,2],[1,2,1],[0,2,3]]}";
        run("parallel edges: target 1", j2, e2, 0, 1, List.of(0, 1), 2L);
        // the answer to vertex 2 may be [0,2] or [0,1,2]: both cost 3, so check cost only
        Map<String, Object> r = Algorithms.dag(Json.parseRequest(j2));
        int[] parent = (int[]) r.get("parent_edge");
        List<Integer> p = Algorithms.route(e2, parent, 0, 2);
        boolean valid = (p.equals(List.of(0, 2)) || p.equals(List.of(0, 1, 2)))
                && pathWeight(e2, parent, p) == 3;
        check("equal paths: target 2", valid, "route(0->2) = " + p + ", weight = " + pathWeight(e2, parent, p));

        // 3) source is not vertex 0
        int[][] e3 = {{0, 2, 5}, {2, 3, -4}, {1, 0, 1}};
        String j3 = "{\"task\":\"dag\",\"n\":4,\"source\":2,\"edges\":[[0,2,5],[2,3,-4],[1,0,1]]}";
        run("source=2, target 3", j3, e3, 2, 3, List.of(2, 3), -4L);
        run("source=2, unreachable 0", j3, e3, 2, 0, List.of(), null);

        System.out.println(failures == 0 ? "ROUTE TESTS PASSED" : failures + " ROUTE CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
