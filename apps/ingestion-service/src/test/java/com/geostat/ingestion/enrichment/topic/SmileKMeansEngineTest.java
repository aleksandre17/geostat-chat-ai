package com.geostat.ingestion.enrichment.topic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SmileKMeansEngineTest {

    @Test
    void clusterSeparatesTwoObviousGroups() {
        UUID groupA1 = UUID.randomUUID();
        UUID groupA2 = UUID.randomUUID();
        UUID groupB1 = UUID.randomUUID();
        UUID groupB2 = UUID.randomUUID();

        Map<UUID, float[]> embeddings = new LinkedHashMap<>();
        embeddings.put(groupA1, new float[] {1.0f, 0.0f});
        embeddings.put(groupA2, new float[] {0.9f, 0.1f});
        embeddings.put(groupB1, new float[] {0.0f, 1.0f});
        embeddings.put(groupB2, new float[] {0.1f, 0.9f});

        SmileKMeansEngine.ClusteringResult result = SmileKMeansEngine.cluster(embeddings, 2);
        Map<Integer, List<UUID>> members = SmileKMeansEngine.groupMembers(result);

        assertThat(members).hasSize(2);
        assertThat(members.values()).anyMatch(group -> group.containsAll(List.of(groupA1, groupA2)));
        assertThat(members.values()).anyMatch(group -> group.containsAll(List.of(groupB1, groupB2)));
    }
}
