import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemDB {

    public static void main(String[] args) {
        InMemDB sql = new InMemDB();
        sql.insert("book", Arrays.asList("title", "author", "price"), Arrays.asList("Harry Potter", "J.K. Rowling",
                "40"));
        sql.insert("book", Arrays.asList("title", "author", "price"),
                Arrays.asList("Lord of the Rings", "J.R.R. Tolkien", "50"));
        sql.insert("book", Arrays.asList("title", "author", "price"), Arrays.asList("The Hobbit", "J.R.R. Tolkien",
                "30"));
        sql.insert("book", Arrays.asList("title", "author", "price"),
                Arrays.asList("The Testaments", "J.R.R. Tolkien", "10"));

        // 按 price 升序，返回所有列
        System.out.println(Arrays.toString(sql.select("book", new HashMap<>() {
            {
                put("logic", "AND");
                put("condition", "author,=,J.R.R. Tolkien#price,>,20");
            }
        }, null, Arrays.asList("price:asc")).toArray()));

        // 按 price 降序，返回所有列
        System.out.println(Arrays.toString(sql.select("book", new HashMap<>() {
            {
                put("logic", "AND");
                put("condition", "author,=,J.R.R. Tolkien#price,>,20");
            }
        }, null, Arrays.asList("price:desc")).toArray()));

        // 按 price 降序，只返回 price 列
        System.out.println(Arrays.toString(sql.select("book", new HashMap<>() {
            {
                put("logic", "AND");
                put("condition", "author,=,J.R.R. Tolkien#price,>,20");
            }
        }, Arrays.asList("price"), Arrays.asList("price:desc")).toArray()));
    }

    class Table {
        String tableName;
        List<Row> rows;

        public Table(String tableName) {
            this.tableName = tableName;
            this.rows = new ArrayList<>();
        }
    }

    class Row {
        Map<String, String> columnValue = new HashMap<>();


        @Override
        public String toString() {
            String str = "";
            for (String key : columnValue.keySet()) {
                str = str + key + ":" + columnValue.get(key) + ",";
            }
            return str.substring(0, str.length() - 1);
        }
    }

    Map<String, Table> tables = new HashMap<>();

    public List<Row> select(String tableName, Map<String, String> conditions, List<String> columns,
            List<String> order) {
        if (!tables.containsKey(tableName))
            return null;
        List<Row> rows = tables.get(tableName).rows;
        List<Row> result = new ArrayList<>();
        if (conditions.size() > 0) {
            String logic = conditions.get("logic");
            String condition = conditions.get("condition");
            for (Row row : rows) {
                if (where(row, logic, condition)) {
                    result.add(row);
                }
            }
        } else {
            result = rows;
        }

        if (order != null) {
            order(result, order);
        }

        if (columns != null) {
            List<Row> newRes = new ArrayList<>();
            for (Row row : result) {
                Row nR = new Row();
                for (String column : row.columnValue.keySet()) {
                    if (columns.contains(column)) {
                        nR.columnValue.put(column, row.columnValue.get(column));
                    }
                }
                newRes.add(nR);
            }
            return newRes;
        }
        return result;
    }

    private void order(List<Row> result, List<String> orders) {
        result.sort((a, b) -> {
            for (String orderSpec : orders) {
                // 支持 "column:direction" 格式 (例如: "price:asc" 或 "price:desc")
                String[] parts = orderSpec.split(":");
                String column = parts[0];
                boolean asc = parts.length == 1 || parts[1].equals("asc");  // 默认升序
                
                String vA = a.columnValue.get(column);
                String vB = b.columnValue.get(column);
                
                // 先判断类型，使用正确的比较方式
                int res;
                if (isNumeric(vA) && isNumeric(vB)) {
                    res = Integer.compare(Integer.parseInt(vA), Integer.parseInt(vB));
                } else {
                    res = vA.compareTo(vB);
                }
                
                // 如果是降序，反转比较结果
                if (!asc) {
                    res = -res;
                }
                
                if (res != 0)
                    return res;
            }
            return 0;
        });
    }

    private boolean isNumeric(String s) {
        if (s == null || s.isEmpty())
            return false;
        return s.matches("-?\\d+");  // FIX #5: 使用正则，可选负号+数字
    }

    private boolean where(Row row, String logic, String condition) {
        if (condition == null)
            return true;

        // both logic and condition is not null
        String[] conditions = condition.split("#");
        for (String c : conditions) {
            String[] cSplit = c.split(",");
            String column = cSplit[0];
            String op = cSplit[1];
            String value = cSplit[2];
            boolean m = compare(row, column, op, value);
            if (logic.equals("AND")) {
                if (!m)
                    return false;
            } else if (m) {
                return true;
            }
        }
        return true;
    }

    public boolean compare(Row row, String column, String op, String value) {
        String rowVal = row.columnValue.get(column);
        if (op.equals("=")) {
            return rowVal.equals(value);
        }
        
        // FIX #3: 数字比较
        int cmp;
        if (isNumeric(rowVal) && isNumeric(value)) {
            cmp = Integer.compare(Integer.parseInt(rowVal), Integer.parseInt(value));
        } else {
            cmp = rowVal.compareTo(value);
        }
        
        if (op.equals(">")) {
            return cmp > 0;
        } else {
            return cmp < 0;
        }
    }

    public void insert(String tableName, List<String> columns, List<String> values) {
        tables.putIfAbsent(tableName, new Table(tableName));  // FIX #1: 只在不存在时创建
        List<Row> rows = tables.get(tableName).rows;
        Row row = new Row();
        for (int i = 0; i < columns.size(); i++) {
            row.columnValue.put(columns.get(i), values.get(i));
        }
        rows.add(row);

    }
}
