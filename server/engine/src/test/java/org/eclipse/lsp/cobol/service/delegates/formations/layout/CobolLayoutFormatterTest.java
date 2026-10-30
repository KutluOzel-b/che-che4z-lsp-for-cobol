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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

/** Test {@link CobolLayoutFormatter} */
class CobolLayoutFormatterTest {
  private static final LayoutSettings DEFAULTS = LayoutSettings.builder().build();

  private static List<String> resource(String name) throws IOException, URISyntaxException {
    return Files.readAllLines(
        Paths.get(CobolLayoutFormatterTest.class.getResource("/formatter/" + name).toURI()),
        StandardCharsets.UTF_8);
  }

  private static List<String> format(LayoutSettings settings, String... lines) {
    return new CobolLayoutFormatter(settings).format(Arrays.asList(lines));
  }

  private static List<String> format(String... lines) {
    return format(DEFAULTS, lines);
  }

  @Test
  void whenSampleIsFormattedWithDefaults_thenItMatchesExpected() throws Exception {
    List<String> result = new CobolLayoutFormatter(DEFAULTS).format(resource("sample.cbl"));
    assertEquals(resource("sample.default.cbl"), result);
  }

  @Test
  void whenSampleIsFormattedWithOptions_thenItMatchesExpected() throws Exception {
    LayoutSettings settings =
        DEFAULTS.toBuilder()
            .standalonePeriods(true)
            .spaceBeforePunctuation(true)
            .singleQuote(false)
            .preserveSequenceNumbers(false)
            .indentWidth(3)
            .build();
    List<String> result = new CobolLayoutFormatter(settings).format(resource("sample.cbl"));
    assertEquals(resource("sample.options.cbl"), result);
  }

  @Test
  void whenFormattedTwice_thenTheResultIsStable() throws Exception {
    List<String> once = new CobolLayoutFormatter(DEFAULTS).format(resource("sample.cbl"));
    assertEquals(once, new CobolLayoutFormatter(DEFAULTS).format(once));
  }

  @Test
  void whenStatementsAreNested_thenTheyAreIndented() {
    assertEquals(
        ImmutableList.of(
            "       PROCEDURE DIVISION.",
            "       MAIN.",
            "           IF A = 1",
            "               PERFORM VARYING I FROM 1 BY 1 UNTIL I > 5",
            "                   DISPLAY I",
            "               END-PERFORM",
            "           ELSE",
            "               PERFORM PARA-1 UNTIL B = 1",
            "               MOVE 1 TO B",
            "           END-IF.",
            "           STOP RUN."),
        format(
            "       PROCEDURE DIVISION.",
            "       MAIN.",
            "           IF A = 1",
            "           PERFORM VARYING I FROM 1 BY 1 UNTIL I > 5",
            "           DISPLAY I",
            "           END-PERFORM",
            "           ELSE",
            "           PERFORM PARA-1 UNTIL B = 1",
            "           MOVE 1 TO B",
            "           END-IF.",
            "           STOP RUN."));
  }

  @Test
  void whenIndentWidthIsSet_thenItIsUsed() {
    LayoutSettings settings = DEFAULTS.toBuilder().indentWidth(2).build();
    assertEquals(
        ImmutableList.of(
            "       PROCEDURE DIVISION.",
            "           IF A = 1",
            "             DISPLAY A",
            "           END-IF."),
        format(
            settings,
            "       PROCEDURE DIVISION.",
            "           IF A = 1",
            "                    DISPLAY A",
            "           END-IF."));
  }

  @Test
  void whenTabsAreUsedInFreeFormat_thenIndentationUsesTabs() {
    LayoutSettings settings =
        DEFAULTS.toBuilder()
            .sourceFormat(LayoutSettings.SourceFormat.FREE)
            .useTabs(true)
            .build();
    assertEquals(
        ImmutableList.of(
            "PROCEDURE DIVISION.", "MAIN.", "\tIF A = 1", "\t\tDISPLAY A", "\tEND-IF."),
        format(settings, "PROCEDURE DIVISION.", "MAIN.", "IF A = 1", "DISPLAY A", "END-IF."));
  }

  @Test
  void whenTabsAreRequestedInFixedFormat_thenSpacesAreKept() {
    LayoutSettings settings = DEFAULTS.toBuilder().useTabs(true).build();
    List<String> result = format(settings, "       PROCEDURE DIVISION.", "           IF A = 1",
        "           DISPLAY A", "           END-IF.");
    assertFalse(String.join("\n", result).contains("\t"));
  }

  @Test
  void whenQuotesAreConverted_thenEmbeddedQuotesAreEscaped() {
    assertEquals(
        ImmutableList.of("       PROCEDURE DIVISION.", "           DISPLAY 'IT''S' X'C1'."),
        format("       PROCEDURE DIVISION.", "           DISPLAY \"IT'S\" X\"C1\"."));
    assertEquals(
        ImmutableList.of("       PROCEDURE DIVISION.", "           DISPLAY \"SAY \"\"HI\"\"\"."),
        format(
            DEFAULTS.toBuilder().singleQuote(false).build(),
            "       PROCEDURE DIVISION.",
            "           DISPLAY 'SAY \"HI\"'."));
  }

  @Test
  void whenEmbeddedSqlIsFormatted_thenItsContentIsNotChanged() {
    assertEquals(
        ImmutableList.of(
            "       PROCEDURE DIVISION.",
            "           IF A = 1",
            "               EXEC SQL",
            "                 SELECT \"COL\" INTO :X FROM T",
            "               END-EXEC",
            "           END-IF."),
        format(
            "       PROCEDURE DIVISION.",
            "           IF A = 1",
            "           EXEC SQL",
            "             SELECT \"COL\" INTO :X FROM T",
            "           END-EXEC",
            "           END-IF."));
  }

  @Test
  void whenStandalonePeriodsAreEnabled_thenSentencePeriodsMoveToTheirOwnLine() {
    LayoutSettings settings = DEFAULTS.toBuilder().standalonePeriods(true).build();
    assertEquals(
        ImmutableList.of(
            "       PROCEDURE DIVISION.",
            "       MAIN.",
            "           IF A = 1",
            "               DISPLAY A",
            "           END-IF",
            "           ."),
        format(
            settings,
            "       PROCEDURE DIVISION.",
            "       MAIN.",
            "           IF A = 1",
            "               DISPLAY A",
            "           END-IF."));
  }

  @Test
  void whenStandalonePeriodsAreDisabled_thenPeriodsAreJoinedToTheStatement() {
    assertEquals(
        ImmutableList.of("       PROCEDURE DIVISION.", "       MAIN.", "           GOBACK."),
        format(
            "       PROCEDURE DIVISION.", "       MAIN.", "           GOBACK", "           ."));
  }

  @Test
  void whenSpaceBeforePunctuationIsSet_thenSeparatorsAreSpaced() {
    LayoutSettings settings = DEFAULTS.toBuilder().spaceBeforePunctuation(true).build();
    assertEquals(
        ImmutableList.of("       PROCEDURE DIVISION.", "           MOVE A TO B , C ."),
        format(settings, "       PROCEDURE DIVISION.", "           MOVE A TO B, C."));
    assertEquals(
        ImmutableList.of("       PROCEDURE DIVISION.", "           MOVE A TO B, C."),
        format("       PROCEDURE DIVISION.", "           MOVE A TO B , C ."));
  }

  @Test
  void whenPictureEndsWithComma_thenNoSpaceIsInsertedBeforeThePeriod() {
    LayoutSettings settings = DEFAULTS.toBuilder().spaceBeforePunctuation(true).build();
    List<String> result =
        format(settings, "       WORKING-STORAGE SECTION.", "       01 A PIC 9,9,.");
    assertEquals("       01  A PIC 9,9,.", result.get(1));
  }

  @Test
  void whenDataIsFormatted_thenLevelsAreNestedAndClausesAligned() {
    assertEquals(
        ImmutableList.of(
            "       WORKING-STORAGE SECTION.",
            "       01  REC.",
            "           05 A          PIC X          VALUE 'A'.",
            "               88 A-ON                  VALUE 'Y'.",
            "           05 LONGER-ONE PIC S9(4) COMP VALUE 0.",
            "           05 GRP.",
            "               10 B      PIC 9."),
        format(
            "       WORKING-STORAGE SECTION.",
            "       01 REC.",
            "          05 A PIC X VALUE 'A'.",
            "             88 A-ON VALUE 'Y'.",
            "          05 LONGER-ONE PIC S9(4) COMP VALUE 0.",
            "          05 GRP.",
            "            10 B PIC 9."));
  }

  @Test
  void whenAlignmentIsDisabled_thenClausesAreSeparatedBySingleSpaces() {
    LayoutSettings settings =
        DEFAULTS.toBuilder()
            .alignPicClauses(false)
            .alignUsageClauses(false)
            .alignValueClauses(false)
            .build();
    assertEquals(
        ImmutableList.of(
            "       01  REC.", "           05 A PIC X VALUE 'A'.", "           05 LONGER PIC 9."),
        format(settings, "       01 REC.", "          05 A    PIC X VALUE 'A'.",
            "          05 LONGER  PIC 9."));
  }

  private static String fixed(String sequenceAndContent, String identification) {
    return StringUtils.rightPad(sequenceAndContent, 72) + identification;
  }

  @Test
  void whenSequenceNumbersArePreserved_thenAreasAreKept() {
    assertEquals(
        ImmutableList.of("000100 01  REC.", fixed("000200     05 A PIC X.", "ABC00001")),
        format("000100 01 REC.", fixed("000200   05   A PIC X.", "ABC00001")));
  }

  @Test
  void whenSequenceNumbersAreNotPreserved_thenAreasAreCleared() {
    LayoutSettings settings = DEFAULTS.toBuilder().preserveSequenceNumbers(false).build();
    assertEquals(
        ImmutableList.of("       01  REC.", "      * COMMENT", "           05 A PIC X."),
        format(
            settings,
            "000100 01 REC.",
            fixed("000150* COMMENT", "ABC00001"),
            fixed("000200     05 A PIC X.", "ABC00001")));
  }

  @Test
  void whenLiteralIsContinued_thenTheLinesAreKept() {
    String first = "           MOVE 'A LITERAL CONTINUED ON THE NEXT LINE............. ";
    String second = "      -    'END' TO B.";
    List<String> result =
        format("       PROCEDURE DIVISION.", "       MAIN.", "          IF A = 1", first, second,
            "          END-IF.");
    assertEquals(first, result.get(3));
    assertEquals(second, result.get(4));
    assertEquals("           END-IF.", result.get(5));
  }

  @Test
  void whenOperandContinuesAStatement_thenItStaysInAreaB() {
    // a word in Area B is a continuation of the previous statement, not a paragraph name
    List<String> result =
        format("       PROCEDURE DIVISION.", "           MOVE A TO", "               B.");
    assertTrue(result.get(2).startsWith("               B."));
  }

  @Test
  void whenLineWouldBeTooLong_thenItIsWrappedInsideTheContentArea() {
    List<String> result =
        format(
            "       PROCEDURE DIVISION.",
            "       MAIN.",
            "           IF A = 1",
            "           IF B = 2",
            "           IF C = 3",
            "           MOVE SOME-VERY-LONG-SOURCE-NAME TO SOME-VERY-LONG-TARGET-NAME",
            "           END-IF END-IF END-IF.");
    for (String line : result) {
      assertTrue(line.length() <= 72, line);
    }
    assertEquals("                       MOVE SOME-VERY-LONG-SOURCE-NAME TO", result.get(5));
    assertEquals("                           SOME-VERY-LONG-TARGET-NAME", result.get(6));
  }

  @Test
  void whenSettingsAreParsed_thenMissingValuesUseDefaults() {
    LayoutSettings settings =
        LayoutSettings.fromJson(
            "{\"sourceFormat\":\"free\",\"indentWidth\":2,\"dataDivision\":"
                + "{\"alignPicClauses\":false},\"standalonePeriods\":\"yes\"}");
    assertEquals(LayoutSettings.SourceFormat.FREE, settings.getSourceFormat());
    assertEquals(2, settings.getIndentWidth());
    assertFalse(settings.isAlignPicClauses());
    assertTrue(settings.isAlignValueClauses());
    assertFalse(settings.isStandalonePeriods());
    assertEquals(DEFAULTS, LayoutSettings.fromJson(null));
    assertEquals(DEFAULTS, LayoutSettings.fromJson("null"));
    assertEquals(DEFAULTS, LayoutSettings.fromJson("{\"indentWidth\":-1}"));
  }
}
