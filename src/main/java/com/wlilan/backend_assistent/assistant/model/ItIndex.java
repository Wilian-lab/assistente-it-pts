package com.wlilan.backend_assistent.assistant.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ItIndex {
  public List<ItIndexEntry> entries = List.of();
}
