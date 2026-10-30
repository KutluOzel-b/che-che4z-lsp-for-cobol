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

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Learns horizontal layout within sections, records and paragraphs, then corrects outliers. */
final class HorizontalLayout {
  private final Map<SourceLine, Profile> profiles = new IdentityHashMap<>();

  HorizontalLayout(List<SourceLine> lines) {
    Profile section = new Profile(null);
    Profile current = section;
    boolean select = false;
    for (SourceLine line : lines) {
      if (line.isHeader()) {
        section = new Profile(null);
        current = section;
        select = false;
      }
      if (line.getKind() == SourceLine.Kind.BLANK && !select) {
        current = new Profile(null);
      }
      if (line.getKind() != SourceLine.Kind.CODE
          || line.isFrozen()
          || (line.getMode() != SourceLine.Mode.TOKENS
              && line.getMode() != SourceLine.Mode.ENTRY)) {
        continue;
      }
      String first = line.upper(0);
      if ("SELECT".equals(first)) {
        current = new Profile(section);
        select = true;
      } else if (Keywords.FILE_DESCRIPTIONS.contains(first)
          || Keywords.levelNumber(line.getWords().get(0)) == 1) {
        current = new Profile(null);
      }
      profiles.put(line, current);
      observe(line, current);
      if (line.endsWithPeriod()) {
        select = false;
      }
    }
  }

  /** Choose a learned column first, then a learned gap; leave ambiguous spacing unchanged. */
  String gap(SourceLine line, int word, int renderedEnd, int fallbackColumn, String fallback) {
    Profile profile = profiles.get(line);
    if (profile == null || word == 0 || fallback.indexOf('\t') >= 0) {
      return fallback;
    }
    String anchor = anchor(line, word);
    String indentation = line.originalIndent() + ":";
    Integer column = anchor == null ? null : profile.column(indentation + anchor);
    if (column != null && fallback.length() > 1 && Math.abs(column - fallbackColumn) <= 1) {
      column = fallbackColumn;
    }
    if (column != null && column > renderedEnd) {
      return spaces(column - renderedEnd);
    }
    // Without a reliable column, padded clause boundaries are not ordinary operand gaps.
    Integer gap = anchor == null ? profile.gap(indentation + gapKey(line, word)) : null;
    if (gap != null) {
      return spaces(gap);
    }
    // Preserve a padded token's column when an earlier learned gap or quote width changed.
    return fallback.length() > 1 ? spaces(Math.max(1, fallbackColumn - renderedEnd)) : fallback;
  }

  private static String spaces(int count) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < count; i++) {
      result.append(' ');
    }
    return result.toString();
  }

  private static void observe(SourceLine line, Profile profile) {
    String content = line.getContent();
    int offset = 0;
    List<Word> words = line.getWords();
    for (int i = 0; i < words.size(); i++) {
      Word word = words.get(i);
      int column = content.indexOf(word.getText() + word.getSeparator(), offset);
      if (column < offset) {
        return;
      }
      if (i > 0 && !word.isPunctuation() && content.substring(offset, column).indexOf('\t') < 0) {
        String anchor = anchor(line, i);
        String indentation = line.originalIndent() + ":";
        if (anchor != null) {
          profile.observeColumn(indentation + anchor, column, offset, column - offset);
        } else {
          profile.observeGap(indentation + gapKey(line, i), column - offset);
        }
      }
      offset = column + word.getText().length() + word.getSeparator().length();
    }
  }

  private static String anchor(SourceLine line, int index) {
    Word word = line.getWords().get(index);
    if (word.isLiteral() || index == 0) {
      return null;
    }
    String upper = word.upper();
    if ("IS".equals(upper) || "TO".equals(upper)) {
      return line.isProcedure() ? line.upper(0) + ":" + upper : upper;
    }
    if (line.getMode() == SourceLine.Mode.ENTRY) {
      if (Keywords.PICTURE.contains(upper)) {
        return "PICTURE";
      }
      if (Keywords.VALUE.contains(upper)) {
        return "VALUE";
      }
      if (Keywords.USAGE.contains(upper)) {
        return "USAGE";
      }
    }
    return null;
  }

  private static String gapKey(SourceLine line, int index) {
    String previous = anchor(line, index - 1);
    if (previous != null) {
      return "AFTER:" + previous;
    }
    return line.upper(0) + ":" + index;
  }

  private static final class Profile {
    private final Profile parent;
    private final Map<String, Column> columns = new HashMap<>();
    private final Map<String, Votes> gaps = new HashMap<>();

    private Profile(Profile parent) {
      this.parent = parent;
    }

    private void observeColumn(String key, int at, int prefix, int gap) {
      columns.computeIfAbsent(key, ignored -> new Column()).add(at, prefix, gap);
      if (parent != null) {
        parent.observeColumn(key, at, prefix, gap);
      }
    }

    private void observeGap(String key, int gap) {
      gaps.computeIfAbsent(key, ignored -> new Votes()).add(gap);
      if (parent != null) {
        parent.observeGap(key, gap);
      }
    }

    private Integer column(String key) {
      Column column = columns.get(key);
      Integer value = column == null ? null : column.preferred();
      return value != null || parent == null ? value : parent.column(key);
    }

    private Integer gap(String key) {
      Votes votes = gaps.get(key);
      Integer value = votes == null ? null : votes.preferred();
      return value != null || parent == null ? value : parent.gap(key);
    }
  }

  /**
   * A column is an alignment preference only when padding or different prefix widths support it.
   */
  private static final class Column {
    private final Votes positions = new Votes();
    private final Map<Integer, Integer> firstPrefix = new HashMap<>();
    private final Map<Integer, Boolean> aligned = new HashMap<>();

    private void add(int at, int prefix, int gap) {
      positions.add(at);
      Integer previous = firstPrefix.putIfAbsent(at, prefix);
      if (gap > 1 || (previous != null && previous != prefix)) {
        aligned.put(at, true);
      }
    }

    private Integer preferred() {
      Integer at = positions.preferred();
      return at != null && aligned.containsKey(at) ? at : null;
    }
  }

  /** Require two supporting observations and a strict majority. */
  private static final class Votes {
    private final Map<Integer, Integer> counts = new HashMap<>();
    private int total;
    private int best;

    private void add(int value) {
      int count = counts.merge(value, 1, Integer::sum);
      total++;
      if (count > counts.getOrDefault(best, 0)) {
        best = value;
      }
    }

    private Integer preferred() {
      int count = counts.getOrDefault(best, 0);
      return count >= 2 && count > total / 2 ? best : null;
    }
  }
}
