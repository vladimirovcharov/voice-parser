package org.example.voiceparser.services;

import org.example.voiceparser.model.ParsedVoiceCommand;

public interface TranslateService {
    ParsedVoiceCommand translate(ParsedVoiceCommand original);
}
