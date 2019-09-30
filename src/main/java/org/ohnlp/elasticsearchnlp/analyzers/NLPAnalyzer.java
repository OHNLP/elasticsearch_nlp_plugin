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

package org.ohnlp.elasticsearchnlp.analyzers;

import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.ohnlp.elasticsearchnlp.ElasticsearchNLPPlugin;

public class NLPAnalyzer extends Analyzer {
    // Denotes the analysis pipeline to use for a given field
    protected TokenStreamComponents createComponents(final String fieldName) {
        // First add ConText payloads and perform tokenization
        Tokenizer tokens = new ConTexTAwareTokenizer();
        TokenStream pipeline = new LowerCaseFilter(tokens);
        pipeline = new StopFilter(pipeline, EnglishAnalyzer.ENGLISH_STOP_WORDS_SET);
        if (ElasticsearchNLPPlugin.CONFIG.enableClinicalStopwords()) {
            pipeline = new StopFilter(pipeline, StopFilter.makeStopSet(CLINICAL_STOPWORDS));
        }
        return new TokenStreamComponents(tokens, pipeline);
    }

    private static final String[] CLINICAL_STOPWORDS = new String[]{"discuss", "concern", "approximate", "estimate",
            "recent", "update", "per", "maintain", "current", "significant", "show", "shows", "severe", "moderate",
            "mild", "eliminate", "eliminates", "eliminated", "status", "subtype", "revealed", "revealing",
            "demonstrated", "demonstrating", "demonstrate", "demonstrates", "regarding", "represents",
            "represent", "represented", "called", "related", "via", "including", "include", "includes", "suggests",
            "suggest", "suggesting", "showed", "shows", "consider", "causes", "extreme", "severe", "massive", "mild",
            "negligable", "redo", "today", "tomorrow", "status", "symptomatic", "secondary", "elects", "elective",
            "maternal", "cousin", "paternal", "grandfather", "grandmother", "aunt", "uncle", "familial", "versus",
            "suspected", "suspect", "diagnose", "must", "may", "should", "without", "with", "possible", "possibly",
            "possibility", "diagnosed", "likely", "known", "probable", "following", "underwent", "undergo", "previous",
            "prior", "post", "who", "whose", "probably", "no", "none", "history", "family", "brother", "mother",
            "father", "sister", "daughter", "son", "i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you",
            "you're", "you've", "you'll", "you'd", "your", "yours", "yourself", "yourselves", "he", "him", "his",
            "himself", "she", "she's", "her", "hers", "herself", "it", "it's", "its", "itself", "they", "them",
            "their", "theirs", "themselves", "what", "which", "who", "whom", "this", "that", "that'll", "these",
            "those", "am", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "having", "do",
            "does", "did", "doing", "a", "an", "the", "and", "but", "if", "or", "because", "as", "until", "while", "of",
            "at", "by", "for", "with", "about", "against", "between", "into", "through", "during", "before", "after",
            "above", "below", "to", "from", "up", "down", "in", "out", "on", "off", "over", "under", "again", "further",
            "then", "once", "here", "there", "when", "where", "why", "how", "all", "any", "both", "each", "few", "more",
            "most", "other", "some", "such", "no", "nor", "not", "only", "own", "same", "so", "than", "too", "very",
            "s", "t", "can", "will", "just", "don", "don't", "should", "should've", "now", "d", "ll", "m", "o", "re",
            "ve", "y", "ain", "aren", "aren't", "couldn", "couldn't", "didn", "didn't", "doesn", "doesn't", "hadn",
            "hadn't", "hasn", "hasn't", "haven", "haven't", "isn", "isn't", "ma", "mightn", "mightn't", "mustn",
            "mustn't", "needn", "needn't", "shan", "shan't", "shouldn", "shouldn't", "wasn", "wasn't", "weren",
            "weren't", "won", "won't", "wouldn", "wouldn't", "can't", "cannot", "yesterday", "today", "tomorrow"
    };
}
