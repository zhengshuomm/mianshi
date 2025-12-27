import java.util.*;

/*
# - Support Query
# - Query with Where condition
# - Query with Where condition on multiple columns
# - Query with Where and Order by one column
# - Query with Where and Order by multiple columns
# 其中where condition只要求考虑And的情况
# where("price", ">", "40")， order("price", "increase")
# select(table_name, where=None, order_by=None)
# insert
 */
public class InMemoryDB {

    public static void main(String[] args) {
        InMemoryDB sql = new InMemoryDB();
        sql.insert("book", Arrays.asList("title", "author", "price"), Arrays.asList("Harry Potter", "J.K. Rowling",
                "40"));
        sql.insert("book", Arrays.asList("title", "author", "price"), Arrays.asList("Lord of the Rings", "J.R.R. Tolkien", "50"));
        sql.insert("book", Arrays.asList("title", "author", "price"), Arrays.asList("The Hobbit", "J.R.R. Tolkien",
                "30"));
        sql.insert("book", Arrays.asList("title", "author", "price"), Arrays.asList("The Testaments", "J.R.R. Tolkien", "10"));

        System.out.println(Arrays.toString(sql.select("book", new HashMap<>() {{
            put("logic", "AND");
            put("condition", "author,=,J.R.R. Tolkien#price,>,20");
        }}, Arrays.asList("price", "increase"), null).toArray()));

        System.out.println(Arrays.toString(sql.select("book", new HashMap<>() {{
            put("logic", "AND");
            put("condition", "author,=,J.R.R. Tolkien#price,>,20");
        }}, Arrays.asList("price", "decrease"), null).toArray()));

        System.out.println(Arrays.toString(sql.select("book", new HashMap<>() {{
            put("logic", "AND");
            put("condition", "author,=,J.R.R. Tolkien#price,>,20");
        }}, Arrays.asList("price", "decrease"), Arrays.asList("price")).toArray()));
    }

    Map<String, Table> tables = new HashMap<>();

    public void insert(String tableName, List<String> columns, List<String> values) {
        tables.putIfAbsent(tableName, new Table());
        Table table = tables.get(tableName);
        Row row = new Row();
        for (int i = 0 ; i < columns.size(); i ++) {
            row.map.put(columns.get(i), values.get(i));
        }
        table.rows.add(row);
    }

    public boolean where(Row row, String condition, String logic) {
        if (condition == null) return true;

        // both logic and condition is not null
        String[] conditions = condition.split("#");
        for (String c: conditions) {
            String[] cSplit = c.split(",");
            String column = cSplit[0];
            String op = cSplit[1];
            String value = cSplit[2];
            boolean m = compare(row, column, op, value);
            if (logic.equals("AND")) {
                if (!m) return false;
            } else if (m){
                return true;
            }
        }
        return true;
    }

    public boolean compare(Row row, String column, String op, String value) {
        String rowVal = row.map.get(column);
        if (op.equals("=")) {
            return rowVal.equals(value);
        } else if (op.equals(">")) {
            return rowVal.compareTo(value) > 0;
        } else {
            return rowVal.compareTo(value) < 0;
        }
    }

    public List<Row> select(String tableName, Map<String, String> conditions, List<String> order, List<String> columns) {
        Table table = tables.get(tableName);
        List<Row> result = new ArrayList<>();
        if (conditions.size() > 0) {
            String logic = conditions.get("logic");
            String condition = conditions.get("condition");
            for (Row row : table.rows) {
                if (where(row, condition, logic)) {
                    result.add(row);
                }
            }
        }
        if (order != null) {
            order(result, order.get(0), order.get(1));
        }

        if (columns!= null ) {
            List<Row> newRes = new ArrayList<>();
            for (Row row: result) {
                Row nR = new Row();
                for (String column: row.map.keySet()) {
                    if (columns.contains(column)) {
                        nR.map.put(column, row.map.get(column));
                    }
                }
                newRes.add(nR);
            }
            return newRes;
        }
        return result;
    }

    public void order(List<Row> rows, String column, String condition) {
        Collections.sort(rows, (a, b) -> {
            if (condition.equals("increase")) {
                return a.map.get(column).compareTo(b.map.get(column));
            } else {
                return b.map.get(column).compareTo(a.map.get(column));
            }
        });
    }

    class Table {
        String name;
        List<Row> rows = new ArrayList<>();

    }

    class Row {
        Map<String, String> map = new HashMap<>();

        @Override
        public String toString() {
            String str = "";
            for (String key : map.keySet()) {
                str = str + key + ":" + map.get(key) + ",";
            }
            return str.substring(0, str.length() - 1);
        }
    }


}
