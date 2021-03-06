/**
 * Licensed to jclouds, Inc. (jclouds) under one or more
 * contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  jclouds licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jclouds.cleanup.data;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A POJO bean we are cleaning up!
 */
public class Bean extends BaseObject {
   // Used to re-order imports
   private static final List<String> ECLIPSE_INPUT_ORDER = ImmutableList.of("import static", "import java.", "import javax.", "import org.", "import ");
   private static final Set<String> DEFAULT_IMPORTS =
         ImmutableSet.of(
               "import static com.google.common.base.Preconditions.checkNotNull;",
               "import java.util.Collections;",
               "import org.jclouds.javax.annotation.Nullable;",
               "import com.google.common.collect.ImmutableList;",
               "import com.google.common.collect.ImmutableMap;",
               "import com.google.common.collect.ImmutableSet;",
               "import com.google.common.base.Objects;",
               "import com.google.common.base.Objects.ToStringHelper;"
         );
   protected final Set<String> imports = Sets.newHashSet(DEFAULT_IMPORTS);
   private final List<InstanceField> instanceFields = Lists.newArrayList();
   private final List<ClassField> staticFields = Lists.newArrayList();
   private final List<InnerClass> innerClasses = Lists.newArrayList();
   private final String packageName;
   private final String superClass;
   private final boolean isAbstract;

   public Bean(String packageName, boolean isAbstract, String type, String superClass, Collection<String> annotations, Collection<String> javadocComment) {
      super(type, annotations, javadocComment);
      this.packageName = packageName;
      this.superClass = superClass;
      this.isAbstract = isAbstract;
   }

   public void addInstanceField(InstanceField field) {
      instanceFields.add(field);
   }

   public void addClassField(ClassField field) {
      staticFields.add(field);
   }

   public void addImports(Collection<String> imports) {
      this.imports.addAll(imports);
   }

   public void addInnerClass(InnerClass innerClass) {
      this.innerClasses.add(innerClass);
   }

   public Collection<String> rawImports() {
      return imports;
   }

   // TODO this should probably be taken care of during parsing, not in this bean!
   public Collection<String> getImports() {
      List<String> result = Lists.newArrayList();
      for (String prefix : ECLIPSE_INPUT_ORDER) {
         boolean needGap = false;
         Iterator<String> importIt = imports.iterator();
         while (importIt.hasNext()) {
            String importLine = importIt.next();
            if (importLine.startsWith(prefix)) {
               result.add(importLine);
               importIt.remove();
               needGap = true;
            }
         }
         if (needGap) result.add("");
      }
      return result;
   }

   public String getPackageName() {
      return packageName;
   }

   public List<InstanceField> getInstanceFields() {
      return instanceFields;
   }

   public List<ClassField> getStaticFields() {
      return staticFields;
   }

   public List<InnerClass> getInnerClasses() {
      return innerClasses;
   }

   public String getSuperClass() {
      return superClass;
   }

   public boolean isAbstract() {
      return isAbstract;
   }

   // Convenience!
   public boolean isSubclass() {
      return superClass != null;
   }
}
