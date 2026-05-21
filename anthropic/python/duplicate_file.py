# Part 1
import hashlib
from io import BytesIO
from typing import List, Dict, Optional

#
# Node representing either a directory or file in the simulated file system
#
class FSNode:
    def __init__(self, isDir=None, content=None):
        if content is not None:
            self.isDir = False
            self.content = content
        else:
            self.isDir = isDir if isDir is not None else True
            if self.isDir:
                self.children = {}

#
# In-memory file system implementation
#
class FileSystem:
    def __init__(self):
        self.root = FSNode(True)

    # Add a file from string content
    def addFile(self, path: str, content: str):
        self.addFileBytes(path, content.encode())

    # Add a file from raw bytes
    def addFileBytes(self, path: str, content: bytes):
        parts = self.splitPath(path)
        curr = self.root

        for i in range(len(parts) - 1):
            part = parts[i]
            if part not in curr.children:
                curr.children[part] = FSNode(True)
            curr = curr.children[part]
        fileName = parts[len(parts) - 1]
        curr.children[fileName] = FSNode(content=content)

    def listFiles(self, path: str) -> List[str]:
        node = self.getNode(path)
        if node is None or not node.isDir:
            return []
        result = []
        prefix = path if path.endswith("/") else path + "/"
        for name in node.children.keys():
            result.append(prefix + name)
        return result

    def isDirectory(self, path: str) -> bool:
        node = self.getNode(path)
        return node is not None and node.isDir

    def getFileSize(self, path: str) -> int:
        node = self.getNode(path)
        if node is None or node.isDir:
            return 0
        return len(node.content)

    def openStream(self, path: str):
        node = self.getNode(path)
        if node is None or node.isDir:
            return BytesIO(b'')
        return BytesIO(node.content)

    def getNode(self, path: str) -> Optional[FSNode]:
        if path == "/":
            return self.root
        parts = self.splitPath(path)
        curr = self.root
        for part in parts:
            if curr is None or not curr.isDir or part not in curr.children:
                return None
            curr = curr.children[part]
        return curr

    def splitPath(self, path: str) -> List[str]:
        cleaned = path
        if cleaned.startswith("/"):
            cleaned = cleaned[1:]
        if cleaned.endswith("/"):
            cleaned = cleaned[:-1]
        if not cleaned:
            return []
        return cleaned.split("/")

class DuplicateFileFinder:
    def __init__(self, fs: FileSystem):
        self.fs = fs

    def findDuplicateFiles(self) -> List[List[str]]:
        # Collect all file paths via DFS traversal
        allFiles = []
        self.collectFiles("/", allFiles)

        # Group files by size (only same-size files can be duplicates)
        sizeMap = {}
        for filePath in allFiles:
            size = self.fs.getFileSize(filePath)
            if size not in sizeMap:
                sizeMap[size] = []
            sizeMap[size].append(filePath)

        # For each size group with 2+ files, compute hash and group by hash
        result = []
        for sizeGroup in sizeMap.values():
            if len(sizeGroup) < 2:
                continue
            hashMap = {}
            for filePath in sizeGroup:
                hash = self.computeHash(filePath)
                if hash not in hashMap:
                    hashMap[hash] = []
                hashMap[hash].append(filePath)
            # Collect groups with 2+ files
            for group in hashMap.values():
                if len(group) >= 2:
                    group.sort()
                    result.append(group)

        return result

    def collectFiles(self, path: str, files: List[str]):
        if not self.fs.isDirectory(path):
            files.append(path)
            return
        children = self.fs.listFiles(path)
        for child in children:
            self.collectFiles(child, files)

    def computeHash(self, filePath: str) -> str:
        try:
            md = hashlib.sha256()
            stream = self.fs.openStream(filePath)
            buffer = bytearray(4096)
            while True:
                bytesRead = stream.readinto(buffer)
                if bytesRead is None or bytesRead == 0:
                    break
                md.update(buffer[:bytesRead])
            stream.close()
            digest = md.digest()

            sb = ""
            for b in digest:
                sb += f"{b:02x}"

            return sb
        except Exception as e:
            raise RuntimeError(e)