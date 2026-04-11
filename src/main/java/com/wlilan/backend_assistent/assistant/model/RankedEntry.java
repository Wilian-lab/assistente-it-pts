package com.wlilan.backend_assistent.assistant.model;

public record RankedEntry(ItIndexEntry entry, double score, boolean matched) {
}
