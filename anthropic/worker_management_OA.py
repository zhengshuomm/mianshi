from typing import List, Optional

class Worker:
    def __init__(self, position: str, compensation: int):
        self.position = position
        self.compensation = compensation
        self.enterTime = -1
        self.isInOffice = False
        self.sessions = []
        self.currentTimeInPosition = 0
        self.pending_promo = None

class OfficeManager:
    def __init__(self):
        self.workers = {}
        # 存储合并后的互不重叠的双倍薪资区间: [[start, end], ...]
        self.double_paid_intervals = []

    def addWorker(self, workerId: str, position: str, compensation: int) -> bool:
        if workerId in self.workers:
            return False
        self.workers[workerId] = Worker(position, int(compensation))
        return True

    def registerWorker(self, workerId: str, timestamp: int) -> str:
        worker = self.workers.get(workerId)
        if not worker: return "invalid_request"
        
        if not worker.isInOffice:
            if worker.pending_promo and timestamp >= worker.pending_promo[2]:
                worker.position = worker.pending_promo[0]
                worker.compensation = worker.pending_promo[1]
                worker.currentTimeInPosition = 0
                worker.pending_promo = None
            worker.enterTime = timestamp
            worker.isInOffice = True
        else:
            duration = timestamp - worker.enterTime
            worker.currentTimeInPosition += duration
            worker.sessions.append((worker.enterTime, timestamp, worker.compensation))
            worker.isInOffice = False
        return "registered"

    def promote(self, workerId: str, newPosition: str, newCompensation: str, startTimestamp: int) -> str:
        worker = self.workers.get(workerId)
        if not worker or worker.pending_promo:
            return "invalid_request"
        worker.pending_promo = (newPosition, int(newCompensation), startTimestamp)
        return "success"

    def calcSalary(self, workerId: str, startTimestamp: int, endTimestamp: int) -> int:
        worker = self.workers.get(workerId)
        if not worker: return -1
        
        total_salary = 0
        for s_start, s_end, rate in worker.sessions:
            # 1. 确定有效查询区间 (工作会话与查询范围的交集)
            effective_start = max(s_start, startTimestamp)
            effective_end = min(s_end, endTimestamp)
            
            if effective_start < effective_end:
                # 基础薪资
                duration = effective_end - effective_start
                total_salary += duration * rate
                
                # 2. 计算双倍薪资的额外补贴
                # 计算 effective 区间与所有 double_paid_intervals 的交集总长
                for d_start, d_end in self.double_paid_intervals:
                    # 求 [effective_start, effective_end] 和 [d_start, d_end] 的交集
                    overlap_s = max(effective_start, d_start)
                    overlap_e = min(effective_end, d_end)
                    
                    if overlap_s < overlap_e:
                        # 额外再加一份薪资（即实现双倍）
                        total_salary += (overlap_e - overlap_s) * rate
                        
        return total_salary

    def get(self, workerId: str) -> int:
        worker = self.workers.get(workerId)
        return sum(s[1]-s[0] for s in worker.sessions) if worker else -1

    def topNWorkers(self, n: int, position: str) -> str:
        filtered = [(wid, w.currentTimeInPosition) for wid, w in self.workers.items() if w.position == position]
        filtered.sort(key=lambda x: (-x[1], x[0]))
        return ", ".join([f"{name}({time})" for name, time in filtered[:n]])

    
    def setDoublePaid(self, startTimestamp: int, endTimestamp: int) -> None:
        # 1. 将新区间加入列表
        self.double_paid_intervals.append([startTimestamp, endTimestamp])
        # 2. 合并所有重叠区间，保持 double_paid_intervals 是互不重叠且升序的
        self.double_paid_intervals.sort()
        if not self.double_paid_intervals:
            return
        
        merged = []
        curr_start, curr_end = self.double_paid_intervals[0]
        
        for i in range(1, len(self.double_paid_intervals)):
            next_start, next_end = self.double_paid_intervals[i]
            if next_start <= curr_end: # 有重叠或恰好相接
                curr_end = max(curr_end, next_end)
            else:
                merged.append([curr_start, curr_end])
                curr_start, curr_end = next_start, next_end
        merged.append([curr_start, curr_end])
        self.double_paid_intervals = merged




"""
    part 3
"""
from typing import List, Optional

class Worker:
    def __init__(self, position: str, compensation: int):
        self.position = position
        self.compensation = compensation
        self.enterTime = -1
        self.isInOffice = False
        
        # 记录已完成的会话: (start, end, rate)
        self.sessions = []
        # 当前职位的累计时长（仅在生效后的职位下统计）
        self.currentTimeInPosition = 0
        # 挂起的晋升: (newPosition, newCompensation, startTimestamp)
        self.pending_promo = None

class OfficeManager:
    def __init__(self):
        self.workers = {}

    def addWorker(self, workerId: str, position: str, compensation: int) -> bool:
        if workerId in self.workers:
            return False
        self.workers[workerId] = Worker(position, int(compensation))
        return True

    def registerWorker(self, workerId: str, timestamp: int) -> str:
        worker = self.workers.get(workerId)
        if not worker:
            return "invalid_request"
        
        if not worker.isInOffice:
            # 关键修正：只有在进入时间 >= 晋升开始时间时，晋升才生效
            if worker.pending_promo and timestamp >= worker.pending_promo[2]:
                worker.position = worker.pending_promo[0]
                worker.compensation = worker.pending_promo[1]
                worker.currentTimeInPosition = 0 # 换岗后，新岗位时间从0开始
                worker.pending_promo = None
            
            worker.enterTime = timestamp
            worker.isInOffice = True
        else:
            duration = timestamp - worker.enterTime
            worker.currentTimeInPosition += duration
            # 保存完结的会话
            worker.sessions.append((worker.enterTime, timestamp, worker.compensation))
            worker.isInOffice = False
            
        return "registered"

    def get(self, workerId: str) -> int:
        worker = self.workers.get(workerId)
        if not worker: return -1
        # get 返回所有已完成会话的总时长
        return sum(s[1] - s[0] for s in worker.sessions)

    def topNWorkers(self, n: int, position: str) -> str:
        filtered = []
        for w_id, w in self.workers.items():
            if w.position == position:
                filtered.append((w_id, w.currentTimeInPosition))
        
        # 排序：时长降序，ID升序
        filtered.sort(key=lambda x: (-x[1], x[0]))
        return ", ".join([f"{name}({time})" for name, time in filtered[:n]])

    def promote(self, workerId: str, newPosition: str, newCompensation: str, startTimestamp: int) -> str:
        worker = self.workers.get(workerId)
        # 关键修正：如果有 pending_promo 且还没生效，则不能再次 promote
        if not worker or worker.pending_promo:
            return "invalid_request"
        
        worker.pending_promo = (newPosition, int(newCompensation), startTimestamp)
        return "success"

    def calcSalary(self, workerId: str, startTimestamp: int, endTimestamp: int) -> int:
        worker = self.workers.get(workerId)
        if not worker:
            return -1
        
        total_salary = 0
        for s_start, s_end, rate in worker.sessions:
            # 关键修正：计算已完成会话与查询区间的交集时长
            overlap_start = max(s_start, startTimestamp)
            overlap_end = min(s_end, endTimestamp)
            
            if overlap_start < overlap_end:
                total_salary += (overlap_end - overlap_start) * rate
        return total_salary