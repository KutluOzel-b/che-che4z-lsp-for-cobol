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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.eclipse.lsp.cobol.service.CobolDocumentModel;
import org.eclipse.lsp.cobol.service.delegates.formations.layout.LayoutSettings;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

public class TrimFormation implements Formation {

  private static final Pattern RTRIM = Pattern.compile("\\s+$");
  private static final int CONTINUATION_INDICATOR = 6;

  private static String rtrim(String s) {
    return RTRIM.matcher(s).replaceAll("");
  }

  @Override
  public List<TextEdit> format(
      @NonNull List<CobolDocumentModel.Line> lines, List<String> settings) {
    List<TextEdit> formattedText = new ArrayList<>();
    String json =
        settings != null && settings.size() > LayoutFormation.SETTINGS_INDEX
            ? settings.get(LayoutFormation.SETTINGS_INDEX)
            : null;
    LayoutSettings layout =
        LayoutSettings.fromJson(
            json,
            lines.stream().map(CobolDocumentModel.Line::getText).collect(Collectors.toList()));
    for (int i = 0; i < lines.size(); i++) {
      CobolDocumentModel.Line line = lines.get(i);
      if (layout.isEnabled()
          && layout.isPreserveFixedPadding()
          && layout.isPreserveSequenceNumbers()
          && line.getText().length() == 80) {
        continue;
      }
      // trailing spaces of a literal continued on the next line are part of the literal
      if (i + 1 < lines.size() && isContinuationLine(lines.get(i + 1).getText())) {
        continue;
      }
      String newText = rtrim(line.getText());
      formattedText.add(addNewTextEditLine(line, newText));
    }
    return formattedText;
  }

  private static boolean isContinuationLine(String text) {
    return text.length() > CONTINUATION_INDICATOR
        && text.charAt(CONTINUATION_INDICATOR) == '-';
  }

  private static TextEdit addNewTextEditLine(CobolDocumentModel.Line line, String text) {
    return new TextEdit(addNewRange(line), text);
  }

  private static Range addNewRange(CobolDocumentModel.Line line) {
    return new Range(
        new Position(line.getNumber(), 0), new Position(line.getNumber(), line.getText().length()));
  }
}
