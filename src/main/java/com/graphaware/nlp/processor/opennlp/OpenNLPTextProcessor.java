/*
 * Copyright (c) 2013-2016 GraphAware
 *
 * This file is part of the GraphAware Framework.
 *
 * GraphAware Framework is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of
 * the GNU General Public License along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */
package com.graphaware.nlp.processor.opennlp;

import com.graphaware.nlp.annotation.NLPTextProcessor;
import com.graphaware.nlp.domain.AnnotatedText;
import com.graphaware.nlp.domain.Phrase;
import com.graphaware.nlp.domain.Sentence;
import com.graphaware.nlp.domain.Tag;
import com.graphaware.nlp.processor.TextProcessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import opennlp.tools.util.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NLPTextProcessor(name = "OpenNLPTextProcessor")
public class OpenNLPTextProcessor implements TextProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(OpenNLPTextProcessor.class);
    public static final String TOKENIZER = "tokenizer";
    public static final String POS = "pos";
    public static final String SENTIMENT = "sentiment";
    public static final String TOKENIZER_AND_SENTIMENT = "tokenizerAndSentiment";
    public static final String PHRASE = "phrase";

    public static final String backgroundSymbol = "O,MISC"; // default value (taken from StanfordNLP)

    private final Map<String, OpenNLPPipeline> pipelines = new HashMap<>();
    private final Pattern patternCheck;

    public OpenNLPTextProcessor() {
        //Creating default pipelines
        createTokenizerPipeline();
        createPosPipeline();
        createPhrasePipeline();

        String pattern = "\\p{Punct}";
        patternCheck = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
    }

    private void createTokenizerPipeline() {
        OpenNLPPipeline pipeline = new PipelineBuilder()
                .tokenize()
                //.defaultStopWordAnnotator()
                .threadNumber(6)
                .build();
        pipelines.put(TOKENIZER, pipeline);
    }

    private void createPosPipeline() {
        OpenNLPPipeline pipeline = new PipelineBuilder()
                //.tokenize()
                //.defaultStopWordAnnotator()
                .threadNumber(6)
                .build();
        pipelines.put(POS, pipeline);
    }

    private void createPhrasePipeline() {
        OpenNLPPipeline pipeline = new PipelineBuilder()
                .tokenize()
                //.defaultStopWordAnnotator()
                .extractSentiment()
                .extractCoref()
                .extractRelations()
                .threadNumber(6)
                .build();
        pipelines.put(PHRASE, pipeline);
    }

    @Override
    public AnnotatedText annotateText(String text, Object id, int level, String lang, boolean store) {
        String pipeline;
        switch (level) {
            case 0:
                pipeline = TOKENIZER;
                break;
            case 1:
                pipeline = TOKENIZER_AND_SENTIMENT;
                break;
            case 2:
                pipeline = PHRASE;
                break;
            default:
                pipeline = TOKENIZER;
        }
        return annotateText(text, id, pipeline, lang, store);
    }

    @Override
    public AnnotatedText annotateText(String text, Object id, String name, String lang, boolean store) {
        OpenNLPPipeline pipeline = pipelines.get(name);
        if (pipeline == null) {
            throw new RuntimeException("Pipeline: " + name + " doesn't exist");
        }
        AnnotatedText result = new AnnotatedText(id);
        OpenNLPAnnotation document = new OpenNLPAnnotation(text);
        pipeline.annotate(document);
        List<OpenNLPAnnotation.Sentence> sentences = document.getSentences();
        final AtomicInteger sentenceSequence = new AtomicInteger(0);
        sentences.stream().map((sentence) -> {
            return sentence;
        }).forEach((sentence) -> {
            int sentenceNumber = sentenceSequence.getAndIncrement();
            String sentenceId = id + "_" + sentenceNumber;
            final Sentence newSentence = new Sentence(sentence.toString(), store, sentenceId, sentenceNumber);
            extractTokens(lang, sentence, newSentence);
            //extractSentiment(sentence, newSentence);
            extractPhrases(sentence, newSentence);
            result.addSentence(newSentence);
        });
        //extractRelationship(result, sentences, document);
        return result;
    }

    private void extractPhrases(OpenNLPAnnotation.Sentence sentence, Sentence newSentence) {
        sentence.getPhrasesIndex().forEach((index) -> {
            Span chunk = sentence.getChunks()[index];
            String chunkString = sentence.getChunkStrings()[index];
            newSentence.addPhraseOccurrence(chunk.getStart(), chunk.getEnd(), new Phrase(chunkString));
        });
    }

//    protected void extractSentiment(OpenNLPAnnotation.Sentence sentence, final Sentence newSentence) {
//        int score = extractSentiment(sentence);
//        newSentence.setSentiment(score);
//    }

    protected void extractTokens(String lang, OpenNLPAnnotation.Sentence sentence, final Sentence newSentence) {
        // TO DO: lemma, named entities
        String[] tokens = sentence.getWords();
        String text = sentence.getSentence();
        //System.out.println("Extracting tokens. Text length "+text.length()+", # tokens " + tokens.length);
        int idx = -1;
        for (String token : tokens) {
          idx++;
          if (token==null || !checkPuntuation(token))
            continue;
          //System.out.println("Processing word: " + token);
          Tag newTag = getTag(sentence, idx, lang);
          newSentence.addTagOccurrence(sentence.getWordStart(idx), sentence.getWordEnd(idx), newSentence.addTag(newTag));
        }

        /*TokenHolder currToken = new TokenHolder();
        currToken.setNe(backgroundSymbol);
        Arrays.asList(tokens).stream()
                .filter(token -> token!=null) // && checkPuntuation(token.get(CoreAnnotations.LemmaAnnotation.class)))
                .map(token -> {
                    String currentNe = StringUtils.getNotNullString(token.get(CoreAnnotations.NamedEntityTagAnnotation.class));
                    if (currentNe.equals(backgroundSymbol) && currToken.getNe().equals(backgroundSymbol)) {
                        Tag tag = getTag(token, lang);
                        if (tag != null) {
                            newSentence.addTagOccurrence(token.beginPosition(), token.endPosition(), newSentence.addTag(tag));
                        }
                    } else if (currentNe.equals(backgroundSymbol) && !currToken.getNe().equals(backgroundSymbol)) {
                        Tag newTag = new Tag(currToken.getToken(), lang);
                        newTag.setNe(currToken.getNe());
                        newSentence.addTagOccurrence(currToken.getBeginPosition(), currToken.getEndPosition(), newSentence.addTag(newTag));
                        currToken.reset();
                        Tag tag = getTag(token, lang);
                        if (tag != null) {
                            newSentence.addTagOccurrence(token.beginPosition(), token.endPosition(), newSentence.addTag(tag));
                        }
                    } else if (!currentNe.equals(currToken.getNe()) && !currToken.getNe().equals(backgroundSymbol)) {
                        Tag tag = new Tag(currToken.getToken(), lang);
                        tag.setNe(currToken.getNe());
                        newSentence.addTagOccurrence(currToken.getBeginPosition(), currToken.getEndPosition(), newSentence.addTag(tag));
                        currToken.reset();
                        currToken.updateToken(StringUtils.getNotNullString(token.get(CoreAnnotations.OriginalTextAnnotation.class)));
                        currToken.setBeginPosition(token.beginPosition());
                        currToken.setEndPosition(token.endPosition());
                    } else if (!currentNe.equals(backgroundSymbol) && currToken.getNe().equals(backgroundSymbol)) {
                        currToken.updateToken(StringUtils.getNotNullString(token.get(CoreAnnotations.OriginalTextAnnotation.class)));
                        currToken.setBeginPosition(token.beginPosition());
                        currToken.setEndPosition(token.endPosition());
                    } else {
                        String before = StringUtils.getNotNullString(token.get(CoreAnnotations.BeforeAnnotation.class));
                        String currentText = StringUtils.getNotNullString(token.get(CoreAnnotations.OriginalTextAnnotation.class));
                        currToken.updateToken(before);
                        currToken.updateToken(currentText);
                        currToken.setBeginPosition(token.beginPosition());
                        currToken.setEndPosition(token.endPosition());
                    }
                    return currentNe;
                });
                .forEach(currentNe -> currToken.setNe(currentNe));

        if (currToken.getToken().length() > 0) {
            Tag tag = new Tag(currToken.getToken(), lang);
            tag.setNe(currToken.getNe());
            newSentence.addTagOccurrence(currToken.getBeginPosition(), currToken.getEndPosition(), newSentence.addTag(tag));
        }*/
    }

//    private void extractRelationship(AnnotatedText annotatedText, List<CoreMap> sentences, Annotation document) {
//        Map<Integer, CorefChain> corefChains = document.get(CorefCoreAnnotations.CorefChainAnnotation.class);
//        if (corefChains != null) {
//            for (CorefChain chain : corefChains.values()) {
//                CorefChain.CorefMention representative = chain.getRepresentativeMention();
//                int representativeSenteceNumber = representative.sentNum - 1;
//                List<CoreLabel> representativeTokens = sentences.get(representativeSenteceNumber).get(CoreAnnotations.TokensAnnotation.class);
//                int beginPosition = representativeTokens.get(representative.startIndex - 1).beginPosition();
//                int endPosition = representativeTokens.get(representative.endIndex - 2).endPosition();
//                Phrase representativePhraseOccurrence = annotatedText.getSentences().get(representativeSenteceNumber).getPhraseOccurrence(beginPosition, endPosition);
//                if (representativePhraseOccurrence == null) {
//                    LOG.warn("Representative Phrase not found: " + representative.mentionSpan);
//                }
//                for (CorefChain.CorefMention mention : chain.getMentionsInTextualOrder()) {
//                    if (mention == representative) {
//                        continue;
//                    }
//                    int mentionSentenceNumber = mention.sentNum - 1;
//
//                    List<CoreLabel> mentionTokens = sentences.get(mentionSentenceNumber).get(CoreAnnotations.TokensAnnotation.class);
//                    int beginPositionMention = mentionTokens.get(mention.startIndex - 1).beginPosition();
//                    int endPositionMention = mentionTokens.get(mention.endIndex - 2).endPosition();
//                    Phrase mentionPhraseOccurrence = annotatedText.getSentences().get(mentionSentenceNumber).getPhraseOccurrence(beginPositionMention, endPositionMention);
//                    if (mentionPhraseOccurrence == null) {
//                        LOG.warn("Mention Phrase not found: " + mention.mentionSpan);
//                    }
//                    if (representativePhraseOccurrence != null
//                            && mentionPhraseOccurrence != null) {
//                        mentionPhraseOccurrence.setReference(representativePhraseOccurrence);
//                    }
//                }
//            }
//        }
//    }

//    public AnnotatedText sentiment(AnnotatedText annotated) {
//        StanfordCoreNLP pipeline = pipelines.get(SENTIMENT);
//        annotated.getSentences().parallelStream().forEach((item) -> {
//            Annotation document = new Annotation(item.getSentence());
//            pipeline.annotate(document);
//            List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
//            Optional<CoreMap> sentence = sentences.stream().findFirst();
//            if (sentence != null && sentence.isPresent()) {
//                extractSentiment(sentence.get(), item);
//            }
//        });
//        return annotated;
//    }
//
//    private int extractSentiment(CoreMap sentence) {
//        Tree tree = sentence
//                .get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
//        if (tree == null) {
//            return Sentence.NO_SENTIMENT;
//        }
//        int score = RNNCoreAnnotations.getPredictedClass(tree);
//        return score;
//    }

    @Override
    public Tag annotateSentence(String text, String lang) {
//        Annotation document = new Annotation(text);
//        pipelines.get(SENTIMENT).annotate(document);
//        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
//        Optional<CoreMap> sentence = sentences.stream().findFirst();
//        if (sentence.isPresent()) {
//            Optional<Tag> oTag = sentence.get().get(CoreAnnotations.TokensAnnotation.class).stream()
//                    .map((token) -> getTag(token))
//                    .filter((tag) -> (tag != null) && checkPuntuation(tag.getLemma()))
//                    .findFirst();
//            if (oTag.isPresent()) {
//                return oTag.get();
//            }
//        }
        return null;
    }

    @Override
    public Tag annotateTag(String text, String lang) {
//        Annotation document = new Annotation(text);
//        pipelines.get(TOKENIZER).annotate(document);
//        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
//        Optional<CoreMap> sentence = sentences.stream().findFirst();
//        if (sentence.isPresent()) {
//            Optional<Tag> oTag = sentence.get().get(CoreAnnotations.TokensAnnotation.class).stream()
//                    .map((token) -> getTag(token))
//                    .filter((tag) -> (tag != null) && checkPuntuation(tag.getLemma()))
//                    .findFirst();
//            if (oTag.isPresent()) {
//                return oTag.get();
//            }
//        }
        return null;
    }

//    private Tag getTag(CoreLabel token) {
//        Pair<Boolean, Boolean> stopword = token.get(StopwordAnnotator.class);
//        if (stopword.first()) {
//            return null;
//        }
//        String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
//        String ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
//        String lemma;
//
//        if (ne.equals(backgroundSymbol)) {
//            lemma = token.get(CoreAnnotations.LemmaAnnotation.class);
//        } else {
//            lemma = token.get(CoreAnnotations.OriginalTextAnnotation.class);
//        }
//
//        Tag tag = new Tag(lemma);
//        tag.setPos(pos);
//        tag.setNe(ne);
//        LOG.info("POS: " + pos + " ne: " + ne + " lemma: " + lemma);
//        return tag;
//    }

    private Tag getTag(OpenNLPAnnotation.Sentence sentence, int tokenIdx, String lang) {
      String pos = " - ";
      String ne  = " - ";
      String lemma = sentence.getWords()[tokenIdx]; // TO DO

      if (sentence.getPosTags()!=null)
        pos = sentence.getPosTags()[tokenIdx]; // TO DO: try ... catch

      if (sentence.getNamedEntities()!=null)
        ne = sentence.getNamedEntities()[tokenIdx]; // TO DO: try ... catch

      Tag tag = new Tag(lemma, lang);
      tag.setPos(pos);
      tag.setNe(pos);
      LOG.info("POS: " + pos + " ne: " + ne + " lemma: " + lemma);
      return tag;
    }

    @Override
    public List<Tag> annotateTags(String text, String lang) {
        /*List<Tag> result = new ArrayList<>();
        Annotation document = new Annotation(text);
        pipelines.get(TOKENIZER).annotate(document);
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        Optional<CoreMap> sentence = sentences.stream().findFirst();
        if (sentence.isPresent()) {
            Stream<Tag> oTags = sentence.get().get(CoreAnnotations.TokensAnnotation.class).stream()
                    .map((token) -> getTag(lang, token))
                    .filter((tag) -> (tag != null) && checkPuntuation(tag.getLemma()));
            oTags.forEach((tag) -> result.add(tag));
        }
        return result;*/
        return null;
    }


    public boolean checkPuntuation(String value) {
        Matcher match = patternCheck.matcher(value);
        return !match.find();
    }

    @Override
    public AnnotatedText sentiment(AnnotatedText annotated) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


    class TokenHolder {

        private String ne;
        private StringBuilder sb;
        private int beginPosition;
        private int endPosition;

        public TokenHolder() {
            reset();
        }

        public String getNe() {
            return ne;
        }

        public String getToken() {
            if (sb == null) {
                return " - ";
            }
            return sb.toString();
        }

        public int getBeginPosition() {
            return beginPosition;
        }

        public int getEndPosition() {
            return endPosition;
        }

        public void setNe(String ne) {
            this.ne = ne;
        }

        public void updateToken(String tknStr) {
            this.sb.append(tknStr);
        }

        public void setBeginPosition(int beginPosition) {
            if (this.beginPosition < 0) {
                this.beginPosition = beginPosition;
            }
        }

        public void setEndPosition(int endPosition) {
            this.endPosition = endPosition;
        }

        public final void reset() {
            sb = new StringBuilder();
            beginPosition = -1;
            endPosition = -1;
        }
    }

    class PhraseHolder implements Comparable<PhraseHolder> {

        private StringBuilder sb;
        private int beginPosition;
        private int endPosition;

        public PhraseHolder() {
            reset();
        }

        public String getPhrase() {
            if (sb == null) {
                return " - ";
            }
            return sb.toString();
        }

        public int getBeginPosition() {
            return beginPosition;
        }

        public int getEndPosition() {
            return endPosition;
        }

        public void updatePhrase(String tknStr) {
            this.sb.append(tknStr);
        }

        public void setBeginPosition(int beginPosition) {
            if (this.beginPosition < 0) {
                this.beginPosition = beginPosition;
            }
        }

        public void setEndPosition(int endPosition) {
            this.endPosition = endPosition;
        }

        public final void reset() {
            sb = new StringBuilder();
            beginPosition = -1;
            endPosition = -1;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PhraseHolder)) {
                return false;
            }
            PhraseHolder otherObject = (PhraseHolder) o;
            if (this.sb != null
                    && otherObject.sb != null
                    && this.sb.toString().equals(otherObject.sb.toString())
                    && this.beginPosition == otherObject.beginPosition
                    && this.endPosition == otherObject.endPosition) {
                return true;
            }
            return false;
        }

        @Override
        public int compareTo(PhraseHolder o) {
            if (o == null) {
                return 1;
            }
            if (this.equals(o)) {
                return 0;
            } else if (this.beginPosition > o.beginPosition) {
                return 1;
            } else if (this.beginPosition == o.beginPosition) {
                if (this.endPosition > o.endPosition) {
                    return 1;
                }
            }
            return -1;
        }
    }

    @Override
    public List<String> getPipelines() {
        return new ArrayList<>(pipelines.keySet());
    }

    @Override    
    public boolean checkPipeline(String name) {
        return pipelines.containsKey(name);
    }
    
    @Override
    public void createPipeline(Map<String, Object> pipelineSpec) {
//        //TODO add validation
//        String name = (String) pipelineSpec.get("name");
//        PipelineBuilder pipelineBuilder = new PipelineBuilder();
//
//        if ((Boolean) pipelineSpec.getOrDefault("tokenize", true)) {
//            pipelineBuilder.tokenize();
//        }
//
//        String stopWords = (String) pipelineSpec.getOrDefault("stopWords", "default");
//        if (stopWords.equalsIgnoreCase("default")) {
//            pipelineBuilder.defaultStopWordAnnotator();
//        } else {
//            pipelineBuilder.customStopWordAnnotator(stopWords);
//        }
//
//        if ((Boolean) pipelineSpec.getOrDefault("sentiment", false)) {
//            pipelineBuilder.extractSentiment();
//        }
//        if ((Boolean) pipelineSpec.getOrDefault("coref", false)) {
//            pipelineBuilder.extractCoref();
//        }
//        if ((Boolean) pipelineSpec.getOrDefault("relations", false)) {
//            pipelineBuilder.extractRelations();
//        }
//        Long threadNumber = (Long) pipelineSpec.getOrDefault("threadNumber", 4);
//        pipelineBuilder.threadNumber(threadNumber.intValue());
//
//        StanfordCoreNLP pipeline = pipelineBuilder.build();
//        pipelines.put(name, pipeline);
    }
    
    @Override
    public void removePipeline(String name) {
        if (!pipelines.containsKey(name))
            throw new RuntimeException("No pipeline found with name: " + name);
        pipelines.remove(name);
    }
}
