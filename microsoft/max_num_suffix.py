def max_count_suffix(nums, query):
    n = len(nums)
    max_from_right = [0] * n
    count_max_from_right = [0] * n

    max_val = float('-inf')
    count = 0

    for i in reversed(range(n)):
        if nums[i] > max_val:
            max_val = nums[i]
            count = 1
        elif nums[i] == max_val:
            count += 1
        # 如果 nums[i] < max_val, count 不变
        max_from_right[i] = max_val
        count_max_from_right[i] = count

    # 查询结果
    result = [count_max_from_right[q] for q in query]
    return result