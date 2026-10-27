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

import java.util.Locale;
import lombok.Value;

/**
 * A COBOL character-string separated by spaces. A trailing separator period, comma or semicolon is
 * kept apart from the text, so it can be re-rendered according to the settings.
 */
@Value
class Word {
  /** Text of the word without trailing separator, empty for standalone punctuation. */
  String text;
  /** Trailing separator ("." "," ";") or empty string. */
  String separator;
  /** True if the word contains an alphanumeric or national literal. */
  boolean literal;
  /** True if the word ends with a literal which is not closed on the line. */
  boolean unterminated;

  /**
   * Create a word from a raw character-string taken from the source line.
   *
   * @param raw source text without surrounding spaces
   * @param literal true if the text contains a literal
   * @param unterminated true if the text ends with an unclosed literal
   * @return the word
   */
  static Word of(String raw, boolean literal, boolean unterminated) {
    if (!unterminated && !raw.isEmpty()) {
      char last = raw.charAt(raw.length() - 1);
      if (last == '.' || last == ',' || last == ';') {
        return new Word(raw.substring(0, raw.length() - 1), String.valueOf(last), literal, false);
      }
    }
    return new Word(raw, "", literal, unterminated);
  }

  /**
   * Upper-cased text for keyword comparison.
   *
   * @return upper-cased text
   */
  String upper() {
    return text.toUpperCase(Locale.ROOT);
  }

  /**
   * Check whether the word terminates with a separator period.
   *
   * @return true if the word ends with a period
   */
  boolean endsWithPeriod() {
    return ".".equals(separator);
  }

  /**
   * Check if the word is a separator only (e.g. a standalone period or comma).
   *
   * @return true for punctuation only words
   */
  boolean isPunctuation() {
    return text.isEmpty();
  }

  /**
   * Create a copy without the trailing separator.
   *
   * @return word without separator
   */
  Word withoutSeparator() {
    return new Word(text, "", literal, unterminated);
  }

  /**
   * Create a copy with a separator period.
   *
   * @return word with a trailing period
   */
  Word withPeriod() {
    return new Word(text, ".", literal, unterminated);
  }

  /**
   * Create a copy with all the literals delimited by the requested quote character.
   *
   * @param quote the target delimiter, either ' or "
   * @return converted word
   */
  Word withQuotes(char quote) {
    if (!literal || unterminated) {
      return this;
    }
    return new Word(convertQuotes(text, quote), separator, true, false);
  }

  private static String convertQuotes(String text, char target) {
    StringBuilder result = new StringBuilder(text.length() + 2);
    int i = 0;
    while (i < text.length()) {
      char ch = text.charAt(i);
      if (ch != '\'' && ch != '"') {
        result.append(ch);
        i++;
        continue;
      }
      StringBuilder value = new StringBuilder();
      int j = i + 1;
      boolean closed = false;
      while (j < text.length()) {
        char c = text.charAt(j);
        if (c == ch) {
          if (j + 1 < text.length() && text.charAt(j + 1) == ch) {
            value.append(ch);
            j += 2;
            continue;
          }
          closed = true;
          break;
        }
        value.append(c);
        j++;
      }
      if (!closed) {
        return text;
      }
      String escaped = value.toString().replace(String.valueOf(target), "" + target + target);
      result.append(target).append(escaped).append(target);
      i = j + 1;
    }
    return result.toString();
  }
}
