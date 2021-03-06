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
package org.jclouds.cleanup.doclet;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.*;
import com.google.common.io.NullOutputStream;
import com.sun.javadoc.*;
import org.jclouds.cleanup.data.Bean;
import org.jclouds.cleanup.data.ClassField;
import org.jclouds.cleanup.data.InnerClass;
import org.jclouds.cleanup.data.InstanceField;
import org.jclouds.cleanup.output.IndentedPrintWriter;
import org.jclouds.util.Strings2;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class ClassDocParser {
   protected Collection<String> getAnnotations(ProgramElementDoc element) {
      List<String> result = Lists.newArrayList();
      for (AnnotationDesc atree : element.annotations()) {
         String tmp = "@" + atree.annotationType().simpleTypeName();
         if (atree.elementValues().length > 0) {
            tmp += "(" + Joiner.on(", ").join(atree.elementValues()) + ")";
         }
         result.add(tmp);
      }
      return result;
   }

   protected boolean annotatatedAsNullable(ProgramElementDoc element) {
      boolean shouldBeRequired = false;
      for (AnnotationDesc atree : element.annotations()) {
         if (Objects.equal("Nullable", atree.annotationType().simpleTypeName())) {
            return true;
         }
         if (ImmutableSet.of("XmlElement", "XmlAttribute", "XmlElementRef").contains(atree.annotationType().simpleTypeName())) {
            shouldBeRequired = true;
            for (AnnotationDesc.ElementValuePair evp : atree.elementValues()) {
               if (Objects.equal("required", evp.element().name()) && Objects.equal(Boolean.TRUE, evp.value().value())) {
                  return false;
               }
            }
         }
      }
      // Note: if we've determined that it should be marked as required we return true
      return shouldBeRequired;
   }

   private Collection<String> extractComment(ProgramElementDoc element) {
      return extractComment(null, null, element);
   }

   private Collection<String> extractComment(String defaultCommentText,
                                             Multimap<String, String> defaultTags, ProgramElementDoc... elements) {
      String commentText = null;
      for (ProgramElementDoc element : elements) {
         if (element != null && element.commentText() != null && !element.commentText().trim().isEmpty()) {
            commentText = element.commentText();
            break;
         }
      }

      if (commentText == null) {
         commentText = defaultCommentText;
      }

      Multimap<String, String> tagsToOutput = LinkedListMultimap.create();
      if (defaultTags != null) tagsToOutput.putAll(defaultTags);

      // TODO multimap?
      for (ProgramElementDoc element : elements) {
         if (element != null) {
            for (Tag tag : element.tags()) {
               tagsToOutput.put(tag.name(), tag.text());
            }
         }
      }

      if (commentText == null && tagsToOutput.isEmpty()) return ImmutableList.of();

      List<String> result = Lists.newArrayList();

      if (commentText != null) {
         for (String line : commentText.split("\n")) {
            result.add(line.trim());
         }
         if (!tagsToOutput.isEmpty()) {
            result.add("");
         }
      }

      for (Map.Entry<String, String> tag : tagsToOutput.entries()) {
         result.add(tag.getKey() + " " + tag.getValue());
      }

      return result;
   }

   protected String properTypeName(FieldDoc field, Collection<String> imports) {
      return fixupTypeName(field.type(), field.containingPackage(), imports);
   }

   protected String fixupTypeName(Type type, PackageDoc currentPackage, Collection<String> imports) {
      String fieldType = removeUnnecessaryPackages(type, currentPackage, imports);
      if (type.asParameterizedType() != null) {
         fieldType += "<";
         for (Type paramType : type.asParameterizedType().typeArguments()) {
            fieldType += fixupTypeName(paramType, currentPackage, imports) + ", ";
         }
         fieldType = fieldType.substring(0, fieldType.length() - 2);
         fieldType += ">";
      }
      return fieldType;
   }

   protected String removeUnnecessaryPackages(Type type, PackageDoc currentPackage, Collection<String> imports) {
      String fieldType = type.simpleTypeName();
      if (type.isPrimitive() || type.asClassDoc().containingPackage().name().equals("java.lang")) {
         ;
      } else if (type.asClassDoc().containingPackage().equals(currentPackage) ||
            imports.contains("import " + type.qualifiedTypeName() + ";") ||
            imports.contains("import " + type.asClassDoc().containingPackage().name() + "*" + ";")) {
         if (type.asClassDoc().containingClass() != null) {
            fieldType = type.asClassDoc().containingClass().simpleTypeName() + "." + fieldType;
         }
      } else {
         fieldType = type.asClassDoc().containingPackage() + "." + fieldType;
      }
      return fieldType;
   }
   
   public Bean parseBean(ClassDoc element) {
      String superClass = null;
      if (element.superclassType() != null && !Objects.equal(element.superclassType().qualifiedTypeName(), "java.lang.Object")) {
         superClass = element.superclassType().simpleTypeName();
      }
      
      
      Bean bean = new Bean(element.containingPackage().toString(), element.isAbstract(), element.simpleTypeName(), superClass, getAnnotations(element), extractComment("Class " + element.name(), null, element));

      // Process imports
      List<String> lines;
      try {
         // This is actually helpful (oddly!)
         lines = ImmutableList.copyOf(Strings2.toStringAndClose(new FileInputStream(element.position().file())).split("\n"));
      } catch (IOException ex) {
         throw Throwables.propagate(ex);
      }

      bean.addImports(Sets.filter(ImmutableSet.copyOf(lines), new Predicate<String>() {
         @Override
         public boolean apply(String input) {
            return input.trim().startsWith("import ");
         }
      }));

      // TODO this is losing the existing indent
      // Process inner classes
      for (ClassDoc clazz : element.innerClasses()) {
         if (!clazz.simpleTypeName().toLowerCase().endsWith("builder")) {
            List<String> content = Lists.newArrayList();
            IndentedPrintWriter ipw = new IndentedPrintWriter(new NullOutputStream());
            ipw.println("{");
            for (int i = clazz.position().line(); ipw.currentIndent() > 0 && i < lines.size(); i++) {
               ipw.println(lines.get(i));
               content.add(lines.get(i).trim());
            }
            content.remove(content.size() - 1);
            bean.addInnerClass(new InnerClass("public static " + (clazz.isEnum() ? "enum" : "class"), clazz.simpleTypeName(), getAnnotations(clazz), extractComment(clazz), content));
         }
      }

      // Process fields
      for (FieldDoc field : element.fields()) {
         if (field.isStatic()) {
            bean.addClassField(new ClassField(field.name(), properTypeName(field, bean.rawImports()), getAnnotations(field), extractComment(null, ImmutableMultimap.<String, String>of(), field)));
         } else {
            // Note we need to pick up any stray comments or annotations on accessors
            InstanceField instanceField = new InstanceField(field.name(), properTypeName(field, bean.rawImports()), annotatatedAsNullable(field), getAnnotations(field), extractComment(null, ImmutableMultimap.<String, String>of(), field));
            for (MethodDoc method : element.methods()) {
               if (Objects.equal(method.name(), instanceField.getAccessorName()) ||
                     Objects.equal(method.name(), instanceField.getName())) {
                  instanceField.addAnnotations(getAnnotations(method));
                  instanceField.adjustJavaDoc(extractComment(method));
               }
            }
            bean.addInstanceField(instanceField);
         }
      }

      return bean;
   }

}
