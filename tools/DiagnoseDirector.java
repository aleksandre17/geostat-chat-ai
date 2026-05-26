import java.sql.*;

public class DiagnoseDirector {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/geostat?currentSchema=ingestion", "geostat", "geostat-dev-change-me")) {
            String[] patterns = {
                "%aghmasrulebeli-direqtori%",
                "%modules/categories/303%",
                "%modules/categories/189%",
                "%/structure%",
                "%xelmdzghvaneloba%"
            };
            for (String p : patterns) {
                System.out.println("=== pattern " + p + " ===");
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT canonical_url, fetch_status, title, "
                                + "(SELECT COUNT(*) FROM chunk ch WHERE ch.document_id = d.id) chunks "
                                + "FROM document d WHERE canonical_url LIKE ?")) {
                    ps.setString(1, p);
                    ResultSet rs = ps.executeQuery();
                    boolean any = false;
                    while (rs.next()) {
                        any = true;
                        System.out.println(rs.getString("fetch_status") + " chunks=" + rs.getInt("chunks")
                                + " | " + rs.getString("canonical_url"));
                        System.out.println("  title: " + rs.getString("title"));
                    }
                    if (!any) System.out.println("(none)");
                }
            }
        }
    }
}
