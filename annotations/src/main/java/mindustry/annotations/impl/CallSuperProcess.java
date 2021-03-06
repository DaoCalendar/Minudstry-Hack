package mindustry.annotations.impl;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import mindustry.annotations.Annotations.CallSuper;
import mindustry.annotations.Annotations.OverrideCallSuper;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes({"java.lang.Override"})
public class CallSuperProcess extends AbstractProcessor{
    private Trees trees;

    @Override
    public void init(ProcessingEnvironment pe){
        super.init(pe);
        trees = Trees.instance(pe);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv){
        for(Element e : roundEnv.getElementsAnnotatedWith(Override.class)){
            if(e.getAnnotation(OverrideCallSuper.class) != null) return false;

            CodeAnalyzerTreeScanner codeScanner = new CodeAnalyzerTreeScanner();
            codeScanner.methodName = e.getSimpleName().toString();

            TreePath tp = trees.getPath(e.getEnclosingElement());
            codeScanner.scan(tp, trees);

            if(codeScanner.callSuperUsed){
                List list = codeScanner.method.getBody().getStatements();

                if(!doesCallSuper(list, codeScanner.methodName)){
                    processingEnv.getMessager().printMessage(Kind.ERROR, "Overriding method '" + codeScanner.methodName + "' must explicitly call super method from its parent class.", e);
                }
            }
        }

        return false;
    }

    private boolean doesCallSuper(List list, String methodName){
        for(Object object : list){
            if(object instanceof JCTree.JCExpressionStatement){
                JCTree.JCExpressionStatement expr = (JCExpressionStatement)object;
                String exprString = expr.toString();
                if(exprString.startsWith("super." + methodName) && exprString.endsWith(");")) return true;
            }
        }

        return false;
    }

    @Override
    public SourceVersion getSupportedSourceVersion(){
        return SourceVersion.RELEASE_8;
    }

    static class CodeAnalyzerTreeScanner extends TreePathScanner<Object, Trees>{
        private String methodName;
        private MethodTree method;
        private boolean callSuperUsed;

        @Override
        public Object visitClass(ClassTree classTree, Trees trees){
            Tree extendTree = classTree.getExtendsClause();

            if(extendTree instanceof JCTypeApply){ //generic classes case
                JCTypeApply generic = (JCTypeApply)extendTree;
                extendTree = generic.clazz;
            }

            if(extendTree instanceof JCIdent){
                JCIdent tree = (JCIdent)extendTree;
                com.sun.tools.javac.code.Scope members = tree.sym.members();

                if(checkScope(members))
                    return super.visitClass(classTree, trees);

                if(checkSuperTypes((ClassType)tree.type))
                    return super.visitClass(classTree, trees);

            }
            callSuperUsed = false;

            return super.visitClass(classTree, trees);
        }

        public boolean checkSuperTypes(ClassType type){
            if(type.supertype_field != null && type.supertype_field.tsym != null){
                if(checkScope(type.supertype_field.tsym.members()))
                    return true;
                else
                    return checkSuperTypes((ClassType)type.supertype_field);
            }

            return false;
        }

        @SuppressWarnings("unchecked")
        public boolean checkScope(Scope members){
            Iterable<Symbol> it;
            try{
                it = (Iterable<Symbol>)members.getClass().getMethod("getElements").invoke(members);
            }catch(Throwable t){
                try{
                    it = (Iterable<Symbol>)members.getClass().getMethod("getSymbols").invoke(members);
                }catch(Exception e){
                    throw new RuntimeException(e);
                }
            }

            for(Symbol s : it){

                if(s instanceof MethodSymbol){
                    MethodSymbol ms = (MethodSymbol)s;

                    if(ms.getSimpleName().toString().equals(methodName)){
                        Annotation annotation = ms.getAnnotation(CallSuper.class);
                        if(annotation != null){
                            callSuperUsed = true;
                            return true;
                        }
                    }
                }
            }

            return false;
        }

        @Override
        public Object visitMethod(MethodTree methodTree, Trees trees){
            if(methodTree.getName().toString().equals(methodName))
                method = methodTree;

            return super.visitMethod(methodTree, trees);
        }

    }
}
