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
        current = new Profile(null, section);
      }
      profiles.put(line, current);
      observe(line, current);
      if (line.endsWithPeriod()) {
        select = false;
      }
    }
  }

  /** Choose a learned column first, then a learned gap; leave ambiguous spacing unchanged. */
  String gap(
      SourceLine line,
      int word,
      int renderedEnd,
      int wordWidth,
      int fallbackColumn,
      String fallback) {
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
    String key = indentation + gapKey(line, word);
    if (anchor == null && followingClause(line, word) != null) {
      Integer end = profile.operandEnd(key, renderedEnd);
      if (end != null && end - wordWidth > renderedEnd) {
        return spaces(end - wordWidth - renderedEnd);
      }
    }
    // Without a reliable column, padded clause boundaries are not ordinary operand gaps.
    Integer gap = anchor == null ? profile.gap(key) : null;
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
          String key = indentation + gapKey(line, i);
          profile.observeGap(key, column - offset);
          if (anchor(line, i - 1) != null && followingClause(line, i) != null) {
            profile.observeOperandEnd(
                key, offset, column + word.getText().length(), word.getText().length());
          }
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
      String next = followingClause(line, index);
      return "AFTER:" + previous + (next == null ? "" : ":BEFORE:" + next);
    }
    return line.upper(0) + ":" + index;
  }

  /** Keep operand spacing separate when another data clause follows it. */
  private static String followingClause(SourceLine line, int index) {
    if (line.getMode() != SourceLine.Mode.ENTRY || index + 1 >= line.getWords().size()) {
      return null;
    }
    Word next = line.getWords().get(index + 1);
    if (next.isLiteral() || !Keywords.isDataClause(next.upper())) {
      return null;
    }
    String nextAnchor = anchor(line, index + 1);
    return nextAnchor == null ? next.upper() : nextAnchor;
  }

  private static final class Profile {
    private final Profile parent;
    private final Profile clauseGaps;
    private final Map<String, Column> columns = new HashMap<>();
    private final Map<String, Votes> gaps = new HashMap<>();
    private final Map<String, Column> operandEnds = new HashMap<>();

    private Profile(Profile parent) {
      this(parent, null);
    }

    private Profile(Profile parent, Profile clauseGaps) {
      this.parent = parent;
      this.clauseGaps = clauseGaps;
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
      if (clauseGaps != null && key.contains(":AFTER:")) {
        clauseGaps.observeGap(key, gap);
      }
    }

    private void observeOperandEnd(String key, int clauseEnd, int end, int width) {
      // Different operand widths must support the same right edge before it is an alignment rule.
      operandEnds
          .computeIfAbsent(key + ":" + clauseEnd, ignored -> new Column())
          .add(end, width, 0);
      if (parent != null) {
        parent.observeOperandEnd(key, clauseEnd, end, width);
      }
      if (clauseGaps != null) {
        clauseGaps.observeOperandEnd(key, clauseEnd, end, width);
      }
    }

    private Integer operandEnd(String key, int clauseEnd) {
      Column column = operandEnds.get(key + ":" + clauseEnd);
      Integer value = column == null ? null : column.preferred();
      Votes localGaps = gaps.get(key);
      if (value != null || (localGaps != null && localGaps.preferred() != null)) {
        return value;
      }
      if (parent != null) {
        return parent.operandEnd(key, clauseEnd);
      }
      return clauseGaps == null ? null : clauseGaps.operandEnd(key, clauseEnd);
    }

    private Integer column(String key) {
      Column column = columns.get(key);
      Integer value = column == null ? null : column.preferred();
      return value != null || parent == null ? value : parent.column(key);
    }

    private Integer gap(String key) {
      Votes votes = gaps.get(key);
      Integer value = votes == null ? null : votes.preferred();
      if (value != null || parent != null) {
        return value != null ? value : parent.gap(key);
      }
      // Small records can borrow clause operand spacing without borrowing alignment columns.
      return clauseGaps != null && key.contains(":AFTER:") ? clauseGaps.gap(key) : null;
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
