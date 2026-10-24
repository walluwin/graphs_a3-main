# Assignment 3 — Graph algorithms: analysis

Language: **Java 17** (written and tested with JDK 21). All code is in `src/Java/Algorithms.java`;
`Main.java`, `Input.java`, `Json.java` are the supplied adapter and were not changed.

## 1. Build, run, test commands

Run everything from the repository root.

```sh
python tools/run.py --setup                       # transport check (compiles the Java sources)
python tools/run.py --check                       # supplied public checks + state-reset check
python tools/run.py --input tests/my_cases.jsonl  # prints the actual answers of my own cases
python tests/verify.py                            # my cases, hand-derived answers, semantic checkers, mixed batch, checker self-test
python tests/verify.py --large                    # + inputs with n=100000, m=250000 and 100000-vertex chains (seed 20261024)
javac -d .build/routetest src/Java/*.java tests/RouteTest.java
java -cp .build/routetest RouteTest               # tests of the route-reconstruction helper
python tests/bench.py                             # Kruskal vs Prim benchmark (rewrites section 9 of this file)
```

## 2. General design

* **One request = one set of local arrays.** Every method builds all of its state (`parent[]`, `size[]`,
  adjacency lists, `visited`, stacks, ...) inside the call from the request arguments. There are no static
  mutable fields, so nothing can leak from one request into the next. This is checked by
  `tests/verify.py` ("mixed batch": all cases, then the same cases reversed, then again — the first and the
  third pass must give identical answers) and by the `state reset` check of `tools/run.py --check`.
* **No recursion.** Every DFS is iterative: an explicit stack plus an array `next[v]` that stores the
  index of the next neighbour of `v` to look at. A suspended vertex therefore continues where it stopped and
  neighbours are never re-scanned from the beginning, which keeps DFS at `O(n+m)`. A chain of 100 000
  vertices would overflow the default Java call stack with recursion; with the explicit stack it works
  (tests `deep scc chain`, `deep topo chain`, `deep dag chain`).
* **Original edge IDs.** Edge `i` of `edges` has ID `i`. Kruskal sorts an array of IDs (not the edges),
  Prim stores the ID in every adjacency entry, DAG stores IDs in `parent_edge`.
* **64-bit arithmetic.** Costs and distances are `long` (`cost`, `candidate`); the unreachable distance is a
  `null` (`Long[]`), never `-1`.
* **Output.** Every method returns a `Map<String,Object>` with exactly the contract fields.

## 3. DSU (`dsu`)

**Steps.** `parent[i]=i`, `size[i]=1`, `components=n`, `largest = n>0 ? 1 : 0`. For each operation `(u,v)`:
`union` finds both roots with `find`; if they differ, the smaller tree is attached to the larger one and
sizes are added, `components--`, `largest=max(largest,size[root])`. After every operation the pair
`[components, largest]` is appended to `states`.

**Invariant.** `parent` is a forest in which every tree is one connected component of the edges added so far;
`size[r]` is the number of vertices in the tree of root `r` (valid for roots); `components` is the number of
roots; `largest` is the maximum root size seen so far (sizes only grow, so it never needs to decrease).

**Correctness.** `find` follows `parent` to the root and then re-points every vertex of the path directly to
the root (path compression), which does not change the partition. A union of two different roots merges
exactly two components, so `components` decreases by one and the new component has size `sizeA+sizeB`; a
repeated union or `u==v` has equal roots and changes nothing. Rank is not used: union is by **size**, so no
separate size array is needed.

**Edge cases.** `n=0` (`states=[]`, `largest=0`), no operations, self-union, repeated union, single vertex.

**Complexity.** Time `O(n + q·α(n))` amortised (path compression + union by size); memory `O(n+q)`
(arrays of size `n` plus `q` state pairs).

## 4. Kruskal (`kruskal`)

**Steps.** Create an array of edge IDs `0..m-1`, sort it by weight (ties by ID). Make a DSU. Scan the IDs in
this order; if `find(u) != find(v)`, take the edge: add its ID, add its weight to `cost` (`long`), union,
`components--` (it starts at `n`).

**Invariant.** After processing a prefix of the sorted list, the chosen edges form a minimum spanning forest of
the graph consisting of this prefix, and the DSU components are the connected components of that prefix graph.

**Correctness.** Cut/cycle property: the lightest edge that connects two different components of the current
forest belongs to some minimum spanning forest, and an edge whose ends are already connected would close a
cycle (it is skipped). Loops have `find(u)==find(v)` and are never taken; parallel edges are handled the same
way (only the first useful one is taken); a repeated ID cannot appear. A forest with `c` components has
exactly `n-c` edges. Negative weights do not matter because only the order of weights is used.

**Edge cases.** `n=0`; no edges (all vertices isolated, `components=n`); disconnected graph; loops; parallel
edges; ties; negative weights; sums beyond 32 bit (`3·10^9` in tests).

**Complexity.** Time `O(n + m log m)` (sorting dominates; DSU `O(m·α(n))`); memory `O(n+m)`.
(Implementation detail: the ID array is `Integer[]` with a comparator, which is slower than sorting
primitives but simple.)

## 5. Prim (`prim`)

**Steps.** Build adjacency lists `adj[v]` of `{neighbour, weight, edgeId}` (both directions, loops skipped).
For every unvisited vertex `s`: `components++`, mark `s`, push all its edges to a min-priority queue ordered
by weight. Repeatedly pop the lightest edge; if its far end is already visited, skip it (lazy deletion);
otherwise mark that vertex, add the weight to `cost`, add the ID, push its edges that lead to unvisited
vertices. When the queue is empty, the component is finished and the next unvisited vertex starts a new one.

**Invariant.** The visited vertices of the current component and the chosen edges form a tree that is a part
of some minimum spanning tree of the component; the queue contains all edges that leave the visited set
(and possibly stale ones that are skipped when popped).

**Correctness.** Cut property: the lightest edge leaving the visited set is safe. Because the queue is
restarted for each unvisited vertex, isolated vertices and every component of a disconnected graph are
handled; `components` equals the number of restarts. Loops are never inserted, so they cannot be chosen.

**Edge cases.** Same as Kruskal (the same graphs are used for both in `tests/verify.py` and in the benchmark).

**Complexity.** Lazy queue: `O(n + m log m)` time (each undirected edge is pushed at most twice), memory
`O(n+m)` (`2m` adjacency entries, queue up to `2m`).

## 6. SCC — Kosaraju (`scc`)

**Steps.** Build the graph and its transpose. **Pass 1**: iterative DFS over all vertices on the original
graph; a vertex is appended to `order` when all its neighbours are finished. **Pass 2**: go through `order`
from the end to the beginning; every vertex without a label starts a new component, which is collected by a
traversal on the **transposed** graph (only unlabeled vertices are entered). Finally, for every input edge
`u->v` with `component[u] != component[v]` the pair `(component[u], component[v])` is added once, using a hash
set of `a*labels+b` (as `long`).

**Invariant.** Pass 2 takes roots in decreasing finishing time, so the root is always in a source component
of the condensation among the still unlabeled vertices; its traversal on the transposed graph cannot leave its
own SCC. Hence each traversal labels exactly one SCC.

**Correctness.** Standard Kosaraju proof (the vertex with the largest finishing time lies in a source SCC of the
condensation DAG; removing it and repeating works by induction). Labels are `0..k-1`, equal labels = same SCC.
The condensation contains each ordered pair of different labels exactly once: internal edges and self-loops
are dropped, duplicates are removed by the set.

**Edge cases.** `n=0` (both arrays empty); isolated vertices (separate SCCs); loops; duplicate edges;
one big cycle (no condensation edges); several cross edges between the same two SCCs (one pair); a deep chain.

**Complexity.** Time `O(n+m)` expected (hash set); memory `O(n+m)`.

## 7. Topological sort by DFS (`topo`)

**Steps.** Iterative DFS with three states: 0 = not visited, 1 = active (on the stack), 2 = finished. When a
vertex is first reached it becomes 1; when all its neighbours are processed it becomes 2 and is appended to
`finished`. If an edge leads to a vertex in state 1, a directed cycle exists: return `acyclic=false`,
`order=[]`. All start vertices are tried, so every component is checked. If no cycle was found, the reversed
`finished` list is the order.

**Invariant.** The stack always holds exactly the active vertices, and they form a path of the DFS tree;
a finished vertex has all its successors finished.

**Correctness.** An edge to an active vertex closes a cycle with the stack path; conversely every directed
cycle makes the DFS meet such an edge. An edge to a finished vertex is a forward/cross edge and is **not** a
cycle (tested by `topo edge to finished vertex is not a cycle`). In an acyclic graph, for every edge `u->v`
the vertex `v` finishes before `u`, so after reversing, `u` is before `v`. A self-loop `u->u` hits state 1 at
once.

**Edge cases.** `n=0` (`true`, `[]`); self-loop; cycle in a component that does not contain vertex 0; parallel
edges; isolated vertices; deep chain.

**Complexity.** `O(n+m)` time and memory (every edge is examined once thanks to `next[]`).

## 8. Shortest paths in a DAG (`dag`) and route helper

**Steps.** 1) Run the same three-state DFS on the **whole** graph (not only from the source); if there is a
cycle anywhere, return `acyclic=false`, `distance=[]`, `parent_edge=[]`. 2) `distance[source]=0`
(`Long[]`, other entries `null` = unreachable), `parent_edge` filled with `-1`. 3) Take the vertices in
topological order; skip those whose distance is `null`; for each outgoing edge `id=(u,v,w)` set
`distance[v]=distance[u]+w` and `parent_edge[v]=id` if `v` was unreachable or the new value is smaller.

**Invariant.** When `u` is taken, `distance[u]` is final: all its predecessors precede it in the order and
have already relaxed their edges into `u`.

**Correctness.** The topological order makes one pass enough, so negative weights are fine (no cycle can
make distances decrease forever because the graph is acyclic). `parent_edge[v]` is the ID of the edge that gave
the final value, so it ends in `v`, and its start `u` satisfies `distance[u]+w=distance[v]`; following parents
from any reachable vertex leads to the source (`-1` there). Unreachable vertices keep `null` and `-1`, and are
never used as a relaxation source. `-1` is never used as "unreachable" because it can be a real distance.

**Edge cases.** One vertex; source ≠ 0; vertices unreachable from the source; a cycle that is unreachable from
the source (answer is still `acyclic=false`); self-loop; parallel edges; equal paths; sums beyond 32 bit.

**Complexity.** `O(n+m)` time and memory.

**Route-reconstruction helper** `static List<Integer> route(int[][] edges, int[] parentEdge, int source, int target)`:
starting from `target` it repeatedly goes to `edges[parentEdge[v]][0]` (the start of the parent edge) until it
reaches `source`, then reverses the list. For the source it returns `[source]`. **For an unreachable target it
returns an empty list** (this choice concerns only the helper; in the JSON the distance stays `null`).
Time and memory `O(L)` for a route with `L` vertices.

Real calls (output of `java -cp .build/routetest RouteTest`; the direction of every step and the sum of
weights are checked against `distance[target]`):

| Case | Call | `parent_edge` | Result | Check |
|---|---|---|---|---|
| reachable target | `route(0→3)` on the published example | `[-1,0,2,3,-1]` | `[0, 1, 2, 3]` (edge IDs 0, 2, 3) | weights 3−7+4 = 0 = `distance[3]` |
| source itself | `route(0→0)` | `[-1,0,2,3,-1]` | `[0]` | cost 0 |
| unreachable target | `route(0→4)` | `[-1,0,2,3,-1]` | `[]` | `distance[4]=null` |
| parallel edges | `route(0→1)` on `[[0,1,5],[0,1,2],[1,2,1],[0,2,3]]` | `[-1,1,3]` | `[0, 1]` via edge **1** (weight 2, not edge 0 with weight 5) | cost 2 = `distance[1]` |
| equal paths | `route(0→2)` on the same graph | `[-1,1,3]` | `[0, 2]` via edge 3 (`[0,1,2]` with cost 3 would also be correct) | cost 3 = `distance[2]` |
| source ≠ 0 | `route(2→3)` on `[[0,2,5],[2,3,-4],[1,0,1]]`, source 2 | `[-1,-1,-1,1]` | `[2, 3]` | cost −4 = `distance[3]` |
| unreachable, source ≠ 0 | `route(2→0)` | `[-1,-1,-1,1]` | `[]` | `distance[0]=null` |

## 9. Experiment: Kruskal vs Prim

**Method.** Three graph sizes, the **same** random graph for both methods at each size
(`java.util.Random(20261024)` in `tests/Bench.java`: endpoints `nextInt(n)`, weights uniform in
`[-10^9, 10^9]`; loops and parallel edges are possible). Before the numbers are reported, the program checks
that both forests are valid (no loops, no cycles, `n-c` edges, cost = sum of weights, `c` recomputed from
scratch) and that Kruskal and Prim return the same cost and the same number of components.

**What is measured.** One call `Algorithms.kruskal(request)` / `Algorithms.prim(request)` with an already
parsed request. Inside the timed region: conversion of the input by `Input.edges`, the building of the
method's own data (ID sorting for Kruskal, adjacency lists for Prim), the algorithm and creating the result
map. **Not** measured: compilation, JVM start, JSON parsing, JSON output, I/O. The boundary is identical for
both methods. Timer: `System.nanoTime()`. Warm-up: 5 untimed calls of each method per size (JIT).
Measurements: 7 per method and size, Kruskal and Prim alternating inside each repetition; the median of
the 7 is reported. Reproduce with `python tests/bench.py` (it rewrites the block below).

<!-- BENCH:START -->
Measured on 2026-10-24 by `python tests/bench.py`.

**Environment.** Windows-11-10.0.26200-SP0; CPU: Intel64 Family 6 Model 186 Stepping 2, GenuineIntel (12 logical cores); RAM: unknown;
java version "25.0.1" 2025-10-21 LTS; default JVM options (no `-Xmx`/`-Xss` flags); Python 3.12.0 only launches the build.

**Results** (all times in milliseconds; the seven runs are listed in execution order):

| n | m | components | MST cost | Kruskal runs, ms | **Kruskal median, ms** | Prim runs, ms | **Prim median, ms** |
|---:|---:|---:|---:|---|---:|---|---:|
| 1000 | 5000 | 1 | -758692488419 | 2.8 / 1.7 / 1.6 / 1.5 / 1.6 / 1.5 / 1.5 | **1.6** | 1.3 / 1.2 / 1.3 / 1.3 / 1.3 / 1.8 / 1.1 | **1.3** |
| 10000 | 50000 | 1 | -7582441104440 | 25.7 / 15.4 / 14.2 / 14.7 / 15.1 / 15.9 / 14.3 | **15.1** | 23.1 / 14.8 / 15.8 / 14.5 / 17.3 / 12.7 / 12.7 | **14.8** |
| 100000 | 250000 | 703 | -53081364265243 | 113.5 / 121.3 / 157.3 / 1010.1 / 431.5 / 488.3 / 286.5 | **286.5** | 160.7 / 170.0 / 1423.5 / 895.7 / 559.8 / 1470.9 / 325.1 | **559.8** |

CHECK: for every size both forests are valid, costs and component counts are identical.
<!-- BENCH:END -->

**Observations.** On the two smaller graphs the methods are practically equal: at n=1000, m=5000 Prim's median is 1.3 ms 
and Kruskal's is 1.6 ms, and at n=10000, m=50000 they are 14.8 ms and 15.1 ms with strongly overlapping runs, so I cannot
call either one faster there. When n and m grow ten times (1000 → 10000), the medians grow about 9.4 times for Kruskal
and 11.4 times for Prim, which is close to the expected O(m log m) (slightly more than linear). At n=100000, m=250000
Kruskal is faster (median 286.5 ms against 559.8 ms for Prim; best runs 113.5 ms against 160.7 ms), but this size is
also the least reliable one: the runs vary from 113.5 to 1010.1 ms for Kruskal and from 160.7 to 1470.9 ms for Prim,
and the medians grew 19 and 38 times for only a 5-fold growth of m, much more than m log m predicts. I attribute this
to JVM effects (garbage collection of the many boxed `Integer` objects and `int[]` adjacency entries with the default
heap, cache misses, other programs on the machine), not to the algorithms, so the best (minimum) runs, which grow about
8 times for Kruskal and 12.7 times for Prim, describe the real cost better than the medians at this size. The safe
conclusion is: both methods behave as O(n + m log m) on these sparse random graphs, Kruskal looks somewhat faster on
the largest one, and the exact ratio would need more repetitions on a quieter machine.

**Limitations.** One random graph per size and one seed; random sparse graphs only (`m ≈ 2.5–5·n`); the
JVM, other programs and CPU frequency scaling add noise, which is why 7 runs and medians are used; the
`Integer[]` sort in Kruskal and the boxed `PriorityQueue` entries in Prim are simple rather than optimal
implementations, so the absolute numbers are not a statement about the algorithms themselves; the
measurement includes `Input.edges` conversion that is common to both methods. **Asymptotics:** both methods
are `O(n + m log m)` (lazy Prim with a binary heap; Kruskal because of sorting), so no large gap is expected on
sparse graphs, and the constants (sorting `m` boxed IDs and DSU vs. building `2m` adjacency entries and heap
operations) decide which one is faster in practice.

## 10. Testing

* **`tests/verify.py`**: 39 small hand-made cases (5 DSU, 7 Kruskal, 7 Prim, 6 SCC, 6 topo, 8 DAG).
  For each case the expected values that are unique were derived by hand (see the `note`/comment of the case),
  e.g. DSU `[[4,2],[3,3],[3,3],[3,3],[3,3],[2,3]]`, MST cost `-15` for the negative-weight graph, `3·10^9` for the
  over-32-bit graph. Answers that may legitimately differ (forest IDs, SCC labels, topological order, parent
  edges) are verified by meaning with independent code: minimal cost by a separate Kruskal, SCC partition by
  an independent iterative Tarjan, order by Kahn's algorithm, DAG distances by a separate DP and parent
  edges by "ends in `v`, tight, chain reaches the source".
* **Inputs and outputs**: `tests/my_cases.jsonl` (inputs), `tests/actual_output.jsonl` (actual answers of the
  program, same line order); command: `python tools/run.py --input tests/my_cases.jsonl`.
* **State reset**: all cases + the same cases reversed + all again in one process; all answers valid and the
  first and third pass identical.
* **Checker self-test**: 19 checks show that the checkers **accept** other valid answers (another optimal
  forest, other SCC labels, another topological order, another parent edge for equal paths) and **reject**
  broken ones (cycle in the forest, non-minimal cost, wrong cost value, merged SCCs, duplicate/missing
  condensation pair, backward edge in the order, wrong flag, non-tight or vertex-ID parent, wrong distance).
* **Large / deep inputs** (`--large`, seed 20261024): DSU `n=10^5, q=2.5·10^5`; Kruskal and Prim on random
  `n=10^5, m=2.5·10^5` and on a 10^5 chain + random edges (both must agree); SCC on a 10^5 chain, a 10^5 cycle
  and random edges; topo on a 10^5 chain and the same chain with a back edge; DAG on a 10^5 chain with weights
  `10^9` (distance `≈10^14`) and a random DAG with 2.5·10^5 edges.
* **Route helper**: `tests/RouteTest.java` (table in section 8).

## 11. Sources and AI use

* Algorithms follow the standard textbook descriptions (DSU with path compression and
  union by size, Kruskal, lazy Prim, Kosaraju, three-state DFS topological sort, DAG relaxation in topological
  order).
* **AI tool: Claude (Anthropic).** Contribution: Claude wrote me tests that ran my code and helped me fix errors.
* My own verification: I ran every command from section 1 and the outputs matched what is written here;
  the benchmark numbers in section 9 come from my own run of `python tests/bench.py` on my computer.