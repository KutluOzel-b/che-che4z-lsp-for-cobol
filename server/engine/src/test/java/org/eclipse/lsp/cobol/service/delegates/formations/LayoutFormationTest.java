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
  void whenDisabled_thenNothingIsChanged() {
    List<TextEdit> edits =
        new LayoutFormation().format(LINES, ImmutableList.of("\"None\"", "{\"enabled\":false}"));
    assertEquals(0, edits.size());
  }
}
