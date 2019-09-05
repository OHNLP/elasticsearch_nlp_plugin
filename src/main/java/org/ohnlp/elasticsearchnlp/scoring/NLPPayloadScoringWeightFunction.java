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

package org.ohnlp.elasticsearchnlp.scoring;

import org.ohnlp.elasticsearchnlp.payloads.NLPPayload;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.util.BytesRef;

import java.util.Arrays;

public class NLPPayloadScoringWeightFunction {
    /**
     * Generates a weight by which the original term similarity can be modified
     * @param queryPyldByteRef The {@link BytesRef} representing the original query payload
     * @param idxPyldByteRef The {@link BytesRef} representing the payload of the term in the index
     * @return A float weight denoting the individual term score
     */
    public static float getScoreMultiplier(BytesRef queryPyldByteRef, BytesRef idxPyldByteRef) {
        // Load bytesref into java POJO
        // TODO this step isn't really necessary, it is here for legibility but causes a (minor) performance hit
        NLPPayload queryPyld = new NLPPayload(queryPyldByteRef);
        NLPPayload idxPyld = new NLPPayload(idxPyldByteRef);
        float ret = 1.00f;
        // Check and modify scores. TODO these weights can be modified
        // Check Negation Status - Remove from consideration if mismatch
        if (queryPyld.isPositive() != idxPyld.isPositive()) {
            ret *= 0.00;
        }
        // Check Subject - Remove from consideration if mismatch, Heavily weight if match
        if (queryPyld.patientIsSubject() != idxPyld.patientIsSubject()) {
            ret *= 0.00;
        }
//        else {
//            ret *= 1.5;
//        }
        // Check Historical -  Penalize if Mismatch Moderately if Query Looks for Historical, Lightly Otherwise
//        if (queryPyld.isPresent() != idxPyld.isPresent()) {
//            if (ret > 0) {
//                ret *= queryPyld.isPresent() ? 1.00 : 0.75;
//            } else {
//                ret *= queryPyld.isPresent() ? 1.00 : 1.25;
//            }
//        } else {
//            if (ret > 0) {
//                ret *= queryPyld.isPresent() ? 1.75 : 1.5;
//            } else {
//                ret *= queryPyld.isPresent() ? 0.5 : 0.75;
//            }
//        }
        // Check Assertion - Penalize if Mismatch Moderately if Query Looks for not Asserted, Lightly Otherwise
        if (queryPyld.isAsserted() != idxPyld.isAsserted()) {
            if (ret > 0) {
                ret *= queryPyld.isAsserted() ? 0.5 : 0.75;
            } else {
                ret *= queryPyld.isAsserted() ? 1.75 : 1.5;
            }
        } else {
//            if (ret > 0) {
//                ret *= queryPyld.isAsserted() ? 1.75 : 1.5;
//            } else {
//                ret *= queryPyld.isAsserted() ? 0.5 : 0.75;
//            }
        }
        return ret;
    }

    /**
     * Generates an explanation for the given score multiplier
     * @param queryPyld The bytes corresponding to the query NLP payload
     * @param idxPyld The bytes corresponding to the NLP payload of the term being matched against in the index
     * @return An explanation for how the float weight is derived
     */
    public static Explanation generateExplanation(BytesRef queryPyld, BytesRef idxPyld) {
        float weight = getScoreMultiplier(queryPyld, idxPyld);
        // Load bytesref into java POJO
        byte[] queryPyldBytes = Arrays.copyOfRange(queryPyld.bytes, queryPyld.offset, queryPyld.offset + queryPyld.length);
        byte[] idxPyldBytes = Arrays.copyOfRange(idxPyld.bytes, idxPyld.offset, idxPyld.offset + idxPyld.length);
        NLPPayload queryPyldPojo = new NLPPayload(queryPyldBytes);
        NLPPayload idxPyldPojo = new NLPPayload(idxPyldBytes);
        if (weight > 0) { // TODO an arbritary delimiter for match/don't match... does this actually matter? Should not affect scoring
            return Explanation.match(weight, "weight(Query NLP Payload=" + queryPyldPojo + ", Idx NLP Payload=" + idxPyldPojo + ") returned a weight of " + weight);
        } else {
            return Explanation.noMatch("weight(Query NLP Payload=" + queryPyldPojo + ", Idx NLP Payload=" + idxPyldPojo + ") returned a weight of " + weight);
        }
    }
}
