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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.eclipse.lsp.cobol.service.CobolDocumentModel;
import org.eclipse.lsp.cobol.service.delegates.formations.layout.CobolLayoutFormatter;
import org.eclipse.lsp.cobol.service.delegates.formations.layout.LayoutSettings;
import org.eclipse.lsp4j.TextEdit;

/**
 * Lays out the source code (indentation, alignment, quotes, spacing) according to the {@code
 * cobol-lsp.formatter} settings.
 */
public class LayoutFormation implements Formation {
  /** Index of the {@code formatter} section in the settings passed by {@link Formations}. */
  static final int SETTINGS_INDEX = 1;

  @Override
  public List<TextEdit> format(
      @NonNull List<CobolDocumentModel.Line> lines, @NonNull List<String> settings) {
    String json = settings.size() > SETTINGS_INDEX ? settings.get(SETTINGS_INDEX) : null;
    LayoutSettings layoutSettings = LayoutSettings.fromJson(json);
    if (!layoutSettings.isEnabled()) {
      return Collections.emptyList();
    }
    List<String> before =
        lines.stream().map(CobolDocumentModel.Line::getText).collect(Collectors.toList());
    List<String> after = new CobolLayoutFormatter(layoutSettings).format(before);
    return FormationEdits.diff(before, after);
  }
}
