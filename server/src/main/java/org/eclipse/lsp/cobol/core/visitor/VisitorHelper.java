/*
 * Copyright (c) 2021 Broadcom.
 * The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Broadcom, Inc. - initial API and implementation
 *
 */
package org.eclipse.lsp.cobol.core.visitor;

import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp.cobol.core.model.Locality;
import org.eclipse.lsp.cobol.core.model.tree.variables.ValueInterval;
import org.eclipse.lsp.cobol.core.model.variables.UsageFormat;
import org.eclipse.lsp4j.Range;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.eclipse.lsp.cobol.core.CobolParser.*;

import static java.util.Optional.ofNullable;
import static org.eclipse.lsp.cobol.core.semantics.outline.OutlineNodeNames.FILLER_NAME;

/**
 * Utility class for visitor and delegates classes with useful methods
 */
@UtilityClass
public class VisitorHelper {

  /**
   * Get variable level from TerminalNode object
   * @param terminalNode is a TerminalNode
   * @return a level of defined variable
   */
  public int getLevel(TerminalNode terminalNode) {
    String levelNumber = terminalNode.getText();
    return Integer.parseInt(levelNumber);
  }

  /**
   * Get name of the statement object
   * @param context is a statement context object
   * @return a name of the statement
   */
  public String getName(EntryNameContext context) {
    return ofNullable(context)
            .map(EntryNameContext::dataName)
            .map(VisitorHelper::getName)
            .orElse(FILLER_NAME);
  }

  /**
   * Get name of statement context
   * @param context is a statement context
   * @return a name of statement context
   */
  public String getName(DataNameContext context) {
    return getName(context.cobolWord());
  }

  /**
   * Get name of statement context
   *
   * @param context is a statement context
   * @return a name of statement context
   */
  public String getName(CobolWordContext context) {
    return context.getText().toUpperCase();
  }

  /**
   * Get interval position of 2 localities
   * @param start is a start position
   * @param stop is end position
   * @return Locality with interval position
   */
  public Locality getIntervalPosition(Locality start, Locality stop) {
    return Locality.builder()
        .uri(start.getUri())
        .range(new Range(start.getRange().getStart(), stop.getRange().getEnd()))
        .recognizer(VisitorHelper.class)
        .copybookId(start.getCopybookId())
        .build();
  }

  /**
   * Extract picture texts
   *
   * @param clauses a list of ANTLR picture clauses
   * @return the list of picture texts
   */
  public List<String> retrievePicTexts(List<DataPictureClauseContext> clauses) {
    return clauses.stream()
        .map(clause -> clause.getText().replaceAll(clause.getStart().getText(), "").trim())
        .collect(toList());
  }

  /**
   * Extract value intervals. It's also applicable for raw values. In case of just a value `to` field will be `null`.
   *
   * @param contexts a list of ANTLR value intervals
   * @return the list of value intervals
   */
  public List<ValueInterval> retrieveValueIntervals(List<DataValueIntervalContext> contexts) {
    return contexts.stream()
        .map(context ->
            new ValueInterval(
                context.dataValueIntervalFrom().getText(),
                ofNullable(context.dataValueIntervalTo())
                    .map(DataValueIntervalToContext::getText)
                    .orElse(null)))
        .collect(toList());
  }

  /**
   * Extract usage format from ANTLR usage clause
   *
   * @param contexts a list of ANTLR usage clauses
   * @return the list of usage formats
   */
  public List<UsageFormat> retrieveUsageFormat(List<DataUsageClauseContext> contexts) {
    return contexts.stream()
        .map(DataUsageClauseContext::usageFormat)
        .map(UsageFormatContext::getStart)
        .map(Token::getText)
        .map(UsageFormat::of)
        .collect(toList());
  }

  /**
   * Convert IntegerLiteralContext into an Integer
   *
   * @param context the IntegerLiteralContext
   * @return converted Integer
   */
  public Integer getInteger(IntegerLiteralContext context) {
    return Integer.parseInt(context.getText());
  }
}