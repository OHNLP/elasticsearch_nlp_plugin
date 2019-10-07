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

package org.ohnlp.elasticsearchnlp.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.ohnlp.elasticsearchnlp.config.components.ConTextConfig;
import org.ohnlp.elasticsearchnlp.config.components.EmbeddingsConfig;

import java.util.Collection;
import java.util.HashSet;

/**
 * Plugin Configuration
 */
public class Config {

    private Collection<NLPComponent> enabled;
    private ComponentSettings settings;

    public Collection<NLPComponent> getEnabled() {
        return enabled;
    }

    public void setEnabled(Collection<NLPComponent> enabled) {
        this.enabled = new HashSet<>(enabled);
    }

    public ComponentSettings getSettings() {
        return settings;
    }

    public void setSettings(ComponentSettings settings) {
        this.settings = settings;
    }

    @JsonIgnore
    public boolean enableConTextSupport() {
        return this.enabled.contains(NLPComponent.CONTEXT);
    }

    @JsonIgnore
    public boolean enableClinicalStopwords() {
        return this.enabled.contains(NLPComponent.CLINICAL_STOP_WORDS);
    }

    @JsonIgnore
    public boolean enableEmbeddings() {
        return this.enabled.contains(NLPComponent.EMBEDDINGS);
    }

    public enum NLPComponent {
        CLINICAL_STOP_WORDS,
        CONTEXT,
        EMBEDDINGS
    }

    public static class ComponentSettings {
        private ConTextConfig context;
        private EmbeddingsConfig embeddings;

        public ConTextConfig getContext() {
            return context;
        }

        public void setContext(ConTextConfig context) {
            this.context = context;
        }

        public EmbeddingsConfig getEmbeddings() {
            return embeddings;
        }

        public void setEmbeddings(EmbeddingsConfig embeddings) {
            this.embeddings = embeddings;
        }
    }
}
