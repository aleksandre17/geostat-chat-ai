import java.sql.*;
public class ListTables {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/geostat?currentSchema=ingestion", "geostat", "geostat-dev-change-me")) {
            ResultSet rs = c.createStatement().executeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'ingestion' ORDER BY table_name");
            System.out.println("Tables in ingestion schema:");
            while (rs.next()) System.out.println("  - " + rs.getString("table_name"));
        }
    }
}