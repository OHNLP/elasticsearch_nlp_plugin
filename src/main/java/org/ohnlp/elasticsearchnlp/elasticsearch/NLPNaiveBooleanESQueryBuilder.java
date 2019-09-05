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
 *  This work is derived from elasticsearch's TermsQueryBuilder. The full license
 *  allowing for this derivation follows:
 *  Licensed to Elasticsearch under one or more contributor
 *  license agreements. See the NOTICE file distributed with
 *  this work for additional information regarding copyright
 *  ownership. Elasticsearch licenses this file to you under
 *  the Apache License, Version 2.0 (the "License"); you may
 *  not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.ohnlp.elasticsearchnlp.elasticsearch;

import org.ohnlp.elasticsearchnlp.analyzers.NLPAnalyzerProvider;
import org.ohnlp.elasticsearchnlp.lucene.NLPTerm;
import org.apache.lucene.search.NLPTermQuery;
import org.ohnlp.elasticsearchnlp.payloads.NLPPayload;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryShardContext;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * A ConTexT aware query builder that simply concatenates each of the tokens together in a simple boolean or
 * Derived from Elasticsearch
 */
public class NLPNaiveBooleanESQueryBuilder extends AbstractQueryBuilder<NLPNaiveBooleanESQueryBuilder> {
    public static final String NAME = "nlp_naive_boolean";

    private final String fieldName;
    private final Object value;

    public NLPNaiveBooleanESQueryBuilder(String fieldName, Object value) {
        if (Strings.isEmpty(fieldName)) {
            throw new IllegalArgumentException("[" + NAME + "] requires fieldName");
        }
        if (value == null) {
            throw new IllegalArgumentException("[" + NAME + "] requires query value");
        }
        this.fieldName = fieldName;
        this.value = value;
    }

    /**
     * Read from a stream.
     */
    public NLPNaiveBooleanESQueryBuilder(StreamInput in) throws IOException {
        super(in);
        this.fieldName = in.readString();
        this.value = in.readGenericValue();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(fieldName);
        out.writeGenericValue(value);
    }

    /** Returns the field name used in this query. */
    public String fieldName() {
        return this.fieldName;
    }

    /** Returns the value used in this query. */
    public Object value() {
        return this.value;
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.startObject(fieldName);
        builder.field(MatchQueryBuilder.QUERY_FIELD.getPreferredName(), value);
        builder.endObject();
        builder.endObject();
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        // First, Analyze the Input Query
        List<NLPTerm> lookups = new LinkedList<>();
        try {
            TokenStream tokenStream = NLPAnalyzerProvider.ANALYZER
                    .tokenStream(fieldName, value.toString());
            tokenStream.reset();
            final CharTermAttribute termAtt = tokenStream.getAttribute(CharTermAttribute.class);
            final PayloadAttribute payloadAtt = tokenStream.getAttribute(PayloadAttribute.class);
            // For each analyzed item, increment through and contribute to score
            while (tokenStream.incrementToken()) {
                Term term = new Term(fieldName, new String(termAtt.buffer(), 0, termAtt.length()));
                lookups.add(new NLPTerm(term, payloadAtt.getPayload()));
            }
            tokenStream.close();
        } catch (Exception ex) {
            throw new IOException(ex);
        }
        // Now, construct a boolean query
        BooleanQuery.Builder bq = new BooleanQuery.Builder();
        bq.setMinimumNumberShouldMatch(1);
        for (NLPTerm term : lookups) {
            NLPPayload pyld = new NLPPayload(term.getPyld());
            if (pyld.isHistoricalTrigger || pyld.isAssertionTrigger || pyld.isNegationTrigger) {
                continue;
            }
            bq.add(new BooleanClause(new NLPTermQuery(term), BooleanClause.Occur.SHOULD));
        }
        return bq.build();
    }

    @Override
    protected boolean doEquals(NLPNaiveBooleanESQueryBuilder other) {
        return Objects.equals(fieldName, other.fieldName)
                && Objects.equals(value, other.value);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(fieldName, value);
    }


    @Override
    public String getWriteableName() {
        return NAME;
    }

    public static NLPNaiveBooleanESQueryBuilder fromXContent(XContentParser parser) throws IOException {
        String fieldName = null;
        Object value = null;
        float boost = AbstractQueryBuilder.DEFAULT_BOOST;
        String queryName = null;
        String currentFieldName = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token == XContentParser.Token.START_OBJECT) {
                throwParsingExceptionOnMultipleFields(NAME, parser.getTokenLocation(), fieldName, currentFieldName);
                fieldName = currentFieldName;
                while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                    if (token == XContentParser.Token.FIELD_NAME) {
                        currentFieldName = parser.currentName();
                    } else if (token.isValue()) {
                        if (MatchQueryBuilder.QUERY_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                            value = parser.objectText();
                        }  else if (AbstractQueryBuilder.BOOST_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                            boost = parser.floatValue();
                        } else if (AbstractQueryBuilder.NAME_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                            queryName = parser.text();
                        }  else {
                            throw new ParsingException(parser.getTokenLocation(),
                                    "[" + NAME + "] query does not support [" + currentFieldName + "]");
                        }
                    } else {
                        throw new ParsingException(parser.getTokenLocation(),
                                "[" + NAME + "] unknown token [" + token + "] after [" + currentFieldName + "]");
                    }
                }
            } else {
                throwParsingExceptionOnMultipleFields(NAME, parser.getTokenLocation(), fieldName, parser.currentName());
                fieldName = parser.currentName();
                value = parser.objectText();
            }
        }

        NLPNaiveBooleanESQueryBuilder matchQuery = new NLPNaiveBooleanESQueryBuilder(fieldName, value);
        matchQuery.queryName(queryName);
        matchQuery.boost(boost);
        return matchQuery;
    }
}
