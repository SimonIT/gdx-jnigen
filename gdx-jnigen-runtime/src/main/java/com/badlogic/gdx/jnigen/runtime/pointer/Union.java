package com.badlogic.gdx.jnigen.runtime.pointer;

public abstract class Union extends StackElement {

    protected Union(long pointer, boolean freeOnGC) {
        super(pointer, freeOnGC);
    }

    protected Union(int size) {
        super(size);
    }

    @Override
    public boolean hasElementOffsets() {
        return false;
    }
}
