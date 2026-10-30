/*
 * Copyright (c) 2020 Broadcom.
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp.cobol.service.CobolDocumentModel;
import org.eclipse.lsp.cobol.service.settings.SettingsService;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;

/** Test {@link Formations} class */
class FormationsTest {

  private static Formations formations(SettingsService settings, Formation... formations) {
    return new Formations(ImmutableSet.copyOf(formations), settings);
  }

  private static SettingsService settings(Object... values) {
    SettingsService service = mock(SettingsService.class);
    when(service.fetchConfigurations(anyList()))
        .thenReturn(CompletableFuture.completedFuture(ImmutableList.copyOf(values)));
    return service;
  }

  @Test
  void WhenCobolDocumentModelNull_thenFormatReturnsEmptyList() {
    List<TextEdit> format = formations(settings()).format(null);
    assertEquals(0, format.size());
  }

  @Test
  void whenFormationsAreChained_thenEachWorksOnThePreviousResult() {
    CobolDocumentModel model = new CobolDocumentModel("", "AAA\nBBB\nCCC");
    // replaces the second line, then the next formation sees the replaced text
    Formation first =
        (lines, settings) ->
            ImmutableList.of(
                new TextEdit(new Range(new Position(1, 0), new Position(1, 3)), "XYZ"));
    Formation second =
        (lines, settings) -> {
          assertEquals("XYZ", lines.get(1).getText());
          return ImmutableList.of(
              new TextEdit(new Range(new Position(1, 1), new Position(1, 2)), "-"));
        };

    List<TextEdit> edits = formations(settings(), first, second).format(model);

    assertEquals(
        ImmutableList.of(new TextEdit(new Range(new Position(1, 0), new Position(1, 3)), "X-Z")),
        edits);
  }

  @Test
  void whenSettingsAreFetched_thenBothSectionsArePassedAsJson() {
    CobolDocumentModel model = new CobolDocumentModel("", "TEXT");
    JsonObject formatter = new JsonObject();
    formatter.addProperty("indentWidth", 2);
    Formation formation =
        (lines, settings) -> {
          assertEquals(ImmutableList.of("\"Uppercase\"", "{\"indentWidth\":2}"), settings);
          return ImmutableList.of();
        };

    List<TextEdit> edits =
        formations(settings(new JsonPrimitive("Uppercase"), formatter), formation).format(model);

    assertEquals(0, edits.size());
  }

  @Test
  void whenNothingChanges_thenNoEdits() {
    CobolDocumentModel model = new CobolDocumentModel("", "SAMPLE TEXT FOR TEST");
    Formation formation =
        (lines, settings) ->
            ImmutableList.of(
                new TextEdit(new Range(new Position(0, 0), new Position(0, 6)), "SAMPLE"));
    assertEquals(0, formations(settings(), formation).format(model).size());
  }

  @Test
  void whenSourceAlreadyHasPreferredLayout_thenFormattingProducesNoEdits() throws Exception {
    String source =
        new String(
            Files.readAllBytes(Paths.get(getClass().getResource("/formatter/SLICKB0.cbl").toURI())),
            StandardCharsets.UTF_8);
    CobolDocumentModel model = new CobolDocumentModel("file:///SLICKB0.cbl", source);
    SettingsService settings = settings(new JsonPrimitive("None"), new JsonObject());
    assertEquals(
        ImmutableList.of(),
        formations(settings, new LayoutFormation(), new TrimFormation(), new CapitalFormation())
            .format(model));
    // Padding must also survive when trimming precedes layout in the formation chain.
    assertEquals(
        ImmutableList.of(),
        formations(settings, new TrimFormation(), new LayoutFormation(), new CapitalFormation())
            .format(model));
  }

  @Test
  void whenOrganizationSpacingIsChanged_thenTheSurroundingStyleIsApplied() throws Exception {
    String original =
        new String(
            Files.readAllBytes(Paths.get(getClass().getResource("/formatter/SLICKB0.cbl").toURI())),
            StandardCharsets.UTF_8);
    String changed =
        original.replace("ORGANIZATION  IS INDEXED", "ORGANIZATION            IS       INDEXED");
    CobolDocumentModel model = new CobolDocumentModel("file:///example.cbl", changed);
    SettingsService settings = settings(new JsonPrimitive("None"), new JsonObject());
    for (Formation[] order :
        new Formation[][] {
          {new LayoutFormation(), new TrimFormation(), new CapitalFormation()},
          {new TrimFormation(), new LayoutFormation(), new CapitalFormation()}
        }) {
      List<String> result =
          FormationEdits.apply(
              model.getLines().stream()
                  .map(CobolDocumentModel.Line::getText)
                  .collect(java.util.stream.Collectors.toList()),
              formations(settings, order).format(model));
      assertEquals(
          new CobolDocumentModel("", original)
              .getLines().stream()
                  .map(CobolDocumentModel.Line::getText)
                  .collect(java.util.stream.Collectors.toList()),
          result);
      assertEquals(
          ImmutableList.of(),
          formations(settings, order)
              .format(new CobolDocumentModel("", String.join("\n", result))));
    }
  }

  @Test
  void whenCicsSourceAlreadyHasPreferredLayout_thenFormattingProducesNoEdits() throws Exception {
    String source =
        new String(
            Files.readAllBytes(
                Paths.get(getClass().getResource("/formatter/URIMAP_CICS1.cbl").toURI())),
            StandardCharsets.UTF_8);
    CobolDocumentModel model = new CobolDocumentModel("file:///example.cbl", source);
    SettingsService settings = settings(new JsonPrimitive("None"), new JsonObject());
    assertEquals(
        ImmutableList.of(),
        formations(settings, new LayoutFormation(), new TrimFormation(), new CapitalFormation())
            .format(model));
    assertEquals(
        ImmutableList.of(),
        formations(settings, new TrimFormation(), new LayoutFormation(), new CapitalFormation())
            .format(model));
  }
}
