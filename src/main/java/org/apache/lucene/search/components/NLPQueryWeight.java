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
import java.util.ArrayList;
import java.util.Set;

/**
 * An implementation of similarity scoring weight to incorporate payload scoring
 */
public class NLPQueryWeight extends Weight {

    private final TermStates termStates;
    private final float boost;
    private final IndexSearcher searcher;
    private final ScoreMode scoreMode;
    private final String field;
    private final Similarity.SimScorer stats;
    private Term term;
    private Similarity similarity;

    public NLPQueryWeight(IndexSearcher searcher,
                          float boost, ScoreMode scoreMode, TermStates termStates, Query srcQry, NLPTerm t, String field) throws IOException {
        super(srcQry);
        if (termStates == null) {
            throw new IllegalStateException("termStates are required for scores");
        }

        this.searcher = searcher;
        this.term = t.getTerm();
        this.field = field;

        this.similarity = new NLPSimilarity(searcher.getSimilarity(), t);
        this.termStates = termStates;
        this.boost = boost;

        this.scoreMode = scoreMode;

        // Initialize underlying stats - copied from ES
        final CollectionStatistics collectionStats;
        final TermStatistics termStats;
        if (scoreMode.needsScores()) {
            collectionStats = searcher.collectionStatistics(term.field());
            termStats = searcher.termStatistics(term, termStates);
        } else {
            // we do not need the actual stats, use fake stats with docFreq=maxDoc=ttf=1
            collectionStats = new CollectionStatistics(term.field(), 1, 1, 1, 1);
            termStats = new TermStatistics(term.bytes(), 1, 1);
        }

        if (termStats == null) {
            this.stats = null; // term doesn't exist in any segment, we won't use similarity at all
        } else {
            this.stats = similarity.scorer(boost, collectionStats, termStats);
        }
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
                float freq = scorer.freq();
                Explanation freqExplanation = Explanation.match(freq, "termFreq=" + freq);
                return scorer.docScorer.explain(freqExplanation, scorer.getNormValue(doc));
            }
        }
        return Explanation.noMatch("No matching term");
    }

    @Override
    public Scorer scorer(LeafReaderContext context) throws IOException {
        final TermsEnum termsEnum = getTermsEnum(context);
        if (termsEnum == null) {
            return null;
        }
        ArrayList<TermStatistics> allTermStats = new ArrayList<>();

        if (scoreMode.needsScores()) {
            TermStatistics termStatistics = searcher.termStatistics(term, termStates);
            if (termStatistics != null) {
                allTermStats.add(termStatistics);
            }
        }
        PostingsEnum docs = termsEnum.postings(null, PostingsEnum.ALL);
        assert docs != null;
        ((NLPDocScorer)stats).setPostings(docs);
        return new NLPTermScorer(this, docs, (NLPDocScorer) stats, context.reader(), field);
    }

    // Mostly copied from elasticsearch
    private TermsEnum getTermsEnum(LeafReaderContext context) throws IOException {
        assert termStates != null;
        assert termStates.wasBuiltFor(ReaderUtil.getTopLevelContext(context)) :
                "The top-reader used to create Weight is not the same as the current reader's top-reader (" + ReaderUtil.getTopLevelContext(context);
        final TermState state = termStates.get(context);
        if (state == null) { // term is not present in that reader
            assert context.reader().docFreq(term) == 0 : "no termstate found but term exists in reader term=" + term;
            return null;
        }
        final TermsEnum termsEnum = context.reader().terms(term.field()).iterator();
        termsEnum.seekExact(term.bytes(), state);
        return termsEnum;
    }

    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
        return false;
    }
}
