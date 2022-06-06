/*
 * Copyright (c) 2022 DAF Trucks NV.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * DAF Trucks NV – implementation of DaCo COBOL statements
 * and DAF development standards
 */
package org.eclipse.lsp.cobol.core.engine.dialects.daco.provider;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp.cobol.core.model.tree.Node;

import java.util.List;

/**
 * Creates DaCo implicit code for future injection
 */
public class DaCoImplicitCodeProvider {

  private static final String WORKING_STORAGE = "WORKING-STORAGE";
  private static final String LINKAGE = "LINKAGE";

  /**
   * Returns DaCo implicit code depend on COBOL text for future injection
   * @param nodes is generated nodes with DaCo dialect processor
   * @return multimap object where the key is a section name and a value is a code to inject
   */
  public Multimap<String, Pair<String, String>> getImplicitCode(List<Node> nodes) {
    Multimap<String, Pair<String, String>> implicitCode = LinkedListMultimap.create();
    implicitCode.put(WORKING_STORAGE, WorkingSectionStaticGenerator.generate());
    WorkingSectionDynamicGenerator.generate(nodes).ifPresent(code -> implicitCode.put(WORKING_STORAGE, code));

    implicitCode.put(LINKAGE, LinkageSectionStaticGenerator.generate());
    return implicitCode;
  }
}
