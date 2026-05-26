import java.sql.*;
import java.util.*;

public class UpdateSeedsFromYaml {
    public static void main(String[] args) throws Exception {
        List<String> seeds = List.of(
                "https://www.geostat.ge/ka",
                "https://www.geostat.ge/en",
                "https://www.geostat.ge/ka/modules/categories/189",
                "https://www.geostat.ge/en/modules/categories/189",
                "https://www.geostat.ge/ka/modules/categories/303",
                "https://www.geostat.ge/en/modules/categories/303",
                "https://www.geostat.ge/ka/structure",
                "https://www.geostat.ge/en/structure",
                "https://www.geostat.ge/ka/page/aghmasrulebeli-direqtori",
                "https://www.geostat.ge/en/page/aghmasrulebeli-direqtori",
                "https://www.geostat.ge/ka/page/moadgile-paata-shavishvili",
                "https://www.geostat.ge/en/page/moadgile-paata-shavishvili",
                "https://www.geostat.ge/ka/page/moadgile-irakli-apkhaidze",
                "https://www.geostat.ge/en/page/moadgile-irakli-apkhaidze",
                "https://www.geostat.ge/ka/modules/categories/589/statistikis-samsakhuris-yofili-khelmdzghvanelebi",
                "https://www.geostat.ge/en/modules/categories/589/statistikis-samsakhuris-yofili-khelmdzghvanelebi");

        String json = seeds.toString().replace(' ', '"').replace('[', '[').replace(']', ']');
        // proper json array
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < seeds.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(seeds.get(i)).append('"');
        }
        sb.append(']');

        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/geostat?currentSchema=ingestion", "geostat", "geostat-dev-change-me")) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE corpus SET seed_urls = ?::jsonb WHERE name = 'geostat-portal'")) {
                ps.setString(1, sb.toString());
                int n = ps.executeUpdate();
                System.out.println("Updated corpus seed_urls: " + n + " row(s), count=" + seeds.size());
            }
        }
    }
}
