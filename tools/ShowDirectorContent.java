import java.sql.*;

public class ShowDirectorContent {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/geostat?currentSchema=ingestion", "geostat", "geostat-dev-change-me")) {
            try (ResultSet rs = c.createStatement().executeQuery(
                    "SELECT canonical_url, content_text FROM document WHERE canonical_url LIKE '%aghmasrulebeli-direqtori%' AND canonical_url NOT LIKE '%#'")) {
                while (rs.next()) {
                    System.out.println("=== " + rs.getString("canonical_url") + " ===");
                    String t = rs.getString("content_text");
                    System.out.println(t == null ? "(null)" : t.substring(0, Math.min(800, t.length())));
                }
            }
        }
    }
}
