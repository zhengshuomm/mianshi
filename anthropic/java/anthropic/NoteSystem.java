package anthropic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Second brain notes + merge + history (from note_system.py). */
public class NoteSystem {

    static class NoteSnap {
        final String title;
        final String content;
        final int createdAt;
        final int updatedAt;
        final String workspace;

        NoteSnap(String t, String c, int cr, int up, String w) {
            title = t;
            content = c;
            createdAt = cr;
            updatedAt = up;
            workspace = w;
        }
    }

    static class HistEntry {
        final int ts;
        final NoteSnap snap;

        HistEntry(int ts, NoteSnap snap) {
            this.ts = ts;
            this.snap = snap;
        }
    }

    static class WorkspaceEntry {
        final int capacity;
        final Set<String> noteIds = new HashSet<>();

        WorkspaceEntry(int capacity) {
            this.capacity = capacity;
        }
    }

    final Map<String, NoteSnap> notes = new HashMap<>();
    final Map<String, String> titleIndex = new HashMap<>();
    final Map<String, WorkspaceEntry> workspaces = new HashMap<>();
    final Map<String, String> noteWorkspace = new HashMap<>();
    int counter = 1;
    final Map<String, List<HistEntry>> noteHistory = new HashMap<>();

    void recordSnapshot(String noteId, int ts, NoteSnap snap) {
        noteHistory.computeIfAbsent(noteId, k -> new ArrayList<>()).add(new HistEntry(ts, snap));
    }

    public String createNote(int timestamp, String title, String content) {
        if (title.isEmpty()) return "";
        String key = title.toLowerCase();
        if (titleIndex.containsKey(key)) return "";
        String noteId = "note" + counter++;
        notes.put(noteId, new NoteSnap(title, content, timestamp, timestamp, "default"));
        titleIndex.put(key, noteId);
        noteWorkspace.put(noteId, "default");
        recordSnapshot(noteId, timestamp, new NoteSnap(title, content, timestamp, timestamp, "default"));
        return noteId;
    }

    public String getNote(int timestamp, String noteId) {
        NoteSnap n = notes.get(noteId);
        if (n == null) return "";
        String ws = noteWorkspace.getOrDefault(noteId, "default");
        return String.join(
                "|",
                Arrays.asList(
                        noteId,
                        n.title,
                        n.content,
                        String.valueOf(n.createdAt),
                        String.valueOf(n.updatedAt),
                        ws));
    }

    public boolean updateNote(int timestamp, String noteId, String title, String content) {
        NoteSnap old = notes.get(noteId);
        if (old == null || title.isEmpty()) return false;
        String newKey = title.toLowerCase();
        String existing = titleIndex.get(newKey);
        if (existing != null && !existing.equals(noteId)) return false;
        String oldKey = old.title.toLowerCase();
        if (!oldKey.equals(newKey)) {
            titleIndex.remove(oldKey);
            titleIndex.put(newKey, noteId);
        }
        String ws = noteWorkspace.getOrDefault(noteId, "default");
        NoteSnap neu = new NoteSnap(title, content, old.createdAt, timestamp, ws);
        notes.put(noteId, neu);
        recordSnapshot(noteId, timestamp, neu);
        return true;
    }

    public boolean deleteNote(int timestamp, String noteId) {
        NoteSnap n = notes.get(noteId);
        if (n == null) return false;
        titleIndex.remove(n.title.toLowerCase());
        String ws = noteWorkspace.getOrDefault(noteId, "default");
        if (!"default".equals(ws) && workspaces.containsKey(ws)) {
            workspaces.get(ws).noteIds.remove(noteId);
        }
        recordSnapshot(noteId, timestamp, null);
        notes.remove(noteId);
        noteWorkspace.remove(noteId);
        return true;
    }

    public boolean mergeNotes(int timestamp, String targetNoteId, String sourceNoteId) {
        if (targetNoteId.equals(sourceNoteId)) return false;
        NoteSnap t = notes.get(targetNoteId);
        NoteSnap s = notes.get(sourceNoteId);
        if (t == null || s == null) return false;
        String mergedContent = t.content + "\n" + s.content;
        String wsTarget = noteWorkspace.getOrDefault(targetNoteId, "default");
        titleIndex.put(s.title.toLowerCase(), targetNoteId);
        String wsSrc = noteWorkspace.getOrDefault(sourceNoteId, "default");
        if (!"default".equals(wsSrc) && workspaces.containsKey(wsSrc)) {
            workspaces.get(wsSrc).noteIds.remove(sourceNoteId);
        }
        NoteSnap merged = new NoteSnap(t.title, mergedContent, t.createdAt, timestamp, wsTarget);
        notes.put(targetNoteId, merged);
        recordSnapshot(targetNoteId, timestamp, merged);
        recordSnapshot(sourceNoteId, timestamp, null);
        notes.remove(sourceNoteId);
        noteWorkspace.remove(sourceNoteId);
        return true;
    }

    public String getNoteAt(int timestamp, String noteId, int atTimestamp) {
        List<HistEntry> h = noteHistory.get(noteId);
        if (h == null) return "";
        NoteSnap last = null;
        for (HistEntry e : h) {
            if (e.ts <= atTimestamp) {
                last = e.snap;
            } else {
                break;
            }
        }
        if (last == null) return "";
        return String.join(
                "|",
                Arrays.asList(
                        noteId,
                        last.title,
                        last.content,
                        String.valueOf(last.createdAt),
                        String.valueOf(last.updatedAt),
                        last.workspace));
    }

    public List<String> getOutgoingLinks(int timestamp, String noteId) {
        NoteSnap n = notes.get(noteId);
        if (n == null) return Collections.emptyList();
        Set<String> result = new HashSet<>();
        for (String linkedTitle : extractLinks(n.content)) {
            String target = resolveTitle(linkedTitle);
            if (target != null && !target.equals(noteId)) {
                result.add(target);
            }
        }
        List<String> out = new ArrayList<>(result);
        Collections.sort(out);
        return out;
    }

    public List<String> getIncomingLinks(int timestamp, String noteId) {
        if (!notes.containsKey(noteId)) return Collections.emptyList();
        Set<String> result = new HashSet<>();
        for (Map.Entry<String, NoteSnap> e : notes.entrySet()) {
            String otherId = e.getKey();
            if (otherId.equals(noteId)) continue;
            for (String linkedTitle : extractLinks(e.getValue().content)) {
                String target = resolveTitle(linkedTitle);
                if (noteId.equals(target)) {
                    result.add(otherId);
                }
            }
        }
        List<String> out = new ArrayList<>(result);
        Collections.sort(out);
        return out;
    }

    public boolean createWorkspace(int timestamp, String workspaceId, int maxCapacity) {
        if (workspaces.containsKey(workspaceId) || maxCapacity < 1) return false;
        workspaces.put(workspaceId, new WorkspaceEntry(maxCapacity));
        return true;
    }

    public boolean moveToWorkspace(int timestamp, String noteId, String workspaceId) {
        if (!notes.containsKey(noteId)) return false;
        String current = noteWorkspace.getOrDefault(noteId, "default");
        if ("default".equals(workspaceId)) {
            if (!"default".equals(current)) {
                workspaces.get(current).noteIds.remove(noteId);
                noteWorkspace.put(noteId, "default");
                snapshotAfterMove(noteId, timestamp);
            }
            return true;
        }
        WorkspaceEntry we = workspaces.get(workspaceId);
        if (we == null) return false;
        if (current.equals(workspaceId)) return false;
        if (we.noteIds.size() >= we.capacity) return false;
        if (!"default".equals(current)) {
            workspaces.get(current).noteIds.remove(noteId);
        }
        we.noteIds.add(noteId);
        noteWorkspace.put(noteId, workspaceId);
        snapshotAfterMove(noteId, timestamp);
        return true;
    }

    void snapshotAfterMove(String noteId, int ts) {
        NoteSnap n = notes.get(noteId);
        String ws = noteWorkspace.getOrDefault(noteId, "default");
        recordSnapshot(noteId, ts, new NoteSnap(n.title, n.content, n.createdAt, n.updatedAt, ws));
    }

    public List<String> getWorkspaceNotes(int timestamp, String workspaceId) {
        if ("default".equals(workspaceId) || !workspaces.containsKey(workspaceId)) {
            return Collections.emptyList();
        }
        Set<String> ids = workspaces.get(workspaceId).noteIds;
        List<String> list = new ArrayList<>(ids);
        list.sort(
                Comparator.comparingInt((String id) -> notes.get(id).createdAt)
                        .thenComparing(Comparator.naturalOrder()));
        return list;
    }

    List<String> extractLinks(String content) {
        List<String> links = new ArrayList<>();
        for (String line : content.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("[[") && t.endsWith("]]")) {
                String inner = t.substring(2, t.length() - 2);
                if (!inner.isEmpty()) {
                    links.add(inner);
                }
            }
        }
        return links;
    }

    String resolveTitle(String title) {
        String noteId = titleIndex.get(title.toLowerCase());
        if (noteId == null || !notes.containsKey(noteId)) return null;
        return noteId;
    }
}
