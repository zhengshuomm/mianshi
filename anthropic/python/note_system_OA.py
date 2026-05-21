from functools import cmp_to_key
from typing import List, Optional, Tuple

# Snapshot: title, content, createdAt, updatedAt, workspace
NoteSnapshot = Tuple[str, str, int, int, str]


class SecondBrainSystem:
    def __init__(self):
        self.notes = {}
        self.titleIndex = {}  # lower(title) -> noteId
        self.workspaces = {}
        self.noteWorkspace = {}  # noteId -> workspaceId or "default"
        self.counter = 1
        # noteId -> list of (event_timestamp, snapshot_or_None)  None = merged away / deleted
        self.note_history: dict[str, list[tuple[int, Optional[NoteSnapshot]]]] = {}

    def _record_snapshot(
        self, noteId: str, ts: int, snapshot: Optional[NoteSnapshot]
    ) -> None:
        self.note_history.setdefault(noteId, []).append((ts, snapshot))

    def createNote(self, timestamp: int, title: str, content: str) -> str:
        if not title:
            return ""

        key = title.lower()
        if key in self.titleIndex:
            return ""

        noteId = "note" + str(self.counter)
        self.counter += 1

        self.notes[noteId] = (title, content, timestamp, timestamp)
        self.titleIndex[key] = noteId
        self.noteWorkspace[noteId] = "default"

        self._record_snapshot(
            noteId,
            timestamp,
            (title, content, timestamp, timestamp, "default"),
        )

        return noteId

    def getNote(self, timestamp: int, noteId: str) -> str:
        if noteId not in self.notes:
            return ""

        title, content, createdAt, updatedAt = self.notes[noteId]
        workspace = self.noteWorkspace.get(noteId, "default")

        return "|".join([noteId, title, content, str(createdAt), str(updatedAt), workspace])

    def updateNote(self, timestamp: int, noteId: str, title: str, content: str) -> bool:
        if noteId not in self.notes or not title:
            return False

        newKey = title.lower()
        existing = self.titleIndex.get(newKey)
        if existing is not None and existing != noteId:
            return False

        oldTitle, _, createdAt, _ = self.notes[noteId]
        oldKey = oldTitle.lower()

        if oldKey != newKey:
            del self.titleIndex[oldKey]
            self.titleIndex[newKey] = noteId

        self.notes[noteId] = (title, content, createdAt, timestamp)

        ws = self.noteWorkspace.get(noteId, "default")
        self._record_snapshot(
            noteId, timestamp, (title, content, createdAt, timestamp, ws)
        )

        return True

    def deleteNote(self, timestamp: int, noteId: str) -> bool:
        if noteId not in self.notes:
            return False

        title, _, _, _ = self.notes[noteId]
        del self.titleIndex[title.lower()]

        ws = self.noteWorkspace.get(noteId, "default")
        if ws != "default" and ws in self.workspaces:
            self.workspaces[ws][1].remove(noteId)

        self._record_snapshot(noteId, timestamp, None)

        del self.notes[noteId]
        del self.noteWorkspace[noteId]

        return True

    def mergeNotes(
        self, timestamp: int, targetNoteId: str, sourceNoteId: str
    ) -> bool:
        if targetNoteId == sourceNoteId:
            return False
        if targetNoteId not in self.notes or sourceNoteId not in self.notes:
            return False

        t_title, t_content, t_created, _ = self.notes[targetNoteId]
        s_title, s_content, _, _ = self.notes[sourceNoteId]

        merged_content = t_content + "\n" + s_content
        ws_target = self.noteWorkspace.get(targetNoteId, "default")

        # Source title now resolves to target (Appendix -> note1)
        self.titleIndex[s_title.lower()] = targetNoteId

        # Remove source from workspace
        ws_src = self.noteWorkspace.get(sourceNoteId, "default")
        if ws_src != "default" and ws_src in self.workspaces:
            self.workspaces[ws_src][1].discard(sourceNoteId)

        self.notes[targetNoteId] = (
            t_title,
            merged_content,
            t_created,
            timestamp,
        )

        self._record_snapshot(
            targetNoteId,
            timestamp,
            (t_title, merged_content, t_created, timestamp, ws_target),
        )
        self._record_snapshot(sourceNoteId, timestamp, None)

        del self.notes[sourceNoteId]
        del self.noteWorkspace[sourceNoteId]

        return True

    def getNoteAt(self, timestamp: int, noteId: str, atTimestamp: int) -> str:
        if noteId not in self.note_history:
            return ""

        last: Optional[NoteSnapshot] = None
        for ts, snap in self.note_history[noteId]:
            if ts <= atTimestamp:
                last = snap
            else:
                break

        if last is None:
            return ""

        title, content, createdAt, updatedAt, workspace = last
        return "|".join(
            [noteId, title, content, str(createdAt), str(updatedAt), workspace]
        )

    def getOutgoingLinks(self, timestamp: int, noteId: str) -> List[str]:
        if noteId not in self.notes:
            return []

        _, content, _, _ = self.notes[noteId]
        result = set()

        for linkedTitle in self._extractLinks(content):
            target = self._resolveTitle(linkedTitle)
            if target is not None and target != noteId:
                result.add(target)

        return sorted(list(result))

    def getIncomingLinks(self, timestamp: int, noteId: str) -> List[str]:
        if noteId not in self.notes:
            return []

        result = set()
        for otherId, (_, content, _, _) in self.notes.items():
            if otherId == noteId:
                continue
            for linkedTitle in self._extractLinks(content):
                target = self._resolveTitle(linkedTitle)
                if noteId == target:
                    result.add(otherId)

        return sorted(list(result))

    def createWorkspace(self, timestamp: int, workspaceId: str, maxCapacity: int) -> bool:
        if workspaceId in self.workspaces or maxCapacity < 1:
            return False

        self.workspaces[workspaceId] = (maxCapacity, set())
        return True

    def moveToWorkspace(self, timestamp: int, noteId: str, workspaceId: str) -> bool:
        if noteId not in self.notes:
            return False

        current = self.noteWorkspace.get(noteId, "default")

        if workspaceId == "default":
            if current != "default":
                self.workspaces[current][1].remove(noteId)
                self.noteWorkspace[noteId] = "default"
                self._snapshot_after_move(noteId, timestamp)
            return True

        if workspaceId not in self.workspaces:
            return False

        capacity, noteIds = self.workspaces[workspaceId]

        if current == workspaceId:
            return False
        if len(noteIds) >= capacity:
            return False

        if current != "default":
            self.workspaces[current][1].remove(noteId)

        noteIds.add(noteId)
        self.noteWorkspace[noteId] = workspaceId
        self._snapshot_after_move(noteId, timestamp)

        return True

    def _snapshot_after_move(self, noteId: str, ts: int) -> None:
        title, content, createdAt, updatedAt = self.notes[noteId]
        ws = self.noteWorkspace.get(noteId, "default")
        self._record_snapshot(noteId, ts, (title, content, createdAt, updatedAt, ws))

    def getWorkspaceNotes(self, timestamp: int, workspaceId: str) -> List[str]:
        if workspaceId == "default" or workspaceId not in self.workspaces:
            return []

        _, noteIds = self.workspaces[workspaceId]
        ids = list(noteIds)

        def compare_notes(a: str, b: str) -> int:
            createdA = self.notes[a][2]
            createdB = self.notes[b][2]
            if createdA != createdB:
                return createdA - createdB
            return -1 if a < b else (1 if a > b else 0)

        ids.sort(key=cmp_to_key(compare_notes))

        return ids

    def _extractLinks(self, content: str) -> List[str]:
        links = []
        for line in content.splitlines():
            line = line.strip()
            if line.startswith("[[") and line.endswith("]]"):
                links.append(line[2:-2])
        return list(filter(None, links))

    def _resolveTitle(self, title: str) -> Optional[str]:
        noteId = self.titleIndex.get(title.lower())
        if noteId is None or noteId not in self.notes:
            return None
        return noteId


def _run_example1() -> None:
    sys = SecondBrainSystem()
    assert sys.createNote(1, "Main", "Main content") == "note1"
    assert sys.createNote(2, "Appendix", "Extra details") == "note2"
    assert sys.updateNote(3, "note1", "Main", "Main content updated")
    assert sys.createNote(4, "Linker", "See\n[[Appendix]]\nfor more") == "note3"
    assert sys.getOutgoingLinks(5, "note3") == ["note2"]
    assert sys.getIncomingLinks(6, "note2") == ["note3"]
    assert sys.mergeNotes(7, "note1", "note2")
    assert (
        sys.getNote(8, "note1")
        == "note1|Main|Main content updated\nExtra details|1|7|default"
    )
    assert sys.getNote(9, "note2") == ""
    assert (
        sys.getNoteAt(10, "note2", 5)
        == "note2|Appendix|Extra details|2|2|default"
    )
    assert sys.getNoteAt(11, "note2", 7) == ""
    assert sys.getOutgoingLinks(12, "note3") == ["note1"]
    assert sys.getIncomingLinks(13, "note1") == ["note3"]
    assert not sys.mergeNotes(14, "note1", "note1")
    assert (
        sys.getNoteAt(15, "note1", 1)
        == "note1|Main|Main content|1|1|default"
    )
    assert (
        sys.getNoteAt(16, "note1", 7)
        == "note1|Main|Main content updated\nExtra details|1|7|default"
    )


def _run_example2() -> None:
    sys = SecondBrainSystem()
    assert sys.createNote(1, "Note A", "Content A\n[[Note B]]") == "note1"
    assert sys.createNote(2, "Note B", "Content B") == "note2"
    assert (
        sys.createNote(3, "Note C", "Links to\n[[Note A]]\n[[Note B]]") == "note3"
    )
    assert sys.createWorkspace(4, "ws1", 5)
    assert sys.moveToWorkspace(5, "note1", "ws1")
    assert sys.moveToWorkspace(6, "note2", "ws1")
    assert sys.getWorkspaceNotes(7, "ws1") == ["note1", "note2"]
    assert sys.mergeNotes(8, "note1", "note2")
    assert sys.getWorkspaceNotes(9, "ws1") == ["note1"]
    assert sys.getOutgoingLinks(10, "note1") == []
    assert sys.getOutgoingLinks(11, "note3") == ["note1"]


if __name__ == "__main__":
    _run_example1()
    _run_example2()
    print("note_system: examples ok")
