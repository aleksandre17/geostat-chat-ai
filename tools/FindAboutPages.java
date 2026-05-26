import java.sql.*;
public class FindAboutPages {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/geostat?currentSchema=ingestion", "geostat", "geostat-dev-change-me")) {
            ResultSet rs = c.createStatement().executeQuery(
                "SELECT canonical_url, title FROM document " +
                "WHERE (canonical_url LIKE '%about%' OR canonical_url LIKE '%samsaxuri%' OR title LIKE '%ხელმძღვანელობა%' " +
                "OR title LIKE '%სტრუქტურა%' OR title LIKE '%Management%' OR title LIKE '%director%') " +
                "AND fetch_status = 'parsed' LIMIT 20");
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println(rs.getString("title") + " | " + rs.getString("canonical_url"));
            }
            if (!found) System.out.println("No about/management pages found");
        }
    }
}