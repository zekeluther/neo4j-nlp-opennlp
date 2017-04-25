/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.graphaware.nlp.processor.opennlp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import opennlp.tools.util.Span;

/**
 *
 * @author ale
 */
public class OpenNLPAnnotation {

    private final String text;
    private List<Sentence> sentences;

    public OpenNLPAnnotation(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
    
    public void setSentences(Span[] sentencesArray) {
        sentences = new ArrayList<>();
        for (Span sentence: sentencesArray) {
            sentences.add(new Sentence(sentence, getText()));
        }
    }

    public List<Sentence> getSentences() {
        return sentences;
    }
    
    

    class Sentence {

        private final Span sentence;
        private final String sentenceText;
        private List<Integer> nounphrases;
        private String[] words;
        private Span[] wordSpans;
        private String[] posTags;
        private Span[] chunks;
        private String[] chunkStrings;
        private String[] namedEntities;

        public Sentence(Span sentence, String text) {
            this.sentence = sentence;
            this.sentenceText = String.valueOf(sentence.getCoveredText(text));
        }
        
        public void addPhraseIndex(int phraseINdex) {
            if (nounphrases == null) {
                nounphrases = new ArrayList<>();
            }
            nounphrases.add(phraseINdex);
        }

        public Span getSentenceSpan() {
            return sentence;
        }
        
        public String getSentence() {
            return sentenceText;
        }
        
        public String[] getWords() {
            return words;
        }

        public void setWords(String[] words) {
            this.words = words;
        }

        public Span[] getWordSpans() {
            return this.wordSpans;
        }

        public void setWordsAndSpans(Span[] spans) {
          if (spans==null) {
            this.wordSpans = null;
            this.words = null;
            return;
          }
          this.wordSpans = spans;
          this.words = new String[this.wordSpans.length];
          this.words = Arrays.asList(spans).stream()
                        .map(span -> new String(this.sentenceText.substring(span.getStart(), span.getEnd())))
                        .collect(Collectors.toList()).toArray(this.words);
        }

        public int getWordStart(int idx) {
          int i = -1;
          if (this.wordSpans.length>idx)
            i = this.wordSpans[idx].getStart();
          return i;
        }

        public int getWordEnd(int idx) {
          int i = -1;
          if (this.wordSpans.length>idx)
            i = this.wordSpans[idx].getEnd();
          return i;
        }

        public String[] getPosTags() {
            return posTags;
        }

        public void setPosTags(String[] posTags) {
            this.posTags = posTags;
        }

        public Span[] getChunks() {
            return chunks;
        }

        public void setChunks(Span[] chunks) {
            this.chunks = chunks;
        }

        public String[] getChunkStrings() {
            return chunkStrings;
        }

        public void setChunkStrings(String[] chunkStrings) {
            this.chunkStrings = chunkStrings;
        }

        public List<Integer> getPhrasesIndex() {
            return nounphrases;
        }

        public String[] getNamedEntities() {
          return this.namedEntities;
        }

        public void setNamedEntity(int idxStart, int idxEnd, String type) {
          if (this.words.length==0)   // words/tokens must be extracted before Named Entities can be saved
            return;
          if (this.namedEntities.length==0)
            this.namedEntities = new String[this.words.length];
          for (int i=idxStart; i<idxEnd && i<this.words.length; i++)
            this.namedEntities[i] = type;
        }
    }
}
