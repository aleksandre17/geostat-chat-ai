import java.sql.*;
public class CountDocs2 {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/geostat", "geostat", "geostat-dev-change-me")) {
            ResultSet rs = c.createStatement().executeQuery(
                "SELECT COUNT(*) as total, COUNT(*) FILTER (WHERE fetch_status = 'parsed') as parsed " +
                "FROM ingestion.document");
            if (rs.next()) {
                System.out.println("Total docs: " + rs.getInt("total") + ", Parsed: " + rs.getInt("parsed"));
            }
            rs = c.createStatement().executeQuery(
                "SELECT status FROM ingestion.crawl_run ORDER BY started_at DESC LIMIT 1");
            if (rs.next()) { System.out.println("Crawl: " + rs.getString("status")); }
            rs = c.createStatement().executeQuery(
                "SELECT COUNT(*) as cnt FROM ingestion.frontier WHERE status = 'pending'");
            if (rs.next()) { System.out.println("Pending frontier: " + rs.getInt("cnt")); }
        }
    }
}