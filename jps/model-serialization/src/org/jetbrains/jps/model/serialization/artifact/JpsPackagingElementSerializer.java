/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.model.serialization.artifact;

import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;

public abstract class JpsPackagingElementSerializer<E extends JpsPackagingElement> {
  private final String myTypeId;
  private final Class<? extends E> myElementClass;

  protected JpsPackagingElementSerializer(String typeId, Class<? extends E> elementClass) {
    myTypeId = typeId;
    myElementClass = elementClass;
  }

  public String getTypeId() {
    return myTypeId;
  }

  public Class<? extends E> getElementClass() {
    return myElementClass;
  }

  public abstract E load(Element element);

  /**
   * @deprecated the build process doesn't save project configuration so there is no need to implement this method, it isn't called by the platform
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public void save(E element, Element tag) {
  }
}
