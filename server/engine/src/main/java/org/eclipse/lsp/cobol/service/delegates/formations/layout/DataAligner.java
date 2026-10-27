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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * Lays out data description entries. Entries of the same record are aligned so that their
 * PICTURE, USAGE and VALUE clauses start in the same columns.
 *
 * <p>An entry is split into cells: the level number, the name and the clauses. The line breaks of
 * the source are kept; a line which does not fit into the content area is broken between clauses.
 * The alignment columns are computed from all the words of the entries, so formatting the result
 * again does not change it.
 */
@RequiredArgsConstructor
final class DataAligner {
  /** A data description entry: its first line and the lines it continues on. */
  @RequiredArgsConstructor
  static final class Entry {
    private final SourceLine first;
    private final int depth;
    private final List<SourceLine> continuations = new ArrayList<>();

    void addContinuation(SourceLine line) {
      continuations.add(line);
    }

    private List<Word> words() {
      List<Word> words = new ArrayList<>(first.getWords());
      continuations.forEach(line -> words.addAll(line.getWords()));
      return words;
    }
  }

  private enum Kind {
    LEVEL,
    NAME,
    PICTURE,
    USAGE,
    VALUE,
    OTHER,
    /** Values of a VALUE clause continued from the previous line. */
    ITEMS
  }

  private static final Kind[] ALIGNED = {Kind.PICTURE, Kind.USAGE, Kind.VALUE};

  /** Words of one clause, rendered as a whole. */
  @RequiredArgsConstructor
  private static final class Cell {
    private final Kind kind;
    private final List<Word> words = new ArrayList<>();
  }

  /** Cells placed on a line. */
  @RequiredArgsConstructor
  private static final class Placed {
    private final List<Cell> cells;
    private final int[] columns;
    private final int end;
  }

  private final LayoutSettings settings;
  private final Renderer renderer;
  private final int areaB;
  private final boolean fixed;

  private Map<Kind, Integer> targets;
  private int clauseColumn;
  private int valueColumn;

  /**
   * Render the entries of a group, setting the rendered lines of their source lines.
   *
   * @param group entries to align with each other
   */
  void align(List<Entry> group) {
    targets = new EnumMap<>(Kind.class);
    List<List<Cell>> cells = new ArrayList<>();
    for (Entry entry : group) {
      cells.add(split(entry.words(), true));
    }
    for (Kind kind : ALIGNED) {
      int target = -1;
      for (int i = 0; i < group.size(); i++) {
        if (group.get(i).first.isFrozen()) {
          continue;
        }
        Placed placed = place(start(group.get(i)), cells.get(i), targets);
        for (int c = 0; c < placed.cells.size(); c++) {
          if (placed.cells.get(c).kind == kind) {
            target = Math.max(target, placed.columns[c]);
            break;
          }
        }
      }
      if (target >= 0) {
        targets.put(kind, target);
      }
    }
    group.forEach(this::render);
  }

  private int start(Entry entry) {
    return entry.depth * settings.getIndentWidth();
  }

  private boolean spaced() {
    return settings.isSpaceBeforePunctuation();
  }

  private Kind clauseKind(String upper) {
    if (Keywords.PICTURE.contains(upper)) {
      return settings.isAlignPicClauses() ? Kind.PICTURE : Kind.OTHER;
    }
    if (Keywords.USAGE.contains(upper)) {
      return settings.isAlignUsageClauses() ? Kind.USAGE : Kind.OTHER;
    }
    if (Keywords.VALUE.contains(upper)) {
      return settings.isAlignValueClauses() ? Kind.VALUE : Kind.OTHER;
    }
    return Keywords.OTHER_DATA_CLAUSES.contains(upper) ? Kind.OTHER : null;
  }

  /** Split words into cells. The first line of an entry starts with the level number and name. */
  private List<Cell> split(List<Word> words, boolean head) {
    List<Cell> cells = new ArrayList<>();
    Cell current = null;
    boolean picture = false;
    for (int i = 0; i < words.size(); i++) {
      Word word = words.get(i);
      String upper = word.upper();
      if (picture) {
        current.words.add(word);
        picture = "IS".equals(upper);
        continue;
      }
      Kind kind;
      if (head && i == 0) {
        kind = Kind.LEVEL;
      } else if (head && i == 1 && !word.isPunctuation() && !Keywords.isDataClause(upper)) {
        kind = Kind.NAME;
      } else {
        kind = clauseKind(upper);
      }
      if (current == null || (kind != null && kind != current.kind)) {
        current = new Cell(kind == null ? Kind.ITEMS : kind);
        cells.add(current);
      }
      current.words.add(word);
      picture = kind == Kind.PICTURE;
    }
    return cells;
  }

  private String text(Cell cell) {
    return renderer.join(cell.words, spaced());
  }

  /** Place the cells on a line, the first one exactly at the column. */
  private Placed place(int column, List<Cell> cells, Map<Kind, Integer> columns) {
    int[] placed = new int[cells.size()];
    int position = column;
    for (int c = 0; c < cells.size(); c++) {
      Cell cell = cells.get(c);
      int at = column;
      if (c > 0) {
        at = position + 1;
        if (cell.kind == Kind.NAME && fixed) {
          at = Math.max(at, areaB);
        }
        Integer target = columns.get(cell.kind);
        at = target == null ? at : Math.max(at, target);
      }
      placed[c] = at;
      position = at + text(cell).length();
    }
    return new Placed(cells, placed, position);
  }

  private boolean fits(Placed placed, String comment) {
    int end = placed.end + (comment == null ? 0 : comment.length() + 1);
    return end <= renderer.width();
  }

  private String text(Placed placed, String comment) {
    int start = placed.columns[0];
    StringBuilder text = new StringBuilder();
    for (int c = 0; c < placed.cells.size(); c++) {
      text.append(StringUtils.repeat(' ', placed.columns[c] - start - text.length()));
      text.append(text(placed.cells.get(c)));
    }
    if (comment != null) {
      text.append(' ').append(comment);
    }
    for (int c = 0; c < placed.cells.size(); c++) {
      Cell cell = placed.cells.get(c);
      int offset = cell.kind == Kind.VALUE ? valueOffset(cell.words) : -1;
      if (offset >= 0) {
        valueColumn = placed.columns[c] + offset;
      }
    }
    return renderer.indent(start) + text;
  }

  private void render(Entry entry) {
    int start = start(entry);
    clauseColumn = Math.max(start + settings.getIndentWidth(), fixed ? areaB : 0);
    valueColumn = -1;
    SourceLine first = entry.first;
    if (first.isFrozen()) {
      first.setMode(SourceLine.Mode.VERBATIM);
    } else {
      first.setRendered(first(start, split(first.getWords(), true), first));
    }
    for (SourceLine line : entry.continuations) {
      if (line.isFrozen()) {
        line.setMode(SourceLine.Mode.VERBATIM);
      } else {
        line.setRendered(flow(split(line.getWords(), false), line.getComment()));
      }
    }
  }

  private List<String> first(int start, List<Cell> cells, SourceLine line) {
    String comment = line.getComment();
    for (Map<Kind, Integer> columns : asList(targets, Collections.<Kind, Integer>emptyMap())) {
      Placed placed = place(start, cells, columns);
      if (fits(placed, comment)) {
        return new ArrayList<>(Collections.singletonList(text(placed, comment)));
      }
    }
    for (int k = cells.size() - 1; k > 0 && comment == null; k--) {
      Placed placed = place(start, cells.subList(0, k), targets);
      if (fits(placed, null)) {
        List<String> rest = flow(cells.subList(k, cells.size()), null);
        if (rest == null) {
          break;
        }
        List<String> result = new ArrayList<>();
        result.add(text(placed, null));
        result.addAll(rest);
        return result;
      }
    }
    return renderer.layout(start, clauseColumn, line.getWords(), comment, spaced());
  }

  private static List<Map<Kind, Integer>> asList(Map<Kind, Integer> one, Map<Kind, Integer> two) {
    List<Map<Kind, Integer>> list = new ArrayList<>();
    list.add(one);
    list.add(two);
    return list;
  }

  /** Lay out clauses on continuation lines, each line starting with the column of its clause. */
  private List<String> flow(List<Cell> cells, String comment) {
    List<String> result = new ArrayList<>();
    int i = 0;
    while (i < cells.size()) {
      Cell head = cells.get(i);
      int column = column(head);
      if (column + text(head).length() > renderer.width()) {
        column = clauseColumn;
      }
      if (column + text(head).length() > renderer.width()) {
        return null;
      }
      int j = i + 1;
      while (j < cells.size() && fits(place(column, cells.subList(i, j + 1), targets), null)) {
        j++;
      }
      Placed placed = place(column, cells.subList(i, j), targets);
      boolean last = j == cells.size();
      if (last && comment != null && !fits(placed, comment)) {
        return null;
      }
      result.add(text(placed, last ? comment : null));
      i = j;
    }
    return result;
  }

  private int column(Cell cell) {
    Integer target = targets.get(cell.kind);
    if (target != null) {
      return target;
    }
    return cell.kind == Kind.ITEMS && valueColumn >= 0 ? valueColumn : clauseColumn;
  }

  /** Offset of the first value after the VALUE [IS | ARE] keywords, or -1 if there is none. */
  private int valueOffset(List<Word> words) {
    int skip = 1;
    if (skip < words.size()
        && ("IS".equals(words.get(skip).upper()) || "ARE".equals(words.get(skip).upper()))) {
      skip++;
    }
    if (!words.get(skip - 1).getSeparator().isEmpty()) {
      return -1;
    }
    return renderer.join(words.subList(0, skip), spaced()).length() + 1;
  }
}
