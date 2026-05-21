from typing import List, Optional
import sys

class FileEntry:
    def __init__(self, name: str, size: int, owner: str):
        self.name = name
        self.size = size
        self.owner = owner


class CloudStorage:
    def __init__(self, ):
        self.files = {}
        self.userCapacity = {}
        self.userUsedSpace = {}

    def addFile(self, name: str, size: int) -> bool:
        # if name in self.files:
        #     return False
        # self.files[name] = size
        # return True
        return self.addFileBy("admin", name, size) != -1

    def copyFile(self, nameFrom: str, nameTo: str) -> bool:
        if nameFrom not in self.files or nameTo in self.files:
            return False
        # self.files[nameTo] = self.files[nameFrom]
        src = self.files[nameFrom]
        owner = src.owner
        size = src.size

        if owner != "admin":
            used = self.userUsedSpace[owner]
            cap = self.userCapacity[owner]
            if used + size > cap:
                return False
            self.userUsedSpace[owner] = used + size
        self.files[nameTo] = FileEntry(nameTo, size, owner)
        return True

    def getFileSize(self, name: str) -> int:
        return self.files[name].size if name in self.files else -1

    def findFile(self, prefix: str, suffix: str) -> List[str]:
        found = []

        for fe in self.files.values():
            if fe.name.startswith(prefix) and fe.name.endswith(suffix):
                found.append(fe)
        
        found.sort(key=lambda file: (-file.size, file.name))
        result = []
        for file in found:
            result.append(file.name + "(" + str(file.size) + ")")
        return result

    def addUser(self, userId: str, capacity: int) -> bool:
        if userId == "admin" or userId in self.userCapacity:
            return False
        self.userCapacity[userId] = capacity
        self.userUsedSpace[userId] = 0
        return True

    def addFileBy(self, userId: str, name: str, size: int) -> int:
        if name in self.files:
            return -1
        if userId == "admin":
            self.files[name] = FileEntry(name, size, userId)
            return sys.maxsize

        if userId not in self.userCapacity:
            return -1
        
        used = self.userUsedSpace[userId]
        cap = self.userCapacity[userId]

        if used + size > cap:
            return -1

        self.files[name] = FileEntry(name, size, userId)
        self.userUsedSpace[userId] = used + size

        return cap - used - size

    def updateCapacity(self, userId: str, capacity: int) -> int:
        if userId not in self.userCapacity:
            return -1
        self.userCapacity[userId] = capacity

        ownedFiles = []
        for fe in self.files.values():
            if userId == fe.owner:
                ownedFiles.append(fe)
        
        ownedFiles.sort(key=lambda fe: (-fe.size, fe.name))

        used = 0
        for fe in ownedFiles:
            used += fe.size

        removed = 0
        idx = 0
        while used > capacity and idx < len(ownedFiles):
            toRemove = ownedFiles[idx]
            idx += 1
            used -= toRemove.size
            del self.files[toRemove.name]
            removed += 1
        self.userUsedSpace[userId] = used
        return removed


    def compressFile(self, userId: str, name: str) -> int:
        if name not in self.files:
            return -1
        entry = self.files[name]
        if entry.owner != userId:
            return -1
        if name.endswith(".COMPRESSED"):
            return -1

        compressedName = name + ".COMPRESSED"
        if compressedName in self.files:
            return -1

        originalSize = entry.size
        compressedSize = originalSize // 2

        used = self.userUsedSpace[userId]
        newUsed = used - originalSize + compressedSize
        capacity = self.userCapacity[userId]
        if newUsed > capacity:
            return -1

        del self.files[name]
        self.files[compressedName] = FileEntry(compressedName, compressedSize, userId)
        self.userUsedSpace[userId] = newUsed
        return capacity - newUsed

    def decompressFile(self, userId: str, name: str) -> int:
        if name not in self.files:
            return -1
        if not name.endswith(".COMPRESSED"):
            return -1

        entry = self.files[name]
        if entry.owner != userId:
            return -1

        decompressedName = name[:-len(".COMPRESSED")]
        if decompressedName in self.files:
            return -1

        compressedSize = entry.size
        decompressedSize = compressedSize * 2

        used = self.userUsedSpace[userId]
        newUsed = used - compressedSize + decompressedSize
        capacity = self.userCapacity[userId]
        if newUsed > capacity:
            return -1

        del self.files[name]
        self.files[decompressedName] = FileEntry(decompressedName, decompressedSize, userId)
        self.userUsedSpace[userId] = newUsed

        return capacity - newUsed





# from typing import List, Optional
# import sys

# class FileEntry:
#     def __init__(self, name: str, size: int, owner: str):
#         self.name, self.size, self.owner = name, size, owner

# class CloudStorage:
#     def __init__(self):
#         self.files = {}          # name -> FileEntry
#         self.userCapacity = {}   # userId -> int
#         self.userUsedSpace = {}  # userId -> int

#     # --- 内部辅助方法：统一管理文件增删和空间计算 ---
#     def _delete_file(self, name: str):
#         file = self.files.pop(name)
#         if file.owner != "admin":
#             self.userUsedSpace[file.owner] -= file.size
#         return file

#     def _save_file(self, name: str, size: int, owner: str):
#         self.files[name] = FileEntry(name, size, owner)
#         if owner != "admin":
#             self.userUsedSpace[owner] += size

#     def _has_space(self, user: str, size_diff: int) -> bool:
#         if user == "admin": return True
#         return self.userUsedSpace[user] + size_diff <= self.userCapacity.get(user, 0)

#     # --- 公开 API ---
#     def addFile(self, name: str, size: int) -> bool:
#         return self.addFileBy("admin", name, size) != -1

#     def copyFile(self, nameFrom: str, nameTo: str) -> bool:
#         src = self.files.get(nameFrom)
#         if not src or nameTo in self.files or not self._has_space(src.owner, src.size):
#             return False
#         self._save_file(nameTo, src.size, src.owner)
#         return True

#     def getFileSize(self, name: str) -> int:
#         return self.files[name].size if name in self.files else -1

#     def findFile(self, prefix: str, suffix: str) -> List[str]:
#         # 筛选并在排序时直接处理逻辑
#         found = [f for f in self.files.values() if f.name.startswith(prefix) and f.name.endswith(suffix)]
#         found.sort(key=lambda f: (-f.size, f.name))
#         return [f"{f.name}({f.size})" for f in found]

#     def addUser(self, userId: str, capacity: int) -> bool:
#         if userId == "admin" or userId in self.userCapacity:
#             return False
#         self.userCapacity[userId], self.userUsedSpace[userId] = capacity, 0
#         return True

#     def addFileBy(self, userId: str, name: str, size: int) -> int:
#         if name in self.files or (userId != "admin" and userId not in self.userCapacity) or not self._has_space(userId, size):
#             return -1
#         self._save_file(name, size, userId)
#         return sys.maxsize if userId == "admin" else self.userCapacity[userId] - self.userUsedSpace[userId]

#     def updateCapacity(self, userId: str, capacity: int) -> int:
#         if userId not in self.userCapacity: return -1
#         self.userCapacity[userId] = capacity
        
#         # 仅获取该用户的临时列表用于清理
#         user_files = sorted([f for f in self.files.values() if f.owner == userId], key=lambda f: (-f.size, f.name))
        
#         removed_count = 0
#         for f in user_files:
#             if self.userUsedSpace[userId] > capacity:
#                 self._delete_file(f.name)
#                 removed_count += 1
#             else: break
#         return removed_count

#     def _process_compression(self, userId: str, name: str, is_compress: bool) -> int:
#         """ 压缩和解压的通用逻辑映射 """
#         file = self.files.get(name)
#         if not file or file.owner != userId: return -1
        
#         is_already_comp = name.endswith(".COMPRESSED")
#         if is_compress == is_already_comp: return -1 # 状态不匹配
        
#         new_name = name + ".COMPRESSED" if is_compress else name[:-11]
#         new_size = file.size // 2 if is_compress else file.size * 2
        
#         if new_name in self.files or not self._has_space(userId, new_size - file.size):
#             return -1
            
#         self._delete_file(name)
#         self._save_file(new_name, new_size, userId)
#         return self.userCapacity[userId] - self.userUsedSpace[userId]

#     def compressFile(self, userId: str, name: str) -> int:
#         return self._process_compression(userId, name, True)

#     def decompressFile(self, userId: str, name: str) -> int:
#         return self._process_compression(userId, name, False)