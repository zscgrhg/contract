package com.zte.crm.framework.contract.processor;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.ListBuffer;
import com.zte.crm.framework.contract.ProcessorSupport;
import com.zte.crm.framework.contract.annotation.Contract;
import com.zte.crm.framework.contract.annotation.CopyDefault;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sun.source.tree.Tree.Kind.INTERFACE;

@SupportedAnnotationTypes("com.zte.crm.framework.contract.annotation.CopyDefault")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class CopyDefaultProcessor extends ProcessorSupport {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> targetClasses = roundEnv.getElementsAnnotatedWith(CopyDefault.class);
        targetClasses.forEach(tc -> {
            JCTree jct = javacTrees.getTree(tc);
            Tree.Kind kind = jct.getKind();
            if (INTERFACE == kind) {
                Type.ClassType clazz = (Type.ClassType) tc.asType();

                ListBuffer<Type.ClassType> contracts = new ListBuffer<>();
                Set<String> key = new HashSet<>();
                filter(contracts, clazz.all_interfaces_field);
                jct.accept(new TreeTranslator() {
                    @Override
                    public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                        super.visitClassDef(jcClassDecl);
                        contracts.forEach(c -> copyMethod(c, jcClassDecl, key));
                    }
                });
            }

        });
        return true;
    }

    private void filter(ListBuffer<Type.ClassType> buffer, List<Type> source) {
        //f.tsym.getAnnotationsByType(Contract.class).length > 0
        if (source.isEmpty()) {
            return;
        }
        source.stream()
                .filter(f -> f.tsym.getAnnotationsByType(Contract.class).length > 0)
                .map(f -> (Type.ClassType) f)
                .forEach(buffer::append);
        List<Type> con = source.stream()
                .map(f -> (Type.ClassType) f)
                .flatMap(f ->
                        Optional.ofNullable(f.all_interfaces_field)
                                .map(t -> t.stream())
                                .orElse(Stream.empty())
                )
                .collect(Collectors.toList());
        filter(buffer, con);
    }

    private void copyMethod(Type.ClassType from, JCTree.JCClassDecl to, Set<String> key) {
        List<Symbol> enclosedElements = from.tsym.getEnclosedElements();
        enclosedElements.stream()
                .filter(e -> e instanceof Symbol.MethodSymbol)
                .map(e -> (Symbol.MethodSymbol) e)
                .filter(e -> !key.contains(e.toString()))
                .filter(e -> e.isDefault())
                .forEach(msym -> {
                    Symbol.MethodSymbol copyed
                            = new Symbol.MethodSymbol(msym.flags_field & (~Flags.DEFAULT),
                            msym.name, msym.type, to.sym);
                    copyed.params = msym.params;
                    copyed.savedParameterNames = msym.savedParameterNames;
                    copyed.appendAttributes(msym.getDeclarationAttributes());
                    to.defs = to.defs.prepend(make.MethodDef(copyed, null));
                    key.add(msym.toString());
                });
        System.out.println(enclosedElements);
    }
}
