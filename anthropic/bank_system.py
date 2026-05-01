import math
from typing import List, Optional

class Payment:
    def __init__(self, id, ts, amount, cashback):
        self.id, self.ts, self.amount, self.cashback = id, ts, amount, cashback
        self.processed = False

class BankingSystem:
    def __init__(self):
        self.accounts = {}              # accountId -> current_balance
        self.outgoingTransactions = {}  # accountId -> total_spend
        self.payments = {}              # accountId -> [Payment]
        self.history = {}               # accountId -> [(timestamp, delta)]
        self.creationTime = {}          # accountId -> timestamp
        self.mergedAt = {}              # accountId -> timestamp_when_merged
        self.paymentCounter = 0
        self.MS_PER_DAY = 86400000

    def _is_active(self, accId):
        return accId in self.accounts and accId not in self.mergedAt

    def _record(self, accId, ts, delta):
        self.accounts[accId] += delta
        self.history[accId].append((ts, delta))

    def createAccount(self, ts, accId):
        if accId in self.accounts: return False
        self.accounts[accId] = 0
        self.outgoingTransactions[accId] = 0
        self.payments[accId] = []
        self.history[accId] = []
        self.creationTime[accId] = ts
        return True

    def processCashback(self, ts, accId):
        for p in self.payments[accId]:
            if not p.processed and ts >= p.ts + self.MS_PER_DAY:
                self._record(accId, p.ts + self.MS_PER_DAY, p.cashback)
                p.processed = True

    def deposit(self, ts, accId, amount):
        if not self._is_active(accId): return -1
        self.processCashback(ts, accId)
        self._record(accId, ts, amount)
        return self.accounts[accId]

    def transfer(self, ts, src, dst, amount):
        if not self._is_active(src) or not self._is_active(dst) or src == dst: return -1
        self.processCashback(ts, src); self.processCashback(ts, dst)
        if self.accounts[src] < amount: return -1
        self._record(src, ts, -amount)
        self._record(dst, ts, amount)
        self.outgoingTransactions[src] += amount
        return self.accounts[src]

    def pay(self, ts, accId, amount):
        if not self._is_active(accId): return ""
        self.processCashback(ts, accId)
        if self.accounts[accId] < amount: return ""
        self._record(accId, ts, -amount)
        self.outgoingTransactions[accId] += amount
        self.paymentCounter += 1
        pid = f"payment{self.paymentCounter}"
        self.payments[accId].append(Payment(pid, ts, amount, amount // 50))
        return pid

    def topSpenders(self, ts, n):
        # 仅统计当前未被合并的活跃账号
        active = [(aid, self.outgoingTransactions[aid]) for aid in self.accounts if aid not in self.mergedAt]
        active.sort(key=lambda x: (-x[1], x[0]))
        return [f"{aid}({amt})" for aid, amt in active[:n]]

    def getPaymentStatus(self, ts, accId, pid):
        if not self._is_active(accId): return ""
        self.processCashback(ts, accId)
        for p in self.payments[accId]:
            if p.id == pid: return "CASHBACK_RECEIVED" if p.processed else "IN_PROGRESS"
        return ""

    def mergeAccounts(self, ts, id1, id2):
        if not self._is_active(id1) or not self._is_active(id2) or id1 == id2: return False
        self.processCashback(ts, id1); self.processCashback(ts, id2)
        
        # 迁移资金、支出额和支付记录
        balance2 = self.accounts[id2]
        self._record(id1, ts, balance2)
        self.outgoingTransactions[id1] += self.outgoingTransactions[id2]
        self.payments[id1].extend(self.payments[id2])
        
        # 标记 id2 已消失
        self.mergedAt[id2] = ts
        return True

    def getBalance(self, ts, accId, timeAt):
        # 基础检查：在该时刻必须已存在且未被合并
        if accId not in self.accounts or self.creationTime[accId] > timeAt: return -1
        if accId in self.mergedAt and self.mergedAt[accId] <= timeAt: return -1
        
        # 计算 timeAt 时刻的所有流水总和
        balance = sum(delta for t, delta in self.history[accId] if t <= timeAt)
        
        # 模拟计算 timeAt 时刻应到账的返现（即使还没有调用 processCashback）
        for p in self.payments[accId]:
            # 如果支付发生且返现到账时间均 <= timeAt，则计入
            if p.ts + self.MS_PER_DAY <= timeAt:
                # 注意：这里需要考虑支付本身是在合并前还是合并后发生的
                # 但由于合并时我们会把 Payment 对象转移，这里直接遍历 accId 现有的 payments 即可
                balance += p.cashback
        return balance



# import math
# from typing import List, Optional, Dict

# class Payment:
#     def __init__(self, id: str, timestamp: int, amount: int, cashback: int):
#         self.id = id
#         self.timestamp = timestamp
#         self.amount = amount
#         self.cashback = cashback
#         self.processed = False

# class Account:
#     def __init__(self, accountId: str, timestamp: int):
#         self.id = accountId
#         self.createTime = timestamp
#         self.mergedAt = None  # 记录该账户何时被合并掉
#         self.balance = 0
#         self.totalOutgoing = 0
#         self.payments = []
#         # 历史流水: List of (timestamp, amount_change)
#         self.history = []

#     def add_history(self, timestamp: int, delta: int):
#         self.history.append((timestamp, delta))
#         self.balance += delta

# class BankingSystem:
#     def __init__(self):
#         self.accounts: Dict[str, Account] = {}
#         self.paymentCounter = 0
#         self.MS_PER_DAY = 86400000

#     def _get_active_account(self, accountId: str) -> Optional[Account]:
#         acc = self.accounts.get(accountId)
#         if acc and acc.mergedAt is None:
#             return acc
#         return None

#     def createAccount(self, timestamp: int, accountId: str) -> bool:
#         if accountId in self.accounts:
#             return False
#         self.accounts[accountId] = Account(accountId, timestamp)
#         return True

#     def processCashbacks(self, timestamp: int, acc: Account):
#         for p in acc.payments:
#             if not p.processed and timestamp >= p.timestamp + self.MS_PER_DAY:
#                 acc.add_history(p.timestamp + self.MS_PER_DAY, p.cashback)
#                 p.processed = True

#     def deposit(self, timestamp: int, accountId: str, amount: int) -> int:
#         acc = self._get_active_account(accountId)
#         if not acc: return -1
#         self.processCashbacks(timestamp, acc)
#         acc.add_history(timestamp, amount)
#         return acc.balance

#     def transfer(self, timestamp: int, srcId: str, dstId: str, amount: int) -> int:
#         acc1 = self._get_active_account(srcId)
#         acc2 = self._get_active_account(dstId)
#         if not acc1 or not acc2 or srcId == dstId: return -1
        
#         self.processCashbacks(timestamp, acc1)
#         if acc1.balance < amount: return -1
        
#         self.processCashbacks(timestamp, acc2)
#         acc1.add_history(timestamp, -amount)
#         acc2.add_history(timestamp, amount)
#         acc1.totalOutgoing += amount
#         return acc1.balance

#     def pay(self, timestamp: int, accountId: str, amount: int) -> str:
#         acc = self._get_active_account(accountId)
#         if not acc: return ""
#         self.processCashbacks(timestamp, acc)
        
#         if acc.balance < amount: return ""
        
#         acc.add_history(timestamp, -amount)
#         acc.totalOutgoing += amount
#         self.paymentCounter += 1
#         pid = f"payment{self.paymentCounter}"
#         acc.payments.append(Payment(pid, timestamp, amount, amount // 50)) # 2% is //50
#         return pid

#     def topSpenders(self, timestamp: int, n: int) -> List[str]:
#         # 仅统计当前活跃的账户
#         active = [acc for acc in self.accounts.values() if acc.mergedAt is None]
#         active.sort(key=lambda x: (-x.totalOutgoing, x.id))
#         return [f"{acc.id}({acc.totalOutgoing})" for acc in active[:n]]

#     def getPaymentStatus(self, timestamp: int, accountId: str, paymentId: str) -> str:
#         acc = self._get_active_account(accountId)
#         if not acc: return ""
#         self.processCashbacks(timestamp, acc)
#         for p in acc.payments:
#             if p.id == paymentId:
#                 return "CASHBACK_RECEIVED" if p.processed else "IN_PROGRESS"
#         return ""

#     def mergeAccounts(self, timestamp: int, id1: str, id2: str) -> bool:
#         acc1 = self._get_active_account(id1)
#         acc2 = self._get_active_account(id2)
#         if not acc1 or not acc2 or id1 == id2: return False
        
#         # 1. 处理双方返现
#         self.processCashbacks(timestamp, acc1)
#         self.processCashbacks(timestamp, acc2)
        
#         # 2. 资金与流水迁移
#         transfer_amount = acc2.balance
#         acc1.add_history(timestamp, transfer_amount)
#         acc1.totalOutgoing += acc2.totalOutgoing
        
#         # 3. 支付记录迁移（未来的返现会进 acc1）
#         acc1.payments.extend(acc2.payments)
        
#         # 4. 销户
#         acc2.mergedAt = timestamp
#         return True

#     def getBalance(self, timestamp: int, accountId: str, timeAt: int) -> int:
#         acc = self.accounts.get(accountId)
#         # 规则：必须已创建，且在 timeAt 时刻未被合并
#         if not acc or acc.createTime > timeAt: return -1
#         if acc.mergedAt is not None and acc.mergedAt <= timeAt: return -1
        
#         # 计算基础流水
#         total = sum(delta for ts, delta in acc.history if ts <= timeAt)
        
#         # 模拟计算 timeAt 时刻已到账的返现
#         for p in acc.payments:
#             # 只有支付时间在 timeAt 之前的支付才可能产生返现
#             # 且返现到账时间 (p.ts + 24h) 必须 <= timeAt
#             if p.timestamp + self.MS_PER_DAY <= timeAt:
#                 total += p.cashback
                
#         return total