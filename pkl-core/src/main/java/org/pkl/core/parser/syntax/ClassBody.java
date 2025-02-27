/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pkl.core.parser.syntax;

import java.util.ArrayList;
import java.util.List;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

public class ClassBody extends AbstractNode {

  public ClassBody(List<Node> nodes, Span span) {
    super(span, nodes);
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitClassBody(this);
  }

  public List<ClassProperty> getProperties() {
    var props = new ArrayList<ClassProperty>();
    assert children != null;
    for (var child : children) {
      if (child instanceof ClassProperty prop) {
        props.add(prop);
      }
    }
    return props;
  }

  public List<ClassMethod> getMethods() {
    var methods = new ArrayList<ClassMethod>();
    assert children != null;
    for (var child : children) {
      if (child instanceof ClassMethod method) {
        methods.add(method);
      }
    }
    return methods;
  }
}
