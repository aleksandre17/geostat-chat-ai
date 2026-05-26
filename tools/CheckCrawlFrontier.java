import java.sql.*;
public class CheckCrawlFrontier {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/geostat?currentSchema=ingestion", "geostat", "geostat-dev-change-me")) {
            ResultSet rs = c.createStatement().executeQuery(
                "SELECT url, status, depth FROM crawl_frontier WHERE url LIKE '%xelmdzghvaneloba%' OR url LIKE '%management%'");
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println(rs.getString("status") + " | depth=" + rs.getInt("depth") + " | " + rs.getString("url"));
            }
            if (!found) System.out.println("No management URLs in frontier");
            
            // Check pending count
            rs = c.createStatement().executeQuery("SELECT COUNT(*) as cnt FROM crawl_frontier WHERE status = 'pending'");
            if (rs.next()) System.out.println("Pending in frontier: " + rs.getInt("cnt"));
        }
    }
}