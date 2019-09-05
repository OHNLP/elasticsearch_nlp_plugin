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

package org.ohnlp.elasticsearchnlp.script;

import org.ohnlp.elasticsearchnlp.analyzers.NLPAnalyzerProvider;
import org.ohnlp.elasticsearchnlp.payloads.NLPPayload;
import org.ohnlp.elasticsearchnlp.scoring.NLPPayloadScoringWeightFunction;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.SearchScript;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements NLP-enabled Script Engine extension that searches using payloads
 * Currently, only one script type is implemented, "context", although more may be planned in the future
 * <p>
 * Takes the script type to run as source, and the query itself as well as the field in params
 */
public class NLPScriptEngine implements ScriptEngine {

    @Override
    public String getType() {
        return "nlp_script";
    }

    @Override
    public <FactoryType> FactoryType compile(String name, final String source2, ScriptContext<FactoryType> context, Map<String, String> params) {
        SearchScript.Factory factory = (p, lookup) -> new SearchScript.LeafFactory() {

            {
                if (!p.containsKey("query")) {
                    throw new IllegalArgumentException("A query must be provided via params");
                }
            }
            /*
             * Generates a relevant search script in the following steps:
             * 1. Analyzes the input query using the parameter "query"
             * 2. Performs weighted BM25 scoring and query coordination on a per-document basis
             */
            @Override
            public SearchScript newInstance(LeafReaderContext context) throws IOException {
                // First, Analyze the Input Query
                TokenStream tokenStream = NLPAnalyzerProvider.ANALYZER
                        .tokenStream(p.getOrDefault("field", "sec.body").toString(), p.get("query").toString());
                tokenStream.reset();
                final CharTermAttribute termAtt = tokenStream.getAttribute(CharTermAttribute.class);
                final PayloadAttribute payloadAtt = tokenStream.getAttribute(PayloadAttribute.class);
                // For each analyzed item, increment through and contribute to score
                List<Tuple<Term, BytesRef>> lookups = new LinkedList<>();
                while (tokenStream.incrementToken()) {
                    Term term = new Term(p.getOrDefault("field", "sec.body").toString(), new String(termAtt.buffer(), 0, termAtt.length()));
                    lookups.add(new Tuple<>(term, payloadAtt.getPayload()));
                }
                tokenStream.close();
                return new SearchScript(p, lookup, context) {


                    private final float collDocCount;
                    private final float avgCollLen;
                    private float docLen;
                    int currentDocid = -1;

                    {
                        collDocCount = (float) context.reader().getDocCount(p.getOrDefault("field", "sec.body").toString());
                        avgCollLen = context.reader().getSumTotalTermFreq(p.getOrDefault("field", "sec.body").toString()) / collDocCount;
                    }

                    @Override
                    public void setDocument(int docid) {
                        currentDocid = docid;
                        super.setDocument(docid);
                        // Calculate document statistics
                        try {
                            docLen = context.reader().getSumDocFreq(p.getOrDefault("field", "sec.body").toString());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    /*
                     * Roughly follows lucene's practical scoring function, as detailed in
                     * https://www.elastic.co/guide/en/elasticsearch/guide/current/practical-scoring-function.html
                     * with the addition of weighting to individual BM25 scores using NLP payloads.
                     *
                     * In summary, only two relevant steps are replicated:
                     * 1. BM25 scoring
                     * 2. Query coordination
                     *
                     * TODO: 10/16/2018 Examine using similarities instead for better performance
                     *
                     * <b> WARNING: Does NOT work on nested fields </b>
                     */
                    public double runAsDouble() {
                        try {
                            double score = 0.00f;
                            Terms fieldTermVec = context.reader().getTermVector(currentDocid, p.getOrDefault("field", "sec.body").toString());
                            TermsEnum terms = fieldTermVec.iterator();

                            // Set up variables
                            // - A set of term byteref tuples eligible for query coordination
                            List<Tuple<Term, BytesRef>> lookupEligible = lookups
                                    .stream()
                                    .filter(t -> new NLPPayload(Arrays.copyOfRange(t.v2().bytes, t.v2().offset, t.v2().offset + t.v2().length)).isQueryTerm())
                                    .collect(Collectors.toList());

                            //-  Prevent expensive reprocessing of the same term/payload combination
                            Map<Tuple<Term, BytesRef>, Float> examined = new HashMap<>();
                            float numMatched = 0; // Used for query coordination
                            // Get equivalent of weighted BM25 score
                            for (Tuple<Term, BytesRef> item : lookupEligible) {
                                if (!terms.seekExact(item.v1().bytes())) {
                                    // No matches found, continue...
                                    continue;
                                }
                                if (examined.containsKey(item)) { // We already did scoring for this term, no need to
                                    // repeat except add to coordination if applicable
                                    numMatched += 1 * examined.get(item);
                                    continue;
                                }
                                // Get the NLP weight for this term
                                long weightCount = 0;
                                float weight = 0;
                                PostingsEnum postings = terms.postings(null, PostingsEnum.ALL);
                                postings.nextDoc();
                                int docFreq = postings.freq();
                                weightCount += docFreq;
                                for (int i = 0; i < docFreq; i++) {
                                    postings.nextPosition();
                                    BytesRef idxPyld = postings.getPayload();
                                    // TODO: some less naive way of combining weights aside from averaging them might be desirable
                                    weight += NLPPayloadScoringWeightFunction.getScoreMultiplier(item.v2(), idxPyld);

                                }
                                if (weightCount > 0) {
                                    weight /= weightCount;
                                }
                                examined.put(item, weight); // Insert so we don't recalculate in the future
                                numMatched += 1;
                                // Now get the BM25 score for this term
                                // - ES Defaults
                                float k1 = 1.2f;
                                float b = 0.75f;
                                long docsContainingTerm = context.reader().docFreq(item.v1());
                                double idf = Math.log((collDocCount - docsContainingTerm + 0.5) / (docsContainingTerm + 0.5));
                                double bm25SubScore = idf * (docFreq * (k1 + 1)) / (docFreq + (k1 * (1 - b + (b * docLen / avgCollLen))));
                                // Weight this term
                                score += bm25SubScore * (bm25SubScore < 0 ? (1 - weight) : weight);
                            }
                            // Perform query coordination
                            score *= score < 0 ? 1 - (numMatched / lookupEligible.size()) : (numMatched / lookupEligible.size());
                            return score;
                        } catch (Exception e) {
                            e.printStackTrace();
                            return 0.0d; // TODO logging
                        }
                    }
                };
            }

            @Override
            public boolean needs_score() {
                return false;
            }
        };
        return context.factoryClazz.cast(factory);
    }

}
