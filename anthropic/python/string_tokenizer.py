# Example 1:

# Input: text = "applepiepear", dictionary = ["app:10", "apple:20", "pie:30"]
# Output: ["20", "30", "p", "e", "a", "r"]
# Explanation: At the start, "apple" is matched instead of the shorter "app". Then "pie" is matched. The remaining characters do not match any token and are kept as literals.

# Example 2:

# Input: text = "acdebe", dictionary = ["a:1", "b:2", "cd:3"]
# Output: ["1", "3", "e", "2", "e"]

# Example 3:

# Input: text = "programmingprogrampropro", dictionary = ["pro:1", "program:2", "programming:3", "gram:4", "ming:5", "pr:6", "og:7"]
# Output: ["3", "2", "1", "1"]

from typing import List

class Trie:
    def __init__(self):
        self.children = {}
        self.id = None

class Solution:
    def tokenize(self, text: str, dictionary: List[str]) -> List[str]:
        root = self.buildTrie(dictionary)
        result = []

        i = 0
        while i < len(text):
            # Try to find longest match starting at position i
            node = root
            bestId = None
            bestEnd = i

            j = i
            while j < len(text):
                c = text[j]
                if c not in node.children:
                    break
                node = node.children[c]
                j = j + 1

                # Track the last terminal node we've seen
                if node.id is not None:
                    bestId = node.id
                    bestEnd = j

            if bestId is not None:
                result.append(bestId)
                i = bestEnd
            else:
                # No match found, emit literal character
                result.append(str(text[i]))
                i = i + 1

        return result


    def buildTrie(self, dictionary: List[str]) -> Trie:
        root = Trie()

        for entry in dictionary:
            colonIndex = entry.find(':')
            if colonIndex == -1:
                continue

            key = entry[:colonIndex]
            val = entry[colonIndex + 1:]

            node = root
            for i in range(len(key)):
                c = key[i]
                if c not in node.children:
                    node.children[c] = Trie()
                node = node.children[c]
            # Last wins if duplicate keys
            node.id = val

        return root


def _run_tests() -> None:
    s = Solution()

    assert s.tokenize(
        "applepiepear",
        ["app:10", "apple:20", "pie:30"],
    ) == ["20", "30", "p", "e", "a", "r"]

    assert s.tokenize("acdebe", ["a:1", "b:2", "cd:3"]) == ["1", "3", "e", "2", "e"]

    assert s.tokenize(
        "programmingprogrampropro",
        [
            "pro:1",
            "program:2",
            "programming:3",
            "gram:4",
            "ming:5",
            "pr:6",
            "og:7",
        ],
    ) == ["3", "2", "1", "1"]

    assert s.tokenize("", ["a:1"]) == []
    assert s.tokenize("xyz", ["ab:1"]) == ["x", "y", "z"]
    assert s.tokenize("aa", ["a:1", "a:2"]) == ["2", "2"]

    print("string_tokenizer: all tests passed")


if __name__ == "__main__":
    _run_tests()