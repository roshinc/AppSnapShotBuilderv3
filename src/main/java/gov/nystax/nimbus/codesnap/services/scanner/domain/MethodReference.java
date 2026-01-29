package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents a method reference with its fully qualified signature and access modifier.
 */
public class MethodReference {

    @JsonProperty("methodName")
    private String methodName;
    @JsonProperty("accessModifier")
    private MethodAccessModifier accessModifier;

    public MethodReference(String methodName, MethodAccessModifier accessModifier) {
        this.methodName = methodName;
        this.accessModifier = accessModifier;
    }

    public String getMethodName() {
        return methodName;
    }

    public MethodAccessModifier getAccessModifier() {
        return accessModifier;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MethodReference that)) return false;
        return Objects.equals(getMethodName(), that.getMethodName()) && getAccessModifier() == that.getAccessModifier();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMethodName(), getAccessModifier());
    }

    @Override
    public String toString() {
        return "MethodReference{" +
                "methodName='" + methodName + '\'' +
                ", accessModifier=" + accessModifier +
                '}';
    }

    public enum MethodAccessModifier {
        PUBLIC, PROTECTED, DEFAULT, PRIVATE;
    }
}

