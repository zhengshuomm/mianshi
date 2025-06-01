import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class TextEditor {
    class State {
        int cursor;
        int selectedLength;
        String content;

        public State(int cursor, int selectedLength, String content) {
            this.cursor = cursor;
            this.selectedLength = selectedLength;
            this.content = content;
        }
    }

    private TextEditorManager textEditorManager;     // Stores the content of the text editor
    private StringBuilder content;      // Stores the current position of the cursor
    private int cursor;
    private int selectedLength;
    private Stack<State> previousStateStack;
    private Stack<State> undoStack;

    public TextEditor(TextEditorManager textEditorManager) {         // Empty content when the editor is opened.
        this.content = new StringBuilder();
        this.cursor = 0;
        this.selectedLength = 0;
        previousStateStack = new Stack<>();
        undoStack = new Stack<>();
        this.textEditorManager = textEditorManager;
    }

    public String append(String input) {
        saveCurrentState();
        clearUndoStack();
        int start = cursor - selectedLength;
        content.replace(start, cursor, input);
        cursor = start + input.length();
        selectedLength = 0;
        return content.toString();
    }

    public String delete() {
        saveCurrentState();
        clearUndoStack();
        int start = cursor - selectedLength;
        if (start == cursor) {
            content.deleteCharAt(start);
        } else {
            content.delete(start, cursor);
        }
        cursor = start;
        selectedLength = 0;
        return content.toString();
    }

    public void move(int position) {         // Single cursor move clears the selected length.
        selectedLength = 0;
        if (position <= 0) {
            cursor = 0;
            return;
        }
        if (cursor >= content.length()) {
            cursor = content.length();
            return;
        }
        cursor = position;
    }

    public String select(int selectPointer1, int selectPointer2) {
        int lowerBound = Math.min(selectPointer1, selectPointer2);
        int upperBound = Math.max(selectPointer1, selectPointer2);
        lowerBound = Math.max(0, lowerBound);
        upperBound = Math.min(content.length(), upperBound);          // Set cursor to the upper bound.
        cursor = upperBound;
        selectedLength = upperBound - lowerBound;
        return content.substring(lowerBound, upperBound);
    }

    public String copy() {
        textEditorManager.sharedClipBoard = content.substring(cursor - selectedLength, cursor);
        return textEditorManager.sharedClipBoard;
    }

    public String paste() {
        return append(textEditorManager.sharedClipBoard);
    }

    public String undo() {         // Push the current state to the undo stack.
        State currentState = new State(cursor, selectedLength, content.toString());
        undoStack.push(currentState);
        if (!previousStateStack.isEmpty()) {
            State previousState = previousStateStack.pop();
            content = new StringBuilder(previousState.content);
            cursor = previousState.cursor;
            selectedLength = previousState.selectedLength;
        } else {             // Set to initial state.
            content = new StringBuilder();
            cursor = 0;
            selectedLength = 0;
        }
        return content.toString();
    }

    public String redo() {
        if (undoStack.isEmpty()) {
            throw new RuntimeException("Cannot Redo!");
        }          // Push current state to previous state
        previousStateStack.push(new State(cursor, selectedLength, content.toString()));
        State beforeUndo = undoStack.pop();
        content = new StringBuilder(beforeUndo.content);
        cursor = beforeUndo.cursor;
        selectedLength = beforeUndo.selectedLength;
        return content.toString();
    }

    public void close() {
        selectedLength = 0;
        cursor = content.length();
        previousStateStack.clear();
        undoStack.clear();
    }

    private void saveCurrentState() {
        previousStateStack.push(new State(cursor, selectedLength, content.toString()));
    }

    private void clearUndoStack() {
        undoStack.clear();
    }

    class TextEditorManager {
        public String sharedClipBoard;
        Map<String, TextEditor> textEditorMap;
        Stack<TextEditor> activeTextEditorStack;

        public TextEditorManager() {
            this.sharedClipBoard = "";
            this.textEditorMap = new HashMap<>();
            this.activeTextEditorStack = new Stack<>();
        }

        public void open(String name) {         // Empty file name?
            TextEditor textEditor = textEditorMap.getOrDefault(name, new TextEditor(this));
            textEditorMap.putIfAbsent(name, textEditor);
            activeTextEditorStack.push(textEditor);
        }

        public void close() {
            TextEditor textEditor = activeTextEditorStack.pop();
            textEditor.close();
            String a ="";
           int c = a.compareTo(a);

        }

        public TextEditor getCurrentActiveTextEditor() {
            return activeTextEditorStack.peek();
        }
    }
}