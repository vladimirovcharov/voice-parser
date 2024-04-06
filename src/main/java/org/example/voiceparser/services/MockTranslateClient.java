package org.example.voiceparser.services;

import org.example.voiceparser.model.ParsedVoiceCommand;

public class MockTranslateClient implements TranslateService {

    public ParsedVoiceCommand translate(ParsedVoiceCommand original) {
        return ParsedVoiceCommand.builder()
                .id(original.getId())
                .text("call Juan")
                .probability(original.getProbability())
                .language(original.getLanguage())
                .build();
    }
}
