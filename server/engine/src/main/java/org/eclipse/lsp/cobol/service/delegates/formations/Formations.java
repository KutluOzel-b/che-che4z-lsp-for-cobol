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

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.lsp.cobol.service.CobolDocumentModel;
import org.eclipse.lsp.cobol.service.settings.SettingsService;
import org.eclipse.lsp4j.TextEdit;

/**
 * Runs the registered {@link Formation}s one after another, each of them on the output of the
 * previous one, and returns the edits turning the document into the final result.
 *
 * <p>The formations get the values of the {@code cobol-lsp.formatting} and {@code
 * cobol-lsp.formatter} settings, in this order, as JSON strings.
 */
@Singleton
public class Formations {

  private static final String FORMATTING = "formatting";
  private static final String FORMATTER = "formatter";
  private final Set<Formation> formatters;
  private final SettingsService settingsService;

  @Inject
  public Formations(Set<Formation> formations, SettingsService settingsService) {
    this.formatters = formations;
    this.settingsService = settingsService;
  }

  /**
   * Format the document.
   *
   * @param model the document, may be null
   * @return edits to apply to the document
   */
  public List<TextEdit> format(CobolDocumentModel model) {
    if (model == null) {
      return Collections.emptyList();
    }
    List<String> settings;
    try {
      settings =
          settingsService
              .fetchConfigurations(ImmutableList.of(FORMATTING, FORMATTER))
              .get()
              .stream()
              .map(String::valueOf)
              .collect(Collectors.toList());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    List<String> original =
        model.getLines().stream().map(CobolDocumentModel.Line::getText).collect(Collectors.toList());
    List<String> current = original;
    for (Formation formation : formatters) {
      current = FormationEdits.apply(current, formation.format(toLines(current), settings));
    }
    return FormationEdits.diff(original, current);
  }

  private static List<CobolDocumentModel.Line> toLines(List<String> texts) {
    List<CobolDocumentModel.Line> lines = new ArrayList<>(texts.size());
    for (int i = 0; i < texts.size(); i++) {
      lines.add(new CobolDocumentModel.Line(i, texts.get(i)));
    }
    return lines;
  }
}
