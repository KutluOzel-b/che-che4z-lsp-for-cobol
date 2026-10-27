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
import java.util.List;
import lombok.Value;

/** Splits the content area of a single line into {@link Word}s and a trailing inline comment. */
final class LineLexer {
  private LineLexer() {}

  /** Result of lexing a line. */
  @Value
  static class Result {
    List<Word> words;
    /** The floating comment starting with "*>" or null. */
    String comment;
    /** True if the line ends inside a literal. */
    boolean unterminated;
  }

  /**
   * Split the line content into words.
   *
   * @param content content area of the line
   * @return lexing result
   */
  static Result lex(String content) {
    List<Word> words = new ArrayList<>();
    String comment = null;
    boolean unterminated = false;
    int length = content.length();
    int i = 0;
    while (i < length) {
      if (Character.isWhitespace(content.charAt(i))) {
        i++;
        continue;
      }
      if (isCommentStart(content, i)) {
        comment = content.substring(i).trim();
        break;
      }
      int start = i;
      boolean literal = false;
      boolean open = false;
      while (i < length && !Character.isWhitespace(content.charAt(i))) {
        char ch = content.charAt(i);
        if (ch == '\'' || ch == '"') {
          literal = true;
          i = skipLiteral(content, i);
          if (i < 0) {
            open = true;
            i = length;
          }
          continue;
        }
        if (isCommentStart(content, i)) {
          break;
        }
        i++;
      }
      words.add(Word.of(content.substring(start, i), literal, open));
      unterminated = open;
    }
    return new Result(words, comment, unterminated);
  }

  private static boolean isCommentStart(String content, int i) {
    return content.charAt(i) == '*' && i + 1 < content.length() && content.charAt(i + 1) == '>';
  }

  /**
   * Skip the literal started at the given position.
   *
   * @return position right after the closing quote or -1 if the literal is not closed
   */
  private static int skipLiteral(String content, int start) {
    char quote = content.charAt(start);
    int i = start + 1;
    while (i < content.length()) {
      if (content.charAt(i) == quote) {
        if (i + 1 < content.length() && content.charAt(i + 1) == quote) {
          i += 2;
          continue;
        }
        return i + 1;
      }
      i++;
    }
    return -1;
  }
}
