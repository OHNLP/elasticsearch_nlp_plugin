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

import org.ohnlp.elasticsearchnlp.context.ConTexTSettings;
import org.ohnlp.elasticsearchnlp.context.ConTexTStatus;
import org.ohnlp.elasticsearchnlp.context.ConTexTTrigger;
import org.ohnlp.elasticsearchnlp.context.ConText;
import org.ohnlp.elasticsearchnlp.payloads.NLPPayload;
import org.ohnlp.elasticsearchnlp.perf.AnnotationIndex;
import org.ohnlp.elasticsearchnlp.perf.AnnotationRoot;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;
import org.ahocorasick.trie.Emit;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.util.BytesRef;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;

/**
 * Annotates input text with {@link NLPPayload} payloads if not present and populates
 * negation, certainty, and temporality into said payload.
 * <p>
 * Generally speaking, this is done via the following using a UIMA-backed MedTagger pipeline:
 * 1. Sentence Boundary Detection
 * 2. Tokenization
 * 3. Annotation of tokens using contextual information
 * 4. Aggregation of tokens throughout the document in order
 */
public final class ConTexTAwareTokenizer extends Tokenizer {

    private static final int[] RULE_PRIORITIES = {1, 2}; // TODO more robust solution that scans files

    /**
     * {@link ConTexTSettings} that denote trigger terms and terminals, in priority order with lowest priority first
     */
    public Deque<ConTexTSettings> contextSettings = new LinkedList<>();
    private StringBuilder str;
    private String document;
    private char[] buffer;
    private Deque<TokenPayloadPair> tokenQueue;

    private final CharTermAttribute termAtt;
    private final OffsetAttribute offsetAtt;
    private final PayloadAttribute payloadAtt;
    private TokenizerME tokenizer;
    private SentenceDetectorME sentenceDetector;
    private static final int MAX_WIN_SIZE = -1;

    // Starts a new UIMA pipeline on initialization
    public ConTexTAwareTokenizer() {
        try {
            initNLPComponents();
            for (int i : RULE_PRIORITIES) {
                this.contextSettings.add(new ConTexTSettings(ConTexTSettings.class.getResourceAsStream("/contextRule.txt"), i));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize backing NLP pipeline", e);
        }
        this.termAtt = this.addAttribute(CharTermAttribute.class);
        this.offsetAtt = this.addAttribute(OffsetAttribute.class);
        this.payloadAtt = this.addAttribute(PayloadAttribute.class);
        this.str = new StringBuilder();
        this.buffer = new char[8192];
    }

    private void initNLPComponents() throws IOException {
        TokenizerModel tokModel = new TokenizerModel(ConTexTAwareTokenizer.class.getResourceAsStream("/models/en-token.bin"));
        tokenizer = new TokenizerME(tokModel);
        SentenceModel sentModel = new SentenceModel(ConTexTAwareTokenizer.class.getResourceAsStream("/models/en-sent.bin"));
        sentenceDetector = new SentenceDetectorME(sentModel);
    }


    /**
     * Returns the next token
     *
     * @return False if input is exhausted, true otherwise
     */
    @Override
    public boolean incrementToken() {
        this.clearAttributes();
        if (tokenQueue.isEmpty()) {
            return false;
        }
        TokenPayloadPair token = tokenQueue.removeFirst();
        termAtt.setEmpty().append(token.token.getCoveredText(document));
        payloadAtt.setPayload(new BytesRef(token.payload.toBytes()));
        this.offsetAtt.setOffset(this.correctOffset(token.token.getStart()), this.correctOffset(token.token.getEnd()));
        return true;
    }

    @Override
    public void end() throws IOException {
        super.end();
        int ofs = this.correctOffset(this.str.length());
        this.offsetAtt.setOffset(ofs, ofs);
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            this.str.setLength(0);
            this.str.trimToSize();
        }

    }

    /**
     * Resets the tokenizer with a given input and annotates ConText information
     *
     * @throws IOException if errors occur during NLP
     */
    @Override
    public void reset() throws IOException {
        super.reset();
        this.readAllFromInput(this.input);
        // Run NLP pipeline
        this.document = this.str.toString();
        this.tokenQueue = annotateConTexts();

    }

    /**
     * Determines ConText status for every BaseToken in the input text and returns as a sequential list of context statuses
     * {@link ConTexTStatus} objects
     */
    private Deque<TokenPayloadPair> annotateConTexts() {
        Deque<TokenPayloadPair> ret = new LinkedList<>();
        ConTexTStatus[] documentContexts = new ConTexTStatus[document.length()];
        for (int i = 0; i < documentContexts.length; i++) {
            documentContexts[i] = new ConTexTStatus();
        }
        // Populate ConTexts by sentence

        for (Span sentence : sentenceDetector.sentPosDetect(document)) {
            // TODO: not really efficient, better to just directly put into the destination array instead of copying
            String text = document.substring(sentence.getStart(), sentence.getEnd());
            int start = sentence.getStart() - 1; // Offset the lack of starting \n for next
            for (String subText : text.split("\n")) {
                start++; // Factor in the \n
                Deque<Map<ConTexTTrigger.TriggerType, List<ConTexTTrigger>>> triggersByPriority = getTriggers(subText);
                Map<ConTexTTrigger.TriggerType, List<ConTexTTrigger>> triggers = flattenByPriority(triggersByPriority);
                // Annotate and copy context statuses to the document contexts
                System.arraycopy(annotateConTextStatuses(triggers, subText), 0, documentContexts, start, subText.length());
                start += subText.length();
            }
        }
        // Iterate through tokens to generate token/payload pairs. Select returns in-order as per UIMA
        for (Span token : tokenizer.tokenizePos(document)) {
            NLPPayload payload = new NLPPayload();
            ConTexTStatus context = documentContexts[token.getStart()]; // TODO: more comprehensive check than first character collision
            if (!context.isPositive) {
                payload.setPositive(false);
            }
            if (!context.isAsserted) {
                payload.setAsserted(false);
            }
            if (!context.isPresent) {
                payload.setPresent(false);
            }
            if (!context.experiencerIsPatient) {
                payload.setPatientIsSubject(false);
            }
            if (context.isNegationTerminal || context.isNegationTrigger) {
                payload.setNegationTrigger(true);
            }
            if (context.isPossibleTerminal || context.isPossibleTrigger || context.isHypotheticalTerminal || context.isHypotheticalTrigger) {
                payload.setAssertionTrigger(true);
            }
            if (context.isHistoricalTerminal || context.isHistoricalTrigger) {
                payload.setHistoricalTrigger(true);
            }
            if (context.isExperiencerTerminal || context.isExperiencerTrigger) {
                payload.setExperiencerTrigger(true);
            }
            ret.addLast(new TokenPayloadPair(token, payload));
        }
        return ret;
    }

    // Public for tests

    /**
     * Iterates through triggersByPriority in reverse order in descending priorities while disallowing overwrites
     *
     * @param triggersByPriority priorities to flatten
     * @return A flattened view with higher priority triggers overwriting lower priority triggers
     */
    private Map<ConTexTTrigger.TriggerType, List<ConTexTTrigger>> flattenByPriority(Deque<Map<ConTexTTrigger.TriggerType, List<ConTexTTrigger>>> triggersByPriority) {
        if (triggersByPriority.size() == 1) {
            return triggersByPriority.poll();
        }
        AnnotationIndex priorityIndex = new AnnotationRoot();
        Map<ConTexTTrigger.TriggerType, List<ConTexTTrigger>> ret = new HashMap<>();
        Map<ConTexTTrigger.TriggerType, List<ConTexTTrigger>> next;
        while ((next = triggersByPriority.pollLast()) != null) {
            next.forEach((type, triggers) -> {
                List<ConTexTTrigger> retained = new LinkedList<>();
                for (ConTexTTrigger trigger : triggers) {
                    if (priorityIndex.getCollisions(trigger.start, trigger.end).isEmpty()) {
                        retained.add(trigger);
                    }
                }
                ret.computeIfAbsent(type, k -> new LinkedList<>()).addAll(retained);
            });
            // Now, add all of these to index to prevent lower priority from adding if they collide. We do this here to
            // ensure that all annotations with the same priority have the chance to appear even if they overlap.
            next.values().forEach(l -> l.forEach(t -> priorityIndex.insert(new Span(t.start, t.end))));
        }
        return ret;
    }


    /**
     * Annotates context statuses by sentence on a character level
     *
     * @param triggers The triggers to use
     * @param text     The text to annotate
     * @return An array of context statuses with indexes corresponding to their respective sentence character positions
     */
    public final ConTexTStatus[] annotateConTextStatuses(Map<ConTexTTrigger.TriggerType, List<ConTexTTrigger>> triggers, String text) {
        ConTexTStatus[] sentence = new ConTexTStatus[text.length()];
        for (int i = 0; i < sentence.length; i++) {
            sentence[i] = new ConTexTStatus();
        }
        // - Transpose pseudos onto the sentence
        for (ConTexTTrigger pseudo : triggers.getOrDefault(ConTexTTrigger.TriggerType.PSEUDO, Collections.emptyList())) {
            for (int i = pseudo.start; i < pseudo.end; i++) {
                sentence[i].isPseudo = true;
            }
        }
        // - Transpose terminals onto the sentence
        for (ConTexTTrigger trigger : triggers.getOrDefault(ConTexTTrigger.TriggerType.TERMINAL, Collections.emptyList())) {
            for (int i = trigger.start; i < trigger.end; i++) {
                switch (trigger.contextType) {
                    case NEGATED:
                        sentence[i].isNegationTerminal = true;
                        break;
                    case POSSIBLE:
                        sentence[i].isPossibleTerminal = true;
                        break;
                    case HISTORICAL:
                        sentence[i].isHistoricalTerminal = true;
                        break;
                    case HYPOTHETICAL:
                        sentence[i].isHypotheticalTerminal = true;
                        break;
                    case EXPERIENCER:
                        sentence[i].isExperiencerTerminal = true;
                        break;
                    default:
                        break;
                }
            }
        }
        // - Traverse left to right TODO better algorithm for traversal
        List<ConTexTTrigger> preTriggers = triggers.getOrDefault(ConTexTTrigger.TriggerType.START_RIGHT, Collections.emptyList());
        for (ConTexTTrigger trigger : preTriggers) {
            // Skip pseudos
            if (sentence[trigger.start].isPseudo || sentence[trigger.end - 1].isPseudo) {
                continue;
            }
            // Tag trigger
            for (int i = trigger.start; i < trigger.end; i++) {
                ConTexTStatus status = sentence[i];
                tagStatusAsTrigger(status, trigger.contextType);
            }
            // Continue to end of sentence, break on terminal
            AtomicInteger wordWindow = new AtomicInteger(0);
            for (int i = trigger.end; i < sentence.length; i++) {
                ConTexTStatus status = sentence[i];
                char sentenceChar = text.toCharArray()[i];
                if (sentenceChar == ' ') {
                    wordWindow.incrementAndGet();
                }
                if (tagContexTStatus(status, trigger, wordWindow)) {
                    break;
                }
            }
        }
        // - Traverse right to left
        List<ConTexTTrigger> postTriggers = triggers.getOrDefault(ConTexTTrigger.TriggerType.START_LEFT, Collections.emptyList());
        for (ConTexTTrigger trigger : postTriggers) {
            // Skip pseudos
            if (sentence[trigger.start].isPseudo || sentence[trigger.end - 1].isPseudo) {
                continue;
            }
            // Tag trigger
            for (int i = trigger.start; i < trigger.end; i++) {
                ConTexTStatus status = sentence[i];
                tagStatusAsTrigger(status, trigger.contextType);
            }
            // Continue to beginning of sentence, break on terminal
            AtomicInteger wordWindow = new AtomicInteger(0);
            for (int i = trigger.start - 1; i >= 0; i--) {
                ConTexTStatus status = sentence[i];
                char sentenceChar = text.toCharArray()[i];
                if (sentenceChar == ' ') {
                    wordWindow.incrementAndGet();
                }
                if (tagContexTStatus(status, trigger, wordWindow)) {
                    break;
                }
            }
        }
        return sentence;
    }


    /**
     * Tags the given status; and returns whether tagging should continue or if a terminal condition was reached
     *
     * @param status  The status to tag
     * @param trigger The trigger definition being tagged
     * @param spaces
     * @return True if a terminal condition was hit and tagging is finished for this trigger, false otherwise
     */
    private boolean tagContexTStatus(ConTexTStatus status, ConTexTTrigger trigger, AtomicInteger spaces) {
        // First check if this is a terminal
        boolean isExit = spaces.get() > MAX_WIN_SIZE && MAX_WIN_SIZE != -1;
        switch (trigger.contextType) {
            case NEGATED:
                if (status.isNegationTerminal || status.isNegationTrigger) {
                    isExit = true;
                }
                break;
            case POSSIBLE:
                if (status.isPossibleTrigger || status.isPossibleTerminal || status.isHypotheticalTrigger || status.isHypotheticalTerminal) {
                    isExit = true;
                }
                break;
            case HYPOTHETICAL:
                if (status.isHypotheticalTrigger || status.isHypotheticalTerminal) {
                    isExit = true;
                }
                break;
            case HISTORICAL:
                if (status.isHistoricalTerminal || status.isHistoricalTrigger) {
                    isExit = true;
                }
                break;
            case EXPERIENCER:
                if (status.isExperiencerTerminal || status.isExperiencerTrigger) {
                    isExit = true;
                }
                break;
            default:
                throw new UnsupportedOperationException("A trigger of type " + trigger.contextType + " was found for sentence tagging");
        }
        if (isExit) {
            // Terminal found, stop encoding
            return true;
        }
        // Not a Terminal, thus encode the status
        switch (trigger.contextType) {
            case NEGATED:
                status.isPositive = false;
                break;
            case POSSIBLE:
                status.isAsserted = false;
                break;
            case HISTORICAL:
                status.isPresent = false;
                break;
            case HYPOTHETICAL:
                status.isAsserted = false; // TODO: ???
                status.isPresent = true;
                break;
            case EXPERIENCER:
                status.experiencerIsPatient = false;
                break;
        }
        return false;
    }


    private void tagStatusAsTrigger(ConTexTStatus status, ConText type) {
        switch (type) {
            case NEGATED:
                status.isNegationTrigger = true;
                break;
            case HISTORICAL:
                status.isHistoricalTrigger = true;
                break;
            case HYPOTHETICAL:
                status.isHypotheticalTrigger = true;
                break;
            case EXPERIENCER:
                status.isExperiencerTrigger = true;
                break;
            case POSSIBLE:
                status.isPossibleTrigger = true;
                break;
            default:
                throw new UnsupportedOperationException("A trigger of type " + type + " was found for sentence tagging");
        }
    }

    // Public for tests

    /**
     * Retrieves all triggers for a given sentence
     *
     * @param sentence The sentence to tag
     * @return A mapping of trigger types to a list of triggers for that type in the sentence, denoted by sentence position
     */
    public final Deque<Map<ConTexTTrigger.TriggerType, List<ConTexTTrigger>>> getTriggers(String sentence) {
        Deque<Map<ConTexTTrigger.TriggerType, List<ConTexTTrigger>>> ret = new LinkedList<>();
        for (ConTexTSettings prioritySettings : contextSettings) {
            Map<ConTexTTrigger.TriggerType, List<ConTexTTrigger>> triggersForThisPriority = new HashMap<>();
            // Run the general trie
            Collection<Emit> values = prioritySettings.getGeneral().parseText(sentence.toLowerCase());
            Map<String, Map<ConTexTTrigger.TriggerType, Set<ConText>>> generalDict = prioritySettings.getGeneralTriggerDict();
            values.forEach(e -> {
                Map<ConTexTTrigger.TriggerType, Set<ConText>> triggers = generalDict.get(e.getKeyword());
                int start = e.getStart();
                int end = e.getEnd() + 1;
                triggers.forEach(((triggerType, conTexts) -> {
                    if (triggerType == ConTexTTrigger.TriggerType.PSEUDO) {
                        triggersForThisPriority.computeIfAbsent(triggerType, k -> new LinkedList<>()).add(new ConTexTTrigger(null, start, end));
                    } else {
                        conTexts.forEach(conText -> triggersForThisPriority.computeIfAbsent(triggerType, k -> new LinkedList<>()).add(new ConTexTTrigger(conText, start, end)));
                    }
                }));
            });
            // First find pseudos (trigger exclusions)
            Matcher matcher;
            if (prioritySettings.getRegexPseudo() != null) {
                matcher = prioritySettings.getRegexPseudo().matcher(sentence);
                while (matcher.find()) {
                    MatchResult mr = matcher.toMatchResult();
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.PSEUDO, k -> new LinkedList<>()).add(new ConTexTTrigger(null, mr.start(), mr.end()));
                }
            }
            // Negation - Right lookup
            if (prioritySettings.getRegexNegPre() != null) {
                matcher = prioritySettings.getRegexNegPre().matcher(sentence);
                while (matcher.find()) {
                    MatchResult mr = matcher.toMatchResult();
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.START_RIGHT, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.NEGATED, mr.start(), mr.end()));
                }
            }
            // Negation - Left lookup
            if (prioritySettings.getRegexNegPost() != null) {
                matcher = prioritySettings.getRegexNegPost().matcher(sentence);
                while (matcher.find()) {
                    MatchResult mr = matcher.toMatchResult();
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.START_LEFT, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.NEGATED, mr.start(), mr.end()));
                }
            }
            // Negation - Terminals
            if (prioritySettings.getRegexNegEnd() != null) {
                matcher = prioritySettings.getRegexNegEnd().matcher(sentence);
                while (matcher.find()) {
                    MatchResult mr = matcher.toMatchResult();
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.TERMINAL, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.NEGATED, mr.start(), mr.end()));
                }
            }
            // Possible - Right lookup
            if (prioritySettings.getRegexPossPre() != null) {
                matcher = prioritySettings.getRegexPossPre().matcher(sentence);
                while (matcher.find()) {
                    MatchResult mr = matcher.toMatchResult();
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.START_RIGHT, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.POSSIBLE, mr.start(), mr.end()));
                }
            }
            // Possible - Left lookup
            if (prioritySettings.getRegexPossPost() != null) {
                matcher = prioritySettings.getRegexPossPost().matcher(sentence);
                while (matcher.find()) {
                    MatchResult mr = matcher.toMatchResult();
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.START_LEFT, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.POSSIBLE, mr.start(), mr.end()));
                }
            }
            // Experiencer - Right Lookup
            if (prioritySettings.getRegexExpPre() != null) {
                matcher = prioritySettings.getRegexExpPre().matcher(sentence);
                while (matcher.find()) {
                    MatchResult mr = matcher.toMatchResult();
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.START_RIGHT, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.EXPERIENCER, mr.start(), mr.end()));
                }
            }
            // Experiencer - Left Lookup
            if (prioritySettings.getRegexExpPost() != null) {
                matcher = prioritySettings.getRegexExpPost().matcher(sentence);
                while (matcher.find()) {
                    MatchResult mr = matcher.toMatchResult();
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.START_LEFT, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.EXPERIENCER, mr.start(), mr.end()));
                }
            }
            // Experiencer - Terminal
            if (prioritySettings.getRegexExpEnd() != null) {
                matcher = prioritySettings.getRegexExpEnd().matcher(sentence);
                while (matcher.find()) {
                    MatchResult mr = matcher.toMatchResult();
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.TERMINAL, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.EXPERIENCER, mr.start(), mr.end()));
                }
            }
            // Hypothetical - Right Lookup
            if (prioritySettings.getRegexHypoPre() != null) {
                matcher = prioritySettings.getRegexHypoPre().matcher(sentence);
                while (matcher.find()) {
                    MatchResult mr = matcher.toMatchResult();
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.START_RIGHT, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.HYPOTHETICAL, mr.start(), mr.end()));
                }
            }
            // No Hypothetical - Left Lookup
            // Hypothetical - Terminal
            if (prioritySettings.getRegexHypoEnd() != null) {
                matcher = prioritySettings.getRegexHypoEnd().matcher(sentence);
                while (matcher.find()) {
                    MatchResult mr = matcher.toMatchResult();
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.TERMINAL, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.HYPOTHETICAL, mr.start(), mr.end()));
                }
            }
            // Historical - Right Lookup
            if (prioritySettings.getRegexHistPre() != null) {
                matcher = prioritySettings.getRegexHistPre().matcher(sentence);
                while (matcher.find()) {
                    MatchResult mr = matcher.toMatchResult();
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.START_RIGHT, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.HISTORICAL, mr.start(), mr.end()));
                }
            }
            // Historical - Left Lookup
            if (prioritySettings.getRegexHistPost() != null) {
                matcher = prioritySettings.getRegexHistPost().matcher(sentence);
                while (matcher.find()) {
                    MatchResult mr = matcher.toMatchResult();
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.START_LEFT, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.HISTORICAL, mr.start(), mr.end()));
                }
            }
            // Historical - Terminal
            if (prioritySettings.getRegexHistEnd() != null) {
                matcher = prioritySettings.getRegexHistEnd().matcher(sentence);
                while (matcher.find()) {
                    MatchResult mr = matcher.toMatchResult();
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.TERMINAL, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.HISTORICAL, mr.start(), mr.end()));
                }
            }
            // Mixed
            if (prioritySettings.getRegexHypoExpEnd() != null) {
                matcher = prioritySettings.getRegexHypoExpEnd().matcher(sentence);
                while (matcher.find()) {
                    MatchResult mr = matcher.toMatchResult();
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.TERMINAL, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.HYPOTHETICAL, mr.start(), mr.end()));
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.TERMINAL, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.EXPERIENCER, mr.start(), mr.end()));
                }
            }
            if (prioritySettings.getRegexHistExpEnd() != null) {
                matcher = prioritySettings.getRegexHistExpEnd().matcher(sentence);
                while (matcher.find()) {
                    MatchResult mr = matcher.toMatchResult();
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.TERMINAL, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.HISTORICAL, mr.start(), mr.end()));
                    triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.TERMINAL, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.EXPERIENCER, mr.start(), mr.end()));
                }
            }
            // Time
            matcher = prioritySettings.getRegexTimeFor().matcher(sentence);
            while (matcher.find()) {
                MatchResult mr = matcher.toMatchResult();
                triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.START_RIGHT, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.HISTORICAL, mr.start(), mr.end()));
            }
            matcher = prioritySettings.getRegexTime().matcher(sentence);
            while (matcher.find()) {
                MatchResult mr = matcher.toMatchResult();
                triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.START_RIGHT, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.HISTORICAL, mr.start(), mr.end()));
            }
            matcher = prioritySettings.getRegexTimeSince().matcher(sentence);
            while (matcher.find()) {
                MatchResult mr = matcher.toMatchResult();
                triggersForThisPriority.computeIfAbsent(ConTexTTrigger.TriggerType.START_LEFT, k -> new LinkedList<>()).add(new ConTexTTrigger(ConText.HISTORICAL, mr.start(), mr.end()));
            }
            ret.add(triggersForThisPriority);
        }
        return ret;
    }

    /**
     * Gets the entire content of the field and dumps it into one big string for NLP operations
     *
     * @param input The input reader that provides field content
     * @throws IOException
     */
    private void readAllFromInput(Reader input) throws IOException {
        this.str.setLength(0);
        int len;
        while ((len = input.read(this.buffer)) > 0) {
            this.str.append(this.buffer, 0, len);
        }

    }

    private static class TokenPayloadPair {
        private final Span token;
        private final NLPPayload payload;

        public TokenPayloadPair(Span token, NLPPayload payload) {
            this.token = token;
            this.payload = payload;
        }
    }
}
