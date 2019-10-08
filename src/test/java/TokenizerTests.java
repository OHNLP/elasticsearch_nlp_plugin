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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ohnlp.elasticsearchnlp.ElasticsearchNLPPlugin;
import org.ohnlp.elasticsearchnlp.analyzers.ConTexTAwareTokenizer;
import org.ohnlp.elasticsearchnlp.config.Config;
import org.ohnlp.elasticsearchnlp.payloads.NLPPayload;

import java.io.IOException;
import java.io.StringReader;
import java.util.Deque;

public class TokenizerTests {
    private static String TESTSTRING = "Mr. Test presents today with heartburn and possible fmhx GERD.";

    @Before
    public void loadConfig() throws IOException {
        ObjectMapper om = new ObjectMapper(new YAMLFactory());
        ElasticsearchNLPPlugin.CONFIG = om.treeToValue(om.readTree(TokenizerTests.class.getResourceAsStream("/elasticsearch-nlp-plugin.yml")).get("esnlp"), Config.class);
    }

    @Test
    public void testTokenization() throws IOException {
        ConTexTAwareTokenizer tokenizer = new ConTexTAwareTokenizer();
        tokenizer.setReader(new StringReader(TESTSTRING));
        tokenizer.reset();
        Deque<ConTexTAwareTokenizer.TokenPayloadPair> tokenQueue = tokenizer.tokenQueue;
        NLPPayload gerdToken = tokenQueue.peekLast().getPayload();
        Assert.assertFalse(gerdToken.isAsserted());
        Assert.assertFalse(gerdToken.patientIsSubject());
        Assert.assertFalse(gerdToken.isPresent());
        tokenQueue.removeLast();
        tokenQueue.removeLast();
        tokenQueue.removeLast();
        NLPPayload heartburnToken = tokenQueue.peekLast().getPayload();
        Assert.assertTrue(heartburnToken.isAsserted());
        Assert.assertTrue(heartburnToken.isPresent());
        Assert.assertTrue(heartburnToken.patientIsSubject());
    }
}
