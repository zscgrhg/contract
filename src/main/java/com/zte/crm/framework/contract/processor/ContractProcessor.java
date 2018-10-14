package com.zte.crm.framework.contract.processor;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.ListBuffer;
import com.zte.crm.framework.contract.ContractNotImplementedException;
import com.zte.crm.framework.contract.ProcessorSupport;
import com.zte.crm.framework.contract.ServiceProvider;
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

    private static final String PROVIDER_CLASS = ServiceProvider.class.getCanonicalName();
    private static final String CNIE_CLASS = ContractNotImplementedException.class.getCanonicalName();
    private static final String OBJECT_CLASS = Object.class.getCanonicalName();


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
                getJavaType(PROVIDER_CLASS)),
                ifProvider,
                null);
        statements.append(ifstmt);
        statements.add(throwByName(CNIE_CLASS));
        JCTree.JCBlock body = make.Block(0, statements.toList());
        jcMethodDecl.body = body;
    }

    //com.zte.crm.framework.contract.annotation.Contract.ServiceProvider


    private JCTree.JCBlock addDelegateBlock(Type type, JCTree.JCMethodDecl jcMethodDecl) {
        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
        JCTree.JCTypeCast cast = castIdent("this", PROVIDER_CLASS);
        JCTree.JCVariableDecl decl = varDef("sp", PROVIDER_CLASS, cast);
        statements.append(decl);
        JCTree.JCExpression expr = invoke("sp.getProvider");
        JCTree.JCVariableDecl declp = varDef("p", OBJECT_CLASS, expr);
        statements.append(declp);
        JCTree.JCExpression type1 = make.Type(type);
        JCTree.JCVariableDecl decl1 = varDef("ppp", type1, make.TypeCast(type1, expr));
        JCTree.JCReturn ppp = make.Return(invokeWithVarDecl("ppp", jcMethodDecl.name, jcMethodDecl.params));
        JCTree.JCStatement ifins = make.If(make.TypeTest(make.Ident(javacNames.fromString("p")),
                type1),
                block(decl1,
                        ppp
                ),
                null);
        statements.append(ifins);
        JCTree.JCBlock body = make.Block(0, statements.toList());
        return body;
    }





}
