package org.jctools.channels.mapping;

public final class Variable {

    public final String type;
    public final String name;
    public final int fieldOffset;
    public final String unsafeMethodSuffix;

    public Variable(String type, String name, int fieldOffset, String unsafeMethodSuffix) {
        this.type = type;
        this.name = name;
        this.fieldOffset = fieldOffset;
        this.unsafeMethodSuffix = unsafeMethodSuffix;
    }

}
