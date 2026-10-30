/*
 * Copyright (c) 2026 Broadcom.
 * The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Broadcom - initial API and implementation
 *
 */
package org.eclipse.lsp.cobol.service.delegates.formations.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Learns layout preferences from a document without storing or changing user configuration. */
final class LayoutStyleDetector {
  private static final Pattern SOURCE_DIRECTIVE =
      Pattern.compile(
          "^>>SOURCE\\s+(?:FORMAT\\s+)?(?:IS\\s+)?(FREE|FIXED)\\b", Pattern.CASE_INSENSITIVE);
  private static final int MAX_INDENT = 16;

  private final LayoutSettings defaults;
  private final Votes quotes = new Votes();
  private final Votes tabs = new Votes();
  private final Votes periods = new Votes();
  private final Votes punctuation = new Votes();
  private final Alignment pictures = new Alignment();
  private final Alignment values = new Alignment();
  private final Alignment usages = new Alignment();
  private final int[] indents = new int[MAX_INDENT + 1];
  private final int[] procedureIndents = new int[MAX_INDENT + 1];
  private final int[] dataIndents = new int[MAX_INDENT + 1];
  private final Deque<int[]> levels = new ArrayDeque<>();
  private List<Word> previous = new ArrayList<>();
  private int previousIndent;
  private boolean procedure;
  private boolean procedureHeader;
  private boolean inExec;
  private boolean inCopy;

  private LayoutStyleDetector(LayoutSettings defaults) {
    this.defaults = defaults;
  }

  /** Infer only preferences for which the source supplies evidence; keep defaults on ties. */
  static LayoutSettings detect(List<String> source, LayoutSettings defaults) {
    LayoutStyleDetector detector = new LayoutStyleDetector(defaults);
    boolean fixed = defaults.getSourceFormat() == LayoutSettings.SourceFormat.FIXED;
    boolean continuedFreeLiteral = false;
    for (int i = 0; i < source.size(); i++) {
      String text = source.get(i);
      if (fixed && (text.length() < 7 || " Dd".indexOf(text.charAt(6)) < 0)) {
        continue;
      }
      String content = fixed ? text.substring(7, Math.min(72, text.length())) : text;
      String trimmed = content.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("*>") || trimmed.startsWith(">>")) {
        continue;
      }
      LineLexer.Result lexed = LineLexer.lex(content);
      List<Word> words = lexed.getWords();
      if (words.isEmpty()) {
        continue;
      }
      Word last = words.get(words.size() - 1);
      boolean continuesFree =
          !fixed && (lexed.isUnterminated() || (last.isLiteral() && last.getText().endsWith("-")));
      if (continuedFreeLiteral) {
        continuedFreeLiteral = continuesFree;
        detector.previous.clear();
        continue;
      }
      continuedFreeLiteral = continuesFree;
      String first = words.get(0).upper();
      if ("EXEC".equals(first)) {
        detector.inExec = true;
      }
      if ("COPY".equals(first) || "REPLACE".equals(first)) {
        detector.inCopy = true;
      }
      if (detector.inExec || detector.inCopy) {
        detector.inExec =
            detector.inExec && words.stream().noneMatch(w -> "END-EXEC".equals(w.upper()));
        detector.inCopy = detector.inCopy && !words.get(words.size() - 1).endsWithPeriod();
        detector.previous.clear();
        continue;
      }
      boolean continued =
          fixed
              && i + 1 < source.size()
              && source.get(i + 1).length() > 6
              && source.get(i + 1).charAt(6) == '-';
      if (lexed.isUnterminated()
          || continuesFree
          || continued
          || Keywords.LISTING_DIRECTIVES.contains(first)
          || "CBL".equals(first)
          || "PROCESS".equals(first)) {
        detector.previous.clear();
        continue;
      }
      detector.observe(content, words, fixed);
    }
    long padded = source.stream().filter(line -> line.length() == 80).count();
    return defaults.toBuilder()
        .preserveSpacing(!source.isEmpty())
        .preserveIndentation(!source.isEmpty())
        .preserveDataAlignment(!source.isEmpty())
        .preserveFixedPadding(fixed && !source.isEmpty() && padded * 4 >= source.size() * 3L)
        .singleQuote(detector.quotes.choose(defaults.isSingleQuote()))
        .useTabs(!fixed && detector.tabs.choose(defaults.isUseTabs()))
        .indentWidth(
            detector.indentWidth(
                detector.dataIndents,
                detector.indentWidth(detector.indents, defaults.getIndentWidth())))
        .procedureIndentWidth(detector.indentWidth(detector.procedureIndents, -1))
        .alignPicClauses(detector.pictures.votes.choose(defaults.isAlignPicClauses()))
        .alignValueClauses(detector.values.votes.choose(defaults.isAlignValueClauses()))
        .alignUsageClauses(detector.usages.votes.choose(defaults.isAlignUsageClauses()))
        .standalonePeriods(detector.periods.choose(defaults.isStandalonePeriods()))
        .spaceBeforePunctuation(detector.punctuation.choose(defaults.isSpaceBeforePunctuation()))
        .build();
  }

  /** Detect reference format before examining columns or tokenizing the document. */
  static LayoutSettings.SourceFormat sourceFormat(List<String> source) {
    boolean freeCode = false;
    boolean numbered = false;
    for (String text : source) {
      String trimmed = text.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("*>")) {
        continue;
      }
      boolean prefix = text.length() >= 7 && text.substring(0, 6).matches("[ 0-9]{6}");
      if (prefix && "*/".indexOf(text.charAt(6)) >= 0) {
        continue;
      }
      Matcher directive = SOURCE_DIRECTIVE.matcher(trimmed);
      boolean found = directive.find();
      if (!found && prefix) {
        directive = SOURCE_DIRECTIVE.matcher(text.substring(7).trim());
        found = directive.find();
      }
      if (found) {
        return "FREE".equalsIgnoreCase(directive.group(1))
            ? LayoutSettings.SourceFormat.FREE
            : LayoutSettings.SourceFormat.FIXED;
      }
      if (trimmed.startsWith(">>")
          || trimmed.startsWith("CBL ")
          || trimmed.startsWith("PROCESS ")) {
        continue;
      }
      boolean fixedLine = prefix && " Dd-".indexOf(text.charAt(6)) >= 0;
      freeCode |= !fixedLine;
      numbered |= fixedLine && !text.substring(0, 6).trim().isEmpty();
    }
    return numbered || !freeCode
        ? LayoutSettings.SourceFormat.FIXED
        : LayoutSettings.SourceFormat.FREE;
  }

  private void observe(String content, List<Word> words, boolean fixed) {
    String first = words.get(0).upper();
    String second = words.size() > 1 ? words.get(1).upper() : "";
    if ("DIVISION".equals(second)) {
      procedure = "PROCEDURE".equals(first);
      procedureHeader = procedure;
      resetRecord();
      previous.clear();
      return;
    }
    if ("SECTION".equals(second)
        || (words.size() == 1 && words.get(0).endsWithPeriod() && Keywords.isUserWord(first))) {
      resetRecord();
      previous.clear();
      return;
    }
    int indent = 0;
    while (indent < content.length() && Character.isWhitespace(content.charAt(indent))) {
      indent++;
    }
    boolean hasTabs = content.substring(0, indent).indexOf('\t') >= 0;
    if (indent > 0) {
      tabs.add(hasTabs);
    }
    int level = Keywords.levelNumber(words.get(0));
    if (level > 0) {
      procedure = false;
      if (level == 1 || level == 77) {
        resetRecord();
      }
      while (!levels.isEmpty() && levels.peek()[0] >= level) {
        levels.pop();
      }
      if (!hasTabs && !levels.isEmpty()) {
        indent(indent - levels.peek()[1], dataIndents);
      }
      levels.push(new int[] {level, indent});
      alignment(content, words);
    } else if (Keywords.VERBS.contains(first)) {
      procedure = true;
      if (!hasTabs && !previous.isEmpty() && opensBody(previous)) {
        indent(indent - previousIndent, procedureIndents);
      } else if (!fixed && !hasTabs && procedureHeader) {
        indent(indent, procedureIndents);
      }
      procedureHeader = false;
    }
    boolean periodOnly =
        words.size() == 1 && words.get(0).isPunctuation() && words.get(0).endsWithPeriod();
    if (procedure && words.get(words.size() - 1).endsWithPeriod()) {
      periods.add(periodOnly);
    }
    if (!periodOnly) {
      for (Word word : words) {
        if (!word.getSeparator().isEmpty()) {
          punctuation.add(word.isPunctuation());
        }
        if (word.isLiteral()) {
          observeQuotes(word.getText());
        }
      }
    }
    previous = new ArrayList<>(words);
    previousIndent = hasTabs ? -MAX_INDENT : indent;
  }

  private static boolean opensBody(List<Word> words) {
    String first = words.get(0).upper();
    if (words.get(words.size() - 1).endsWithPeriod()) {
      return false;
    }
    return "IF".equals(first)
        || "ELSE".equals(first)
        || "WHEN".equals(first)
        || "EVALUATE".equals(first)
        || "SEARCH".equals(first)
        || ("PERFORM".equals(first)
            && words.stream()
                .skip(1)
                .anyMatch(w -> Keywords.INLINE_PERFORM_OPTIONS.contains(w.upper())));
  }

  private void indent(int width, int[] group) {
    if (width > 0 && width <= MAX_INDENT) {
      indents[width]++;
      group[width]++;
    }
  }

  private int indentWidth(int[] votes, int fallback) {
    int best = 0;
    for (int width = 1; width <= MAX_INDENT; width++) {
      if (votes[width] > votes[best]) {
        best = width;
      }
    }
    for (int width = 1; width <= MAX_INDENT; width++) {
      if (width != best && votes[width] == votes[best]) {
        return fallback;
      }
    }
    return best;
  }

  private void observeQuotes(String text) {
    for (int i = 0; i < text.length(); i++) {
      char quote = text.charAt(i);
      if (quote != '\'' && quote != '"') {
        continue;
      }
      quotes.add(quote == '\'');
      while (++i < text.length()) {
        if (text.charAt(i) == quote) {
          if (i + 1 < text.length() && text.charAt(i + 1) == quote) {
            i++;
          } else {
            break;
          }
        }
      }
    }
  }

  private void resetRecord() {
    levels.clear();
    pictures.reset();
    values.reset();
    usages.reset();
  }

  private void alignment(String content, List<Word> words) {
    int offset = 0;
    boolean picture = false;
    boolean picSeen = false;
    boolean valueSeen = false;
    boolean usageSeen = false;
    for (int i = 0; i < words.size(); i++) {
      Word word = words.get(i);
      String raw = word.getText() + word.getSeparator();
      int column = content.indexOf(raw, offset);
      int gap = column - offset;
      offset = column + raw.length();
      String upper = word.upper();
      if (picture) {
        picture = "IS".equals(upper);
        continue;
      }
      if (i < 2 || word.isLiteral()) {
        continue;
      }
      if (Keywords.PICTURE.contains(upper) && !picSeen) {
        pictures.add(column, column - gap);
        picSeen = true;
        picture = true;
      } else if (Keywords.VALUE.contains(upper) && !valueSeen) {
        values.add(column, column - gap);
        valueSeen = true;
      } else if (Keywords.USAGE.contains(upper) && !usageSeen && !valueSeen) {
        usages.add(column, column - gap);
        usageSeen = true;
      }
    }
  }

  /** Ignore coincidental alignment between entries whose prefixes have the same length. */
  private static final class Alignment {
    private final Map<Integer, Integer> columns = new HashMap<>();
    private final Map<Integer, Integer> prefixes = new HashMap<>();
    private final Map<String, Integer> pairs = new HashMap<>();
    private final Votes votes = new Votes();
    private int count;

    private void add(int column, int prefix) {
      String pair = column + ":" + prefix;
      int comparable = count - prefixes.getOrDefault(prefix, 0);
      int aligned = columns.getOrDefault(column, 0) - pairs.getOrDefault(pair, 0);
      votes.yes += aligned;
      votes.no += comparable - aligned;
      columns.merge(column, 1, Integer::sum);
      prefixes.merge(prefix, 1, Integer::sum);
      pairs.merge(pair, 1, Integer::sum);
      count++;
    }

    private void reset() {
      columns.clear();
      prefixes.clear();
      pairs.clear();
      count = 0;
    }
  }

  private static final class Votes {
    private long yes;
    private long no;

    private void add(boolean value) {
      if (value) {
        yes++;
      } else {
        no++;
      }
    }

    private boolean choose(boolean fallback) {
      return yes == no ? fallback : yes > no;
    }
  }
}
