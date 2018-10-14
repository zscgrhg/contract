package com.zte.crm.framework.contract.processor;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.zte.crm.framework.contract.ProcessorSupport;
import com.zte.crm.framework.contract.ServiceProvider;
import com.zte.crm.framework.contract.annotation.Contract;
import com.zte.crm.framework.contract.annotation.Producers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Set;

@SupportedAnnotationTypes("com.zte.crm.framework.contract.annotation.Producers")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ProducersProcessor extends ProcessorSupport {
    public static final String SP_CLASS = ServiceProvider.class.getCanonicalName();
    public static final String AUTOWIRED_CLASS = Autowired.class.getCanonicalName();
    public static final String RC_CLASS = RestController.class.getCanonicalName();
    public static final String COMP_CLASS = Component.class.getCanonicalName();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> set = roundEnv.getElementsAnnotatedWith(Contract.class);
        Set<? extends Element> contractClasses = roundEnv.getElementsAnnotatedWith(Producers.class);
        contractClasses.forEach(contractClass -> {
            JCTree jct = javacTrees.getTree(contractClass);
            Tree.Kind kind = jct.getKind();
            jct.accept(new TreeTranslator() {
                @Override
                public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                    super.visitClassDef(jcClassDecl);
                    List<JCTree.JCExpression> implementing = jcClassDecl.implementing;
                    ListBuffer<JCTree.JCExpression> buffer = new ListBuffer<>();
                    implementing.forEach(im -> {
                        im.accept(new TreeTranslator() {
                            @Override
                            public void visitIdent(JCTree.JCIdent tree) {
                                super.visitIdent(tree);
                                Contract annotation = tree.sym.getAnnotation(Contract.class);
                                if (annotation != null) {
                                    buffer.append(im);
                                }
                            }
                        });
                    });


                    buffer.forEach(t -> {
                        jcClassDecl.defs = jcClassDecl.defs.prepend(makeStaticInnerClassDecl(t));
                    });

                    JCTree.JCAnnotation comp =
                            make.Annotation(getJavaType(COMP_CLASS),
                                    List.<JCTree.JCExpression>nil());
                    jcClassDecl.mods.annotations = jcClassDecl.mods.annotations.prepend(comp);
                }
            });

        });
        return true;
    }

    private JCTree.JCClassDecl makeStaticInnerClassDecl(JCTree.JCExpression contract) {
        JCTree.JCClassDecl generatedClass = make
                .ClassDef(make.Modifiers(Flags.STATIC | Flags.PUBLIC),
                        javacNames.fromString("WebServiceOf" + contract.type.tsym.name.toString()),
                        List.nil(),
                        null,
                        List.nil(),
                        List.nil());

        JCTree.JCAnnotation annotation =
                make.Annotation(getJavaType(RC_CLASS),
                        List.<JCTree.JCExpression>nil());
        generatedClass.mods.annotations = List.<JCTree.JCAnnotation>of(annotation);
        JCTree.JCTypeApply jcTypeApply = make.TypeApply(getJavaType(SP_CLASS), List.of(contract));
        generatedClass.implementing = List.of(contract, jcTypeApply);
        JCTree.JCAnnotation autowired = make.Annotation(
                getJavaType(AUTOWIRED_CLASS),
                List.<JCTree.JCExpression>nil());

        JCTree.JCVariableDecl decl = fieldDef(make.Modifiers(0L, List.of(autowired)),
                "provider", contract, null);


        generatedClass.defs = generatedClass.defs.prepend(decl);


        JCTree.JCMethodDecl getProvider = make.MethodDef(make.Modifiers(Flags.PUBLIC),
                javacNames.fromString("getProvider"),
                contract, List.nil(), List.nil(), List.nil(),
                block(returnField("this.provider")),
                null);
        generatedClass.defs = generatedClass.defs.prepend(getProvider);

        return generatedClass;
    }
}
