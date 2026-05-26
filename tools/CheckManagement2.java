import java.sql.*;
public class CheckManagement2 {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/geostat?currentSchema=ingestion", "geostat", "geostat-dev-change-me")) {
            ResultSet rs = c.createStatement().executeQuery(
                "SELECT id, canonical_url, title, fetch_status FROM document " +
                "WHERE canonical_url LIKE '%xelmdzghvaneloba%' OR canonical_url LIKE '%management%'");
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println("Found: " + rs.getString("fetch_status") + " | " + rs.getString("title") + " | " + rs.getString("canonical_url"));
            }
            if (!found) System.out.println("No management docs found yet");
            
            // Also check total doc count
            rs = c.createStatement().executeQuery("SELECT COUNT(*) as cnt FROM document");
            if (rs.next()) System.out.println("Total docs: " + rs.getInt("cnt"));
        }
    }
}