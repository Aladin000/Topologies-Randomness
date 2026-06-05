package aaron;

/**
 * Represents an undirected edge between two nodes.
 * By convention, from &lt;= to for consistent representation.
 */
public record Edge(int from, int to) {

    public Edge {
        if (from > to) {
            int temp = from;
            from = to;
            to = temp;
        }
    }
}

