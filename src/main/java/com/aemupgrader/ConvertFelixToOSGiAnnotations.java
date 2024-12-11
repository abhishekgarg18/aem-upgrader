package com.aemupgrader;

import org.checkerframework.errorprone.checker.units.qual.C;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.ReplaceAnnotation;
import org.openrewrite.java.tree.Expression;
import lombok.Value;
import lombok.EqualsAndHashCode;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.service.ImportService;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import org.openrewrite.Recipe;
@Value
@EqualsAndHashCode(callSuper = false)
public class ConvertFelixToOSGiAnnotations extends Recipe {
    String annotationPatternToReplace = "org.apache.felix.scr.annotations.Service";
    String annotationTemplateToInsert="org.apache.felix.scr.annotations.Service";
    @Override
    public String getDisplayName() {
        return "Replace annotation";
    }
    @Override
    public String getDescription() {
        return "Replace an Annotation with another one if the annotation pattern matches. " +
               "Only fixed parameters can be set in the replacement.";
    }
    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                JavaTemplate.Builder templateBuilder = JavaTemplate.builder(annotationTemplateToInsert);
                templateBuilder.javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));

                return new ReplaceFelixToOSGiVisitor(new org.openrewrite.java.AnnotationMatcher(annotationPatternToReplace), templateBuilder.build())
                        .visit(tree, ctx);
            }
        };
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class ReplaceFelixToOSGiVisitor extends JavaIsoVisitor<ExecutionContext> {
        org.openrewrite.java.AnnotationMatcher matcher;
        JavaTemplate replacement;

        @Override
        public J.Annotation visitAnnotation(J.Annotation oldAnnotation, ExecutionContext ctx) {
            J.Annotation annotation = super.visitAnnotation(oldAnnotation, ctx);

            if (TypeUtils.isOfClassType(annotation.getType(),"org.apache.felix.scr.annotations.Service")) {
                maybeRemoveImport(TypeUtils.asFullyQualified(annotation.getType()));
                return null; // Remove the @Service annotation
            }  else if (TypeUtils.isOfClassType(annotation.getType(),"org.apache.felix.scr.annotations.Component")) {
                maybeRemoveImport(TypeUtils.asFullyQualified(annotation.getType()));

                J.ClassDeclaration classDeclaration = getCursor().getParent().getValue();
                String className  = classDeclaration.getName().toString();


                String name = null;
                boolean immediate = false;
                List<Expression> args  = annotation.getArguments();
                for (J argument : args) {
                    if (argument instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) argument;
                        if (assignment.getVariable().toString().equals("name")) {
                            name = assignment.getAssignment().toString();
                        } else if (assignment.getVariable().toString().equals("immediate")) {
                            immediate = Boolean.parseBoolean(assignment.getAssignment().toString());
                        }
                    }
                }

                // Build the new @Component annotation for OSGi
                StringBuilder replacementTemplate = new StringBuilder("@org.osgi.service.component.annotations.Component(");
                if (name != null) {
                    replacementTemplate.append("name = ").append("\"").append(name).append("\"").append(", ");
                }
                replacementTemplate.append("service = ").append(className+".class").append(", ");
                replacementTemplate.append("immediate = ").append(immediate).append(")");

                // Create the replacement annotation
                maybeRemoveImport(TypeUtils.asFullyQualified(annotation.getType()));

                JavaTemplate.Builder templateBuilder = JavaTemplate.builder(replacementTemplate.toString());
                annotation = templateBuilder.build().apply(getCursor(),annotation.getCoordinates().replace());

                maybeAddImport(TypeUtils.asFullyQualified(annotation.getType()));
                maybeAddImport("org.osgi.service.component.annotations.Component");
                doAfterVisit(service(ImportService.class).shortenFullyQualifiedTypeReferencesIn(annotation));

                return annotation ;
            }

            // If @Service is found, we remove it as it's part of the @Component transformation
            /*if (a.getType().getClass().getCanonicalName().equals("org.apache.felix.scr.annotations.Service")) {
                return null; // Remove the @Service annotation
            }*/

            return annotation;
        }
    }
}