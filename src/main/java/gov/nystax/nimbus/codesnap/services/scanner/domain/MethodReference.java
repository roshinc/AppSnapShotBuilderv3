package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a reference to a method with its access modifier.
 */
public class MethodReference {
    
    public enum MethodAccessModifier {
        PUBLIC,
        PROTECTED,
        PRIVATE,
        PACKAGE_PRIVATE
    }
    
    private final String methodSignature;
    private final MethodAccessModifier accessModifier;
    
    @JsonCreator
    public MethodReference(
            @JsonProperty("methodSignature") String methodSignature,
            @JsonProperty("accessModifier") MethodAccessModifier accessModifier) {
        this.methodSignature = methodSignature;
        this.accessModifier = accessModifier;
    }
    
    public String getMethodSignature() {
        return methodSignature;
    }
    
    public String getMethodName() {
        return methodSignature;
    }
    
    public MethodAccessModifier getAccessModifier() {
        return accessModifier;
    }
    
    @Override
    public String toString() {
        return methodSignature + " [" + accessModifier + "]";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodReference that = (MethodReference) o;
        return methodSignature.equals(that.methodSignature);
    }
    
    @Override
    public int hashCode() {
        return methodSignature.hashCode();
    }
}
