import java.sql.*;
public class ResetAndStartCrawl {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/geostat?currentSchema=ingestion", "geostat", "geostat-dev-change-me")) {
            // Reset all active crawls
            int reset = c.createStatement().executeUpdate(
                "UPDATE crawl_run SET status = 'completed', finished_at = NOW() WHERE status IN ('running', 'pending')");
            System.out.println("Reset " + reset + " crawl runs");
            
            // Check seed_urls in corpus
            ResultSet rs = c.createStatement().executeQuery("SELECT seed_urls FROM corpus WHERE name = 'geostat-portal'");
            if (rs.next()) {
                String seeds = rs.getString("seed_urls");
                System.out.println("Corpus seeds contains management: " + (seeds != null && seeds.contains("xelmdzghvaneloba")));
            }
        }
    }
}