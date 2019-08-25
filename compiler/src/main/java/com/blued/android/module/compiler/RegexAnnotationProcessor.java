package com.blued.android.module.compiler;

import com.blued.android.module.interfaces.annotation.RouterRegex;
import com.blued.android.module.interfaces.interfaces.Const;
import com.google.auto.service.AutoService;

import com.squareup.javapoet.CodeBlock;
import com.sun.tools.javac.code.Symbol;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;

@AutoService(Processor.class)
public class RegexAnnotationProcessor extends BaseProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        if (annotations == null || annotations.isEmpty()) {
            return false;
        }
        CodeBlock.Builder builder = CodeBlock.builder();
        String hash = null;
        for (Element element : env.getElementsAnnotatedWith(RouterRegex.class)) {
            if (!(element instanceof Symbol.ClassSymbol)) {
                continue;
            }
            boolean isActivity = isActivity(element);
            boolean isHandler = isHandler(element);
            if (!isActivity && !isHandler) {
                continue;
            }

            Symbol.ClassSymbol cls = (Symbol.ClassSymbol) element;
            RouterRegex regex = cls.getAnnotation(RouterRegex.class);
            if (regex == null) {
                continue;
            }

            if (hash == null) {
                hash = hash(cls.className());
            }

            CodeBlock handler = buildHandler(isActivity, cls);
            CodeBlock interceptors = buildInterceptors(getInterceptors(regex));

            // regex, activityClassName/new Handler(), exported, priority, new Interceptors()
            builder.addStatement("handler.register($S, $L, $L, $L$L)",
                    regex.regex(),
                    handler,
                    regex.exported(),
                    regex.priority(),
                    interceptors
            );
        }
        if (hash == null) {
            hash = randomHash();
        }
        buildHandlerInitClass(builder.build(), "RegexAnnotationInit" + Const.SPLITTER + hash,
                Const.REGEX_ANNOTATION_HANDLER_CLASS, Const.REGEX_ANNOTATION_INIT_CLASS);
        return true;
    }

    private static List<? extends TypeMirror> getInterceptors(RouterRegex regex) {
        try {
            regex.interceptors();
        } catch (MirroredTypesException mte) {
            return mte.getTypeMirrors();
        }
        return null;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        //支持的java版本
        return SourceVersion.RELEASE_7;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return new HashSet<>(Collections.singletonList(RouterRegex.class.getName()));
    }
}
