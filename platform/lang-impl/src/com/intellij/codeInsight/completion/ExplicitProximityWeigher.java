/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class ExplicitProximityWeigher extends CompletionWeigher {

  @Override
  public Integer weigh(@NotNull final LookupElement item, @NotNull final CompletionLocation location) {
    PrioritizedLookupElement<?> prioritized = item.as(PrioritizedLookupElement.CLASS_CONDITION_KEY);
    return prioritized != null ? prioritized.getExplicitProximity() : 0;
  }
}
