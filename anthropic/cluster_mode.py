class Worker:
    def __init__(self, workerId, k, localData, cluster):
        self.workerId = workerId
        self.k = k
        self.localData = localData
        self.cluster = cluster

    # Sends a string-represented payload data to the specified worker
    # asynchronously. You should NOT modify this method.
    def sendAsyncMessage(self, targetWorkerId, payload):
        self.cluster.sendAsyncMessage(targetWorkerId, payload)

    # Receives a string-represented payload data from the worker's mailbox. You
    # should NOT modify this method.
    def receive(self):
        return self.cluster.receive(self.workerId)

    def scatterFreq(self):
        localFreq = {}
        for value in self.localData:
            localFreq[value] = localFreq.get(value, 0) + 1

        # Send each (value, count) to its owner based on hash partition
        for value, count in localFreq.items():
            owner = value % self.k
            self.cluster.sendAsyncMessage(owner, f"DATA {value} {count}")

        # Send DONE to all workers to signal end of scatter
        for i in range(self.k):
            self.cluster.sendAsyncMessage(i, "DONE")

    def aggregateFreq(self):
        ownedFreq = {}
        done = 0

        while done < self.k:
            msg = self.cluster.receive(self.workerId)
            if msg.startswith("DATA"):
                parts = msg.split(" ")
                value = int(parts[1])
                count = int(parts[2])
                ownedFreq[value] = ownedFreq.get(value, 0) + count
            elif msg == "DONE":
                done += 1

        if not ownedFreq:
            return None

        # Find local mode among owned values (smaller value wins ties)
        bestValue = float('inf')
        bestCount = 0
        for value, count in ownedFreq.items():
            if count > bestCount or (count == bestCount and value < bestValue):
                bestValue = value
                bestCount = count

        return [bestValue, bestCount]

    def reportLocal(self, localMode):
        if localMode is not None:
            self.cluster.sendAsyncMessage(0, f"DATA {int(localMode[0])} {int(localMode[1])}")
        self.cluster.sendAsyncMessage(0, "DONE")

    def gatherGlobal(self, myLocalMode):
        bestValue = float('inf')
        bestCount = 0

        if myLocalMode is not None:
            bestValue = int(myLocalMode[0])
            bestCount = myLocalMode[1]

        # Drain all messages sent to worker 0 during the gathering stage
        done = 0
        while done < self.k - 1:
            msg = self.cluster.receive(self.workerId)
            if msg.startswith("DATA"):
                parts = msg.split(" ")
                value = int(parts[1])
                count = int(parts[2])
                if count > bestCount or (count == bestCount and value < bestValue):
                    bestValue = value
                    bestCount = count
            elif msg == "DONE":
                done += 1

        return bestValue


class Cluster:
    def __init__(self, data, k):
        self.k = k

        self.shards = []
        for i in range(k):
            self.shards.append([])

        # Distribute data evenly across workers
        totalSize = len(data)
        baseSize = totalSize // k
        remainder = totalSize % k

        index = 0
        for w in range(k):
            chunkSize = baseSize + (1 if w < remainder else 0)
            for j in range(chunkSize):
                self.shards[w].append(data[index])
                index += 1

        self.mailboxes = {}
        self.readIndices = {}
        for i in range(k):
            self.mailboxes[i] = []
            self.readIndices[i] = 0

        self.workers = [None] * k
        for i in range(k):
            self.workers[i] = Worker(i, k, self.shards[i], self)

    def sendAsyncMessage(self, targetWorkerId, payload):
        self.mailboxes[targetWorkerId].append(payload)

    def receive(self, workerId):
        myMailbox = self.mailboxes[workerId]
        idx = self.readIndices[workerId]
        if idx < len(myMailbox):
            self.readIndices[workerId] = idx + 1
            return myMailbox[idx]

        return ""

    def findMode(self):
        # All workers scatter local frequencies to value owners
        for i in range(self.k):
            self.workers[i].scatterFreq()

        # All workers aggregate counts for their owned values
        localModes = [None] * self.k
        for i in range(self.k):
            localModes[i] = self.workers[i].aggregateFreq()

        # Non-zero workers report their local mode to worker 0
        for i in range(1, self.k):
            self.workers[i].reportLocal(localModes[i])

        # Worker 0 gathers and returns the global mode
        return self.workers[0].gatherGlobal(localModes[0])

    @staticmethod
    def main():
        Cluster.test1()
        Cluster.test2()
        Cluster.test3()

    @staticmethod
    def test1():
        print("===== Test 1 =====")
        data = [1, 2, 2, 3, 3, 3, 4, 4, 4, 4]
        cluster = Cluster(data, 3)
        print(cluster.findMode())  # Expected: 4

    @staticmethod
    def test2():
        print("===== Test 2 =====")
        data = [1, 2, 3, 1, 2, 3]
        cluster = Cluster(data, 2)
        print(cluster.findMode())  # Expected: 1

    @staticmethod
    def test3():
        print("===== Test 3 =====")
        data = []
        for i in range(100):
            data.append(7)
        for i in range(50):
            data.append(3)
        for i in range(30):
            data.append(11)
        for i in range(20):
            data.append(5)
        cluster = Cluster(data, 10)
        print(cluster.findMode())  # Expected: 7


if __name__ == "__main__":
    Cluster.main()