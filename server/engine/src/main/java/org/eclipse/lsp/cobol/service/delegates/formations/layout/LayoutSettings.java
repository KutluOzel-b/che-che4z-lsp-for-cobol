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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.Locale;
import lombok.Builder;
import lombok.Value;

/**
 * User configurable options of the COBOL layout formatter. Mirrors the {@code
 * cobol-lsp.formatter.*} client settings.
 */
@Value
@Builder(toBuilder = true)
public class LayoutSettings {
  private static final int DEFAULT_INDENT = 4;
  private static final int MAX_INDENT = 16;

  /** Source reference format of the document. */
  public enum SourceFormat {
    FIXED,
    FREE
  }

  @Builder.Default boolean enabled = true;
  @Builder.Default SourceFormat sourceFormat = SourceFormat.FIXED;
  @Builder.Default boolean singleQuote = true;
  @Builder.Default int indentWidth = DEFAULT_INDENT;
  @Builder.Default boolean useTabs = false;
  @Builder.Default boolean alignPicClauses = true;
  @Builder.Default boolean alignValueClauses = true;
  @Builder.Default boolean alignUsageClauses = true;
  @Builder.Default boolean standalonePeriods = false;
  @Builder.Default boolean requireScopeTerminators = false;
  @Builder.Default boolean spaceBeforePunctuation = false;
  @Builder.Default boolean preserveSequenceNumbers = true;

  /**
   * Create settings from the JSON representation sent by the client. Missing or invalid values
   * fall back to defaults.
   *
   * @param json the {@code cobol-lsp.formatter} configuration object as JSON text, may be null
   * @return parsed settings
   */
  public static LayoutSettings fromJson(String json) {
    LayoutSettings defaults = LayoutSettings.builder().build();
    if (json == null) {
      return defaults;
    }
    JsonElement element;
    try {
      element = JsonParser.parseString(json);
    } catch (RuntimeException e) {
      return defaults;
    }
    if (!element.isJsonObject()) {
      return defaults;
    }
    JsonObject root = element.getAsJsonObject();
    JsonObject data =
        root.has("dataDivision") && root.get("dataDivision").isJsonObject()
            ? root.getAsJsonObject("dataDivision")
            : new JsonObject();
    return LayoutSettings.builder()
        .enabled(bool(root, "enabled", defaults.enabled))
        .sourceFormat(format(root, defaults.sourceFormat))
        .singleQuote(bool(root, "singleQuote", defaults.singleQuote))
        .indentWidth(indent(root, defaults.indentWidth))
        .useTabs(bool(root, "useTabs", defaults.useTabs))
        .alignPicClauses(bool(data, "alignPicClauses", defaults.alignPicClauses))
        .alignValueClauses(bool(data, "alignValueClauses", defaults.alignValueClauses))
        .alignUsageClauses(bool(data, "alignUsageClauses", defaults.alignUsageClauses))
        .standalonePeriods(bool(root, "standalonePeriods", defaults.standalonePeriods))
        .requireScopeTerminators(
            bool(root, "requireScopeTerminators", defaults.requireScopeTerminators))
        .spaceBeforePunctuation(
            bool(root, "spaceBeforePunctuation", defaults.spaceBeforePunctuation))
        .preserveSequenceNumbers(
            bool(root, "preserveSequenceNumbers", defaults.preserveSequenceNumbers))
        .build();
  }

  private static JsonPrimitive primitive(JsonObject object, String key) {
    JsonElement value = object.get(key);
    return value != null && value.isJsonPrimitive() ? value.getAsJsonPrimitive() : null;
  }

  private static boolean bool(JsonObject object, String key, boolean fallback) {
    JsonPrimitive value = primitive(object, key);
    return value != null && value.isBoolean() ? value.getAsBoolean() : fallback;
  }

  private static int indent(JsonObject object, int fallback) {
    JsonPrimitive value = primitive(object, "indentWidth");
    if (value == null || !value.isNumber()) {
      return fallback;
    }
    int indent = value.getAsInt();
    return indent < 0 || indent > MAX_INDENT ? fallback : indent;
  }

  private static SourceFormat format(JsonObject object, SourceFormat fallback) {
    JsonPrimitive value = primitive(object, "sourceFormat");
    if (value == null || !value.isString()) {
      return fallback;
    }
    try {
      return SourceFormat.valueOf(value.getAsString().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return fallback;
    }
  }
}
