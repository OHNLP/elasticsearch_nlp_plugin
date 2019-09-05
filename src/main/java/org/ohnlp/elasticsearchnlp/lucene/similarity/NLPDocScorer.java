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

import org.ohnlp.elasticsearchnlp.payloads.NLPPayload;
import org.ohnlp.elasticsearchnlp.scoring.NLPPayloadScoringWeightFunction;
import org.apache.lucene.index.*;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.LinkedList;

/**
 * Wrapper for doc scorer
 */
public class NLPDocScorer extends Similarity.SimScorer {

    private final Term term;
    private final BytesRef pyld;
    private final Similarity.SimScorer baseScorer;
    private final LeafReaderContext context;

    private PostingsEnum postings;

    public NLPDocScorer(Similarity.SimScorer baseScorer, Term term, BytesRef pyld, LeafReaderContext context) {
        this.baseScorer = baseScorer;
        this.term = term;
        this.pyld = pyld;
        this.context = context;
    }

    public void setPostings(PostingsEnum postings) {
        this.postings = postings;
    }

    @Override
    public float score(int doc, float freq) throws IOException {

        // Get the NLP weight for this term
        long weightCount = 0;
        float weight = 0;
        int docFreq = postings.freq();
        weightCount += docFreq;
        for (int i = 0; i < docFreq; i++) {
            postings.nextPosition();
            BytesRef idxPyld = postings.getPayload();
            // TODO: some less naive way of combining weights aside from averaging them might be desirable
            float val = NLPPayloadScoringWeightFunction.getScoreMultiplier(pyld, idxPyld);
            if (val > 0) {
                weight += val;
            } else {
                weightCount--; // Not a valid match/different subject
            }
        }
        if (weightCount > 0) {
            weight /= weightCount;
        }
        if (!new NLPPayload(pyld).isPositive() && weightCount == 0) { // Why? Because negative mention is not found here
            weightCount = 1;
            weight = 1;
        }
        float base = baseScorer.score(doc, weightCount);
        return base * weight;
    }

    @Override
    public Explanation explain(int doc, Explanation freq) throws IOException {
        LinkedList<Explanation> subs = new LinkedList<>();

        // Get the NLP weight for this term
        long weightCount = 0;
        float weight = 0;
        int docFreq = postings.freq();
        weightCount += docFreq;
        for (int i = 0; i < docFreq; i++) {
            postings.nextPosition();
            BytesRef idxPyld = postings.getPayload();
            // TODO: some less naive way of combining weights aside from averaging them might be desirable
            float val = NLPPayloadScoringWeightFunction.getScoreMultiplier(pyld, idxPyld);
            if (val > 0) {
                weight += val;
                subs.add(NLPPayloadScoringWeightFunction.generateExplanation(pyld, idxPyld));
            } else {
                weightCount--; // Not a valid match/different subject
            }
        }
        if (weightCount > 0) {
            weight /= weightCount;
        }
        Explanation correctBaseFreq;
        if (!new NLPPayload(pyld).isPositive() && weightCount == 0) { // Why? Because negative mention is not found here
            weightCount = 1;
            weight = 1;
            correctBaseFreq = Explanation.match(1, "No matches found for negated mention of " + term);
        } else {
            correctBaseFreq = freq.isMatch() ?
                    Explanation.match(weightCount, freq.getDescription(), freq.getDetails()) :
                    Explanation.noMatch(freq.getDescription(), freq.getDetails());
        }
        Explanation base = baseScorer.explain(doc, correctBaseFreq);
        subs.addFirst(Explanation.match(base.getValue(), baseScorer.getClass().getSimpleName() + ": " + base.getDescription(), base.getDetails()));
        return Explanation.match(
                base.getValue() * weight,
                "score(doc=" + doc + ",term=" + term + ", term frequency=" + freq + ", same subject match=" + weightCount + ",nlp context weight=" + weight + "), product of:", subs);
    }


    @Override
    @Deprecated
    public float computeSlopFactor(int distance) {
        return baseScorer.computeSlopFactor(distance); // Not implemented due to deprecation
    }

    @Override
    @Deprecated
    public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
        return baseScorer.computePayloadFactor(doc, start, end, payload);
    }
}
