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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.eclipse.lsp.cobol.service.CobolDocumentModel;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;

/** Test {@link LayoutFormation} */
class LayoutFormationTest {
  private static final List<CobolDocumentModel.Line> LINES =
      new CobolDocumentModel("", "       PROCEDURE DIVISION.\n       MOVE A TO B.").getLines();

  @Test
  void whenEnabled_thenChangedLinesAreEdited() {
    List<TextEdit> edits =
        new LayoutFormation().format(LINES, ImmutableList.of("\"None\"", "{\"indentWidth\":2}"));
    assertEquals(
        ImmutableList.of(
            new TextEdit(
                new Range(new Position(1, 0), new Position(1, 19)), "           MOVE A TO B.")),
        edits);
  }

  @Test
  void whenSettingsAreMissing_thenDefaultsAreUsed() {
    assertEquals(1, new LayoutFormation().format(LINES, ImmutableList.of("\"None\"")).size());
  }

  @Test
  void whenSettingsAreMissing_thenDocumentStyleIsLearned() {
    List<CobolDocumentModel.Line> lines =
        new CobolDocumentModel(
                "", "PROCEDURE DIVISION.\n  IF A = B\n    DISPLAY    \"hello\"\n  END-IF.")
            .getLines();
    List<TextEdit> edits = new LayoutFormation().format(lines, ImmutableList.of("\"None\""));
    assertEquals(
        ImmutableList.of(
            "PROCEDURE DIVISION.", "  IF A = B", "    DISPLAY    \"hello\"", "  END-IF."),
        FormationEdits.apply(
            lines.stream()
                .map(CobolDocumentModel.Line::getText)
                .collect(java.util.stream.Collectors.toList()),
            edits));
  }

  @Test
  void whenDisabled_thenNothingIsChanged() {
    List<TextEdit> edits =
        new LayoutFormation().format(LINES, ImmutableList.of("\"None\"", "{\"enabled\":false}"));
    assertEquals(0, edits.size());
  }

  @Test
  void whenRequiredAreaAEntriesAreInAreaB_thenTheirPositionsAreCorrected() {
    List<String> source =
        java.util.Arrays.asList(
            "           IDENTIFICATION DIVISION.",
            "       PROGRAM-ID. EXAMPLE.",
            "           DATA DIVISION.",
            "           FILE SECTION.",
            "               FD  INPUT-FILE.",
            "               01  FILE-RECORD PIC X.",
            "           WORKING-STORAGE SECTION.",
            "               01  ROOT-RECORD.",
            "           05 CHILD PIC X.",
            "               77  INDEPENDENT-ITEM PIC 9.",
            "           PROCEDURE DIVISION.",
            "               MAIN-SECTION SECTION.",
            "               MAIN-PARAGRAPH.",
            "           DISPLAY 'hello'.",
            "               NEXT-PARAGRAPH.",
            "           GOBACK.",
            "           END PROGRAM EXAMPLE.");
    List<String> expected =
        java.util.Arrays.asList(
            "       IDENTIFICATION DIVISION.",
            "       PROGRAM-ID. EXAMPLE.",
            "       DATA DIVISION.",
            "       FILE SECTION.",
            "       FD  INPUT-FILE.",
            "       01  FILE-RECORD PIC X.",
            "       WORKING-STORAGE SECTION.",
            "       01  ROOT-RECORD.",
            "           05 CHILD PIC X.",
            "       77  INDEPENDENT-ITEM PIC 9.",
            "       PROCEDURE DIVISION.",
            "       MAIN-SECTION SECTION.",
            "       MAIN-PARAGRAPH.",
            "           DISPLAY 'hello'.",
            "       NEXT-PARAGRAPH.",
            "           GOBACK.",
            "       END PROGRAM EXAMPLE.");
    assertEquals(expected, format(source));
    assertEquals(expected, format(expected));
  }

  @Test
  void whenOperandContinuesAStatement_thenItStaysInAreaB() {
    List<String> source =
        java.util.Arrays.asList(
            "       PROCEDURE DIVISION.", "           MOVE SOURCE TO", "               TARGET.");
    assertEquals(source, format(source));
  }

  @Test
  void whenEntriesAreAlreadyWithinAreaA_thenTheirIndentationIsPreserved() {
    List<String> source =
        java.util.Arrays.asList(
            "        DATA DIVISION.",
            "         FILE SECTION.",
            "        FD  INPUT-FILE.",
            "          01 RECORD-A PIC X.",
            "       WORKING-STORAGE SECTION.",
            "         77 SCALAR PIC 9.",
            "        PROCEDURE DIVISION.",
            "         MAIN-SECTION SECTION.",
            "          MAIN-PARAGRAPH.",
            "           GOBACK.",
            "         END PROGRAM EXAMPLE.");
    assertEquals(source, format(source));
  }

  private static List<String> format(List<String> source) {
    CobolDocumentModel model = new CobolDocumentModel("", String.join("\n", source));
    return FormationEdits.apply(
        source, new LayoutFormation().format(model.getLines(), ImmutableList.of("\"None\"")));
  }
}
