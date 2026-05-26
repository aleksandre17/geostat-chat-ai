import java.sql.*;
public class FindManagement {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/geostat?currentSchema=ingestion", "geostat", "geostat-dev-change-me")) {
            ResultSet rs = c.createStatement().executeQuery(
                "SELECT id, url_hash, title, fetch_status FROM document " +
                "WHERE canonical_url LIKE '%xelmdzghvaneloba%' OR canonical_url LIKE '%management%' LIMIT 5");
            while (rs.next()) {
                System.out.println(rs.getString("id") + " | " + rs.getString("fetch_status") + " | " + rs.getString("title"));
            }
        }
    }
}