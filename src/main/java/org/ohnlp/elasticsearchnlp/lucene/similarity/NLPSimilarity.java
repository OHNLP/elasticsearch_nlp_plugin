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

package org.ohnlp.elasticsearchnlp.lucene.similarity;

import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;
import org.ohnlp.elasticsearchnlp.lucene.NLPTerm;

/**
 * Wraps any lucene similarity implementation and incorporates NLP payload scoring into the weight
 */
public class NLPSimilarity extends Similarity {
    private final Similarity similarity;
    private final Term term;
    private final BytesRef pyld;

    public NLPSimilarity(Similarity similarity, NLPTerm term) {
        this.similarity = similarity;
        this.term = term.getTerm();
        this.pyld = term.getPyld();
    }


    @Override
    public long computeNorm(FieldInvertState state) {
        return similarity.computeNorm(state);
    }

    @Override
    public SimScorer scorer(float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
        return new NLPDocScorer(similarity.scorer(boost, collectionStats, termStats), term, pyld);
    }
}
