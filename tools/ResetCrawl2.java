import java.sql.*;
public class ResetCrawl2 {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/geostat?currentSchema=ingestion", "geostat", "geostat-dev-change-me")) {
            int rows = c.createStatement().executeUpdate(
                "UPDATE crawl_run SET status = 'completed', finished_at = NOW() WHERE status IN ('running', 'pending', 'cancelled')");
            System.out.println("Reset " + rows + " crawl runs");
        }
    }
}