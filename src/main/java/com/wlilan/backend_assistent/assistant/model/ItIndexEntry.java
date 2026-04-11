package com.wlilan.backend_assistent.assistant.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ItIndexEntry {
  @JsonProperty("document_code")
  public String documentCode;
  @JsonProperty("document_title")
  public String documentTitle;
  public String author;
  public String authorizer;
  @JsonProperty("print_date")
  public String printDate;
  @JsonProperty("create_date")
  public String createDate;
  @JsonProperty("file_path")
  public String filePath;
  public Integer page;
  public Integer step;
  @JsonProperty("section_number")
  public Integer sectionNumber;
  @JsonProperty("section_title")
  public String sectionTitle;
  @JsonProperty("entry_type")
  public String entryType;
  public String what;
  public String how;
  public String care;
  @JsonProperty("possible_causes")
  public String possibleCauses;
  @JsonProperty("action_text")
  public String actionText;
  public String normalized;
  @JsonProperty("normalized_what")
  public String normalizedWhat;
  @JsonProperty("normalized_how")
  public String normalizedHow;
  @JsonProperty("normalized_care")
  public String normalizedCare;
}
