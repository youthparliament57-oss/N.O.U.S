## 2026-09-03 - O(1) Pre-Indexed Lookups in SkillRegistry

**Learning:** `SkillRegistry` lookups (`findSkillsForIntent`, `findById`) were performing $O(N)$ linear scans on every intent routing check. Pre-indexing skills into immutable hash maps (`skillsById` and `skillsByIntent`) inside a volatile `CacheHolder` reduces lookup overhead to $O(1)$ without lock contention.
**Action:** When managing collections that are queried frequently by key/category, pre-index them into immutable maps in a volatile cache holder to avoid repeated linear iterations on high-frequency code paths.
