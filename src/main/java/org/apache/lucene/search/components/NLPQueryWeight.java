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

package org.apache.lucene.search.components;

import org.ohnlp.elasticsearchnlp.lucene.NLPTerm;
import org.ohnlp.elasticsearchnlp.lucene.similarity.NLPDocScorer;
import org.ohnlp.elasticsearchnlp.lucene.similarity.NLPSimilarity;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Set;

/**
 * An implementation of similarity scoring weight to incorporate payload scoring
 */
public class NLPQueryWeight extends Weight {

    private Term term;
    private BytesRef pyld;
    private TermContext termState;
    private Similarity similarity;
    private Similarity.SimWeight stats;

    public NLPQueryWeight(IndexSearcher searcher,
                          float boost, TermContext termStates, Query srcQry, NLPTerm t) throws IOException {
        super(srcQry);
        if (termStates == null) {
            throw new IllegalStateException("termStates are required for scores");
        }
        this.term = t.getTerm();
        this.pyld = t.getPyld();
        this.termState = termStates;

        this.similarity = new NLPSimilarity(searcher.getSimilarity(true), t);

        final CollectionStatistics collectionStats = searcher.collectionStatistics(term.field());
        final TermStatistics termStats = searcher.termStatistics(term, termStates);


        this.stats = similarity.computeWeight(boost, collectionStats, termStats);
    }

    @Override
    public void extractTerms(Set<Term> terms) {
        terms.add(term);
    }

    @Override
    public Explanation explain(LeafReaderContext context, int doc) throws IOException {
        NLPTermScorer scorer = (NLPTermScorer) scorer(context);
        if (scorer != null) {
            int newDoc = scorer.iterator().advance(doc);
            if (newDoc == doc) {
                float freq = scorer.freq(); // TODO
                Explanation freqExplanation = Explanation.match(freq, "termFreq=" + freq);
                return scorer.docScorer.explain(doc, freqExplanation);
            }
        }
        return Explanation.noMatch("No matching term");
    }

    @Override
    public Scorer scorer(LeafReaderContext context) throws IOException {
        assert termState == null || termState.wasBuiltFor(ReaderUtil.getTopLevelContext(context)) : "The top-reader used to create Weight is not the same as the current reader's top-reader (" + ReaderUtil.getTopLevelContext(context);
        ;
        final TermsEnum termsEnum = getTermsEnum(context);
        if (termsEnum == null) {
            return null;
        }
        PostingsEnum docs = termsEnum.postings(null, PostingsEnum.ALL);
        assert docs != null;
        NLPDocScorer simScorer = (NLPDocScorer) similarity.simScorer(stats, context);
        simScorer.setPostings(docs);
        return new NLPTermScorer(this, docs, simScorer);
    }

    // Mostly copied from elasticsearch
    private TermsEnum getTermsEnum(LeafReaderContext context) throws IOException {
        if (termState != null) {
            // TermQuery either used as a Query or the term states have been provided at construction time
            assert termState.wasBuiltFor(ReaderUtil.getTopLevelContext(context)) : "The top-reader used to create Weight is not the same as the current reader's top-reader (" + ReaderUtil.getTopLevelContext(context);
            final TermState state = termState.get(context.ord);
            if (state == null) { // term is not present in that reader
                return null;
            }
            final TermsEnum termsEnum = context.reader().terms(term.field()).iterator();
            termsEnum.seekExact(term.bytes(), state);
            return termsEnum;
        } else {
            // TermQuery used as a filter, so the term states have not been built up front
            Terms terms = context.reader().terms(term.field());
            if (terms == null) {
                return null;
            }
            final TermsEnum termsEnum = terms.iterator();
            if (termsEnum.seekExact(term.bytes())) {
                return termsEnum;
            } else {
                return null;
            }
        }
    }

    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
        return false;
    }
}
