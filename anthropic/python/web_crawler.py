# Input:
# urls = ["http://example.com/page1", "http://example.com/page2", "http://example.com/page3#sectionA", "http://example.net/page4#"],
# edges = [[0, 1], [0, 2], [1, 3], [2, 0]],
# startUrl = "http://example.com/page1"

# Output:
# ["http://example.com/page1", "http://example.com/page2", "http://example.com/page3"]

# Explanation:

# All three reachable pages share the hostname "example.com".
# The URL "http://example.com/page3#sectionA" is sanitized to "http://example.com/page3".
# The page "http://example.net/page4#" is ignored due to a different hostname.
# Example 2:

# Input:
# urls = ["http://news.yahoo.com/home", "http://news.google.com/top", "http://news.yahoo.com/news"],
# edges = [[1, 0], [0, 2]],
# startUrl = "http://news.google.com/top"

# Output:
# ["http://news.google.com/top"]

# Example 3:

# Input:
# urls = ["http://site.com/a", "http://site.com/b#frag1", "http://site.com/b#frag2", "http://site.com/c", "http://other.com/x", "http://site.com/d", "http://site.com/e#", "http://site.com/f"],
# edges = [[0, 1], [0, 2], [1, 3], [2, 3], [3, 4], [3, 5], [5, 0], [5, 6], [6, 7], [7, 0]],
# startUrl = "http://site.com/a"

# Output:
# ["http://site.com/a", "http://site.com/b", "http://site.com/c", "http://site.com/d", "http://site.com/e", "http://site.com/f"]

import threading
import time
from concurrent.futures import ThreadPoolExecutor
from urllib.parse import urlparse

"""
Provided Html Parser implementation. You should NOT modify it.
"""
class HtmlParser:
    def __init__(self, urls, edges):
        self.graph = {}
        for u in urls:
            self.graph[u] = []
        
        for edge in edges:
            from_idx = edge[0]
            to_idx = edge[1]
            self.graph[urls[from_idx]].append(urls[to_idx])

    def getUrls(self, url):
        try:
            time.sleep(0.01)  # Simulate network latency
        except:
            pass
        
        links = self.graph.get(url)
        if links is None:
            return []
        return links

class Solution:
    def __init__(self):
        self.active = 0
        self.done = threading.Condition()

    @staticmethod
    def canonical_url(url: str) -> str:
        """Strip #fragment so the same page is not crawled multiple times."""
        h = url.find("#")
        return url if h == -1 else url[:h]

    @staticmethod
    def get_host(url: str) -> str:
        """Host only (no port); stdlib handles edge cases better than manual split."""
        return urlparse(url).hostname or ""

    def crawl(self, startUrl, htmlParser):
        start_host = self.get_host(self.canonical_url(startUrl))
        start_canon = self.canonical_url(startUrl)

        visited = {start_canon}
        # Single Condition: its internal lock protects both visited and active (+ wait/notify).
        with self.done:
            self.active = 1

        def task(raw_url: str) -> None:
            try:
                for link in htmlParser.getUrls(raw_url):
                    canon = self.canonical_url(link)
                    if self.get_host(canon) != start_host:
                        continue
                    with self.done:
                        if canon in visited:
                            continue
                        visited.add(canon)
                        self.active += 1
                    executor.submit(task, link)
            finally:
                with self.done:
                    self.active -= 1
                    if self.active == 0:
                        self.done.notify_all()

        with ThreadPoolExecutor(max_workers=10) as executor:
            executor.submit(task, startUrl)
            with self.done:
                while self.active > 0:
                    self.done.wait()

        return sorted(visited)



# import threading
# import time
# from queue import Queue
# from urllib.parse import urlparse

# """
# Provided Html Parser implementation.
# """
# class HtmlParser:
#     def __init__(self, urls, edges):
#         self.graph = {u: [] for u in urls}
#         for u_idx, v_idx in edges:
#             self.graph[urls[u_idx]].append(urls[v_idx])

#     def getUrls(self, url):
#         try:
#             time.sleep(0.01)  # 模拟网络延迟
#         except:
#             pass
#         return self.graph.get(url, [])

# class Solution:
#     @staticmethod
#     def canonical_url(url: str) -> str:
#         h = url.find("#")
#         return url if h == -1 else url[:h]

#     @staticmethod
#     def get_host(url: str) -> str:
#         return urlparse(url).hostname or ""

#     def crawl(self, startUrl: str, htmlParser: 'HtmlParser') -> list[str]:
#         # 1. 初始化基础信息
#         start_canon = self.canonical_url(startUrl)
#         start_host = self.get_host(start_canon)
        
#         # 2. 核心同步组件
#         q = Queue()
#         q.put(startUrl)
        
#         visited = {start_canon}
#         visited_lock = threading.Lock() # 保护对 visited 集合的并发读写
        
#         def worker():
#             while True:
#                 # 从队列获取一个任务，如果队列为空会阻塞在这里
#                 url = q.get()
#                 try:
#                     # IO 操作在锁外进行，最大化并发效率
#                     for link in htmlParser.getUrls(url):
#                         canon = self.canonical_url(link)
                        
#                         # 域名限制检查
#                         if self.get_host(canon) != start_host:
#                             continue
                        
#                         # 线程安全地更新 visited 集合并决定是否继续生产任务
#                         with visited_lock:
#                             if canon not in visited:
#                                 visited.add(canon)
#                                 q.put(canon) # 发现新大陆，放入队列
#                 finally:
#                     # 关键！通知队列该任务处理完毕，内部计数器 -1
#                     q.task_done()

#         # 3. 启动固定数量的线程 (Pool Size)
#         # daemon=True 意味着主线程退出时，这些子线程会自动被回收
#         for _ in range(10):
#             threading.Thread(target=worker, daemon=True).start()

#         # 4. 主线程在此阻塞，直到队列中所有 put 的任务都被 task_done 处理完
#         q.join()

#         return sorted(list(visited))

# import time
# import threading
# from concurrent.futures import ThreadPoolExecutor
# import os
# from collections import defaultdict

# """
# Provided Html Parser implementation. You should NOT modify it.
# """
# class HtmlParser:
#     def __init__(self, urls, edges):
#         self.graph = {}
#         for u in urls:
#             self.graph[u] = []
        
#         for edge in edges:
#             from_idx = edge[0]
#             to_idx = edge[1]
#             self.graph[urls[from_idx]].append(urls[to_idx])

#     def getUrls(self, url):
#         try:
#             time.sleep(0.01)  # Simulate network latency
#         except:
#             pass
        
#         links = self.graph.get(url)
#         if links is None:
#             return []
#         return links

# class Solution:
#     def crawl(self, startUrl, htmlParser):
#         # 1. 提取 Host (保留你的逻辑)
#         start_host = self.getHost(startUrl)
        
#         # 2. 共享资源与锁
#         visited = {startUrl}
#         visited_lock = threading.Lock()
        
#         # 3. 任务队列与状态管理
#         # 用来追踪当前还有多少正在运行的任务
#         self.active_count = 1
#         self.cv = threading.Condition() # 用于优雅地通知主线程结束

#         def task(url):
#             try:
#                 # 获取网页中的所有链接
#                 next_urls = htmlParser.getUrls(url)
#                 for next_url in next_urls:
#                     # 只有相同 host 才继续
#                     if self.getHost(next_url) == start_host:
#                         with visited_lock:
#                             if next_url not in visited:
#                                 visited.add(next_url)
#                                 # 提交新任务
#                                 with self.cv:
#                                     self.active_count += 1
#                                 executor.submit(task, next_url)
#             finally:
#                 # 任务完成，计数减一并通知
#                 with self.cv:
#                     self.active_count -= 1
#                     if self.active_count == 0:
#                         self.cv.notify_all()

#         # 4. 执行
#         with ThreadPoolExecutor(max_workers=10) as executor:
#             executor.submit(task, startUrl)
#             # 主线程在这里挂起，直到 active_count 归零
#             with self.cv:
#                 while self.active_count > 0:
#                     self.cv.wait()

#         return list(visited)

#     def getHost(self, url):
#         # 你的原版逻辑：快速提取 host
#         start = url.find("://")
#         start = start + 3 if start != -1 else 0
#         end = url.find('/', start)
#         return url[start:end] if end != -1 else url[start:]