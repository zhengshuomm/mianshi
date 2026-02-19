import java.util.*;

public class SQLManager {

    class Table {
        List<String> columns = new ArrayList<>();
        Map<String, Integer> columnIndex = new HashMap<>();
        List<Row> rows = new ArrayList<>();
        String name;

        public Table(String name, List<String> columns) {
            this.name = name;
            this.columns = columns;
            for (int i = 0 ; i < columns.size() ; i ++) {
                columnIndex.put(columns.get(i), i);
            }
        }
    }

    class Row {
        int rowId;
        List<String> values = new ArrayList<>();
    }

    Map<String, Table> map;

    public SQLManager() {
        this.map = new HashMap<>();
    }

    public void createTable(String tableName, List<String> columnNames) {
        map.putIfAbsent(tableName, new Table(tableName, columnNames));
        map.get(tableName).columns = columnNames;
    }

    public void insert(String tableName, List<String> values) {
        if (!map.containsKey(tableName)) return;
        List<Row> rows = map.get(tableName).rows;
        Row row = new Row();
        row.rowId = rows.size();
        row.values = values;
        rows.add(row);
    }

    public List<Integer> select(String tableName, List<List<String>> conditions, List<String> orderBy) {
        Table table = map.get(tableName);
        List<Row> res = new ArrayList<>();
        for (Row row: table.rows) {
            if (where(row, conditions, table)) {
                res.add(row);
            }
        }

        if (!orderBy.isEmpty()) {
            order(res, orderBy, table);
        }
        List<Integer> result = new ArrayList<>();
        for (Row row: res) {
            result.add(row.rowId);
        }
        return result;

    }

    private boolean where(Row row, List<List<String>> conditions, Table table) {
        if (conditions == null) return true;
        for (List<String> condition : conditions) {
            String column = condition.get(0);
            String op = condition.get(1);
            String value = condition.get(2);
            boolean m = compare(row, column, op, value, table);
            if (!m) return false;
        }
        return true;

    }

    private boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        for (char c : s.toCharArray()) {
            if (!Character.isDigit(c) && c != '-') return false;
        }
        return true;
    }

    private boolean compare(Row row, String column, String op, String value, Table table) {
        int index = table.columnIndex.get(column);
        String rowVal = row.values.get(index);

        // 如果都是数字，用数值比较
        if (isNumeric(rowVal) && isNumeric(value)) {
            int rowNum = Integer.parseInt(rowVal);
            int valNum = Integer.parseInt(value);
            if (op.equals(">")) return rowNum > valNum;
            if (op.equals("<")) return rowNum < valNum;
            return rowNum == valNum;
        }
        
        // 否则用字符串比较
        if (op.equals(">")) return rowVal.compareTo(value) > 0;
        if (op.equals("<")) return rowVal.compareTo(value) < 0;
        return rowVal.equals(value);
    }

    private void order(List<Row> rows, List<String> orderBy, Table table) {
        rows.sort((a, b) -> {
            for (String col : orderBy) {
                int idx = table.columnIndex.get(col);
                String aVal = a.values.get(idx);
                String bVal = b.values.get(idx);
                
                if (aVal.equals(bVal)) continue;
                
                // 数值比较
                if (isNumeric(aVal) && isNumeric(bVal)) {
                    return Integer.compare(Integer.parseInt(aVal), Integer.parseInt(bVal));
                }
                
                // 字符串比较
                return aVal.compareTo(bVal);
            }
            return 0;
        });
    }

    // Helper method to print row details
    private void printResults(String query, List<Integer> rowIds) {
        System.out.println("\n" + query);
        System.out.println("Row IDs: " + rowIds);
        Table table = map.get("books");
        for (int id : rowIds) {
            Row row = table.rows.get(id);
            System.out.println("  [" + id + "] " + String.join(", ", row.values));
        }
    }

    public static void main(String[] args) {
        SQLManager sql = new SQLManager();
        
        // createTable
        sql.createTable("books", Arrays.asList("title", "year", "rating"));
        System.out.println("Table 'books' created with columns: title, year, rating");
        
        // insert
        sql.insert("books", Arrays.asList("1984", "1949", "9"));
        sql.insert("books", Arrays.asList("Dune", "1965", "10"));
        sql.insert("books", Arrays.asList("Foundation", "1951", "8"));
        sql.insert("books", Arrays.asList("Brave New World", "1932", "8"));
        sql.insert("books", Arrays.asList("Neuromancer", "1984", "9"));
        System.out.println("\nInserted 5 books");
        
        // select: year = 1965
        sql.printResults("SELECT WHERE year = 1965:", 
            sql.select("books", Arrays.asList(Arrays.asList("year", "=", "1965")), new ArrayList<>()));
        
        // select: year < 1950
        sql.printResults("SELECT WHERE year < 1950:", 
            sql.select("books", Arrays.asList(Arrays.asList("year", "<", "1950")), new ArrayList<>()));
        
        // select: rating > 8
        sql.printResults("SELECT WHERE rating > 8:", 
            sql.select("books", Arrays.asList(Arrays.asList("rating", ">", "8")), new ArrayList<>()));
        
        // select: year > 1940 AND rating > 8
        sql.printResults("SELECT WHERE year > 1940 AND rating > 8:", 
            sql.select("books", Arrays.asList(
                Arrays.asList("year", ">", "1940"),
                Arrays.asList("rating", ">", "8")
            ), new ArrayList<>()));
        
        // select: order by year
        sql.printResults("SELECT ORDER BY year:", 
            sql.select("books", new ArrayList<>(), Arrays.asList("year")));
        
        // select: order by rating, title
        sql.printResults("SELECT ORDER BY rating, title:", 
            sql.select("books", new ArrayList<>(), Arrays.asList("rating", "title")));
    }
}