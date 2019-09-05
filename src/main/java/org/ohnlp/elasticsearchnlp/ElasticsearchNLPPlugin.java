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

package org.ohnlp.elasticsearchnlp;

import org.ohnlp.elasticsearchnlp.analyzers.ConTexTAwareTokenizer;
import org.ohnlp.elasticsearchnlp.analyzers.NLPAnalyzerProvider;
import org.ohnlp.elasticsearchnlp.elasticsearch.NLPNaiveBooleanESQueryBuilder;
import org.ohnlp.elasticsearchnlp.script.NLPScriptEngine;
import org.apache.lucene.analysis.Analyzer;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.analysis.AnalyzerProvider;
import org.elasticsearch.index.analysis.TokenizerFactory;
import org.elasticsearch.indices.analysis.AnalysisModule;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.plugins.SearchPlugin;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ElasticsearchNLPPlugin extends Plugin implements AnalysisPlugin, ScriptPlugin, SearchPlugin {

    @Override
    public Map<String, AnalysisModule.AnalysisProvider<TokenizerFactory>> getTokenizers() {
        return Collections.singletonMap("nlp", (indexSettings, env, name, settings) -> ConTexTAwareTokenizer::new);
    }

    @Override
    public Map<String, AnalysisModule.AnalysisProvider<AnalyzerProvider<? extends Analyzer>>> getAnalyzers() {
        return Collections.singletonMap("nlp", (indexSettings, env, name, settings) -> new NLPAnalyzerProvider(indexSettings, name, settings));
    }

    @Override
    public ScriptEngine getScriptEngine(Settings settings, Collection<ScriptContext<?>> contexts) {
        return new NLPScriptEngine();
    }

    @Override
    public List<QuerySpec<?>> getQueries() {
        return Collections.singletonList(
                new QuerySpec<>(new ParseField(NLPNaiveBooleanESQueryBuilder.NAME), NLPNaiveBooleanESQueryBuilder::new, NLPNaiveBooleanESQueryBuilder::fromXContent)
        );
    }
}
