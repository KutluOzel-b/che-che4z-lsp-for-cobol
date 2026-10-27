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

import java.util.Collections;
import java.util.List;
import lombok.Data;

/** A physical source line split into the reference format areas, with its rendering decision. */
@Data
class SourceLine {
  /** Kind of the line. */
  enum Kind {
    BLANK,
    COMMENT,
    /** Compiler directives and lines with unknown indicators: never changed. */
    DIRECTIVE,
    CODE
  }

  /** How the line is rendered. */
  enum Mode {
    /** Keep the original content. */
    VERBATIM,
    /** Re-render the words starting at {@link #column}. */
    TOKENS,
    /** Data description entry, laid out by the data division aligner. */
    ENTRY,
    /** Move the original content horizontally by {@link #shift} columns. */
    SHIFT,
    /** The line content has been merged into the previous line. */
    REMOVED
  }

  private final String original;
  private final String sequence;
  private final char indicator;
  private final String content;
  private final String identification;
  private Kind kind = Kind.CODE;
  private List<Word> words = Collections.emptyList();
  private String comment;
  /** Part of a continued literal or word: the content must not change. */
  private boolean frozen;
  private boolean procedure;
  private boolean header;
  private boolean periodOnly;

  private Mode mode = Mode.VERBATIM;
  private int column;
  private int wrapColumn;
  private int shift;
  private boolean splitPeriod;
  private boolean appendPeriod;
  /** Rendered content area lines, set by the data division aligner. */
  private List<String> rendered;

  /**
   * Leading whitespace of the original content.
   *
   * @return number of leading spaces
   */
  int originalIndent() {
    int i = 0;
    while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
      i++;
    }
    return i;
  }

  /**
   * Check whether the last word terminates with a separator period.
   *
   * @return true if the line ends with a period
   */
  boolean endsWithPeriod() {
    return !words.isEmpty() && words.get(words.size() - 1).endsWithPeriod();
  }

  /**
   * Get an upper-cased word.
   *
   * @param index word index
   * @return the word or empty string when out of range
   */
  String upper(int index) {
    return index < words.size() ? words.get(index).upper() : "";
  }
}
