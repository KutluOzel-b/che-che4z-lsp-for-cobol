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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Test {@link FormationEdits} */
class FormationEditsTest {
  private static final List<String> LINES = ImmutableList.of("A", "B", "C", "D");

  @Test
  void whenEditsAreApplied_thenTheyDoNotShiftEachOther() {
    List<String> result =
        FormationEdits.apply(
            LINES,
            ImmutableList.of(
                new TextEdit(new Range(new Position(0, 0), new Position(0, 1)), "A1\nA2"),
                new TextEdit(new Range(new Position(2, 0), new Position(3, 1)), "CD")));
    assertEquals(ImmutableList.of("A1", "A2", "B", "CD"), result);
  }

  static List<Arguments> changes() {
    return ImmutableList.of(
        Arguments.of(ImmutableList.of("A", "X", "C", "D")),
        Arguments.of(ImmutableList.of("A", "B", "B2", "C", "D")),
        Arguments.of(ImmutableList.of("A", "C", "D")),
        Arguments.of(ImmutableList.of("A", "B", "C")),
        Arguments.of(ImmutableList.of("A", "B", "C", "D", "E")),
        Arguments.of(ImmutableList.of("Z", "A", "B", "C", "D")),
        Arguments.of(ImmutableList.of("B", "C", "D")),
        Arguments.of(ImmutableList.of("X")));
  }

  @ParameterizedTest
  @MethodSource("changes")
  void whenDiffIsApplied_thenTheResultIsReproduced(List<String> after) {
    assertEquals(after, FormationEdits.apply(LINES, FormationEdits.diff(LINES, after)));
  }

  @Test
  void whenLineCountIsKept_thenOnlyChangedLinesAreEdited() {
    List<TextEdit> edits = FormationEdits.diff(LINES, ImmutableList.of("A", "X", "C", "Y"));
    assertEquals(
        ImmutableList.of(
            new TextEdit(new Range(new Position(1, 0), new Position(1, 1)), "X"),
            new TextEdit(new Range(new Position(3, 0), new Position(3, 1)), "Y")),
        edits);
  }
}
