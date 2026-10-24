#!/usr/bin/env python3
"""
My own tests for Assignment 3.

Run from the repository root:
    python tests/verify.py            # small hand-checked cases + mixed/reset run + checker self-test
    python tests/verify.py --large    # also the large / deep inputs (seeded, generated on the fly)

What it does
  1. writes tests/my_cases.jsonl (inputs of the small cases),
  2. runs them through `python tools/run.py --input ...` (the real Java program),
  3. saves the raw answers to tests/actual_output.jsonl,
  4. checks every answer:
       - against the answer I derived BY HAND (field `expect`, exact values), and
       - semantically (valid forest, valid SCC partition, valid order, valid parents),
         so any other correct answer is accepted and a wrong one is rejected,
  5. repeats everything in a different order in ONE process (state reset check),
  6. self-tests the checkers: they must accept another valid answer and reject broken ones.
"""
import json
import random
import subprocess
import sys
from collections import deque
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests"
W = 1_000_000_000


# ------------------------------------------------------------------ cases
# expect = values I computed by hand (see the comment above each case).
# Only fields that are uniquely determined are listed in `expect`.
def small_cases():
    C = []

    def add(name, inp, expect=None, note=""):
        C.append({"name": name, "input": inp, "expect": expect or {}, "note": note})

    # ---- DSU ----
    add("dsu n=0", {"task": "dsu", "n": 0, "operations": []}, {"states": []},
        "n=0 -> no operations, states=[]")
    add("dsu single vertex self-union", {"task": "dsu", "n": 1, "operations": [[0, 0]]},
        {"states": [[1, 1]]}, "self union changes nothing")
    add("dsu no operations", {"task": "dsu", "n": 3, "operations": []}, {"states": []})
    add("dsu repeats and self unions",
        {"task": "dsu", "n": 5, "operations": [[0, 1], [1, 2], [0, 2], [3, 3], [2, 1], [3, 4]]},
        {"states": [[4, 2], [3, 3], [3, 3], [3, 3], [3, 3], [2, 3]]},
        "01 merge ->4 comps,max2; 12 ->3,3; 02, 33, 21 repeat -> unchanged; 34 ->2,3")
    add("dsu union by size",
        {"task": "dsu", "n": 6, "operations": [[0, 1], [2, 3], [0, 2], [4, 5], [4, 0]]},
        {"states": [[5, 2], [4, 2], [3, 4], [2, 4], [1, 6]]},
        "{0,1}+{2,3}=4 ; {4,5}; then 2+4=6 -> one component")

    # ---- Kruskal / Prim (same graphs, both tasks) ----
    def mst(name, n, edges, cost, comps, note=""):
        for t in ("kruskal", "prim"):
            add(f"{t} {name}", {"task": t, "n": n, "edges": edges},
                {"cost": cost, "components": comps, "edge_count": n - comps}, note)

    mst("n=0", 0, [], 0, 0, "n=0 -> cost 0, no edges, 0 components")
    mst("isolated vertices", 3, [], 0, 3)
    mst("disconnected", 6, [[0, 1, 1], [1, 2, 2], [0, 2, 3], [3, 4, 5]], 8, 3,
        "{0,1,2}: 1+2, {3,4}: 5, {5} isolated -> 8, 3 components")
    mst("loop, parallel edges, ties", 3,
        [[0, 0, -5], [0, 1, 4], [0, 1, 2], [1, 2, 2], [0, 2, 2]], 4, 1,
        "loop must not be chosen; any two of the three weight-2 edges -> cost 4")
    mst("negative weights", 4, [[0, 1, -3], [1, 2, -2], [2, 3, -1], [0, 3, -10], [0, 2, 5]], -15, 1,
        "take -10, -3, -2 (edge -1 would close a cycle)")
    mst("sum over 32 bit", 4,
        [[0, 1, W], [1, 2, W], [2, 3, W], [0, 3, W], [0, 2, W]], 3 * W, 1,
        "3 edges * 1e9 = 3e9 > 2^31")
    mst("very negative weights", 3, [[0, 1, -W], [1, 2, -W], [0, 2, -W]], -2 * W, 1)

    # ---- SCC ----
    add("scc n=0", {"task": "scc", "n": 0, "edges": []}, {"groups": [], "cond_count": 0})
    add("scc isolated", {"task": "scc", "n": 3, "edges": []},
        {"groups": [[0], [1], [2]], "cond_count": 0})
    add("scc loops duplicates",
        {"task": "scc", "n": 4,
         "edges": [[0, 1], [1, 0], [1, 0], [2, 2], [1, 2], [0, 2], [2, 3]]},
        {"groups": [[0, 1], [2], [3]], "cond_count": 2},
        "{0,1},{2},{3}; pairs AB and BC exactly once, loop/inner edges dropped")
    add("scc one big cycle", {"task": "scc", "n": 4, "edges": [[0, 1], [1, 2], [2, 3], [3, 0]]},
        {"groups": [[0, 1, 2, 3]], "cond_count": 0})
    add("scc two cycles two cross edges",
        {"task": "scc", "n": 6,
         "edges": [[0, 1], [1, 2], [2, 0], [3, 4], [4, 5], [5, 3], [2, 3], [1, 4]]},
        {"groups": [[0, 1, 2], [3, 4, 5]], "cond_count": 1},
        "two cross edges give the SAME pair -> listed once")
    add("scc DAG all singletons",
        {"task": "scc", "n": 4, "edges": [[0, 1], [1, 2], [0, 2], [2, 3]]},
        {"groups": [[0], [1], [2], [3]], "cond_count": 4})

    # ---- topo ----
    add("topo n=0", {"task": "topo", "n": 0, "edges": []}, {"acyclic": True})
    add("topo self loop", {"task": "topo", "n": 1, "edges": [[0, 0]]}, {"acyclic": False})
    add("topo cycle not reachable from 0",
        {"task": "topo", "n": 5, "edges": [[0, 1], [2, 3], [3, 4], [4, 2]]}, {"acyclic": False})
    add("topo diamond + parallel edge",
        {"task": "topo", "n": 4, "edges": [[0, 1], [0, 2], [1, 3], [2, 3], [0, 1]]},
        {"acyclic": True}, "valid orders: 0,1,2,3 and 0,2,1,3")
    add("topo isolated", {"task": "topo", "n": 3, "edges": []}, {"acyclic": True})
    add("topo edge to finished vertex is not a cycle",
        {"task": "topo", "n": 3, "edges": [[0, 1], [1, 2], [0, 2]]}, {"acyclic": True},
        "0->2 goes to an already finished vertex: no cycle")

    # ---- DAG ----
    add("dag single vertex", {"task": "dag", "n": 1, "source": 0, "edges": []},
        {"acyclic": True, "distance": [0], "parent_edge": [-1]})
    add("dag published example",
        {"task": "dag", "n": 5, "source": 0,
         "edges": [[0, 1, 3], [0, 2, 8], [1, 2, -7], [2, 3, 4]]},
        {"acyclic": True, "distance": [0, 3, -4, 0, None], "parent_edge": [-1, 0, 2, 3, -1]},
        "0->1 =3 ; 0->1->2 = 3-7 = -4 (better than 8) ; 2->3 = 0 ; vertex 4 unreachable")
    add("dag cycle not reachable from source",
        {"task": "dag", "n": 4, "source": 0, "edges": [[0, 1, 1], [2, 3, 1], [3, 2, 1]]},
        {"acyclic": False, "distance": [], "parent_edge": []})
    add("dag parallel edges and equal paths",
        {"task": "dag", "n": 3, "source": 0, "edges": [[0, 1, 5], [0, 1, 2], [1, 2, 1], [0, 2, 3]]},
        {"acyclic": True, "distance": [0, 2, 3], "parent_edge_of": {"1": [1]}},
        "to 1: edge 1 (w=2). to 2: 0->2 (id 3) and 0->1->2 (id 2) both cost 3 -> either parent is fine")
    add("dag unreachable vs distance -1",
        {"task": "dag", "n": 3, "source": 0, "edges": [[0, 1, -1]]},
        {"acyclic": True, "distance": [0, -1, None], "parent_edge": [-1, 0, -1]},
        "distance -1 is a real value; vertex 2 is null")
    add("dag source is not 0, edges into the unreachable part",
        {"task": "dag", "n": 4, "source": 2, "edges": [[0, 2, 5], [2, 3, -4], [1, 0, 1]]},
        {"acyclic": True, "distance": [None, None, 0, -4], "parent_edge": [-1, -1, -1, 1]})
    add("dag 64 bit distances",
        {"task": "dag", "n": 4, "source": 0, "edges": [[0, 1, W], [1, 2, W], [2, 3, W]]},
        {"acyclic": True, "distance": [0, W, 2 * W, 3 * W], "parent_edge": [-1, 0, 1, 2]})
    add("dag self loop", {"task": "dag", "n": 2, "source": 0, "edges": [[0, 1, 1], [1, 1, 1]]},
        {"acyclic": False, "distance": [], "parent_edge": []})
    return C


# ------------------------------------------------------- reference helpers
def kahn(n, edges):
    adj = [[] for _ in range(n)]
    indeg = [0] * n
    for e in edges:
        adj[e[0]].append(e[1])
        indeg[e[1]] += 1
    q = deque(i for i in range(n) if indeg[i] == 0)
    order = []
    while q:
        u = q.popleft()
        order.append(u)
        for v in adj[u]:
            indeg[v] -= 1
            if indeg[v] == 0:
                q.append(v)
    return order if len(order) == n else None


class DSU:
    def __init__(self, n):
        self.p = list(range(n))

    def find(self, x):
        while self.p[x] != x:
            self.p[x] = self.p[self.p[x]]
            x = self.p[x]
        return x

    def union(self, a, b):
        a, b = self.find(a), self.find(b)
        if a == b:
            return False
        self.p[a] = b
        return True


def ref_mst(n, edges):
    d = DSU(n)
    cost = 0
    taken = 0
    for u, v, w in sorted(edges, key=lambda e: e[2]):
        if d.union(u, v):
            cost += w
            taken += 1
    return cost, n - taken


def ref_scc_groups(n, edges):
    """Iterative Tarjan (independent of the Kosaraju in the Java code)."""
    adj = [[] for _ in range(n)]
    for u, v in edges:
        adj[u].append(v)
    index = [-1] * n
    low = [0] * n
    on = [False] * n
    comp = [-1] * n
    st = []
    counter = 0
    cc = 0
    for s in range(n):
        if index[s] != -1:
            continue
        work = [(s, 0)]
        index[s] = low[s] = counter
        counter += 1
        st.append(s)
        on[s] = True
        while work:
            u, i = work.pop()
            if i < len(adj[u]):
                work.append((u, i + 1))
                v = adj[u][i]
                if index[v] == -1:
                    index[v] = low[v] = counter
                    counter += 1
                    st.append(v)
                    on[v] = True
                    work.append((v, 0))
                elif on[v]:
                    low[u] = min(low[u], index[v])
            else:
                if work:
                    p = work[-1][0]
                    low[p] = min(low[p], low[u])
                if low[u] == index[u]:
                    while True:
                        x = st.pop()
                        on[x] = False
                        comp[x] = cc
                        if x == u:
                            break
                    cc += 1
    return comp


# ----------------------------------------------------------------- checkers
class Bad(Exception):
    pass


def need(cond, msg):
    if not cond:
        raise Bad(msg)


def check_dsu(req, ans):
    n = req["n"]
    d = DSU(n)
    size = [1] * n
    comps = n
    largest = 1 if n else 0
    states = []
    for u, v in req["operations"]:
        a, b = d.find(u), d.find(v)
        if a != b:
            d.p[a] = b
            size[b] += size[a]
            comps -= 1
            largest = max(largest, size[b])
        states.append([comps, largest])
    need(ans["states"] == states, "states differ from the naive simulation")


def check_forest(req, ans, expect_cost=None, expect_comps=None):
    n, edges = req["n"], req["edges"]
    ids = ans["edge_ids"]
    need(len(set(ids)) == len(ids), "repeated edge id")
    d = DSU(n)
    cost = 0
    for i in ids:
        need(0 <= i < len(edges), "edge id out of range")
        u, v, w = edges[i]
        need(u != v, "loop selected")
        need(d.union(u, v), "selected edges contain a cycle")
        cost += w
    rc, rcomps = ref_mst(n, edges)
    need(ans["cost"] == cost, "cost != sum of chosen weights")
    need(cost == rc, f"cost {cost} is not minimal (reference {rc})")
    need(ans["components"] == rcomps, "wrong number of components")
    need(len(ids) == n - rcomps, "forest must have n-c edges")


def check_scc(req, ans):
    n, edges = req["n"], req["edges"]
    comp = ans["component"]
    need(len(comp) == n, "component length")
    ref = ref_scc_groups(n, edges)
    mapping = {}
    for a, b in zip(comp, ref):
        need(mapping.setdefault(a, b) == b, "one label covers two SCCs")
    need(len(set(mapping.values())) == len(mapping), "one SCC has two labels")
    expected = {(comp[u], comp[v]) for u, v in edges if comp[u] != comp[v]}
    got = [tuple(p) for p in ans["condensation"]]
    need(len(got) == len(set(got)), "duplicate pair in condensation")
    need(set(got) == expected, "condensation is not exact")


def check_topo(req, ans):
    n, edges = req["n"], req["edges"]
    ref = kahn(n, edges)
    need(ans["acyclic"] == (ref is not None), "wrong acyclic flag")
    if ref is None:
        need(ans["order"] == [], "order must be [] for a cyclic graph")
    else:
        order = ans["order"]
        need(sorted(order) == list(range(n)), "order is not a permutation")
        pos = {v: i for i, v in enumerate(order)}
        need(all(pos[u] < pos[v] for u, v in edges), "an edge goes backwards")


def check_dag(req, ans):
    n, edges, s = req["n"], req["edges"], req["source"]
    order = kahn(n, [[u, v] for u, v, _ in edges])
    need(ans["acyclic"] == (order is not None), "wrong acyclic flag")
    if order is None:
        need(ans["distance"] == [] and ans["parent_edge"] == [], "arrays must be empty")
        return
    INF = None
    dist = [INF] * n
    dist[s] = 0
    adj = [[] for _ in range(n)]
    for u, v, w in edges:
        adj[u].append((v, w))
    for u in order:
        if dist[u] is None:
            continue
        for v, w in adj[u]:
            if dist[v] is None or dist[u] + w < dist[v]:
                dist[v] = dist[u] + w
    need(ans["distance"] == dist, "distances differ from the reference")
    par = ans["parent_edge"]
    need(len(par) == n and par[s] == -1, "parent_edge shape / source")
    for v in range(n):
        if v == s:
            continue
        if dist[v] is None:
            need(par[v] == -1, "unreachable vertex must have parent -1")
            continue
        e = par[v]
        need(0 <= e < len(edges) and edges[e][1] == v, "parent edge does not end at v")
        u, _, w = edges[e]
        need(dist[u] is not None and dist[u] + w == dist[v], "parent edge is not tight")
    # walking up must reach the source (no loops); `good` memoises checked vertices
    good = {s}
    for v in range(n):
        if dist[v] is None or v in good:
            continue
        path = []
        x = v
        while x not in good:
            need(len(path) <= n, "parent chain loops")
            path.append(x)
            x = edges[par[x]][0]
        good.update(path)


def check_expect(req, ans, expect):
    t = req["task"]
    for key, val in expect.items():
        if key in ("cost", "components", "acyclic", "distance", "parent_edge", "states"):
            need(ans.get(key) == val, f"{key}: expected {val}, got {ans.get(key)}")
        elif key == "edge_count":
            need(len(ans["edge_ids"]) == val, f"edge_ids count: expected {val}")
        elif key == "groups":
            groups = {}
            for v, c in enumerate(ans["component"]):
                groups.setdefault(c, []).append(v)
            need(sorted(map(sorted, groups.values())) == sorted(map(sorted, val)),
                 "SCC groups differ from the hand answer")
        elif key == "cond_count":
            need(len(ans["condensation"]) == val, f"condensation size expected {val}")
        elif key == "parent_edge_of":
            for v, allowed in val.items():
                need(ans["parent_edge"][int(v)] in allowed, f"parent of {v} not in {allowed}")


CHECKERS = {"dsu": check_dsu, "kruskal": check_forest, "prim": check_forest,
            "scc": check_scc, "topo": check_topo, "dag": check_dag}


def check_one(req, ans, expect):
    CHECKERS[req["task"]](req, ans)
    check_expect(req, ans, expect)


# ------------------------------------------------------------- run program
def run_program(requests, name):
    path = TESTS / name
    with open(path, "w", encoding="utf-8") as f:
        for r in requests:
            f.write(json.dumps(r, separators=(",", ":")) + "\n")
    res = subprocess.run([sys.executable, str(ROOT / "tools" / "run.py"), "--input", str(path)],
                         capture_output=True, text=True, encoding="utf-8", timeout=600, cwd=ROOT)
    if res.returncode:
        raise SystemExit(f"run.py failed:\n{res.stdout}\n{res.stderr}")
    lines = res.stdout.splitlines()
    if len(lines) != len(requests):
        raise SystemExit(f"expected {len(requests)} answers, got {len(lines)}\n{res.stderr}")
    return [json.loads(l) for l in lines]


failures = 0


def report(ok, name, why=""):
    global failures
    if ok:
        print(f"PASS {name}")
    else:
        failures += 1
        print(f"FAIL {name}: {why}")


# --------------------------------------------------------------- self-test
def self_test():
    print("--- checker self-test (valid alternatives accepted, broken answers rejected) ---")

    def expect_ok(name, fn, *a):
        try:
            fn(*a)
            report(True, f"checker accepts: {name}")
        except Bad as e:
            report(False, f"checker accepts: {name}", str(e))

    def expect_bad(name, fn, *a):
        try:
            fn(*a)
            report(False, f"checker rejects: {name}", "broken answer was accepted")
        except Bad:
            report(True, f"checker rejects: {name}")

    tri = {"task": "kruskal", "n": 3, "edges": [[0, 1, 2], [1, 2, 2], [0, 2, 2]]}
    expect_ok("MST ids [0,1]", check_forest, tri, {"cost": 4, "edge_ids": [0, 1], "components": 1})
    expect_ok("MST ids [2,0] (other optimum)", check_forest, tri,
              {"cost": 4, "edge_ids": [2, 0], "components": 1})
    expect_bad("MST with a cycle", check_forest, tri, {"cost": 6, "edge_ids": [0, 1, 2], "components": 1})
    expect_bad("MST not minimal", check_forest,
               {"task": "prim", "n": 3, "edges": [[0, 1, 1], [1, 2, 1], [0, 2, 5]]},
               {"cost": 6, "edge_ids": [0, 2], "components": 1})
    expect_bad("MST wrong cost value", check_forest, tri, {"cost": 5, "edge_ids": [0, 1], "components": 1})

    sc = {"task": "scc", "n": 3, "edges": [[0, 1], [1, 0], [1, 2]]}
    expect_ok("SCC labels 5,5,7", check_scc, sc, {"component": [5, 5, 7], "condensation": [[5, 7]]})
    expect_ok("SCC labels 9,9,0", check_scc, sc, {"component": [9, 9, 0], "condensation": [[9, 0]]})
    expect_bad("SCC merged two components", check_scc, sc, {"component": [1, 1, 1], "condensation": []})
    expect_bad("SCC duplicate pair", check_scc, sc,
               {"component": [5, 5, 7], "condensation": [[5, 7], [5, 7]]})
    expect_bad("SCC missing pair", check_scc, sc, {"component": [5, 5, 7], "condensation": []})

    tp = {"task": "topo", "n": 4, "edges": [[0, 2], [1, 2], [2, 3]]}
    expect_ok("topo [1,0,2,3]", check_topo, tp, {"acyclic": True, "order": [1, 0, 2, 3]})
    expect_ok("topo [0,1,2,3]", check_topo, tp, {"acyclic": True, "order": [0, 1, 2, 3]})
    expect_bad("topo edge backwards", check_topo, tp, {"acyclic": True, "order": [2, 0, 1, 3]})
    expect_bad("topo says cyclic for DAG", check_topo, tp, {"acyclic": False, "order": []})

    dg = {"task": "dag", "n": 3, "source": 0, "edges": [[0, 1, 5], [0, 1, 2], [1, 2, 1], [0, 2, 3]]}
    expect_ok("dag parent via 0->2", check_dag, dg,
              {"acyclic": True, "distance": [0, 2, 3], "parent_edge": [-1, 1, 3]})
    expect_ok("dag parent via 1->2", check_dag, dg,
              {"acyclic": True, "distance": [0, 2, 3], "parent_edge": [-1, 1, 2]})
    expect_bad("dag parent not tight (edge 0 costs 5)", check_dag, dg,
               {"acyclic": True, "distance": [0, 2, 3], "parent_edge": [-1, 0, 3]})
    expect_bad("dag wrong distance", check_dag, dg,
               {"acyclic": True, "distance": [0, 5, 3], "parent_edge": [-1, 0, 3]})
    expect_bad("dag vertex id instead of edge id", check_dag, dg,
               {"acyclic": True, "distance": [0, 2, 3], "parent_edge": [-1, 0, 1]})


# --------------------------------------------------------------- large data
def large_cases():
    rnd = random.Random(20261024)
    N, M = 100000, 250000
    C = []

    def add(name, inp):
        C.append({"name": name, "input": inp, "expect": {}, "note": ""})

    add("large dsu n=1e5 q=2.5e5",
        {"task": "dsu", "n": N, "operations": [[rnd.randrange(N), rnd.randrange(N)] for _ in range(250000)]})
    wedges = [[rnd.randrange(N), rnd.randrange(N), rnd.randint(-W, W)] for _ in range(M)]
    add("large kruskal random", {"task": "kruskal", "n": N, "edges": wedges})
    add("large prim random", {"task": "prim", "n": N, "edges": wedges})
    path = [[i, i + 1, rnd.randint(-W, W)] for i in range(N - 1)]
    extra = [[rnd.randrange(N), rnd.randrange(N), rnd.randint(-W, W)] for _ in range(M - len(path))]
    add("large kruskal chain+random", {"task": "kruskal", "n": N, "edges": path + extra})
    add("large prim chain+random", {"task": "prim", "n": N, "edges": path + extra})
    chain = [[i, i + 1] for i in range(N - 1)]
    add("deep scc chain 1e5", {"task": "scc", "n": N, "edges": chain})
    add("deep scc cycle 1e5", {"task": "scc", "n": N, "edges": chain + [[N - 1, 0]]})
    add("large scc random",
        {"task": "scc", "n": N, "edges": [[rnd.randrange(N), rnd.randrange(N)] for _ in range(M)]})
    add("deep topo chain 1e5", {"task": "topo", "n": N, "edges": chain})
    add("deep topo chain + back edge", {"task": "topo", "n": N, "edges": chain + [[N - 1, 0]]})
    wchain = [[i, i + 1, W] for i in range(N - 1)]
    add("deep dag chain 1e9 weights", {"task": "dag", "n": N, "source": 0, "edges": wchain})
    dag_edges = []
    for _ in range(M):
        a, b = rnd.randrange(N), rnd.randrange(N)
        if a == b:
            b = (a + 1) % N if a + 1 < N else a - 1
        u, v = min(a, b), max(a, b)
        dag_edges.append([u, v, rnd.randint(-W, W)])
    add("large dag random", {"task": "dag", "n": N, "source": 0, "edges": dag_edges})
    return C


def run_group(cases, file_name, label):
    answers = run_program([c["input"] for c in cases], file_name)
    for c, a in zip(cases, answers):
        try:
            check_one(c["input"], a, c["expect"])
            report(True, f"{label}: {c['name']}")
        except Bad as e:
            report(False, f"{label}: {c['name']}", str(e))
    return answers


def main():
    cases = small_cases()
    answers = run_group(cases, "my_cases.jsonl", "case")
    with open(TESTS / "actual_output.jsonl", "w", encoding="utf-8") as f:
        for a in answers:
            f.write(json.dumps(a, separators=(",", ":")) + "\n")

    print("--- mixed batch in ONE process: all cases, reversed, all again (state reset) ---")
    mixed = cases + list(reversed(cases)) + cases
    mixed_answers = run_program([c["input"] for c in mixed], "mixed_cases.jsonl")
    ok = True
    for c, a in zip(mixed, mixed_answers):
        try:
            check_one(c["input"], a, c["expect"])
        except Bad as e:
            ok = False
            report(False, f"mixed: {c['name']}", str(e))
    k = len(cases)
    same = mixed_answers[:k] == mixed_answers[-k:]
    report(ok, "mixed batch: every answer valid")
    report(same, "mixed batch: first pass == third pass (no leaked state)")

    self_test()

    if "--large" in sys.argv:
        print("--- large / deep inputs (seed 20261024) ---")
        big = large_cases()
        # kruskal and prim run on the same graph: costs must be identical
        results = {}
        for c in big:
            a = run_program([c["input"]], "large_tmp.jsonl")[0]
            try:
                check_one(c["input"], a, c["expect"])
                report(True, f"large: {c['name']}")
                results[c["name"]] = a
            except Bad as e:
                report(False, f"large: {c['name']}", str(e))
        for suffix in ("random", "chain+random"):
            k_, p_ = results.get(f"large kruskal {suffix}"), results.get(f"large prim {suffix}")
            if k_ and p_:
                report(k_["cost"] == p_["cost"] and k_["components"] == p_["components"],
                       f"large: kruskal and prim agree ({suffix})")
        (TESTS / "large_tmp.jsonl").unlink(missing_ok=True)

    print()
    print("ALL TESTS PASSED" if not failures else f"{failures} CHECK(S) FAILED")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
