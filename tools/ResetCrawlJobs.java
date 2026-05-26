import java.sql.*;

public class ResetCrawlJobs {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://127.0.0.1:5432/geostat?currentSchema=ingestion";
        String user = "geostat";
        String pass = "geostat-dev-change-me";
        
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            String sql = "UPDATE crawl_run SET status = 'completed', finished_at = NOW() " +
                         "WHERE status IN ('running', 'pending') RETURNING id, status";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    System.out.println("Updated: " + rs.getString("id") + " -> " + rs.getString("status"));
                    count++;
                }
                System.out.println("Total updated: " + count);
            }
        }
    }
}
