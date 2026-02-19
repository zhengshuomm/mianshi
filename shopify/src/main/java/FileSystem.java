import java.util.*;

public class FileSystem {
    // Node class to represent both files and directories
    class FileNode {
        String name;
        boolean isFile;
        StringBuilder content;  // For files
        Map<String, FileNode> children;  // For directories
        
        FileNode(String name, boolean isFile) {
            this.name = name;
            this.isFile = isFile;
            this.content = isFile ? new StringBuilder() : null;
            this.children = isFile ? null : new TreeMap<>();
        }
    }
    
    private FileNode root;
    
    public FileSystem() {
        root = new FileNode("", false);  // Root is a directory
    }
    
    public List<String> ls(String path) {
        FileNode node = getNode(path);
        List<String> result = new ArrayList<>();
        
        if (node == null) {
            return result;
        }
        
        if (node.isFile) {
            // If it's a file, return only the file name
            result.add(node.name);
        } else {
            // If it's a directory, return all children names in lexicographic order
            result.addAll(node.children.keySet());
        }
        
        return result;
    }
    
    public void mkdir(String path) {
        String[] parts = parsePath(path);
        FileNode current = root;
        
        for (String part : parts) {
            if (!current.children.containsKey(part)) {
                current.children.put(part, new FileNode(part, false));
            }
            current = current.children.get(part);
        }
    }
    
    public void addContentToFile(String filePath, String content) {
        String[] parts = parsePath(filePath);
        FileNode current = root;
        
        // Navigate to parent directory
        for (int i = 0; i < parts.length - 1; i++) {
            if (!current.children.containsKey(parts[i])) {
                current.children.put(parts[i], new FileNode(parts[i], false));
            }
            current = current.children.get(parts[i]);
        }
        
        // Create or get the file
        String fileName = parts[parts.length - 1];
        if (!current.children.containsKey(fileName)) {
            current.children.put(fileName, new FileNode(fileName, true));
        }
        
        FileNode file = current.children.get(fileName);
        file.content.append(content);
    }
    
    public String readContentFromFile(String filePath) {
        FileNode node = getNode(filePath);
        if (node != null && node.isFile) {
            return node.content.toString();
        }
        return "";
    }
    
    // Helper method to get a node at the given path
    private FileNode getNode(String path) {
        String[] parts = parsePath(path);
        FileNode current = root;
        
        for (String part : parts) {
            if (!current.children.containsKey(part)) {
                return null;
            }
            current = current.children.get(part);
        }
        
        return current;
    }
    
    // Helper method to parse path into parts
    private String[] parsePath(String path) {
        if (path.equals("/")) {
            return new String[0];
        }
        // Remove leading slash and split
        String[] parts = path.substring(1).split("/");
        return parts;
    }
}
