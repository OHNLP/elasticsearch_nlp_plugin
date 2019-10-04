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

package org.ohnlp.elasticsearchnlp.config.components;

/**
 * Configuration options
 */
public class ConTextConfig {

    public ConTextWeightConfig weights;

    public ConTextWeightConfig getWeights() {
        return weights;
    }

    public void setWeights(ConTextWeightConfig weights) {
        this.weights = weights;
    }

    public static class ConTextWeightConfig {
        public ConTextWeights match;
        public ConTextWeights mismatch;

        public ConTextWeights getMatch() {
            return match;
        }

        public void setMatch(ConTextWeights match) {
            this.match = match;
        }

        public ConTextWeights getMismatch() {
            return mismatch;
        }

        public void setMismatch(ConTextWeights mismatch) {
            this.mismatch = mismatch;
        }
    }

    public static class ConTextWeights {
        private double negation;
        private double subject;
        private ComplexWeight temporal;
        private ComplexWeight assertion;

        public double getNegation() {
            return negation;
        }

        public void setNegation(double negation) {
            this.negation = negation;
        }

        public double getSubject() {
            return subject;
        }

        public void setSubject(double subject) {
            this.subject = subject;
        }

        public ComplexWeight getTemporal() {
            return temporal;
        }

        public void setTemporal(ComplexWeight temporal) {
            this.temporal = temporal;
        }

        public ComplexWeight getAssertion() {
            return assertion;
        }

        public void setAssertion(ComplexWeight assertion) {
            this.assertion = assertion;
        }
    }

    public static class ComplexWeight {
        private double light;
        private double heavy;

        public double getLight() {
            return light;
        }

        public void setLight(double light) {
            this.light = light;
        }

        public double getHeavy() {
            return heavy;
        }

        public void setHeavy(double heavy) {
            this.heavy = heavy;
        }
    }
}
