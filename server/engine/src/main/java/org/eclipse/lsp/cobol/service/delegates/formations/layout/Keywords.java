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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** COBOL words the layout formatter needs to recognize. All the words are upper-cased. */
final class Keywords {
  private Keywords() {}

  static final Set<String> DIVISIONS =
      ImmutableSet.of("IDENTIFICATION", "ID", "ENVIRONMENT", "DATA", "PROCEDURE");

  static final Set<String> END_MARKERS =
      ImmutableSet.of("PROGRAM", "CLASS", "METHOD", "FACTORY", "OBJECT", "FUNCTION");

  static final Set<String> ID_PARAGRAPHS =
      ImmutableSet.of(
          "PROGRAM-ID",
          "CLASS-ID",
          "METHOD-ID",
          "FUNCTION-ID",
          "AUTHOR",
          "INSTALLATION",
          "DATE-WRITTEN",
          "DATE-COMPILED",
          "SECURITY",
          "REMARKS");

  static final Set<String> ENV_PARAGRAPHS =
      ImmutableSet.of(
          "SOURCE-COMPUTER",
          "OBJECT-COMPUTER",
          "SPECIAL-NAMES",
          "REPOSITORY",
          "FILE-CONTROL",
          "I-O-CONTROL");

  static final Set<String> DATA_SECTIONS =
      ImmutableSet.of(
          "FILE", "WORKING-STORAGE", "LOCAL-STORAGE", "LINKAGE", "SCREEN", "REPORT",
          "COMMUNICATION");

  static final Set<String> ENV_SECTIONS = ImmutableSet.of("CONFIGURATION", "INPUT-OUTPUT");

  static final Set<String> FILE_DESCRIPTIONS = ImmutableSet.of("FD", "SD", "RD", "CD");

  /** Source text manipulation and listing control statements which are kept untouched. */
  static final Set<String> LISTING_DIRECTIVES =
      ImmutableSet.of("EJECT", "SKIP1", "SKIP2", "SKIP3", "TITLE");

  /** Words starting a statement in the procedure division. */
  static final Set<String> VERBS =
      ImmutableSet.of(
          "ACCEPT", "ADD", "ALLOCATE", "ALTER", "CALL", "CANCEL", "CLOSE", "COMPUTE", "CONTINUE",
          "DELETE", "DISABLE", "DISPLAY", "DIVIDE", "ENABLE", "ENTRY", "EVALUATE", "EXHIBIT",
          "EXIT", "FREE", "GENERATE", "GO", "GOBACK", "IF", "INITIALIZE", "INITIATE", "INSPECT",
          "INVOKE", "JSON", "MERGE", "MOVE", "MULTIPLY", "OPEN", "PERFORM", "PURGE", "RAISE",
          "READ", "RECEIVE", "RELEASE", "RESUME", "RETURN", "REWRITE", "SEARCH", "SEND", "SERVICE",
          "SET", "SORT", "START", "STOP", "STRING", "SUBTRACT", "SUPPRESS", "TERMINATE",
          "UNSTRING", "USE", "VALIDATE", "WRITE", "XML",
          // IDMS DML
          "BIND", "COMMIT", "CONNECT", "DISCONNECT", "ERASE", "FIND", "FINISH", "MODIFY", "OBTAIN",
          "READY", "ROLLBACK", "STORE");

  /** Words which may start a conditional phrase. */
  static final Set<String> PHRASE_STARTS =
      ImmutableSet.of(
          "AT", "NOT", "ON", "INVALID", "SIZE", "EXCEPTION", "OVERFLOW", "END", "EOP",
          "END-OF-PAGE");

  /** Statements accepting conditional phrases, like AT END or ON SIZE ERROR. */
  static final Set<String> PHRASE_VERBS =
      ImmutableSet.of(
          "READ", "WRITE", "REWRITE", "DELETE", "START", "RETURN", "ADD", "SUBTRACT", "MULTIPLY",
          "DIVIDE", "COMPUTE", "CALL", "ACCEPT", "DISPLAY", "INVOKE", "XML", "JSON", "STRING",
          "UNSTRING");

  static final String AT_END = "AT END";
  static final String INVALID_KEY = "INVALID KEY";
  static final String SIZE_ERROR = "SIZE ERROR";
  static final String EXCEPTION = "EXCEPTION";
  static final String OVERFLOW = "OVERFLOW";
  static final String END_OF_PAGE = "END-OF-PAGE";

  /** Statements a conditional phrase may belong to. */
  static final Map<String, Set<String>> PHRASE_OWNERS =
      ImmutableMap.<String, Set<String>>builder()
          .put(AT_END, ImmutableSet.of("READ", "RETURN", "SEARCH"))
          .put(INVALID_KEY, ImmutableSet.of("READ", "WRITE", "REWRITE", "DELETE", "START"))
          .put(SIZE_ERROR, ImmutableSet.of("ADD", "SUBTRACT", "MULTIPLY", "DIVIDE", "COMPUTE"))
          .put(EXCEPTION, ImmutableSet.of("CALL", "ACCEPT", "DISPLAY", "INVOKE", "XML", "JSON"))
          .put(OVERFLOW, ImmutableSet.of("STRING", "UNSTRING", "CALL"))
          .put(END_OF_PAGE, ImmutableSet.of("WRITE"))
          .build();

  /** Explicit scope terminators mapped to the statement they close. */
  static final Map<String, String> SCOPE_TERMINATORS =
      ImmutableMap.<String, String>builder()
          .put("END-IF", "IF")
          .put("END-EVALUATE", "EVALUATE")
          .put("END-SEARCH", "SEARCH")
          .put("END-PERFORM", "PERFORM")
          .put("END-READ", "READ")
          .put("END-WRITE", "WRITE")
          .put("END-REWRITE", "REWRITE")
          .put("END-DELETE", "DELETE")
          .put("END-START", "START")
          .put("END-RETURN", "RETURN")
          .put("END-ADD", "ADD")
          .put("END-SUBTRACT", "SUBTRACT")
          .put("END-MULTIPLY", "MULTIPLY")
          .put("END-DIVIDE", "DIVIDE")
          .put("END-COMPUTE", "COMPUTE")
          .put("END-CALL", "CALL")
          .put("END-ACCEPT", "ACCEPT")
          .put("END-DISPLAY", "DISPLAY")
          .put("END-INVOKE", "INVOKE")
          .put("END-XML", "XML")
          .put("END-JSON", "JSON")
          .put("END-STRING", "STRING")
          .put("END-UNSTRING", "UNSTRING")
          .build();

  /** Options of a PERFORM statement which make it an inline PERFORM. */
  static final Set<String> INLINE_PERFORM_OPTIONS =
      ImmutableSet.of("UNTIL", "VARYING", "WITH", "TEST", "FOREVER");

  static final Set<String> PICTURE = ImmutableSet.of("PIC", "PICTURE");
  static final Set<String> VALUE = ImmutableSet.of("VALUE", "VALUES");
  static final Set<String> USAGE =
      ImmutableSet.of(
          "USAGE", "BINARY", "COMP", "COMP-1", "COMP-2", "COMP-3", "COMP-4", "COMP-5",
          "COMPUTATIONAL", "COMPUTATIONAL-1", "COMPUTATIONAL-2", "COMPUTATIONAL-3",
          "COMPUTATIONAL-4", "COMPUTATIONAL-5", "PACKED-DECIMAL", "DISPLAY", "DISPLAY-1",
          "NATIONAL", "INDEX", "POINTER", "POINTER-32", "PROCEDURE-POINTER", "FUNCTION-POINTER",
          "UTF-8");

  /** Data description clauses other than PICTURE, USAGE and VALUE. */
  static final Set<String> OTHER_DATA_CLAUSES =
      ImmutableSet.of(
          "REDEFINES", "OCCURS", "JUST", "JUSTIFIED", "SYNC", "SYNCHRONIZED", "BLANK", "SIGN",
          "LEADING", "TRAILING", "EXTERNAL", "GLOBAL", "RENAMES", "GROUP-USAGE", "VOLATILE",
          "DYNAMIC", "TYPEDEF", "BASED", "INDEXED", "ASCENDING", "DESCENDING", "DEPENDING");

  private static final Pattern USER_WORD = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");
  private static final Pattern LEVEL_NUMBER = Pattern.compile("\\d{1,2}");
  private static final int MAX_GROUP_LEVEL = 49;
  static final int LEVEL_RENAMES = 66;
  static final int LEVEL_INDEPENDENT = 77;
  static final int LEVEL_CONSTANT = 78;
  static final int LEVEL_CONDITION = 88;

  /**
   * Check whether a word can be a user-defined name, like a paragraph name.
   *
   * @param upper upper-cased word
   * @return true if the word is a valid user-defined word
   */
  static boolean isUserWord(String upper) {
    return USER_WORD.matcher(upper).matches()
        && !VERBS.contains(upper)
        && !SCOPE_TERMINATORS.containsKey(upper)
        && !"ELSE".equals(upper)
        && !"WHEN".equals(upper)
        && !"EXEC".equals(upper)
        && !"END-EXEC".equals(upper);
  }

  /**
   * Get the level number of a data description entry.
   *
   * @param word the first word of the entry
   * @return level number or -1 if the word is not a level number
   */
  static int levelNumber(Word word) {
    if (word.isLiteral() || !LEVEL_NUMBER.matcher(word.getText()).matches()) {
      return -1;
    }
    int level = Integer.parseInt(word.getText());
    boolean valid =
        (level >= 1 && level <= MAX_GROUP_LEVEL)
            || level == LEVEL_RENAMES
            || level == LEVEL_INDEPENDENT
            || level == LEVEL_CONSTANT
            || level == LEVEL_CONDITION;
    return valid ? level : -1;
  }

  /**
   * Check whether the word starts a data description clause.
   *
   * @param upper upper-cased word
   * @return true for the clause keywords
   */
  static boolean isDataClause(String upper) {
    return PICTURE.contains(upper)
        || VALUE.contains(upper)
        || USAGE.contains(upper)
        || OTHER_DATA_CLAUSES.contains(upper);
  }
}
