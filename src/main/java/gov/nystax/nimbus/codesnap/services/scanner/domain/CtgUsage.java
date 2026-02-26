package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the usage information for a CTG (CICS Transaction Gateway) component.
 * Groups all invocations of the same CTG component (identified by the
 * {@code @CTGClient} annotation value).
 */
public class CtgUsage {

    @JsonProperty("ctgComponentId")
    private String ctgComponentId;

    @JsonProperty("invocations")
    private List<CtgInvocation> invocations;

    public CtgUsage() {
    }

    public CtgUsage(String ctgComponentId) {
        this.ctgComponentId = ctgComponentId;
    }

    public String getCtgComponentId() {
        return ctgComponentId;
    }

    public void setCtgComponentId(String ctgComponentId) {
        this.ctgComponentId = ctgComponentId;
    }

    public List<CtgInvocation> getInvocations() {
        return invocations == null ? null : new ArrayList<>(invocations);
    }

    public void setInvocations(List<CtgInvocation> invocations) {
        this.invocations = invocations == null ? null : new ArrayList<>(invocations);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CtgUsage that = (CtgUsage) o;
        return Objects.equals(ctgComponentId, that.ctgComponentId) &&
                Objects.equals(invocations, that.invocations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ctgComponentId, invocations);
    }

    @Override
    public String toString() {
        return "CtgUsage{" +
                "ctgComponentId='" + ctgComponentId + '\'' +
                ", invocations=" + invocations +
                '}';
    }
}
