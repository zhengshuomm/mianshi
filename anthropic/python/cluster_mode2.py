import sys
from typing import List

class Worker:
    def __init__(self, workerId: int, k: int, localData: List[int], cluster):
        self.workerId = workerId
        self.k = k
        self.localData = localData
        self.cluster = cluster

    # Sends a string-represented payload data to the specified worker
    # asynchronously. You should NOT modify this method.
    def sendAsyncMessage(self, targetWorkerId: int, payload: str):
        self.cluster.sendAsyncMessage(targetWorkerId, payload)

    # Receives a string-represented payload data from the worker's mailbox. You
    # should NOT modify this method.
    def receive(self) -> str:
        return self.cluster.receive(self.workerId)

    def sendLocalStats(self) -> None:
        d = self.localData
        if d:
            self.sendAsyncMessage(0, f"STAT {len(d)} {min(d)} {max(d)}")
        self.sendAsyncMessage(0, "DONE_STATS")

    def aggregateGlobalStats(self) -> List[int]:
        total = 0
        g_lo, g_hi = sys.maxsize, -sys.maxsize - 1
        done = 0
        while done < self.k:
            msg = self.cluster.receive(self.workerId)
            if msg.startswith("STAT"):
                _, s, lo, hi = msg.split()
                s = int(s)
                total += s
                if s > 0:
                    g_lo = min(g_lo, int(lo))
                    g_hi = max(g_hi, int(hi))
            elif msg == "DONE_STATS":
                done += 1
        if total == 0:
            return [0, 0, 0]
        return [total, g_lo, g_hi]

    def sendCountsForPivot(self, pivot: int) -> None:
        # #{x : x <= pivot}; coordinator finds smallest v with global_le(v) > targetIndex
        le_c = sum(1 for v in self.localData if v <= pivot)
        self.sendAsyncMessage(0, f"CNT {le_c}")
        self.sendAsyncMessage(0, "DONE_CNT")

    def aggregateLeCount(self) -> int:
        total = 0
        done = 0
        while done < self.k:
            msg = self.cluster.receive(self.workerId)
            if msg.startswith("CNT"):
                _, a = msg.split()
                total += int(a)
            elif msg == "DONE_CNT":
                done += 1
        return total


class Cluster:
    def __init__(self, data: List[int], k: int):
        self.k = k

        self.shards = []
        for i in range(k):
            self.shards.append([])

        totalSize = len(data)
        baseSize = totalSize // k
        remainder = totalSize % k

        index = 0
        for w in range(k):
            chunkSize = baseSize
            if w < remainder:
                chunkSize = chunkSize + 1
            for j in range(chunkSize):
                self.shards[w].append(data[index])
                index = index + 1

        self.mailboxes = {}
        self.readIndices = {}
        for i in range(k):
            self.mailboxes[i] = []
            self.readIndices[i] = 0

        self.workers = [None] * k
        for i in range(k):
            self.workers[i] = Worker(i, k, self.shards[i], self)

    def sendAsyncMessage(self, targetWorkerId: int, payload: str):
        self.mailboxes[targetWorkerId].append(payload)

    def receive(self, workerId: int) -> str:
        myMailbox = self.mailboxes[workerId]
        idx = self.readIndices[workerId]
        if idx < len(myMailbox):
            self.readIndices[workerId] = idx + 1
            return myMailbox[idx]

        return ""

    def findMedian(self) -> float:
        for w in self.workers:
            w.sendLocalStats()
        n, lo, hi = self.workers[0].aggregateGlobalStats()
        if n == 0:
            return float("nan")
        i, j = (n - 1) // 2, n // 2
        return (self.selectKth(i, lo, hi) + self.selectKth(j, lo, hi)) / 2.0

    def selectKth(self, targetIndex: int, low: int, high: int) -> int:
        # 0-based targetIndex-th smallest = smallest v with #{x <= v} > targetIndex
        left, right = low, high
        while left < right:
            mid = left + (right - left) // 2
            for w in self.workers:
                w.sendCountsForPivot(mid)
            le = self.workers[0].aggregateLeCount()
            if le <= targetIndex:
                left = mid + 1
            else:
                right = mid
        return left

    @staticmethod
    def main():
        Cluster.test1()
        Cluster.test2()
        Cluster.test3()
        Cluster.test4()

    @staticmethod
    def test1():
        print("===== Test 1 =====")
        data = [1, 3, 5]
        cluster = Cluster(data, 2)
        print(cluster.findMedian())  # Expected: 3.0

    @staticmethod
    def test2():
        print("===== Test 2 =====")
        data = [1, 2, 3, 4]
        cluster = Cluster(data, 3)
        print(cluster.findMedian())  # Expected: 2.5

    @staticmethod
    def test3():
        print("===== Test 3 =====")
        data = []
        for i in range(50):
            data.append(10)
        for i in range(30):
            data.append(20)
        for i in range(20):
            data.append(-5)
        cluster = Cluster(data, 5)
        print(cluster.findMedian())  # Expected: 10.0

    @staticmethod
    def test4():
        print("===== Test 4 =====")
        data = [10, 20, 30]
        cluster = Cluster(data, 5)
        print(cluster.findMedian())  # Expected: 20.0


if __name__ == "__main__":
    Cluster.main()