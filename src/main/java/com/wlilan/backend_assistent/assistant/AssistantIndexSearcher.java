package com.wlilan.backend_assistent.assistant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.wlilan.backend_assistent.assistant.dto.AssistantAskRequest;
import com.wlilan.backend_assistent.assistant.model.AssistantIntent;
import com.wlilan.backend_assistent.assistant.model.ItIndex;
import com.wlilan.backend_assistent.assistant.model.ItIndexEntry;
import com.wlilan.backend_assistent.assistant.model.RankedEntry;
import com.wlilan.backend_assistent.it.ItEntity;

@Component
public class AssistantIndexSearcher {

  private static final Set<String> STOP_WORDS = Set.of(
      "a", "o", "e", "de", "do", "da", "dos", "das", "para", "por", "com", "sem",
      "em", "no", "na", "nos", "nas", "uma", "um", "as", "os", "que", "qual", "quais",
      "como", "sobre", "me", "traga", "mostrar", "mostre", "favor", "it", "its",
      "instrucao", "instrucoes", "tecnica", "tecnicas", "operacao", "operar");
  private static final Set<String> LOW_SIGNAL_SECTION_TITLES = Set.of(
      "referencias", "anexos", "definicoes", "historico de revisao", "registros");
  private static final Set<String> GENERIC_OPERATION_LABELS = Set.of(
      "monitorar itens", "consideracoes finais", "consideracoes", "resultados esperados");
  private static final Pattern CODE_LIKE_STEP_TITLE = Pattern.compile(
      "^(?:[a-z]{1,3}\\s*)?\\d[\\d./-]*(?:\\s+(?:e|item|itens?|zs|sensor|sensors?)\\s*[a-z0-9./-]+)*$",
      Pattern.CASE_INSENSITIVE);

  private final AssistantIntentDetector intentDetector;
  private final AssistantDocumentIndexService assistantDocumentIndexService;

  public AssistantIndexSearcher(
      AssistantIntentDetector intentDetector,
      AssistantDocumentIndexService assistantDocumentIndexService) {
    this.intentDetector = intentDetector;
    this.assistantDocumentIndexService = assistantDocumentIndexService;
  }

  public ItIndex loadIndex(ItEntity selectedIt) {
    return this.assistantDocumentIndexService.loadIndexFor(selectedIt);
  }

  public List<RankedEntry> findTopMatches(
      ItIndex index, ItEntity selectedIt,
      AssistantAskRequest request, AssistantIntent intent) {

    var retrievalText = buildRetrievalText(request);
    var normalizedQuestion = intentDetector.normalize(retrievalText);
    var keywords = extractKeywords(retrievalText);
    if (keywords.isEmpty())
      return List.of();

    var stepHint = intentDetector.extractStepHint(request.message());
    var searchableEntries = structuredEntries(index);
    var sourceEntries = intent == AssistantIntent.GENERAL
        ? index.entries
        : (searchableEntries.isEmpty() ? index.entries : searchableEntries);

    var rankedEntries = rankEntries(sourceEntries, keywords, stepHint, intent);
    if (rankedEntries.isEmpty() && sourceEntries != index.entries) {
      rankedEntries = rankEntries(index.entries, keywords, stepHint, intent);
    }

    return selectBestResults(rankedEntries, keywords, stepHint, normalizedQuestion);
  }

  public List<RankedEntry> findExactOptionMatches(ItIndex index, AssistantAskRequest request) {
    if (request.selectedStep() == null) {
      return List.of();
    }

    return findBestEntryForStep(index, request.selectedStep(), request.selectedPage(), request.selectedOptionTitle())
        .map(entry -> List.of(new RankedEntry(entry, 999d, true)))
        .orElseGet(List::of);
  }

  public java.util.Optional<ItIndexEntry> findBestEntryForStep(
      ItIndex index,
      Integer step,
      Integer preferredPage,
      String preferredTitle) {
    if (step == null) {
      return java.util.Optional.empty();
    }

    var normalizedPreferredTitle = intentDetector.normalize(firstNonBlank(preferredTitle, ""));

    return stepEntries(index, step).stream()
        .max(Comparator
            .<ItIndexEntry>comparingInt(entry -> scoreStepDisplayCandidate(entry, preferredPage, normalizedPreferredTitle))
            .thenComparing(entry -> entry.page == null ? Integer.MIN_VALUE : -entry.page));
  }

  public List<ItIndexEntry> stepEntries(ItIndex index, Integer step) {
    if (step == null) {
      return List.of();
    }

    return index.entries.stream()
        .filter(this::isStepEntry)
        .filter(entry -> step.equals(entry.step))
        .sorted(Comparator.comparing((ItIndexEntry entry) -> entry.page == null ? Integer.MAX_VALUE : entry.page))
        .toList();
  }

  public List<ItIndexEntry> structuredEntries(ItIndex index) {
    return index.entries.stream()
        .filter(this::isStructuredEntry)
        .toList();
  }

  private List<RankedEntry> selectBestResults(
      List<RankedEntry> ranked,
      List<String> keywords,
      Integer stepHint,
      String normalizedQuestion) {
    var distinctResults = limitDistinctResults(ranked);
    if (distinctResults.size() <= 1) {
      return distinctResults;
    }

    if (stepHint != null) {
      return List.of(distinctResults.get(0));
    }

    var top = distinctResults.get(0);
    if (isSpecificTopMatch(top, keywords, normalizedQuestion)) {
      return List.of(top);
    }

    if (isStructuredFocusedMatch(top, keywords, normalizedQuestion)) {
      return List.of(top);
    }

    var topEntryType = normalizedEntryType(top);
    if ("step".equals(topEntryType) || "anomaly".equals(topEntryType)) {
      var structuredResults = distinctResults.stream()
          .filter(candidate -> normalizedEntryType(candidate).equals(topEntryType))
          .filter(candidate -> candidate.score() >= top.score() * 0.45d)
          .limit(3)
          .toList();
      if (!structuredResults.isEmpty()) {
        if (structuredResults.size() == 1) {
          return structuredResults;
        }
        return structuredResults;
      }
    }

    var second = distinctResults.get(1);
    var broadQuery = keywords.size() <= 1;
    var strongDominance = top.score() >= (second.score() * 1.45);
    var exactWhatLead = hasText(top.entry().normalizedWhat)
        && keywords.stream().allMatch(keyword -> containsKeyword(top.entry().normalizedWhat, keyword));

    if (!broadQuery && (strongDominance || exactWhatLead)) {
      return List.of(top);
    }

    return distinctResults.stream()
        .filter(candidate -> candidate.score() >= top.score() * 0.35d)
        .limit(3)
        .toList();
  }

  private List<RankedEntry> limitDistinctResults(List<RankedEntry> ranked) {
    var selected = new ArrayList<RankedEntry>();
    var seen = new LinkedHashSet<String>();
    for (var r : ranked) {
      var e = r.entry();
      if (isGenericOperationBlock(e)) {
        var hasSpecificAlternative = ranked.stream()
            .anyMatch(candidate -> candidate != r
                && !"section".equals(normalizedEntryType(candidate))
                && !isGenericOperationBlock(candidate.entry())
                && candidate.score() >= r.score());
        if (hasSpecificAlternative) {
          continue;
        }
      }
      if ("section".equalsIgnoreCase(firstNonBlank(e.entryType, ""))
          && LOW_SIGNAL_SECTION_TITLES.contains(intentDetector.normalize(firstNonBlank(e.sectionTitle, e.what)))) {
        continue;
      }
      var key = intentDetector.normalize(firstNonBlank(e.documentCode, "-")) + "::"
          + (e.page == null ? "-" : e.page) + "::"
          + (e.step == null ? "-" : e.step) + "::"
          + firstNonBlank(e.entryType, "-");
      if (!seen.add(key))
        continue;
      selected.add(r);
      if (selected.size() == 3)
        break;
    }
    return selected;
  }

  private RankedEntry rankEntry(ItIndexEntry entry, List<String> keywords,
      Integer stepHint, AssistantIntent intent) {
    var haystack = intentDetector.normalize(firstNonBlank(entry.normalized, buildSearchText(entry)));
    var what = intentDetector.normalize(firstNonBlank(entry.normalizedWhat, entry.what));
    var how = intentDetector.normalize(firstNonBlank(entry.normalizedHow, entry.how));
    var care = intentDetector.normalize(firstNonBlank(entry.normalizedCare, entry.care));
    var causes = intentDetector.normalize(firstNonBlank(entry.possibleCauses, ""));
    var action = intentDetector.normalize(firstNonBlank(entry.actionText, ""));

    double score = 0;
    int hits = 0;

    for (var kw : keywords) {
      boolean hit = false;
      if (containsKeyword(what, kw)) {
        score += 9;
        hit = true;
      }
      if (containsKeyword(how, kw)) {
        score += 5;
        hit = true;
      }
      if (containsKeyword(care, kw)) {
        score += 3;
        hit = true;
      }
      if (containsKeyword(causes, kw)) {
        score += 6;
        hit = true;
      }
      if (containsKeyword(action, kw)) {
        score += 6;
        hit = true;
      }
      if (containsKeyword(haystack, kw)) {
        score += 2;
        hit = true;
      }
      if (hit)
        hits++;
    }

    if (stepHint != null && stepHint.equals(entry.step))
      score += 12;
    if ("step".equalsIgnoreCase(firstNonBlank(entry.entryType, "")))
      score += 1.5;
    if ("anomaly".equalsIgnoreCase(firstNonBlank(entry.entryType, "")))
      score += 1;
    if ("section".equalsIgnoreCase(firstNonBlank(entry.entryType, ""))
        && LOW_SIGNAL_SECTION_TITLES.contains(intentDetector.normalize(firstNonBlank(entry.sectionTitle, entry.what)))) {
      score -= 8;
    }
    if (isGenericOperationBlock(entry)) {
      score -= 5;
    }
    if ("step".equalsIgnoreCase(firstNonBlank(entry.entryType, ""))) {
      score += isMeaningfulStepTitle(entry) ? 4 : -9;
    }

    if (intent == AssistantIntent.OPERATION) {
      if ("step".equalsIgnoreCase(firstNonBlank(entry.entryType, "")))
        score += 8;
      if ("section".equalsIgnoreCase(firstNonBlank(entry.entryType, "")))
        score -= 1;
    }
    if (intent == AssistantIntent.ANOMALY) {
      if ("anomaly".equalsIgnoreCase(firstNonBlank(entry.entryType, "")))
        score += 10;
      if ("step".equalsIgnoreCase(firstNonBlank(entry.entryType, "")))
        score += 2;
    }

    return new RankedEntry(entry, score, hits > 0);
  }

  private List<RankedEntry> rankEntries(
      List<ItIndexEntry> entries,
      List<String> keywords,
      Integer stepHint,
      AssistantIntent intent) {
    return entries.stream()
        .map(entry -> rankEntry(entry, keywords, stepHint, intent))
        .filter(RankedEntry::matched)
        .sorted(Comparator.comparingDouble(RankedEntry::score).reversed()
            .thenComparing(r -> r.entry().page == null ? Integer.MAX_VALUE : r.entry().page))
        .collect(Collectors.toList());
  }

  private String buildRetrievalText(AssistantAskRequest request) {
    var builder = new StringBuilder(firstNonBlank(request.message(), ""));
    var includeHistory = this.intentDetector.isLowSignalFollowUp(request.message())
        && request.history() != null
        && !request.history().isEmpty();
    if (includeHistory) {
      var recentUserTurns = request.history().stream()
          .filter(turn -> turn != null && "user".equalsIgnoreCase(firstNonBlank(turn.role(), "")))
          .filter(turn -> hasText(turn.content()))
          .toList();

      var maxTurns = 3;
      for (int index = recentUserTurns.size() - 1, used = 0; index >= 0 && used < maxTurns; index -= 1) {
        var content = recentUserTurns.get(index).content().trim();
        if (content.equalsIgnoreCase(firstNonBlank(request.message(), ""))) {
          continue;
        }
        builder.append(' ').append(content);
        used += 1;
      }
    }
    if (hasText(request.selectedOptionTitle())) {
      builder.append(' ').append(request.selectedOptionTitle().trim());
    }
    return builder.toString().trim();
  }

  private List<String> extractKeywords(String message) {
    var expanded = new LinkedHashSet<String>();
    intentDetector.tokenize(message).stream()
        .filter(t -> t.length() >= 3 || "icm".equals(t))
        .filter(t -> !STOP_WORDS.contains(t))
        .forEach(token -> {
          expanded.add(token);
          if (token.endsWith("s") && token.length() > 4) {
            expanded.add(token.substring(0, token.length() - 1));
          }
          if (token.endsWith("es") && token.length() > 5) {
            expanded.add(token.substring(0, token.length() - 2));
          }
          if (token.endsWith("mento") && token.length() > 7) {
            expanded.add(token.substring(0, token.length() - 1));
          }
        });
    return expanded.stream().toList();
  }

  private String buildSearchText(ItIndexEntry e) {
    return String.join(" ",
        firstNonBlank(e.documentTitle, ""), firstNonBlank(e.sectionTitle, ""),
        firstNonBlank(e.what, ""), firstNonBlank(e.how, ""),
        firstNonBlank(e.care, ""), firstNonBlank(e.possibleCauses, ""),
        firstNonBlank(e.actionText, ""));
  }

  public String firstNonBlank(String... values) {
    for (var v : values) {
      if (hasText(v))
        return v.trim();
    }
    return "";
  }

  public boolean hasText(String value) {
    return value != null && !value.trim().isBlank();
  }

  private String normalizedEntryType(RankedEntry entry) {
    return firstNonBlank(entry.entry().entryType, "").trim().toLowerCase();
  }

  private boolean isSpecificTopMatch(RankedEntry top, List<String> keywords, String normalizedQuestion) {
    var what = firstNonBlank(top.entry().normalizedWhat, top.entry().what);
    if (!hasText(what)) {
      return false;
    }
    if (isGenericOperationBlock(top.entry())) {
      return false;
    }

    if (hasText(normalizedQuestion) && containsKeyword(what, normalizedQuestion)) {
      return true;
    }

    return keywords.size() >= 2
        && keywords.stream().allMatch(keyword -> containsKeyword(what, keyword));
  }

  private boolean isStructuredFocusedMatch(RankedEntry top, List<String> keywords, String normalizedQuestion) {
    var entryType = normalizedEntryType(top);
    if (!"step".equals(entryType) && !"anomaly".equals(entryType)) {
      return false;
    }

    var searchableText = intentDetector.normalize(String.join(" ",
        firstNonBlank(top.entry().normalizedWhat, top.entry().what),
        firstNonBlank(top.entry().normalizedHow, top.entry().how),
        firstNonBlank(top.entry().normalizedCare, top.entry().care),
        firstNonBlank(top.entry().possibleCauses, ""),
        firstNonBlank(top.entry().actionText, "")));

    if (hasText(normalizedQuestion) && containsKeyword(searchableText, normalizedQuestion)) {
      return true;
    }

    if (keywords.isEmpty()) {
      return false;
    }

    var matchedKeywords = keywords.stream()
        .filter(keyword -> containsKeyword(searchableText, keyword))
        .count();

    return matchedKeywords == keywords.size()
        || (keywords.size() >= 2 && matchedKeywords >= keywords.size() - 1);
  }

  private boolean containsKeyword(String text, String keyword) {
    var normalizedText = firstNonBlank(text, "");
    var normalizedKeyword = firstNonBlank(keyword, "");
    if (!hasText(normalizedText) || !hasText(normalizedKeyword)) {
      return false;
    }
    if (normalizedText.contains(normalizedKeyword)) {
      return true;
    }

    for (var token : normalizedText.split("\\s+")) {
      if (token.equals(normalizedKeyword)) {
        return true;
      }
      var sharedPrefix = sharedPrefixLength(token, normalizedKeyword);
      if (sharedPrefix >= Math.min(6, normalizedKeyword.length() - 1)) {
        return true;
      }
    }
    return false;
  }

  private int sharedPrefixLength(String left, String right) {
    int max = Math.min(left.length(), right.length());
    int count = 0;
    while (count < max && left.charAt(count) == right.charAt(count)) {
      count += 1;
    }
    return count;
  }

  private boolean isGenericOperationBlock(ItIndexEntry entry) {
    var normalizedWhat = intentDetector.normalize(firstNonBlank(entry.normalizedWhat, entry.what));
    if (!hasText(normalizedWhat)) {
      return false;
    }
    if (GENERIC_OPERATION_LABELS.stream().anyMatch(normalizedWhat::startsWith)) {
      return true;
    }
    return normalizedWhat.contains("monitorar os seguintes parametros")
        || normalizedWhat.contains("para operar com o processo")
        || normalizedWhat.contains("necessario monitorar")
        || normalizedWhat.contains("é necessario monitorar");
  }

  public boolean isMeaningfulStepTitle(ItIndexEntry entry) {
    var normalizedWhat = intentDetector.normalize(firstNonBlank(entry.normalizedWhat, entry.what));
    if (!hasText(normalizedWhat)) {
      return false;
    }
    if (isGenericOperationBlock(entry) || looksLikeNoiseStepTitle(normalizedWhat)) {
      return false;
    }

    return tokenizeTitle(normalizedWhat).stream()
        .anyMatch(token -> token.length() >= 4 && token.chars().anyMatch(Character::isLetter));
  }

  private boolean looksLikeNoiseStepTitle(String normalizedWhat) {
    var value = firstNonBlank(normalizedWhat, "");
    if (!hasText(value)) {
      return true;
    }
    if (CODE_LIKE_STEP_TITLE.matcher(value).matches()) {
      return true;
    }
    if (value.startsWith("zs ") || value.startsWith("e ") || value.startsWith("item ")) {
      return true;
    }
    var tokens = tokenizeTitle(value);
    var digitHeavyTokens = tokens.stream()
        .filter(token -> token.chars().anyMatch(Character::isDigit))
        .count();
    return !tokens.isEmpty() && digitHeavyTokens >= Math.max(1, tokens.size() - 1);
  }

  private List<String> tokenizeTitle(String value) {
    return List.of(firstNonBlank(value, "").split("\\s+")).stream()
        .map(String::trim)
        .filter(this::hasText)
        .toList();
  }

  private boolean isStructuredEntry(ItIndexEntry entry) {
    var entryType = firstNonBlank(entry.entryType, "").toLowerCase();
    return "step".equals(entryType) || "anomaly".equals(entryType);
  }

  private boolean isStepEntry(ItIndexEntry entry) {
    return "step".equalsIgnoreCase(firstNonBlank(entry.entryType, "")) && entry.step != null;
  }

  private int scoreStepDisplayCandidate(ItIndexEntry entry, Integer preferredPage, String normalizedPreferredTitle) {
    int score = 0;
    if (matchesExactPage(entry, preferredPage)) {
      score += 30;
    }
    if (matchesExactTitle(entry, normalizedPreferredTitle)) {
      score += 12;
    }
    if (isMeaningfulStepTitle(entry)) {
      score += 25;
    }
    if (hasText(entry.how)) {
      score += 8;
    }
    if (hasText(entry.care)) {
      score += 5;
    }
    if (isGenericOperationBlock(entry)) {
      score -= 15;
    }
    if (looksLikeNoiseStepTitle(intentDetector.normalize(firstNonBlank(entry.normalizedWhat, entry.what)))) {
      score -= 25;
    }
    return score;
  }

  private boolean matchesExactPage(ItIndexEntry entry, Integer selectedPage) {
    return selectedPage != null && entry.page != null && selectedPage.equals(entry.page);
  }

  private boolean matchesExactTitle(ItIndexEntry entry, String normalizedOptionTitle) {
    if (!hasText(normalizedOptionTitle)) {
      return false;
    }

    var normalizedWhat = intentDetector.normalize(firstNonBlank(entry.normalizedWhat, entry.what));
    return hasText(normalizedWhat)
        && (normalizedWhat.equals(normalizedOptionTitle)
            || normalizedWhat.contains(normalizedOptionTitle)
            || normalizedOptionTitle.contains(normalizedWhat));
  }
}
