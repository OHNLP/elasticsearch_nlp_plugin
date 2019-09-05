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
 *  =============================================================================
 *  This work is derived from Lucene's BooleanQueryBuilder. The full license
 *  allowing for this derivation follows:
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.ohnlp.elasticsearchnlp.lucene.builders;


import org.ohnlp.elasticsearchnlp.analyzers.NLPAnalyzerProvider;
import org.ohnlp.elasticsearchnlp.lucene.NLPTerm;
import org.apache.lucene.search.NLPTermQuery;
import org.ohnlp.elasticsearchnlp.payloads.NLPPayload;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.xml.DOMUtils;
import org.apache.lucene.queryparser.xml.ParserException;
import org.apache.lucene.queryparser.xml.QueryBuilder;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.w3c.dom.Element;

import java.util.LinkedList;
import java.util.List;

public class NLPNaiveBooleanQueryBuilder implements QueryBuilder {
    @Override
    public Query getQuery(Element e) throws ParserException {
        String field = DOMUtils.getAttributeWithInheritanceOrFail(e, "field");
        String text = DOMUtils.getAttributeWithInheritanceOrFail(e, "query");
        // First, Analyze the Input Query
        List<NLPTerm> lookups = new LinkedList<>();
        try {
            TokenStream tokenStream = NLPAnalyzerProvider.ANALYZER
                    .tokenStream(field, text);
            tokenStream.reset();
            final CharTermAttribute termAtt = tokenStream.getAttribute(CharTermAttribute.class);
            final PayloadAttribute payloadAtt = tokenStream.getAttribute(PayloadAttribute.class);
            // For each analyzed item, increment through and contribute to score
            while (tokenStream.incrementToken()) {
                Term term = new Term(field, new String(termAtt.buffer(), 0, termAtt.length()));
                lookups.add(new NLPTerm(term, payloadAtt.getPayload()));
            }
            tokenStream.close();
        } catch (Exception ex) {
            throw new ParserException(ex);
        }
        // Now, construct a boolean query
        BooleanQuery.Builder bq = new BooleanQuery.Builder();
        bq.setMinimumNumberShouldMatch(DOMUtils.getAttribute(e, "minimumNumberShouldMatch", 0));
        for (NLPTerm term : lookups) {
            // Do not add any terms that are triggers, with the exception of experiencer since subject matters
            NLPPayload pyld = new NLPPayload(term.getPyld());
            if (pyld.isHistoricalTrigger || pyld.isAssertionTrigger || pyld.isNegationTrigger) {
                continue;
            }
            // Not a trigger -> add a clause
            bq.add(new BooleanClause(new NLPTermQuery(term), BooleanClause.Occur.SHOULD));
        }
        return bq.build();
    }
}
