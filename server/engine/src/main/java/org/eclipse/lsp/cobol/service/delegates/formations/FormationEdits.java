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
package org.eclipse.lsp.cobol.service.delegates.formations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

/** Conversions between text edits and lists of lines, used to chain the formations. */
final class FormationEdits {
  private FormationEdits() {}

  /**
   * Apply non-overlapping text edits to the lines.
   *
   * @param lines lines of the document
   * @param edits edits to apply
   * @return lines of the edited document
   */
  static List<String> apply(List<String> lines, List<? extends TextEdit> edits) {
    if (edits.isEmpty()) {
      return lines;
    }
    int[] starts = new int[lines.size() + 1];
    for (int i = 0; i < lines.size(); i++) {
      starts[i + 1] = starts[i] + lines.get(i).length() + 1;
    }
    StringBuilder text = new StringBuilder(String.join("\n", lines));
    List<TextEdit> sorted = new ArrayList<>(edits);
    sorted.sort(
        Comparator.comparingInt(
                (TextEdit edit) -> offset(lines, starts, edit.getRange().getStart()))
            .reversed());
    for (TextEdit edit : sorted) {
      int start = offset(lines, starts, edit.getRange().getStart());
      int end = Math.max(start, offset(lines, starts, edit.getRange().getEnd()));
      text.replace(start, end, edit.getNewText());
    }
    return Arrays.asList(text.toString().split("\n", -1));
  }

  private static int offset(List<String> lines, int[] starts, Position position) {
    if (lines.isEmpty()) {
      return 0;
    }
    int line = Math.max(0, Math.min(position.getLine(), lines.size() - 1));
    int character = Math.max(0, Math.min(position.getCharacter(), lines.get(line).length()));
    return starts[line] + character;
  }

  /**
   * Create text edits turning the original lines into the formatted ones. Every changed line gets
   * its own edit when the number of lines is kept, otherwise a single edit replaces the changed
   * block.
   *
   * @param before the original lines
   * @param after the formatted lines
   * @return edits, empty if nothing changed
   */
  static List<TextEdit> diff(List<String> before, List<String> after) {
    if (before.equals(after)) {
      return Collections.emptyList();
    }
    List<TextEdit> edits = new ArrayList<>();
    if (before.size() == after.size()) {
      for (int i = 0; i < before.size(); i++) {
        if (!before.get(i).equals(after.get(i))) {
          edits.add(replace(i, i, before, after.get(i)));
        }
      }
      return edits;
    }
    int common = Math.min(before.size(), after.size());
    int prefix = 0;
    while (prefix < common && before.get(prefix).equals(after.get(prefix))) {
      prefix++;
    }
    int suffix = 0;
    while (suffix < common - prefix
        && before.get(before.size() - 1 - suffix).equals(after.get(after.size() - 1 - suffix))) {
      suffix++;
    }
    // keep both changed blocks non-empty, so a plain replacement is enough
    while (suffix > 0 && (before.size() - suffix == prefix || after.size() - suffix == prefix)) {
      suffix--;
    }
    while (prefix > 0 && (before.size() - suffix == prefix || after.size() - suffix == prefix)) {
      prefix--;
    }
    if (before.isEmpty()) {
      edits.add(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)),
          String.join("\n", after)));
      return edits;
    }
    String text = String.join("\n", after.subList(prefix, after.size() - suffix));
    edits.add(replace(prefix, before.size() - suffix - 1, before, text));
    return edits;
  }

  private static TextEdit replace(int from, int to, List<String> before, String text) {
    return new TextEdit(
        new Range(new Position(from, 0), new Position(to, before.get(to).length())), text);
  }
}
