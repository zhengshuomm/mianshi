from typing import List

class Solution:
    def stackEvents(self, samples: List[str]) -> List[str]:
        results = []
        prev_stack = []

        for sample in samples:
            # 1. 解析采样数据，例如 "10:main->calc" -> timestamp=10, stack=["main", "calc"]
            parts = sample.split(":")
            timestamp = parts[0]
            # 处理空采样的情况，如 "0:"
            current_stack = parts[1].split("->") if parts[1] else []
            
            # 2. 找到当前栈和前一个栈的共同前缀长度
            shared_len = 0
            min_len = min(len(prev_stack), len(current_stack))
            while shared_len < min_len and prev_stack[shared_len] == current_stack[shared_len]:
                shared_len += 1
                
            # 3. 处理 "end" 事件：在 prev_stack 中但不在 current_stack 中的部分
            # 必须从内向外（倒序）处理
            for i in range(len(prev_stack) - 1, shared_len - 1, -1):
                results.append(f"end:{timestamp}:{prev_stack[i]}")
                
            # 4. 处理 "start" 事件：在 current_stack 中但不在 prev_stack 中的部分
            # 必须从外向内（正序）处理
            for i in range(shared_len, len(current_stack)):
                results.append(f"start:{timestamp}:{current_stack[i]}")
                
            # 更新 prev_stack 用于下一次循环
            prev_stack = current_stack
            
        return results

class Solution2:
    def debouncedEvents(self, samples: List[str], n: int) -> List[str]:
        if n <= 0:
            raise ValueError("n must be positive")

        results = []
        # active[i] 对应当前栈深度 i 上的 frame；下标即 parent path 前缀
        active = []

        for sample in samples:
            parts = sample.split(":")
            timestamp = parts[0]
            current = parts[1].split("->") if parts[1] else []

            common = 0
            while common < len(active) and common < len(current) and active[common]["name"] == current[common]:
                common += 1

            # 栈在 common 深度被打断：从内到外结束，未 confirmed 的不输出 end
            for i in range(len(active) - 1, common - 1, -1):
                frame = active[i]
                if frame["started"]:
                    results.append(f"end:{timestamp}:{frame['name']}")
                active.pop(i)

            # 公共前缀连续出现，streak++
            for i in range(common):
                frame = active[i]
                frame["streak"] += 1
                if not frame["started"] and frame["streak"] >= n:
                    frame["started"] = True
                    results.append(f"start:{timestamp}:{frame['name']}")

            # 新 frame，streak 从 1 开始
            for i in range(common, len(current)):
                started = n == 1
                active.append({"name": current[i], "streak": 1, "started": started})
                if started:
                    results.append(f"start:{timestamp}:{current[i]}")

        return results


class Solution3:
    def suffixDebouncedEvents(self, samples: List[str], n: int) -> List[str]:
        path_info = {} # key: suffix_tuple, value: {count, start_time, is_active}
        prev_suffixes = set()
        results = []

        for sample in samples:
            parts = sample.split(":")
            timestamp = parts[0]
            raw_stack = parts[1].split("->") if parts[1] else []
            # 翻转栈，使 index 0 永远是叶子节点 (Innermost)
            reversed_stack = raw_stack[::-1] 
            
            current_sample_suffixes = set()
            # 1. 更新计数：识别当前采样的所有后缀
            for i in range(1, len(reversed_stack) + 1):
                suffix_tuple = tuple(reversed_stack[:i])
                current_sample_suffixes.add(suffix_tuple)
                if suffix_tuple not in path_info:
                    path_info[suffix_tuple] = {"count": 1, "start_time": timestamp, "is_active": False}
                else:
                    path_info[suffix_tuple]["count"] += 1

            # 2. 处理消失的后缀 (End 事件)
            # 对后缀链来说，短后缀先 end：save -> process -> taskB
            for suffix_tuple in sorted(prev_suffixes, key=len):
                if suffix_tuple not in current_sample_suffixes:
                    info = path_info[suffix_tuple]
                    if info["is_active"]:
                        results.append(f"end:{timestamp}:{suffix_tuple[-1]}")
                    del path_info[suffix_tuple]

            # 3. 处理 Start 事件：Outermost to Innermost
            # 同一时间戳先 end 再 start，避免切换栈时顺序颠倒
            for i in range(len(reversed_stack), 0, -1):
                suffix_tuple = tuple(reversed_stack[:i])
                if suffix_tuple in path_info:
                    info = path_info[suffix_tuple]
                    if info["count"] >= n and not info["is_active"]:
                        results.append(f"start:{info['start_time']}:{suffix_tuple[-1]}")
                        info["is_active"] = True

            prev_suffixes = current_sample_suffixes

        return results


def _test_nested_helper_stack():
    samples = [
        "10:main->calc",
        "20:main->calc->helper",
        "30:main->calc->helper->helper",
        "40:main->calc->helper",
        "50:main",
    ]
    expected = [
        "start:10:main",
        "start:10:calc",
        "start:20:helper",
        "start:30:helper",
        "end:40:helper",
        "end:50:helper",
        "end:50:calc",
    ]
    assert Solution().stackEvents(samples) == expected


def _test_solution2_debounce_chain():
    # n=2：路径连续出现 2 次采样才 emit start；消失且曾激活则 emit end
    samples = [
        "1:main",
        "2:main->A",
        "3:main->A",
        "4:main->B",
        "5:main->B->C",
        "6:main->B",
    ]
    expected = [
        "start:2:main",
        "start:3:A",
        "end:4:A",
        "start:5:B",
    ]
    assert Solution2().debouncedEvents(samples, 2) == expected


def _test_solution3_task_flush():
    samples = [
        "10:taskA->process->save",
        "20:taskB->process->save",
        "30:taskB->process->save",
        "40:taskB->flush",
    ]
    # n=2：后缀连续出现 2 次采样才 start；栈切换时按 active 顺序 end
    expected = [
        "start:10:process",
        "start:10:save",
        "start:20:taskB",
        "end:40:save",
        "end:40:process",
        "end:40:taskB",
    ]
    assert Solution3().suffixDebouncedEvents(samples, 2) == expected


def _test_solution3_switch_suffix_n1():
    samples = [
        "5:main->foo",
        "10:main->bar",
        "15:main->foo",
    ]
    expected = [
        "start:5:main",
        "start:5:foo",
        "end:10:foo",
        "end:10:main",
        "start:10:main",
        "start:10:bar",
        "end:15:bar",
        "end:15:main",
        "start:15:main",
        "start:15:foo",
    ]
    assert Solution3().suffixDebouncedEvents(samples, 1) == expected


if __name__ == "__main__":
    _test_nested_helper_stack()
    _test_solution2_debounce_chain()
    _test_solution3_task_flush()
    _test_solution3_switch_suffix_n1()
    print("function_profile: test ok")