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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests document style inference and explicit setting precedence. */
class LayoutStyleDetectorTest {
  private static LayoutSettings detect(String... source) {
    return LayoutSettings.fromJson(null, Arrays.asList(source));
  }

  @Test
  void learnsFixedFormatIndentationQuotesAndPeriods() {
    LayoutSettings style =
        detect(
            "000100 PROCEDURE DIVISION.",
            "000200     IF A = B",
            "000300       DISPLAY \"don't change\"",
            "000400     END-IF",
            "000500     .");
    assertEquals(LayoutSettings.SourceFormat.FIXED, style.getSourceFormat());
    assertEquals(2, style.getIndentWidth());
    assertFalse(style.isSingleQuote());
    assertTrue(style.isStandalonePeriods());
    assertTrue(style.isPreserveSequenceNumbers());
  }

  @Test
  void learnsFreeFormatAndSpacingWithoutCountingHeaders() {
    LayoutSettings style =
        detect(
            "IDENTIFICATION DIVISION.",
            "PROGRAM-ID. EXAMPLE.",
            "PROCEDURE DIVISION.",
            "  IF A = B",
            "    DISPLAY 'hello' , 'world' .",
            "  END-IF .");
    assertEquals(LayoutSettings.SourceFormat.FREE, style.getSourceFormat());
    assertEquals(2, style.getIndentWidth());
    assertTrue(style.isSingleQuote());
    assertTrue(style.isSpaceBeforePunctuation());
    assertFalse(style.isStandalonePeriods());
  }

  @Test
  void learnsTabsOnlyInFreeFormat() {
    assertTrue(
        detect("PROCEDURE DIVISION.", "\tIF A = B", "\t\tDISPLAY A", "\tEND-IF.").isUseTabs());
    assertFalse(
        LayoutSettings.fromJson(
                "{\"sourceFormat\":\"fixed\"}",
                Arrays.asList("       PROCEDURE DIVISION.", "       \tDISPLAY A."))
            .isUseTabs());
  }

  @Test
  void sourceDirectiveWinsOverAmbiguousIndentation() {
    assertEquals(
        LayoutSettings.SourceFormat.FREE,
        detect(
                "       >>source format is free",
                "       PROCEDURE DIVISION.",
                "           DISPLAY A.")
            .getSourceFormat());
    assertEquals(
        LayoutSettings.SourceFormat.FIXED,
        detect(
                "      * >>SOURCE FORMAT FREE",
                "       >>SOURCE FIXED",
                "       PROCEDURE DIVISION.")
            .getSourceFormat());
  }

  @Test
  void learnsDataIndentationAndEachClauseAlignmentIndependently() {
    LayoutSettings style =
        detect(
            "       DATA DIVISION.",
            "       WORKING-STORAGE SECTION.",
            "       01 REC.",
            "         05 A           PIC 9 COMP VALUE 1.",
            "         05 LONGER-NAME PIC 9(5) COMP VALUE 100.");
    assertEquals(2, style.getIndentWidth());
    assertTrue(style.isAlignPicClauses());
    assertFalse(style.isAlignUsageClauses());
    assertFalse(style.isAlignValueClauses());
    assertFalse(detect("01 REC.", "  05 A PIC X.", "  05 LONGER PIC X.").isAlignPicClauses());
    assertTrue(
        detect("01 REC.", "  05 A PIC X     VALUE 'A'.", "  05 B PIC X(10) VALUE 'B'.")
            .isAlignValueClauses());
  }

  @Test
  void commentsDirectivesBlocksAndContinuedLiteralsDoNotVote() {
    LayoutSettings style =
        detect(
            "       PROCEDURE DIVISION.",
            "      * DISPLAY 'comment' .",
            "           DISPLAY \"real\".",
            "           EXEC SQL",
            "               SELECT 'sql', 'more' FROM T",
            "           END-EXEC.",
            "           COPY 'copy' REPLACING ==A== BY =='pseudo'==.",
            "           REPLACE ==A== BY =='pseudo'==.",
            "           DISPLAY 'continued",
            "      -    'value'.",
            "           >>SOMETHING 'directive' .");
    assertFalse(style.isSingleQuote());
    assertFalse(style.isSpaceBeforePunctuation());
  }

  @Test
  void explicitSettingsOverrideDetectionAndNullRestoresIt() {
    List<String> source =
        Arrays.asList("PROCEDURE DIVISION.", "  IF A = B", "    DISPLAY \"hi\"", "  END-IF.");
    LayoutSettings style =
        LayoutSettings.fromJson(
            "{\"sourceFormat\":\"free\",\"singleQuote\":true,\"indentWidth\":3,"
                + "\"standalonePeriods\":true,\"dataDivision\":{\"alignPicClauses\":false},"
                + "\"preserveSequenceNumbers\":false}",
            source);
    assertEquals(3, style.getIndentWidth());
    assertTrue(style.isSingleQuote());
    assertTrue(style.isStandalonePeriods());
    assertFalse(style.isAlignPicClauses());
    assertFalse(style.isPreserveSequenceNumbers());
    assertEquals(
        LayoutSettings.fromJson(null, source),
        LayoutSettings.fromJson(
            "{\"sourceFormat\":\"auto\",\"singleQuote\":null,\"indentWidth\":null,\"dataDivision\":null}",
            source));
    assertEquals(
        LayoutSettings.fromJson(null, source), LayoutSettings.fromJson("invalid JSON", source));
  }

  @Test
  void noEvidenceAndTiesUseDefaults() {
    assertEquals(
        LayoutSettings.builder().build(), LayoutSettings.fromJson(null, Collections.emptyList()));
    assertTrue(detect("PROCEDURE DIVISION.", "    DISPLAY 'one' \"two\".").isSingleQuote());
    assertEquals(
        4,
        detect(
                "       PROCEDURE DIVISION.",
                "           IF A = B",
                "           DISPLAY A",
                "           END-IF.")
            .getIndentWidth());
  }

  @Test
  void eachDocumentIsLearnedIndependently() {
    assertFalse(detect("PROCEDURE DIVISION.", "  DISPLAY \"first\".").isSingleQuote());
    assertTrue(detect("PROCEDURE DIVISION.", "    DISPLAY 'second'.").isSingleQuote());
  }

  @Test
  void freeHeadersAreRecognizedEvenWhenMostStatementsStartAfterColumnSeven() {
    assertEquals(
        LayoutSettings.SourceFormat.FREE,
        detect(
                "PROCEDURE DIVISION.",
                "        IF A = B",
                "                DISPLAY A",
                "        END-IF",
                "        STOP RUN.")
            .getSourceFormat());
  }

  @Test
  void majorityStyleWinsOverAnOutlier() {
    LayoutSettings style =
        detect(
            "PROCEDURE DIVISION.",
            "  IF A = B",
            "    DISPLAY \"one\"",
            "  END-IF",
            "  IF A = C",
            "    DISPLAY \"two\"",
            "  END-IF",
            "  IF A = D",
            "     DISPLAY 'outlier'",
            "  END-IF.");
    assertEquals(2, style.getIndentWidth());
    assertFalse(style.isSingleQuote());
  }

  @Test
  void automaticFormattingOfSamplesIsStable() throws Exception {
    for (String resource :
        Arrays.asList("sample.cbl", "sample.default.cbl", "sample.options.cbl")) {
      List<String> source =
          Files.readAllLines(
              Paths.get(getClass().getResource("/formatter/" + resource).toURI()),
              StandardCharsets.UTF_8);
      List<String> once =
          new CobolLayoutFormatter(LayoutSettings.fromJson(null, source)).format(source);
      assertEquals(
          once,
          new CobolLayoutFormatter(LayoutSettings.fromJson(null, once)).format(once),
          resource);
    }
  }

  @Test
  void preservesExistingColumnsAndContinuationIndentation() {
    List<String> source =
        Arrays.asList(
            "       ENVIRONMENT DIVISION.",
            "       INPUT-OUTPUT SECTION.",
            "       FILE-CONTROL.",
            "           SELECT INFILE",
            "               ASSIGN        TO INPUT-FILE",
            "               ORGANIZATION  IS SEQUENTIAL.",
            "       DATA DIVISION.",
            "       WORKING-STORAGE SECTION.",
            "       77  A                         PIC X VALUE SPACE.",
            "       77  LONG-NAME                 PIC X VALUE SPACE.",
            "       PROCEDURE DIVISION.",
            "           MOVE A                    TO LONG-NAME.",
            "           CALL 'PROGRAM' USING      A",
            "                                    LONG-NAME.",
            "           EVALUATE A",
            "           WHEN OTHER",
            "               DISPLAY A",
            "           END-EVALUATE.");
    assertEquals(
        source, new CobolLayoutFormatter(LayoutSettings.fromJson(null, source)).format(source));
  }

  @Test
  void preservesAlignmentWhileRepairingAreaAStatementIndentation() {
    List<String> source =
        Arrays.asList(
            "       PROCEDURE DIVISION.",
            "           MOVE 1        TO A.",
            "       MOVE 2        TO B.");
    List<String> formatted =
        new CobolLayoutFormatter(LayoutSettings.fromJson(null, source)).format(source);
    assertEquals(
        Arrays.asList(
            "       PROCEDURE DIVISION.",
            "           MOVE 1        TO A.",
            "           MOVE 2        TO B."),
        formatted);
  }

  @Test
  void explicitIndentationQuotesPunctuationAndDataAlignmentStillApply() {
    List<String> source =
        Arrays.asList(
            "       PROCEDURE DIVISION.",
            "           IF A = B",
            "                   DISPLAY    'one'.");
    List<String> formatted =
        new CobolLayoutFormatter(
                LayoutSettings.fromJson(
                    "{\"indentWidth\":2,\"singleQuote\":false,\"spaceBeforePunctuation\":true}",
                    source))
            .format(source);
    assertEquals("             DISPLAY    \"one\" .", formatted.get(2));
    List<String> data =
        Arrays.asList(
            "       77  A                         PIC X.",
            "       77  LONG-NAME                 PIC X.");
    List<String> unaligned =
        new CobolLayoutFormatter(
                LayoutSettings.fromJson("{\"dataDivision\":{\"alignPicClauses\":false}}", data))
            .format(data);
    assertEquals("       77  A PIC X.", unaligned.get(0));
    assertEquals("       77  LONG-NAME PIC X.", unaligned.get(1));
  }

  @Test
  void quoteConversionKeepsFollowingClausesAndCommentsInTheirColumns() {
    List<String> source =
        Arrays.asList(
            "       PROCEDURE DIVISION.",
            "           MOVE 'say \"hi\"'       TO A.    *> aligned comment");
    List<String> formatted =
        new CobolLayoutFormatter(LayoutSettings.fromJson("{\"singleQuote\":false}", source))
            .format(source);
    assertTrue(formatted.get(1).contains("\"say \"\"hi\"\"\""));
    assertEquals(source.get(1).indexOf("TO"), formatted.get(1).indexOf("TO"));
    assertEquals(source.get(1).indexOf("*>"), formatted.get(1).indexOf("*>"));
  }

  @Test
  void learnsClauseColumnsAndOrdinaryGapsFromOtherSelectClauses() {
    List<String> source =
        Arrays.asList(
            "       ENVIRONMENT DIVISION.",
            "       INPUT-OUTPUT SECTION.",
            "       FILE-CONTROL.",
            "           SELECT INFILE",
            "               ASSIGN        TO IN-FILE",
            "               ORGANIZATION            IS       INDEXED",
            "               ACCESS MODE   IS DYNAMIC",
            "               RECORD KEY    IS ACCOUNT-KEY",
            "               FILE STATUS   IS FILE-STATUS.");
    List<String> expected = new java.util.ArrayList<>(source);
    expected.set(5, "               ORGANIZATION  IS INDEXED");
    assertEquals(
        expected, new CobolLayoutFormatter(LayoutSettings.fromJson(null, source)).format(source));
    List<String> compact = new java.util.ArrayList<>(source);
    compact.set(5, "               ORGANIZATION IS INDEXED");
    assertEquals(
        expected, new CobolLayoutFormatter(LayoutSettings.fromJson(null, compact)).format(compact));
  }

  @Test
  void learnsMoveAndDataClauseColumnsWithoutMixingParagraphsOrRecords() {
    List<String> source =
        Arrays.asList(
            "       DATA DIVISION.",
            "       WORKING-STORAGE SECTION.",
            "       77  A                  PIC X VALUE SPACE.",
            "       77  LONGER             PIC X VALUE SPACE.",
            "       77  THIRD                          PIC      X VALUE SPACE.",
            "       PROCEDURE DIVISION.",
            "       FIRST-PARAGRAPH.",
            "           MOVE A            TO B.",
            "           MOVE LONGER       TO C.",
            "           MOVE THIRD                      TO        D.",
            "       SECOND-PARAGRAPH.",
            "           MOVE A TO B.",
            "           MOVE C TO D.");
    List<String> expected = new java.util.ArrayList<>(source);
    expected.set(4, "       77  THIRD              PIC X VALUE SPACE.");
    expected.set(9, "           MOVE THIRD        TO D.");
    assertEquals(
        expected, new CobolLayoutFormatter(LayoutSettings.fromJson(null, source)).format(source));
  }

  @Test
  void ambiguousSpacingIsPreservedAndLiteralContentsDoNotTeachSpacing() {
    List<String> source =
        Arrays.asList(
            "PROCEDURE DIVISION.",
            "    MOVE A    TO X.",
            "    MOVE B        TO Y.",
            "    DISPLAY 'ORGANIZATION            IS       INDEXED'.");
    assertEquals(
        source, new CobolLayoutFormatter(LayoutSettings.fromJson(null, source)).format(source));
  }

  @Test
  void differentSelectBlocksCanHaveTheirOwnSpacing() {
    List<String> source =
        Arrays.asList(
            "       ENVIRONMENT DIVISION.",
            "       INPUT-OUTPUT SECTION.",
            "       FILE-CONTROL.",
            "           SELECT FIRST-FILE",
            "               ORGANIZATION  IS SEQUENTIAL",
            "               ACCESS MODE   IS SEQUENTIAL",
            "               FILE STATUS   IS FIRST-STATUS.",
            "           SELECT SECOND-FILE",
            "               ORGANIZATION          IS   INDEXED",
            "               ACCESS MODE           IS   DYNAMIC",
            "               FILE STATUS           IS   SECOND-STATUS.");
    assertEquals(
        source, new CobolLayoutFormatter(LayoutSettings.fromJson(null, source)).format(source));
  }

  @Test
  void differentRecordsKeepTheirOwnPictureColumns() {
    List<String> source =
        Arrays.asList(
            "       DATA DIVISION.",
            "       WORKING-STORAGE SECTION.",
            "       01 FIRST-RECORD.",
            "           05 A             PIC X.",
            "           05 LONGER        PIC X.",
            "       01 SECOND-RECORD.",
            "           05 A                       PIC X.",
            "           05 LONGER                  PIC X.");
    assertEquals(
        source, new CobolLayoutFormatter(LayoutSettings.fromJson(null, source)).format(source));
  }

  @Test
  void learnsDifferentGapsAfterIsAndToFromOtherSelectBlocks() {
    List<String> source =
        Arrays.asList(
            "       ENVIRONMENT DIVISION.",
            "       INPUT-OUTPUT SECTION.",
            "       FILE-CONTROL.",
            "           SELECT FIRST-FILE",
            "               ASSIGN        TO    FIRST-INPUT",
            "               ORGANIZATION  IS SEQUENTIAL.",
            "           SELECT SECOND-FILE",
            "               ASSIGN        TO    SECOND-INPUT",
            "               ORGANIZATION  IS SEQUENTIAL.",
            "           SELECT THIRD-FILE",
            "               ASSIGN        TO    THIRD-INPUT",
            "               ORGANIZATION  IS SEQUENTIAL.");
    assertEquals(
        source, new CobolLayoutFormatter(LayoutSettings.fromJson(null, source)).format(source));
    List<String> changed = new java.util.ArrayList<>(source);
    changed.set(4, "               ASSIGN        TO         FIRST-INPUT");
    assertEquals(
        source, new CobolLayoutFormatter(LayoutSettings.fromJson(null, changed)).format(changed));
  }

  @Test
  void doesNotAssumeIsAndToMustUseTheSameColumn() {
    List<String> source =
        Arrays.asList(
            "       ENVIRONMENT DIVISION.",
            "       INPUT-OUTPUT SECTION.",
            "       FILE-CONTROL.",
            "           SELECT FIRST-FILE",
            "               ASSIGN   TO INPUT-FILE",
            "               ORGANIZATION  IS SEQUENTIAL",
            "               ACCESS MODE   IS SEQUENTIAL",
            "               FILE STATUS   IS FIRST-STATUS.");
    assertEquals(
        source, new CobolLayoutFormatter(LayoutSettings.fromJson(null, source)).format(source));
  }

  @Test
  void learnedStyleIsStableWhenFormattingTwice() {
    List<String> source =
        Arrays.asList(
            "       PROCEDURE DIVISION.",
            "           IF A = B",
            "             DISPLAY \"hi\"",
            "           END-IF",
            "           .");
    List<String> once =
        new CobolLayoutFormatter(LayoutSettings.fromJson(null, source)).format(source);
    assertEquals(source, once);
    assertEquals(once, new CobolLayoutFormatter(LayoutSettings.fromJson(null, once)).format(once));
  }

  @Test
  void learnsProcedureIndentationSeparatelyFromDataIndentation() {
    List<String> source =
        Arrays.asList(
            "       DATA DIVISION.",
            "       WORKING-STORAGE SECTION.",
            "       01 RECORD-A.",
            "           05 FIRST-ITEM PIC X.",
            "           05 SECOND-ITEM PIC X.",
            "           05 THIRD-ITEM PIC X.",
            "       PROCEDURE DIVISION.",
            "           IF A = B",
            "              GO TO DONE",
            "           END-IF.");
    LayoutSettings settings = LayoutSettings.fromJson(null, source);
    assertEquals(4, settings.getIndentWidth());
    assertEquals(3, settings.getProcedureIndentWidth());
    assertEquals(source, new CobolLayoutFormatter(settings).format(source));
    List<String> reversed =
        source.stream()
            .map(
                line ->
                    line.replace("           05", "         05")
                        .replace("              GO TO DONE", "               GO TO DONE"))
            .collect(java.util.stream.Collectors.toList());
    LayoutSettings reversedSettings = LayoutSettings.fromJson(null, reversed);
    assertEquals(2, reversedSettings.getIndentWidth());
    assertEquals(4, reversedSettings.getProcedureIndentWidth());
    assertEquals(reversed, new CobolLayoutFormatter(reversedSettings).format(reversed));
    LayoutSettings explicit = LayoutSettings.fromJson("{\"indentWidth\":2}", source);
    assertEquals(
        "             GO TO DONE", new CobolLayoutFormatter(explicit).format(source).get(8));
  }

  @Test
  void keepsContinuedDataClausesAtTheirExistingColumnsBeforeConsideringWrapping() {
    List<String> source =
        Arrays.asList(
            "       DATA DIVISION.",
            "       WORKING-STORAGE SECTION.",
            "       01 TERM-DATA.",
            "           05 TERM-LINES OCCURS 24 TIMES",
            "                          INDEXED BY TERM-IDX",
            "                                       PIC X(80) VALUE SPACES.");
    assertEquals(
        source, new CobolLayoutFormatter(LayoutSettings.fromJson(null, source)).format(source));
  }

  @Test
  void separateStatementGroupsAndNestingLevelsKeepTheirOwnColumns() {
    List<String> source =
        Arrays.asList(
            "       PROCEDURE DIVISION.",
            "           MOVE A                     TO X.",
            "           MOVE B                     TO Y.",
            "           MOVE C                     TO Z.",
            "",
            "           MOVE D              TO X.",
            "           MOVE E              TO Y.",
            "           IF A = B",
            "              MOVE F     TO X",
            "              MOVE G     TO Y",
            "           END-IF.");
    assertEquals(
        source, new CobolLayoutFormatter(LayoutSettings.fromJson(null, source)).format(source));
  }

  @Test
  void slightVariationInPaddedColumnsIsRetainedButLargeOutliersAreCorrected() {
    List<String> source =
        Arrays.asList(
            "       PROCEDURE DIVISION.",
            "           MOVE A        TO X.",
            "           MOVE B        TO Y.",
            "           MOVE C         TO Z.");
    assertEquals(
        source, new CobolLayoutFormatter(LayoutSettings.fromJson(null, source)).format(source));
    List<String> changed = new java.util.ArrayList<>(source);
    changed.set(3, "           MOVE C                   TO Z.");
    List<String> expected = new java.util.ArrayList<>(source);
    expected.set(3, "           MOVE C        TO Z.");
    assertEquals(
        expected, new CobolLayoutFormatter(LayoutSettings.fromJson(null, changed)).format(changed));
  }
}
