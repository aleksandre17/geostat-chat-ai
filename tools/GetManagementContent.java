import java.sql.*;

public class GetManagementContent {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/geostat?currentSchema=ingestion", "geostat", "geostat-dev-change-me")) {
            try (ResultSet rs = c.createStatement().executeQuery(
                    "SELECT canonical_url, content_text FROM document WHERE canonical_url LIKE '%/303%' AND canonical_url LIKE '%/ka/%'")) {
                if (rs.next()) {
                    String text = rs.getString("content_text");
                    System.out.println("URL: " + rs.getString("canonical_url"));
                    System.out.println("Length: " + (text == null ? 0 : text.length()));
                    System.out.println("--- content ---");
                    System.out.println(text == null ? "(null)" : text.substring(0, Math.min(1500, text.length())));
                }
            }
        }
    }
}
