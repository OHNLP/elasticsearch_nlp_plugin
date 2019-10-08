/*
 *  Copyright: (c) 2019 Mayo Foundation for Medical Education and
 *  Research (MFMER). All rights reserved. MAYO, MAYO CLINIC, and the
 *  triple-shield Mayo logo are trademarks and service marks of MFMER.
 *
 *  Except as contained in the copyright notice above, or as used to identify
 *  MFMER as the author of this software, the trade names, trademarks, service
 *  marks, or product names of the copyright holder shall not be used in
 *  advertising, promotion or otherwise in connection with this software without
 *  prior written authorization of the copyright holder.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.test.ESIntegTestCase;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ohnlp.elasticsearchnlp.ElasticsearchNLPPlugin;
import org.ohnlp.elasticsearchnlp.config.Config;
import org.ohnlp.elasticsearchnlp.elasticsearch.NLPNaiveBooleanESQueryBuilder;

import java.util.*;

/**
 * Loads plugin into an ES cluster and runs individual test cases via queries
 */
@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.SUITE, numDataNodes = 1, numClientNodes = 1)
public class ElasticsearchNLPPluginQueryTest extends ESIntegTestCase {

    private static Map<String, String> DOCUMENTS;

    /**
     * Sets up indices for testing. Crucially, also tests to make sure analysis will proceed correctly.
     */
    @Before
    public void setupIndices() {
        DOCUMENTS = new HashMap<>();
        DOCUMENTS.put("1", "Mr. Test presents today with heartburn, possible GERD, but no evident symptoms");
        DOCUMENTS.put("2", "Mr. Test presents today without heartburn, but with very evident symptoms of GERD");
        DOCUMENTS.put("3", "Mr. Test presents today with GERD and his family history includes heartburn.");
        prepareCreate("nlp")
                .setSettings(Settings.builder().put("index.number_of_shards", 1))
                .addMapping("text", "body", "type=text,analyzer=nlp").execute();
        DOCUMENTS.forEach((id, body) -> {
            index("nlp", "text", id, JsonNodeFactory.instance.objectNode().put("body", body).toString());
        });
    }

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.singletonList(ElasticsearchNLPPlugin.class);
    }

    /**
     * Verifies that the configuration loaded properly by first asserting not null then checking enabled components
     */
    @Test
    public void verifyConfigLoad() {
        Assert.assertNotNull(ElasticsearchNLPPlugin.CONFIG);
        Assert.assertThat(new HashSet<>(ElasticsearchNLPPlugin.CONFIG.getEnabled()), Matchers.is(new HashSet<>(Arrays.asList(
                Config.NLPComponent.CLINICAL_STOP_WORDS,
                Config.NLPComponent.CONTEXT
        ))));
    }

    /**
     * Verifies (very naively) that context-aware queries are working
     */
    @Test
    public void verifyContextAwareQueries() {
        // Pos w/ FMHX
        client().admin().indices().prepareRefresh("nlp").execute().actionGet();
        SearchResponse resp = cluster().client()
                .prepareSearch("nlp")
                .setTypes("text")
                .setQuery(new NLPNaiveBooleanESQueryBuilder(
                        "body",
                        "has GERD with fmhx heartburn"))
                .setExplain(true)
                .execute()
                .actionGet();
        Object[] orderedIDs = Arrays.stream(resp.getHits().getHits()).map(SearchHit::getId).toArray();
        Object[] expectedIDs = new Object[]{"3", "2", "1"};
        Assert.assertArrayEquals(expectedIDs, orderedIDs);
    }

}
