package com.zte.crm.framework.contract.processor;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.ListBuffer;
import com.zte.crm.framework.contract.Delegate;
import com.zte.crm.framework.contract.ProcessorSupport;
import com.zte.crm.framework.contract.annotation.Contract;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Set;

import static com.sun.source.tree.Tree.Kind.INTERFACE;

@SupportedAnnotationTypes("com.zte.crm.framework.contract.annotation.Contract")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ContractProcessor extends ProcessorSupport {

    private static final String CLASS_DELEGATE = Delegate.class.getCanonicalName();
    private static final String CLASS_UNSUPPORTED = UnsupportedOperationException.class.getCanonicalName();
    private static final String CLASS_OBJECT = Object.class.getCanonicalName();
    private static final String NOT_IMPLEMENTED = "this feature is not yet implemented!";


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> contractClasses = roundEnv.getElementsAnnotatedWith(Contract.class);
        contractClasses.forEach(contractClass -> {
            JCTree jct = javacTrees.getTree(contractClass);
            Tree.Kind kind = jct.getKind();
            if (INTERFACE == kind) {
                Type.ClassType contractType = (Type.ClassType) contractClass.asType();
                jct.accept(new TreeTranslator() {


                    @Override
                    public void visitMethodDef(JCTree.JCMethodDecl jcMethodDecl) {
                        super.visitMethodDef(jcMethodDecl);

                        boolean isDefault = (jcMethodDecl.mods.flags & Flags.DEFAULT) == Flags.DEFAULT;
                        if (!isDefault) {
                            addDefault(contractType, jcMethodDecl);
                        }

                    }
                });
            }

        });
        return true;
    }

    private void addDefault(Type type, JCTree.JCMethodDecl jcMethodDecl) {
        jcMethodDecl.mods.flags = jcMethodDecl.mods.flags | Flags.DEFAULT;
        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
        JCTree.JCBlock ifProvider = addDelegateBlock(type, jcMethodDecl);
        JCTree.JCStatement ifstmt = make.If(make.TypeTest(make.Ident(javacNames.fromString("this")),
                getJavaType(CLASS_DELEGATE)),
                ifProvider,
                null);
        statements.append(ifstmt);
        statements.add(throwByName(CLASS_UNSUPPORTED, NOT_IMPLEMENTED));
        JCTree.JCBlock body = make.Block(0, statements.toList());
        jcMethodDecl.body = body;
    }


    private JCTree.JCBlock addDelegateBlock(Type type, JCTree.JCMethodDecl jcMethodDecl) {
        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
        JCTree.JCTypeCast cast2delegate = castIdent("this", CLASS_DELEGATE);
        JCTree.JCVariableDecl delegateVar = varDef("delegate", CLASS_DELEGATE, cast2delegate);
        statements.append(delegateVar);
        JCTree.JCExpression getExpr = invoke("delegate.getProducer");
        JCTree.JCVariableDecl producerVar = varDef("producer", CLASS_OBJECT, getExpr);
        statements.append(producerVar);
        JCTree.JCExpression contractType = make.Type(type);
        JCTree.JCVariableDecl contractorVar = varDef("contractor", contractType, make.TypeCast(contractType, getExpr));
        JCTree.JCReturn ppp = make.Return(invokeWithVarDecl("contractor", jcMethodDecl.name, jcMethodDecl.params));
        JCTree.JCStatement ifins = make.If(make.TypeTest(make.Ident(javacNames.fromString("producer")),
                contractType),
                block(contractorVar,
                        ppp
                ),
                null);
        statements.append(ifins);
        JCTree.JCBlock body = make.Block(0, statements.toList());
        return body;
    }


}
