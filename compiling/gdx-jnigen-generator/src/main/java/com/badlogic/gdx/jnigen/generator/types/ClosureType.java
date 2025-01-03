package com.badlogic.gdx.jnigen.generator.types;

import com.badlogic.gdx.jnigen.generator.ClassNameConstants;
import com.badlogic.gdx.jnigen.generator.Manager;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.UnknownType;

import java.util.Arrays;

public class ClosureType implements MappedType, WritableClass {

    private final FunctionSignature signature;
    private final MappedType parent;
    private String comment;

    public ClosureType(FunctionSignature signature, MappedType parent) {
        this.signature = signature;
        this.parent = parent;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public ClassOrInterfaceDeclaration generateClass() {
        return new ClassOrInterfaceDeclaration()
                .setInterface(true)
                .addExtendedType("Closure")
                .addExtendedType(internalClassName())
                .setModifier(Keyword.PUBLIC, true)
                .setName(signature.getName());
    }

    @Override
    public ClassOrInterfaceDeclaration generateClassInternal() {
        return new ClassOrInterfaceDeclaration()
                .setInterface(true)
                .addExtendedType("Closure")
                .setModifier(Keyword.PUBLIC, true)
                .setName(internalClassName());
    }

    public void writeHelper(CompilationUnit cu, ClassOrInterfaceDeclaration closureHelperClass) {
        cu.addImport(classFile() + "." + signature.getName());
        cu.addImport(ClassNameConstants.CLOSUREENCODER_CLASS);

        MethodDeclaration downcallMethod = closureHelperClass.addMethod(getName() + "_downcall", Keyword.PUBLIC, Keyword.STATIC);
        downcallMethod.addParameter(long.class, "fnPtr");
        downcallMethod.setType(getName());

        BlockStmt stmt = downcallMethod.createBody();

        VariableDeclarationExpr encoderDeclaration = new VariableDeclarationExpr(
                new VariableDeclarator()
                        .setType("ClosureEncoder")
                        .setName("encoder")
                        .setInitializer(new ObjectCreationExpr()
                                .setType("ClosureEncoder")
                                .addArgument(new NameExpr("fnPtr"))
                                .addArgument(new FieldAccessExpr(
                                        new NameExpr(internalClassName()),
                                        "__ffi_cache"
                                ))
                        )
        );

        VariableDeclarationExpr useEncoderDeclaration = new VariableDeclarationExpr(
                new VariableDeclarator()
                        .setType("ClosureEncoder")
                        .setName("useEncoder")
                        .setInitializer(new MethodCallExpr(
                                new NameExpr("encoder"),
                                "lockOrDuplicate"
                        ))
        );

        NodeList<Statement> arguments = new NodeList<>();
        for (int i = 0; i < signature.getArguments().length; i++) {
            NamedType argument = signature.getArguments()[i];
            ExpressionStmt setValueStmt = new ExpressionStmt(
                    new MethodCallExpr(new NameExpr("useEncoder"), "setValue")
                            .addArgument(new IntegerLiteralExpr(String.valueOf(i)))
                            .addArgument(new NameExpr(argument.getName()))
            );
            arguments.add(setValueStmt);
        }

        BlockStmt lambdaBody = new BlockStmt()
                .addStatement(new ExpressionStmt(useEncoderDeclaration));

        for (Statement statement : arguments) {
            lambdaBody.addStatement(statement);
        }

        if (signature.getReturnType().getTypeKind() == TypeKind.VOID) {
            lambdaBody.addStatement(new MethodCallExpr(
                    new NameExpr("useEncoder"),
                    "invoke"
            ));
        } else {

            VariableDeclarationExpr returnConvertDeclaration = new VariableDeclarationExpr(
                    new VariableDeclarator()
                            .setType("JavaTypeWrapper")
                            .setName("returnConvert")
                            .setInitializer(new ObjectCreationExpr()
                                    .setType("JavaTypeWrapper")
                                    .addArgument(new ArrayAccessExpr(
                                            new FieldAccessExpr(
                                                    new NameExpr(internalClassName()),
                                                    "__ffi_cache"
                                            ),
                                            new IntegerLiteralExpr("0")
                                    ))
                            )
            );

            ExpressionStmt returnSetValueStatement = new ExpressionStmt(
                    new MethodCallExpr(
                            new NameExpr("returnConvert"),
                            "setValue"
                    ).addArgument(new MethodCallExpr(
                            new NameExpr("useEncoder"),
                            "invoke"
                    ))
            );

            Statement returnStatement = new ReturnStmt(getMethodCallExpr(new NameExpr("returnConvert"), signature.getReturnType()));

            lambdaBody.addStatement(new ExpressionStmt(returnConvertDeclaration))
                    .addStatement(returnSetValueStatement)
                    .addStatement(returnStatement);
        }

        LambdaExpr lambda = new LambdaExpr()
                .setEnclosingParameters(true)
                .setBody(lambdaBody);

        for (NamedType namedType : signature.getArguments()) {
            namedType.getDefinition().getMappedType().importType(cu);
            lambda.addAndGetParameter(new UnknownType(), namedType.getName());
        }

        ReturnStmt returnStmt = new ReturnStmt(lambda);
        stmt.addStatement(new ExpressionStmt(encoderDeclaration));
        stmt.addStatement(returnStmt);
    }

    @Override
    public void write(CompilationUnit cuPublic, ClassOrInterfaceDeclaration toWriteToPublic, CompilationUnit cuPrivate, ClassOrInterfaceDeclaration toWriteToPrivate) {
        String name = signature.getName();
        TypeDefinition returnType = signature.getReturnType();
        NamedType[] arguments = signature.getArguments();
        if (!returnType.getMappedType().isLibFFIConvertible() || !Arrays.stream(arguments).map(namedType -> namedType.getDefinition().getMappedType()).allMatch(MappedType::isLibFFIConvertible)) {
            throw new IllegalArgumentException("Unions are not allowed to be passed via stack from/to a closure, failing closure: " + name);
        }

        importType(cuPublic);
        cuPublic.addImport(ClassNameConstants.CLOSURE_CLASS);
        cuPublic.addImport(internalClass());

        cuPrivate.addImport(ClassNameConstants.JAVATYPEWRAPPER_CLASS);
        cuPrivate.addImport(ClassNameConstants.CTYPEINFO_CLASS);
        cuPrivate.addImport(ClassNameConstants.CLOSURE_CLASS);

        NodeList<Expression> arrayInitializerExpr = new NodeList<>();
        ArrayCreationExpr arrayCreationExpr = new ArrayCreationExpr();
        arrayCreationExpr.setElementType("CTypeInfo");
        arrayCreationExpr.setInitializer(new ArrayInitializerExpr(arrayInitializerExpr));

        toWriteToPrivate.addFieldWithInitializer("CTypeInfo[]", "__ffi_cache", arrayCreationExpr);

        MethodDeclaration callMethod = toWriteToPublic.addMethod(name + "_call");
        if (comment != null)
            callMethod.setJavadocComment(comment);

        callMethod.setBody(null);
        for (NamedType namedType : arguments) {
            namedType.getDefinition().getMappedType().importType(cuPublic);
            callMethod.addAndGetParameter(namedType.getDefinition().getMappedType().abstractType(), namedType.getName());
            MethodCallExpr getTypeID = new MethodCallExpr("getCTypeInfo");
            getTypeID.setScope(new NameExpr("FFITypes"));
            getTypeID.addArgument(new IntegerLiteralExpr(String.valueOf(namedType.getDefinition().getMappedType().typeID())));
            arrayInitializerExpr.add(getTypeID);
        }
        returnType.getMappedType().importType(cuPublic);
        callMethod.setType(returnType.getMappedType().abstractType());

        toWriteToPrivate.addMember(new MethodDeclaration(callMethod.getModifiers(), callMethod.getNameAsString(), callMethod.getType(), callMethod.getParameters()).setBody(null));

        MethodCallExpr getTypeID = new MethodCallExpr("getCTypeInfo");
        getTypeID.setScope(new NameExpr("FFITypes"));
        getTypeID.addArgument(new IntegerLiteralExpr(String.valueOf(returnType.getMappedType().typeID())));
        arrayInitializerExpr.add(0, getTypeID);

        toWriteToPrivate.addMethod("functionSignature", Keyword.DEFAULT)
                .setType("CTypeInfo[]")
                .createBody().addStatement("return __ffi_cache;");

        MethodDeclaration invokeMethod = toWriteToPrivate.addMethod("invoke", Keyword.DEFAULT);
        invokeMethod.addParameter("JavaTypeWrapper[]", "parameters");
        invokeMethod.addParameter("JavaTypeWrapper", "returnType");
        BlockStmt invokeBody = new BlockStmt();
        MethodCallExpr callExpr = new MethodCallExpr(callMethod.getNameAsString());
        for (int i = 0; i < arguments.length; i++) {
            NamedType type = arguments[i];
            TypeDefinition definition = type.getDefinition();
            ArrayAccessExpr arrayAccessExpr = new ArrayAccessExpr(new NameExpr("parameters"), new IntegerLiteralExpr(String.valueOf(i)));
            callExpr.addArgument(getMethodCallExpr(arrayAccessExpr, definition));
        }

        if (returnType.getTypeKind() == TypeKind.VOID) {
            invokeBody.addStatement(callExpr);
        } else {
            MethodCallExpr returnTypeSetMethodCall = new MethodCallExpr("setValue");
            returnTypeSetMethodCall.setScope(new NameExpr("returnType"));
            Statement assertStatement = returnType.getMappedType().assertJava(new NameExpr("_ret"));
            if (!assertStatement.isEmptyStmt()) {
                VariableDeclarationExpr declarationExpr = new VariableDeclarationExpr(new VariableDeclarator(StaticJavaParser.parseType(returnType.getMappedType().abstractType()), "_ret", callExpr));
                returnTypeSetMethodCall.addArgument("_ret");
                invokeBody.addStatement(declarationExpr);
                invokeBody.addStatement(assertStatement);
            } else {
                returnTypeSetMethodCall.addArgument(callExpr);
            }
            invokeBody.addStatement(returnTypeSetMethodCall);
        }

        invokeMethod.setBody(invokeBody);

        writeHelper(cuPrivate, toWriteToPrivate);
    }

    private static Expression getMethodCallExpr(Expression expression, TypeDefinition type) {
        String methodName = "asLong";
        if (type.getTypeKind() == TypeKind.FLOAT)
            methodName = "asFloat";
        else if (type.getTypeKind() == TypeKind.DOUBLE)
            methodName = "asDouble";
        MethodCallExpr methodCallExpr = new MethodCallExpr(methodName);
        methodCallExpr.setScope(expression);
        return type.getMappedType().fromC(methodCallExpr);
    }

    public String getName() {
        return signature.getName();
    }

    public FunctionSignature getSignature() {
        return signature;
    }

    @Override
    public String abstractType() {
        return "ClosureObject<" + signature.getName() + ">";
    }

    @Override
    public String primitiveType() {
        return long.class.getName();
    }

    @Override
    public void importType(CompilationUnit cu) {
        cu.addImport(ClassNameConstants.CLOSUREOBJECT_CLASS);
        cu.addImport(ClassNameConstants.CHANDLER_CLASS);
        if (cu.getClassByName(parent.abstractType()).isPresent())
            return;
        cu.addImport(classFile() + "." + signature.getName());
        cu.addImport(internalClass());
    }

    @Override
    public String classFile() {
        return parent.classFile();
    }

    @Override
    public String packageName() {
        return parent.packageName();
    }

    @Override
    public Expression fromC(Expression cRetrieved) {
        MethodCallExpr methodCallExpr = new MethodCallExpr("getClosureObject");
        methodCallExpr.setScope(new NameExpr("CHandler"));
        methodCallExpr.addArgument(cRetrieved);
        methodCallExpr.addArgument(internalClassName() + "::" + getName() + "_downcall");
        return methodCallExpr;
    }

    @Override
    public Expression toC(Expression cSend) {
        MethodCallExpr methodCallExpr = new MethodCallExpr("getPointer");
        methodCallExpr.setScope(cSend);
        return methodCallExpr;
    }

    @Override
    public int typeID() {
        return Manager.POINTER_FFI_ID;
    }

    @Override
    public String internalClassName() {
        return getName() + "_Internal";
    }

    @Override
    public String internalClass() {
        return parent.internalClass() + "." + internalClassName();
    }
}
