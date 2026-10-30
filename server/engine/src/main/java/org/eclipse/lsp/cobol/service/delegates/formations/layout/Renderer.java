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
import java.util.function.Predicate;
import org.apache.commons.lang3.StringUtils;

/** Turns words into the text of the content area according to the formatter settings. */
final class Renderer {
  private final boolean tabs;
  private final int indent;
  private final int width;
  private final char quote;
  private HorizontalLayout horizontal;

  Renderer(LayoutSettings settings, int width) {
    this.indent = settings.getIndentWidth();
    this.tabs =
        settings.isUseTabs()
            && indent > 0
            && settings.getSourceFormat() == LayoutSettings.SourceFormat.FREE;
    this.width = width;
    this.quote = settings.isSingleQuote() ? '\'' : '"';
  }

  /**
   * Maximal width of the content area.
   *
   * @return the width in columns
   */
  int width() {
    return width;
  }

  /**
   * Leading white space reaching the given column.
   *
   * @param column column inside the content area
   * @return the indentation string
   */
  String indent(int column) {
    if (!tabs) {
      return StringUtils.repeat(' ', column);
    }
    return StringUtils.repeat('\t', column / indent) + StringUtils.repeat(' ', column % indent);
  }

  /**
   * Render the words joined by single spaces.
   *
   * @param words words to render
   * @param spaced true to put a space before separator commas, semicolons and periods
   * @return the text
   */
  String join(List<Word> words, boolean spaced) {
    List<String> texts = new ArrayList<>();
    pieces(words, spaced).forEach(piece -> texts.add(piece.text));
    return String.join(" ", texts);
  }

  /** Learn local spacing and alignment before rendering any line. */
  void learn(List<SourceLine> lines) {
    horizontal = new HorizontalLayout(lines);
  }

  /**
   * Apply learned local spacing, retaining original whitespace where the evidence is inconclusive.
   *
   * @param line source line with its original words
   * @param words replacement words, in the same order
   * @param column requested indentation
   * @param keepIndent whether to retain the original leading whitespace
   * @param spaced whether to separate punctuation with a space
   * @return content or null when the changes require wrapping or a different token count
   */
  String applyLearnedLayout(
      SourceLine line, List<Word> words, int column, boolean keepIndent, boolean spaced) {
    List<Word> original = line.getWords();
    if (words.size() != original.size()) {
      return null;
    }
    String content = line.getContent();
    int leading = line.originalIndent();
    StringBuilder result =
        new StringBuilder(keepIndent ? content.substring(0, leading) : indent(column));
    int offset = leading;
    for (int i = 0; i < words.size(); i++) {
      Word before = original.get(i);
      Word after = words.get(i);
      String raw = before.getText() + before.getSeparator();
      int at = content.indexOf(raw, offset);
      if (at < offset) {
        return null;
      }
      String gap = content.substring(offset, at);
      if (i > 0 && after.isPunctuation()) {
        gap = spaced ? " " : "";
      } else {
        int target = at - leading + (keepIndent ? leading : column);
        if (horizontal != null) {
          gap = horizontal.gap(line, i, result.length(), target, gap);
        } else if (gap.length() > 1 && gap.indexOf('\t') < 0) {
          // Keep the token's column even if quote conversion changes the preceding token's width.
          gap = StringUtils.repeat(' ', Math.max(1, target - result.length()));
        }
      }
      result.append(gap);
      Word converted = after.withQuotes(quote);
      result.append(converted.getText());
      if (!converted.getSeparator().isEmpty()) {
        if (spaced && !converted.isPunctuation()) {
          result.append(' ');
        }
        result.append(converted.getSeparator());
      }
      offset = at + raw.length();
    }
    int comment = line.getComment() == null ? -1 : content.indexOf("*>", offset);
    if (comment > offset + 1) {
      int target = comment - leading + (keepIndent ? leading : column);
      result.append(StringUtils.repeat(' ', Math.max(1, target - result.length())));
      result.append(content.substring(comment));
    } else {
      result.append(content.substring(offset));
    }
    String rendered = StringUtils.stripEnd(result.toString(), null);
    return rendered.length() <= width ? rendered : null;
  }

  /**
   * Lay out the words starting at the column. When the text does not fit into the content area it
   * is wrapped at word boundaries, continuation lines start at the wrap column.
   *
   * @param column the start column
   * @param wrapColumn the start column of the continuation lines
   * @param words words to render
   * @param comment floating comment to append or null
   * @param spaced true to put a space before separator commas, semicolons and periods
   * @return rendered lines or null if the words cannot be placed inside the content area
   */
  List<String> layout(
      int column, int wrapColumn, List<Word> words, String comment, boolean spaced) {
    return layout(column, wrapColumn, words, comment, spaced, word -> true);
  }

  /**
   * Lay out the words like {@link #layout(int, int, List, String, boolean)}, breaking the lines
   * only before the words accepted by the predicate.
   *
   * @param column the start column
   * @param wrapColumn the start column of the continuation lines
   * @param words words to render
   * @param comment floating comment to append or null
   * @param spaced true to put a space before separator commas, semicolons and periods
   * @param breakable tells whether a continuation line may start with the word
   * @return rendered lines or null if the words cannot be placed inside the content area
   */
  List<String> layout(
      int column,
      int wrapColumn,
      List<Word> words,
      String comment,
      boolean spaced,
      Predicate<Word> breakable) {
    List<Piece> pieces = pieces(words, spaced);
    String text = join(words, spaced);
    if (comment != null) {
      text = text.isEmpty() ? comment : text + " " + comment;
    }
    List<String> result = new ArrayList<>();
    if (column + text.length() <= width) {
      result.add(indent(column) + text);
      return result;
    }
    if (comment != null) {
      return null;
    }
    int lineColumn = column;
    int lineStart = 0;
    while (lineStart < pieces.size()) {
      // the longest run of pieces fitting on the line and followed by a breakable piece
      int end = lineStart;
      int length = -1;
      int lineEnd = -1;
      while (end < pieces.size() && lineColumn + length + 1 + pieces.get(end).text.length() <= width) {
        length += 1 + pieces.get(end).text.length();
        end++;
        if (end == pieces.size() || breakable.test(pieces.get(end).word)) {
          lineEnd = end;
        }
      }
      if (lineEnd < 0) {
        return null;
      }
      List<String> texts = new ArrayList<>();
      pieces.subList(lineStart, lineEnd).forEach(piece -> texts.add(piece.text));
      result.add(indent(lineColumn) + String.join(" ", texts));
      lineStart = lineEnd;
      lineColumn = wrapColumn;
    }
    return result;
  }

  /** Rendered text of a word with the punctuation attached to it. */
  private static final class Piece {
    private final Word word;
    private String text;

    Piece(Word word, String text) {
      this.word = word;
      this.text = text;
    }
  }

  private List<Piece> pieces(List<Word> words, boolean spaced) {
    List<Piece> pieces = new ArrayList<>();
    for (Word word : words) {
      Word converted = word.withQuotes(quote);
      if (converted.isPunctuation()) {
        if (pieces.isEmpty()) {
          pieces.add(new Piece(word, converted.getSeparator()));
        } else {
          // removing the space would glue the punctuation of the previous word to it
          Piece last = pieces.get(pieces.size() - 1);
          boolean space = spaced || endsWithPunctuation(last.text);
          last.text = last.text + (space ? " " : "") + converted.getSeparator();
        }
        continue;
      }
      String separator = converted.getSeparator();
      // a space after a trailing comma or period of the text would turn it into a separator
      boolean space = spaced && !endsWithPunctuation(converted.getText());
      String text =
          separator.isEmpty()
              ? converted.getText()
              : converted.getText() + (space ? " " : "") + separator;
      pieces.add(new Piece(word, text));
    }
    return pieces;
  }

  private static boolean endsWithPunctuation(String text) {
    if (text.isEmpty()) {
      return false;
    }
    char last = text.charAt(text.length() - 1);
    return last == '.' || last == ',' || last == ';';
  }
}
