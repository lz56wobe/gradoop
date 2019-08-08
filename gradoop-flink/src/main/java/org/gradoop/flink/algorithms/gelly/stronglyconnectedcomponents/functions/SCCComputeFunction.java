package org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents.functions;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.graph.Edge;
import org.apache.flink.graph.Vertex;
import org.apache.flink.graph.pregel.ComputeFunction;
import org.apache.flink.graph.pregel.MessageIterator;
import org.apache.flink.types.IntValue;
import org.apache.flink.types.NullValue;
import org.gradoop.common.model.impl.id.GradoopId;

public class SCCComputeFunction extends
  ComputeFunction<GradoopId, SCCVertexValue, NullValue, Tuple2<String, Boolean>> {

  public static String PHASE_AGGREGATOR = "phaseAggregator";
  public static String NEW_MAX_AGGREGATOR = "newMaxAggregator";
  public static String CONVERGED_AGGREGATOR = "convergedAggregator";

  public enum Phases {
    TRANSPOSE,
    TRIMMING,
    FORWARD,
    BACKWARD_START,
    BACKWARD_REST;
  }

  private SccPhaseAggregator phaseAggregator;
  private SccNewMaxAggregator newMaxAggregator;
  private SccConvergedAggregator convergedAggregator;
  private int currentPhase;

  @Override
  public void preSuperstep() throws Exception {
    // get phase agg
    phaseAggregator = getIterationAggregator(PHASE_AGGREGATOR);
    if (getSuperstepNumber() == 0) {
      currentPhase = Phases.TRANSPOSE.ordinal();
    } else {
      IntValue previousAggregate = getPreviousIterationAggregate(PHASE_AGGREGATOR);
      if (previousAggregate != null) {
        currentPhase = previousAggregate.getValue();
      }
    }
    // get new max agg
    newMaxAggregator = getIterationAggregator(NEW_MAX_AGGREGATOR);
    newMaxAggregator.reset();
    // get converged agg
    convergedAggregator = getIterationAggregator(CONVERGED_AGGREGATOR);
    convergedAggregator.reset();

    System.out.println("------ preStep " + getSuperstepNumber() + " with Phase: " + currentPhase);
  }

  @Override
  public void compute(Vertex<GradoopId, SCCVertexValue> vertex,
    MessageIterator<Tuple2<String, Boolean>> messages) throws Exception {

    SCCVertexValue vertexValue = vertex.getValue();

    if (!vertexValue.isActive()) {
      return;
    }

    // TRANSPOSE
    if (currentPhase == Phases.TRANSPOSE.ordinal()) {
      vertexValue.clearParents();
      doSendMessageToAllNeighbors( Tuple2.of(vertex.getId().toString(), false));
    }
    // TRIMMING
    if (currentPhase == Phases.TRIMMING.ordinal()) {
      trim(vertex, messages);
    }
    // FORWARD
    if (currentPhase == Phases.FORWARD.ordinal()) {
      forward(vertex, messages);
    }
    // BACKWARD_START
    if (currentPhase == Phases.BACKWARD_START.ordinal()) {
      backwardStart(vertex);
    }
    // BACKWARD_REST
    if (currentPhase == Phases.BACKWARD_REST.ordinal()) {
      backwardRest(vertex, messages);
    }
  }

  private void trim(Vertex<GradoopId, SCCVertexValue> vertex,
    MessageIterator<Tuple2<String, Boolean>> messages) {
    while (messages.hasNext()) {
      Tuple2<String, Boolean> message = messages.next();
      vertex.getValue().addToParents(GradoopId.fromString(message.f0));
    }
    SCCVertexValue vertexValue = vertex.getValue();
    vertexValue.setSccId(vertex.getId().toString());
    updateVertexValue(vertexValue);
    Iterable<Edge<GradoopId, NullValue>> outEdges = getEdges();
    if (!outEdges.iterator().hasNext() || vertex.getValue().getParents().isEmpty()) {
      vertex.getValue().deactivate();
    } else {
      for (Edge<GradoopId, NullValue> edge : outEdges) {
        sendMessageTo(edge.getTarget(), Tuple2.of(vertex.getValue().getSccId(), false));
      }
    }
  }

  private void forward(Vertex<GradoopId, SCCVertexValue> vertex,
    MessageIterator<Tuple2<String, Boolean>> messages) {
    String maxMessage = vertex.getValue().getSccId();
    while (messages.hasNext()) {
      Tuple2<String, Boolean> message = messages.next();
      if (!message.f1 && maxMessage.compareTo(message.f0) > 0) {
        maxMessage = message.f0;
      }
    }
    if (!vertex.getValue().getSccId().equals(maxMessage)) {
      SCCVertexValue newValue = vertex.getValue();
      newValue.setSccId(maxMessage);
      updateVertexValue(newValue);
      doSendMessageToAllNeighbors(Tuple2.of(maxMessage, false));
      System.out.println("--------- found new maximum");
      newMaxAggregator.aggregate(true);
    } else {
      // we always need to send messages at the end on this phase, to prevent an iteration stop
      System.out.println("--------- no new maximum, send empty msg");
      doSendMessageToAllNeighbors(Tuple2.of("", true));
    }
  }

  private void backwardStart(Vertex<GradoopId, SCCVertexValue> vertex) {
    SCCVertexValue vertexValue = vertex.getValue();
    if (vertexValue.getSccId().equals(vertex.getId().toString())) {
      System.out.println("--------- vertex sent msg to all parents in Phase 3");
      sendMessagesToAllParents(vertex, Tuple2.of(vertexValue.getSccId(), false));
    } else {
      // we always need to send messages at the end on this phase, to prevent an iteration stop
      doSendMessageToAllNeighbors(Tuple2.of("", true));
    }
  }

  private void backwardRest(Vertex<GradoopId, SCCVertexValue> vertex,
    MessageIterator<Tuple2<String, Boolean>> messages) {
    SCCVertexValue vertexValue = vertex.getValue();
    while (messages.hasNext()) {
      Tuple2<String, Boolean> message = messages.next();
      if (!message.f1 && vertexValue.getSccId().equals(message.f0)) {
        sendMessagesToAllParents(vertex, Tuple2.of(message.f0, false));
        convergedAggregator.aggregate(true);
        vertexValue.deactivate();
        updateVertexValue(vertexValue);
        System.out.println("--------- vertex has been deactivated");
        break;
      }
    }
    // we always need to send messages at the end on this phase, to prevent an iteration stop
    sendMessageToAllNeighbors(Tuple2.of("", true));
  }

  @Override
  public void postSuperstep() throws Exception {
    int nextPhase = currentPhase;

    if (currentPhase == Phases.TRANSPOSE.ordinal()) {
      nextPhase = Phases.TRIMMING.ordinal();
    } else if (currentPhase == Phases.TRIMMING.ordinal()) {
      nextPhase = Phases.FORWARD.ordinal();
    } else if (currentPhase == Phases.FORWARD.ordinal()) {
      if (!newMaxAggregator.getAggregate().getValue()) {
        nextPhase = Phases.BACKWARD_START.ordinal();
      }
    } else if (currentPhase == Phases.BACKWARD_START.ordinal()) {
      nextPhase = Phases.BACKWARD_REST.ordinal();
    } else if (currentPhase == Phases.BACKWARD_REST.ordinal()) {
      if (!convergedAggregator.getAggregate().getValue()) {
        nextPhase = Phases.TRANSPOSE.ordinal();
      }
    }
    System.out.println("------ postStep " + getSuperstepNumber() + " - switched Phase " + currentPhase +
      " to " + nextPhase);
    // we always have to define the next phase, even if its the same phase
    phaseAggregator.aggregate(nextPhase);
  }

  private void updateVertexValue(SCCVertexValue newValue) {
    setNewVertexValue(newValue);
  }

  private void doSendMessageToAllNeighbors(Tuple2<String, Boolean> message) {
    sendMessageToAllNeighbors(message);
  }

  private void sendMessagesToAllParents(Vertex<GradoopId, SCCVertexValue> vertex,
    Tuple2<String, Boolean> message) {
    for (GradoopId parent : vertex.getValue().getParents()) {
      sendMessageTo(parent, message);
    }
  }
}
