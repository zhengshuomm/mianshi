from typing import List, Optional, Dict

class ValueWithTTL:
    def __init__(self, value: str, setTimestamp: int, ttl: Optional[int]):
        self.value = value
        self.setTimestamp = setTimestamp
        self.ttl = ttl  # None means no TTL

    # Determines if the field is valid at the provided timestamp
    def isAliveAt(self, queryTimestamp: int) -> bool:
        if self.ttl is None:
            return True
        return queryTimestamp >= self.setTimestamp and queryTimestamp < self.setTimestamp + self.ttl

class Backup:
    def __init__(self, timestamp: int, dbSnapshot: Dict[str, Dict[str, ValueWithTTL]]):
        self.timestamp = timestamp
        self.dbSnapshot = dbSnapshot

class InMemoryDB:
    def __init__(self):
        self.db = {}
        self.backups: List[Backup] = []

    def setData(self, key: str, field: str, value: str) -> None:
        # 使用 setdefault 自动初始化缺失的 key
        self.db.setdefault(key, {})[field] = ValueWithTTL(value, 0, None)

    def getData(self, key: str, field: str) -> str:
        # 链式 get，如果 key 不存在返回空字典，如果 field 不存在返回空字符串
        entry = self.db.get(key, {}).get(field)
        if entry is not None:
            return entry.value

    def deleteData(self, key: str, field: str) -> bool:
        if key in self.db and field in self.db[key]:
            self.db[key].pop(field)
            # 如果该 key 下没有其他 field 了，清理掉这个 key
            if not self.db[key]:
                self.db.pop(key)
            return True
        return False

    def scanData(self, key: str) -> List[str]:
        # 使用列表推导式和 sorted 处理 items
        fields = self.db.get(key, {})
        return [f"{f}({fields[f].value})" for f in sorted(fields.keys())]

    def scanDataByPrefix(self, key: str, prefix: str) -> List[str]:
        # 在列表推导式中加入 prefix 过滤条件
        fields = self.db.get(key, {})
        return [f"{f}({fields[f].value})" for f in sorted(fields.keys()) if f.startswith(prefix)]

    def setDataAt(self, key: str, field: str, value: str, timestamp: int) -> None:
        if key not in self.db:
            self.db[key] = {}
        self.db[key][field] = ValueWithTTL(value, timestamp, None)

    def setDataAtWithTtl(self, key: str, field: str, value: str, timestamp: int, ttl: int) -> None:
        if key not in self.db:
            self.db[key] = {}
        self.db[key][field] = ValueWithTTL(value, timestamp, ttl)

    def deleteDataAt(self, key: str, field: str, timestamp: int) -> bool:
        fields = self.db.get(key)
        if fields is None:
            return False

        entry = fields.get(field)

        if entry is None or not entry.isAliveAt(timestamp):
            return False
        del fields[field]

        if not fields:
            del self.db[key]
        return True

    def getDataAt(self, key: str, field: str, timestamp: int) -> str:
        fields = self.db.get(key)
        if fields is None:
            return ""

        entry = fields.get(field)

        if entry is not None and entry.isAliveAt(timestamp):
            return entry.value if entry.value is not None else ""
        return ""

    def scanDataAt(self, key: str, timestamp: int) -> List[str]:
        fields = self.db.get(key)
        if fields is None:
            return []

        keys = list(fields.keys())
        keys.sort()
        result = []
        
        for field in keys:
            entry = fields[field]
            if entry is not None and entry.isAliveAt(timestamp):
                result.append(field + "(" + entry.value + ")")
        return result

    def scanDataByPrefixAt(self, key: str, prefix: str, timestamp: int) -> List[str]:
        fields = self.db.get(key)
        if fields is None:
            return []

        keys = []

        for field in fields.keys():
            if field.startswith(prefix):
                keys.append(field)

        keys.sort()
        result = []
        
        for field in keys:
            entry = fields[field]
            if entry is not None and entry.isAliveAt(timestamp):
                result.append(field + "(" + entry.value + ")")
        return result

    def backup(self, timestamp: int) -> int:
        # Only backup fields alive at this timestamp, and calculate remaining TTL (if any)
        snapshot = {}
        count = 0

        for key, fieldMap in self.db.items():
            newFieldMap = {}
            for field, v in fieldMap.items():
                if v.isAliveAt(timestamp):
                    remainingTtl = None if v.ttl is None else (v.setTimestamp + v.ttl - timestamp)
                    if remainingTtl is None or remainingTtl > 0:
                        newFieldMap[field] = ValueWithTTL(v.value, timestamp, remainingTtl)

            if newFieldMap:
                snapshot[key] = newFieldMap
                count += 1

        self.backups.append(Backup(timestamp, snapshot))
        return count

    def restore(self, timestamp: int, timestampToRestore: int) -> None:
        # Find the latest backup at or before timestampToRestore
        chosen = None
        for b in self.backups:
            if b.timestamp <= timestampToRestore and (chosen is None or b.timestamp > chosen.timestamp):
                chosen = b

        if chosen is None:
            return

        # Deep copy, adjust all fields' setTimestamp to now (timestamp), and recalc TTLs
        self.db.clear()

        for key, fieldMap in chosen.dbSnapshot.items():
            newFieldMap = {}
            for field, v in fieldMap.items():
                # At restore, setTimestamp = now, TTL = remaining TTL at backup (may be None)
                newFieldMap[field] = ValueWithTTL(v.value, timestamp, v.ttl)
            
            self.db[key] = newFieldMap




# from typing import List, Optional, Dict

# class ValueWithTTL:
#     def __init__(self, value: str, set_ts: int, ttl: Optional[int]):
#         self.value, self.set_ts, self.ttl = value, set_ts, ttl

#     def is_alive(self, now: int) -> bool:
#         return self.ttl is None or (self.set_ts <= now < self.set_ts + self.ttl)

# class InMemoryDB:
#     def __init__(self):
#         self.db: Dict[str, Dict[str, ValueWithTTL]] = {}
#         self.backups: List[Dict] = [] # 存储 (timestamp, snapshot)

#     # --- 内部辅助：统一处理设置逻辑 ---
#     def _set(self, key: str, field: str, value: str, ts: int, ttl: Optional[int] = None):
#         self.db.setdefault(key, {})[field] = ValueWithTTL(value, ts, ttl)

#     # --- 基础 API 映射 ---
#     def setData(self, k, f, v): self._set(k, f, v, 0)
#     def setDataAt(self, k, f, v, ts): self._set(k, f, v, ts)
#     def setDataAtWithTtl(self, k, f, v, ts, ttl): self._set(k, f, v, ts, ttl)

#     def getDataAt(self, key: str, field: str, ts: int) -> str:
#         entry = self.db.get(key, {}).get(field)
#         return entry.value if entry and entry.is_alive(ts) else ""

#     def deleteDataAt(self, key: str, field: str, ts: int) -> bool:
#         fields = self.db.get(key, {})
#         if field in fields and fields[field].is_alive(ts):
#             fields.pop(field)
#             if not fields: self.db.pop(key, None)
#             return True
#         return False

#     # --- 扫描逻辑整合 ---
#     def _scan(self, key: str, ts: int, prefix: str = "") -> List[str]:
#         fields = self.db.get(key, {})
#         # 过滤：存活且匹配前缀
#         alive = [(f, v.value) for f, v in fields.items() 
#                  if v.is_alive(ts) and f.startswith(prefix)]
#         return [f"{f}({v})" for f, v in sorted(alive)]

#     def scanDataAt(self, k, ts): return self._scan(k, ts)
#     def scanDataByPrefixAt(self, k, p, ts): return self._scan(k, ts, p)

#     # --- 备份与恢复 ---
#     def backup(self, ts: int) -> int:
#         snapshot = {}
#         for k, fields in self.db.items():
#             # 仅备份当前存活的字段，并计算剩余 TTL
#             alive_fields = {
#                 f: ValueWithTTL(v.value, ts, (v.set_ts + v.ttl - ts) if v.ttl else None)
#                 for f, v in fields.items() if v.is_alive(ts)
#             }
#             if alive_fields: snapshot[k] = alive_fields
        
#         self.backups.append({"ts": ts, "data": snapshot})
#         return len(snapshot)

#     def restore(self, now: int, target_ts: int) -> None:
#         # 寻找最近的备份：满足 b.ts <= target_ts 的最大 ts 备份
#         valid_backups = [b for b in self.backups if b["ts"] <= target_ts]
#         if not valid_backups: return
        
#         chosen = max(valid_backups, key=lambda b: b["ts"])
#         self.db = {}
#         # 恢复时，所有字段的起始时间重置为当前时间 'now'
#         for k, fields in chosen["data"].items():
#             self.db[k] = {f: ValueWithTTL(v.value, now, v.ttl) for f, v in fields.items()}

#     # 兼容旧版不带时间的接口
#     def getData(self, k, f): return self.getDataAt(k, f, 0)
#     def deleteData(self, k, f): return self.deleteDataAt(k, f, 0)
#     def scanData(self, k): return self._scan(k, 0)
#     def scanDataByPrefix(self, k, p): return self._scan(k, 0, p)