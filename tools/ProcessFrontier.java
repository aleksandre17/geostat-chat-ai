import java.sql.*;
public class ProcessFrontier {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/geostat?currentSchema=ingestion", "geostat", "geostat-dev-change-me")) {
            // Check crawl status
            ResultSet rs = c.createStatement().executeQuery(
                "SELECT id, status FROM crawl_run ORDER BY started_at DESC LIMIT 1");
            if (rs.next()) {
                System.out.println("Latest crawl: " + rs.getString("status") + " (id: " + rs.getString("id") + ")");
            }
            
            // Count queued URLs
            rs = c.createStatement().executeQuery("SELECT COUNT(*) as cnt FROM url_frontier WHERE status = 'queued'");
            if (rs.next()) System.out.println("Queued URLs: " + rs.getInt("cnt"));
            
            // Check if management pages exist
            rs = c.createStatement().executeQuery(
                "SELECT canonical_url, title FROM document WHERE canonical_url LIKE '%xelmdzghvaneloba%' OR canonical_url LIKE '%management%'");
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println("Found doc: " + rs.getString("title") + " | " + rs.getString("canonical_url"));
            }
            if (!found) System.out.println("Management docs NOT found yet");
            
            System.out.println("Total docs: " + countDocs(c));
        }
    }
    static int countDocs(Connection c) throws Exception {
        ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM document");
        return rs.next() ? rs.getInt(1) : 0;
    }
}