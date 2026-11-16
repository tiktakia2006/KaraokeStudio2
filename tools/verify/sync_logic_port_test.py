import sys

passed, failed = 0, []


def check(name, cond, info=""):
    global passed
    if cond:
        passed += 1
    else:
        failed.append(f"{name} {info}")


class ApiError(Exception):
    def __init__(self, retryable):
        self.retryable = retryable


def merge_decide(change, pending, exists_locally, same_as_local):
    kind = change["type"]
    key = None
    if kind == "favorite":
        key = f"favorite:{change['songSlug']}"
    elif kind == "performance" and change.get("clientId"):
        key = f"performance:{change['clientId']}"
    if key is None:
        return "SKIP_UNCHANGED"
    if key in pending:
        return "SKIP_PENDING"
    if change.get("deleted"):
        return "APPLY_DELETE" if exists_locally else "SKIP_UNCHANGED"
    return "SKIP_UNCHANGED" if exists_locally and same_as_local else "APPLY_UPSERT"


class Api:
    def __init__(self):
        self.song_pages, self.pull_pages = [], []
        self.song_requests, self.pull_requests, self.push_batches = [], [], []
        self.push_handler = lambda batch: {"results": [{"index": i, "status": "applied"} for i in range(len(batch))]}
        self.failure = None

    def fetch_songs(self, since):
        if self.failure: raise self.failure
        self.song_requests.append(since)
        return self.song_pages.pop(0) if self.song_pages else {"items": [], "hasMore": False, "nextSince": since}

    def push(self, batch):
        if self.failure: raise self.failure
        self.push_batches.append(batch)
        return self.push_handler(batch)

    def pull(self, since):
        if self.failure: raise self.failure
        self.pull_requests.append(since)
        return self.pull_pages.pop(0) if self.pull_pages else {"changes": [], "hasMore": False, "nextSince": since}


class Store:
    def __init__(self):
        self.queue, self.settled, self.attempts = [], [], []
        self.upserts, self.deleted, self.applied = [], [], []

    def next_batch(self, limit): return self.queue[:limit]
    def settle(self, ids):
        self.settled += ids
        self.queue = [e for e in self.queue if e not in ids]
    def mark_attempt(self, ids): self.attempts += ids
    def has_pending(self): return bool(self.queue)
    def apply_catalog(self, ups, dels): self.upserts += ups; self.deleted += dels
    def apply_user_changes(self, changes): self.applied += changes; return len(changes)


class Cursors:
    def __init__(self, signed_in=True):
        self.signed_in, self.catalog, self.pull = signed_in, 0, 0
        self.error = None


def sync(api, store, cursors, max_pages=500, max_batch=200, max_rounds=50):
    try:
        since, total = cursors.catalog, 0
        for _ in range(max_pages):
            page = api.fetch_songs(since)
            ups = [i for i in page["items"] if not i.get("deleted")]
            gone = [i["slug"] for i in page["items"] if i.get("deleted")]
            if ups or gone: store.apply_catalog(ups, gone)
            total += len(page["items"])
            advanced = page["nextSince"] > since
            if advanced:
                since = page["nextSince"]; cursors.catalog = since
            if not page["hasMore"] or not advanced: break
        report = {"catalog": total, "pushed": 0, "stale": 0, "rejected": 0, "pulled": 0, "deferred": False}
        if not cursors.signed_in: return report
        done = False
        for _ in range(max_rounds):
            batch = store.next_batch(max_batch)
            if not batch: break
            response = api.push(batch)
            settled, retry, answered = [], [], set()
            for r in response["results"]:
                if r["index"] >= len(batch): continue
                answered.add(r["index"]); item = batch[r["index"]]
                if r["status"] == "applied": report["pushed"] += 1; settled.append(item)
                elif r["status"] == "stale": report["stale"] += 1; settled.append(item)
                elif r.get("reason") == "timestamp_in_future": retry.append(item)
                else: report["rejected"] += 1; settled.append(item)
            retry += [item for i, item in enumerate(batch) if i not in answered]
            if settled: store.settle(settled)
            if retry:
                store.mark_attempt(retry); break
        if store.has_pending():
            report["deferred"] = True; return report
        since = cursors.pull
        for _ in range(max_pages):
            page = api.pull(since)
            report["pulled"] += store.apply_user_changes(page["changes"])
            advanced = page["nextSince"] > since
            if advanced:
                since = page["nextSince"]; cursors.pull = since
            if not page["hasMore"] or not advanced: break
        return report
    except ApiError as e:
        cursors.error = e
        raise


def fresh(signed_in=True):
    return Api(), Store(), Cursors(signed_in)


api, store, cur = fresh(False)
api.song_pages = [{"items": [{"slug": "a"}, {"slug": "b"}], "hasMore": True, "nextSince": 5},
                  {"items": [{"slug": "c"}, {"slug": "a", "deleted": True}], "hasMore": False, "nextSince": 9}]
r = sync(api, store, cur)
check("гість: сторінки каталогу", api.song_requests == [0, 5] and cur.catalog == 9)
check("гість: оновлення й видалення", [u["slug"] for u in store.upserts] == ["a", "b", "c"] and store.deleted == ["a"] and r["catalog"] == 4)
check("гість: без push і pull", not api.push_batches and not api.pull_requests)

api, store, cur = fresh(False); cur.catalog = 7
api.song_pages = [{"items": [], "hasMore": True, "nextSince": 7}, {"items": [{"slug": "late"}], "hasMore": False, "nextSince": 8}]
sync(api, store, cur)
check("курсор не рухається: цикл зупиняється", api.song_requests == [7] and not store.upserts)

api, store, cur = fresh()
store.queue = [1, 2, 3]
api.push_handler = lambda b: {"results": [{"index": 0, "status": "applied"}, {"index": 1, "status": "stale"}, {"index": 2, "status": "rejected", "reason": "unknown_song"}]}
api.pull_pages = [{"changes": [{"type": "favorite", "songSlug": "x"}], "hasMore": True, "nextSince": 40},
                  {"changes": [{"type": "favorite", "songSlug": "y"}], "hasMore": False, "nextSince": 50}]
r = sync(api, store, cur)
check("push: усі три вирішені", store.settled == [1, 2, 3] and not store.queue)
check("push: підрахунки", (r["pushed"], r["stale"], r["rejected"]) == (1, 1, 1))
check("pull: курсори й сторінки", api.pull_requests == [0, 40] and cur.pull == 50 and r["pulled"] == 2 and not r["deferred"])

api, store, cur = fresh()
store.queue = list(range(1, 451))
sync(api, store, cur)
check("пакети по 200", [len(b) for b in api.push_batches] == [200, 200, 50] and not store.queue)

api, store, cur = fresh()
store.queue = [1]
api.push_handler = lambda b: {"results": [{"index": 0, "status": "rejected", "reason": "timestamp_in_future"}]}
api.pull_pages = [{"changes": [{"type": "favorite", "songSlug": "x"}], "hasMore": False, "nextSince": 10}]
r = sync(api, store, cur)
check("чужі зміни не застосовуються, поки є власні", r["deferred"] and not api.pull_requests and store.attempts == [1] and not store.settled)

api, store, cur = fresh()
store.queue = [1, 2]
api.push_handler = lambda b: {"results": [{"index": 0, "status": "applied"}]}
r = sync(api, store, cur)
check("без відповіді сервера запис лишається", store.settled == [1] and store.attempts == [2] and r["deferred"] and len(api.push_batches) == 1)

api, store, cur = fresh(); api.failure = ApiError(True)
try:
    sync(api, store, cur); check("помилка мережі пробрасується", False)
except ApiError as e:
    check("помилка мережі пробрасується", e.retryable and cur.error is not None)

check("злиття: відкладена власна зміна", merge_decide({"type": "favorite", "songSlug": "a"}, {"favorite:a"}, True, True) == "SKIP_PENDING")
check("злиття: нове вдалене", merge_decide({"type": "favorite", "songSlug": "a"}, set(), False, False) == "APPLY_UPSERT")
check("злиття: однакове", merge_decide({"type": "performance", "songSlug": "a", "clientId": "p"}, set(), True, True) == "SKIP_UNCHANGED")
check("злиття: видалення існуючого", merge_decide({"type": "favorite", "songSlug": "a", "deleted": True}, set(), True, False) == "APPLY_DELETE")
check("злиття: видалення неіснуючого", merge_decide({"type": "favorite", "songSlug": "a", "deleted": True}, set(), False, False) == "SKIP_UNCHANGED")
check("злиття: невідомий тип", merge_decide({"type": "other", "songSlug": "a"}, set(), False, False) == "SKIP_UNCHANGED")

print(f"Пройдено: {passed}, провалено: {len(failed)}")
for item in failed: print("  ПРОВАЛ:", item)
sys.exit(1 if failed else 0)
