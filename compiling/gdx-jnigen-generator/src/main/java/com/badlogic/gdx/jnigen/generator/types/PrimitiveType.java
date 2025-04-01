package com.badlogic.gdx.jnigen.generator.types;

import com.badlogic.gdx.jnigen.generator.Manager;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BinaryExpr.Operator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;

public class PrimitiveType implements MappedType {

    private final Class<?> javaRepresentation;

    private final TypeDefinition definition;

    public PrimitiveType(TypeDefinition typeDefinition, Class<?> javaRepresentation) {
        this.definition = typeDefinition;
        this.javaRepresentation = javaRepresentation;
    }

    public static PrimitiveType fromTypeDefinition(TypeDefinition definition) {
        if (definition.getTypeKind().isSpecial())
            throw new IllegalArgumentException();
        switch (definition.getTypeKind()) {
        case VOID:
            return new PrimitiveType(definition, void.class);
        case BYTE:
            return new PrimitiveType(definition, byte.class);
        case PROMOTED_BYTE:
        case CHAR:
            return new PrimitiveType(definition, char.class);
        case SHORT:
            return new PrimitiveType(definition, short.class);
        case INT:
            return new PrimitiveType(definition, int.class);
        case PROMOTED_INT:
        case PROMOTED_LONG:
        case LONG_LONG:
        case PROMOTED_LONG_LONG:
        case LONG:
            return new PrimitiveType(definition, long.class);
        case FLOAT:
            return new PrimitiveType(definition, float.class);
        case DOUBLE:
            return new PrimitiveType(definition, double.class);
        case BOOLEAN:
            return new PrimitiveType(definition, boolean.class);
        default:
            throw new IllegalArgumentException(definition.getTypeName() + " is not primitive.");
        }
    }

    @Override
    public String abstractType() {
        return javaRepresentation.getName();
    }

    @Override
    public String primitiveType() {
        return javaRepresentation.getName();
    }

    @Override
    public void importType(CompilationUnit cu) {
        // Unimportable
    }

    @Override
    public String classFile() {
        throw new IllegalArgumentException();
    }

    @Override
    public String packageName() {
        throw new IllegalArgumentException();
    }

    @Override
    public Expression fromC(Expression cRetrieved) {
        if (javaRepresentation == boolean.class) {
            BinaryExpr compare = new BinaryExpr();
            compare.setLeft(cRetrieved);
            compare.setOperator(Operator.NOT_EQUALS);
            compare.setRight(new IntegerLiteralExpr("0"));
            return compare;
        } else {
            CastExpr castExpr = new CastExpr();
            castExpr.setType(javaRepresentation);
            castExpr.setExpression(cRetrieved);
            return castExpr;
        }
    }

    @Override
    public Expression toC(Expression cSend) {
        return cSend;
    }

    @Override
    public int typeID() {
        if (definition.getTypeKind() == TypeKind.VOID)
            return Manager.VOID_FFI_ID;
        else
            return Manager.getInstance().getCTypeID(definition.getTypeName());
    }
}
