import java.sql.*;

public class DiagnoseCrawlFailures {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/geostat?currentSchema=ingestion", "geostat", "geostat-dev-change-me")) {

            System.out.println("=== LATEST CRAWL RUN ===");
            try (ResultSet rs = c.createStatement().executeQuery(
                    "SELECT id, status, stats, config_snapshot FROM crawl_run ORDER BY created_at DESC LIMIT 3")) {
                while (rs.next()) {
                    System.out.println("id=" + rs.getString("id"));
                    System.out.println("  status=" + rs.getString("status"));
                    System.out.println("  stats=" + rs.getString("stats"));
                    System.out.println("  config=" + rs.getString("config_snapshot"));
                }
            }

            System.out.println("\n=== CORPUS seed_urls count ===");
            try (ResultSet rs = c.createStatement().executeQuery(
                    "SELECT name, jsonb_array_length(seed_urls) as cnt, seed_urls, policy FROM corpus WHERE name = 'geostat-portal'")) {
                if (rs.next()) {
                    System.out.println("seeds count: " + rs.getInt("cnt"));
                    String seeds = rs.getString("seed_urls");
                    System.out.println("has xelmdzghvaneloba: " + (seeds != null && seeds.contains("xelmdzghvaneloba")));
                    System.out.println("has management: " + (seeds != null && seeds.contains("management")));
                    System.out.println("policy: " + rs.getString("policy"));
                }
            }

            System.out.println("\n=== FRONTIER BY STATUS (latest run) ===");
            try (ResultSet rs = c.createStatement().executeQuery(
                    """
                    SELECT f.status, COUNT(*) cnt
                    FROM url_frontier f
                    JOIN crawl_run r ON r.id = f.crawl_run_id
                    WHERE r.id = (SELECT id FROM crawl_run ORDER BY created_at DESC LIMIT 1)
                    GROUP BY f.status
                    ORDER BY cnt DESC
                    """)) {
                while (rs.next()) {
                    System.out.println(rs.getString("status") + ": " + rs.getInt("cnt"));
                }
            }

            System.out.println("\n=== SAMPLE FAILURES / SKIPPED (latest run) ===");
            try (ResultSet rs = c.createStatement().executeQuery(
                    """
                    SELECT f.status, f.url, f.last_error
                    FROM url_frontier f
                    JOIN crawl_run r ON r.id = f.crawl_run_id
                    WHERE r.id = (SELECT id FROM crawl_run ORDER BY created_at DESC LIMIT 1)
                      AND f.status IN ('failed', 'skipped')
                    ORDER BY f.url
                    LIMIT 30
                    """)) {
                while (rs.next()) {
                    System.out.println(rs.getString("status") + " | " + rs.getString("url"));
                    String err = rs.getString("last_error");
                    if (err != null && !err.isBlank()) {
                        System.out.println("  error: " + err);
                    }
                }
            }

            System.out.println("\n=== DOCUMENTS for management/about pages ===");
            try (ResultSet rs = c.createStatement().executeQuery(
                    """
                    SELECT canonical_url, fetch_status, LEFT(content_text, 80) as preview
                    FROM document
                    WHERE canonical_url LIKE '%xelmdzghvaneloba%'
                       OR canonical_url LIKE '%management%'
                       OR canonical_url LIKE '%saqartvelos-statistikis-erovnuli%'
                    """)) {
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    System.out.println(rs.getString("fetch_status") + " | " + rs.getString("canonical_url"));
                    System.out.println("  " + rs.getString("preview"));
                }
                if (!any) System.out.println("(none)");
            }
        }
    }
}
