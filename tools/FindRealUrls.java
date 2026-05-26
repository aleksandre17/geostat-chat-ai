import java.sql.*;

public class FindRealUrls {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/geostat?currentSchema=ingestion", "geostat", "geostat-dev-change-me")) {
            System.out.println("=== EXISTING DOCS matching org keywords ===");
            try (ResultSet rs = c.createStatement().executeQuery(
                    """
                    SELECT canonical_url, fetch_status, title
                    FROM document
                    WHERE canonical_url ILIKE '%page%'
                      AND (canonical_url ILIKE '%xelm%'
                        OR canonical_url ILIKE '%manage%'
                        OR canonical_url ILIKE '%direq%'
                        OR canonical_url ILIKE '%samsax%'
                        OR canonical_url ILIKE '%about%'
                        OR canonical_url ILIKE '%contact%'
                        OR canonical_url ILIKE '%kontak%'
                        OR title ILIKE '%დირექ%'
                        OR title ILIKE '%ხელმძ%'
                        OR title ILIKE '%management%')
                    ORDER BY canonical_url
                    LIMIT 30
                    """)) {
                while (rs.next()) {
                    System.out.println(rs.getString("fetch_status") + " | " + rs.getString("title"));
                    System.out.println("  " + rs.getString("canonical_url"));
                }
            }
            System.out.println("\n=== CONTENT mentioning director ===");
            try (ResultSet rs = c.createStatement().executeQuery(
                    """
                    SELECT canonical_url, LEFT(content_text, 200) preview
                    FROM document
                    WHERE content_text ILIKE '%დირექტ%'
                       OR content_text ILIKE '%director%'
                    LIMIT 10
                    """)) {
                while (rs.next()) {
                    System.out.println(rs.getString("canonical_url"));
                    System.out.println("  " + rs.getString("preview").replace('\n', ' '));
                }
            }
        }
    }
}
