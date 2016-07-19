package Algorithm;

import java.util.ArrayList;

import Algorithm.CostFunctions.CostFunction;
import Algorithm.CostFunctions.ExponentialCostFunction;
import Algorithm.CostFunctions.OperationalCostFunction;
import Network.AuxiliaryNetwork;
import Network.Network;
import Network.Request;
import Network.Server;
import NetworkGenerator.AuxiliaryGraphBuilder;
import Simulation.Parameters;

@SuppressWarnings("Duplicates") public class Algorithm {
  private final Network originalNetwork;
  private final Request request;
  private final Parameters parameters;
  private AuxiliaryNetwork auxiliaryNetwork;

  public Algorithm(Network originalNetwork, Request request, Parameters parameters) {
    this.originalNetwork = originalNetwork;
    this.request = request;
    this.parameters = parameters;
  }

  public Result minOpCostWithoutDelay() {
    CostFunction costFunction = new OperationalCostFunction();
    auxiliaryNetwork = AuxiliaryGraphBuilder.buildAuxiliaryGraph(originalNetwork, request, costFunction, this.parameters);
    if (auxiliaryNetwork == null) { //this means that some servers cannot be reached due to insufficient bandwidth
      return new Result.Builder().build(); //this generates a no-admittance result
    }
    auxiliaryNetwork.generateOfflineNetwork();
    ArrayList<Server> path = auxiliaryNetwork.findShortestPath();
    double finalPathCost = auxiliaryNetwork.calculatePathCost(path, costFunction);
    return new Result.Builder().path(path)
                               .pathCost(finalPathCost)
                               .admit(true)
                               .build();
  }

  public Result minOpCostWithDelay() {
    CostFunction costFunction = new ExponentialCostFunction();
    auxiliaryNetwork = AuxiliaryGraphBuilder.buildAuxiliaryGraph(originalNetwork, request, costFunction, this.parameters);
    if (auxiliaryNetwork == null) { //this means that some servers cannot be reached due to insufficient bandwidth
      return new Result.Builder().build(); //this generates a no-admittance result
    }
    auxiliaryNetwork.generateOnlineNetwork();
    ArrayList<Server> path = auxiliaryNetwork.LARAC();
    double finalPathCost = auxiliaryNetwork.calculatePathCost(path, costFunction);
    return new Result.Builder().path(path)
                               .pathCost(finalPathCost)
                               .build();
  }

  public Result maxThroughputWithoutDelay() { //s is source, t is sink
    auxiliaryNetwork = AuxiliaryGraphBuilder.buildAuxiliaryGraph(originalNetwork, request, parameters.costFunc, this.parameters);
    if (auxiliaryNetwork == null) { //this means that some servers cannot be reached due to insufficient bandwidth
      return new Result.Builder().build(); //this generates a no-admittance result
    }
    auxiliaryNetwork.generateOnlineNetwork();
    ArrayList<Server> path = auxiliaryNetwork.findShortestPath();
    double finalPathCost = auxiliaryNetwork.calculatePathCost(path, parameters.costFunc);
    boolean admit = admissionControl(finalPathCost);
    if (admit) {
      auxiliaryNetwork.admitRequestAndReserveResources(path);
    }
    return new Result.Builder().path(path)
                               .pathCost(finalPathCost)
                               .admit(admit)
                               .build();
  }

  public Result maxThroughputWithDelay() { //s is source, t is sink
    auxiliaryNetwork = AuxiliaryGraphBuilder.buildAuxiliaryGraph(originalNetwork, request, parameters.costFunc, this.parameters);
    if (auxiliaryNetwork == null) { //this means that some servers cannot be reached due to insufficient bandwidth
      return new Result.Builder().build(); //this generates a no-admittance result
    }
    auxiliaryNetwork.generateOnlineNetwork();
    ArrayList<Server> path = auxiliaryNetwork.LARAC();
    double finalPathCost = auxiliaryNetwork.calculatePathCost(path, parameters.costFunc);
    boolean admit = admissionControl(finalPathCost);
    if (admit) {
      auxiliaryNetwork.admitRequestAndReserveResources(path);
    }
    return new Result.Builder().build();
  }

  private boolean admissionControl(double pathCost) {
    return pathCost < auxiliaryNetwork.size() * parameters.threshold - 1;
  }
}
