package org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents.functions;

import org.gradoop.common.model.impl.id.GradoopId;

import java.util.ArrayList;
import java.util.List;

public class SCCVertexValue {

  private List<GradoopId> parents;
  private String sccId;
  private boolean isActive;

  public SCCVertexValue(String initialSccId) {
    this.parents = new ArrayList<>();
    this.sccId = initialSccId;
    this.isActive = true;
  }

  public List<GradoopId> getParents() {
    return parents;
  }

  public void setParents(List<GradoopId> parents) {
    this.parents = parents;
  }

  public void clearParents() {
    this.parents.clear();
  }

  public void addToParents(GradoopId parentId) {
    this.parents.add(parentId);
  }

  public String getSccId() {
    return this.sccId;
  }

  public void setSccId(String sccId) {
    this.sccId = sccId;
  }

  public boolean isActive() {
    return isActive;
  }

  public void deactivate() {
    this.isActive = false;
  }
}
