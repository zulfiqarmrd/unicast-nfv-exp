package Simulation;

import Algorithm.Algorithm;
import Algorithm.CostFunctions.ExponentialCostFunction;
import Algorithm.CostFunctions.LinCostFunction;
import Algorithm.Result;
import Network.Network;
import Network.Request;
import Network.Server;
import NetworkGenerator.NetworkGenerator;
import NetworkGenerator.NetworkValueSetter;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"Duplicates", "unused"}) public class Simulation {
  public static final Random random = new Random();
  private static final ExecutorService threadPool = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());
  public static final Parameters defaultParameters = new Parameters.Builder().build();
  private static final ArrayList<TopologyFile> topologyFiles = new ArrayList<>();

  public static void main(String[] args) {
    ArrayList<Runnable> listOfTasks = new ArrayList<>();
    for (String arg : args) {
      switch (arg) {
        case "0":
          listOfTasks.add(new Thread(() -> LEffectWithoutDelays()));
          break;
        case "1":
          listOfTasks.add(new Thread(() -> LEffectWithDelays()));
          break;
        case "2":
          listOfTasks.add(new Thread(() -> betaImpactWithoutDelays()));
          break;
        case "3":
          listOfTasks.add(new Thread(() -> betaImpactWithDelays()));
          break;
        case "4":
          listOfTasks.add(new Thread(() -> ThresholdEffectWithoutDelays()));
          break;
        case "5":
          listOfTasks.add(new Thread(() -> ThresholdEffectWithDelays()));
          break;
        case "6":
          listOfTasks.add(new Thread(() -> CompareCostFnsWithoutDelays()));
          break;
        case "7":
          listOfTasks.add(new Thread(() -> CompareCostFnsWithDelays()));
          break;
        default:
          System.out.println("Unknown argument: " + arg);
          System.exit(1);
      }
    }

    listOfTasks.forEach(threadPool::execute);

    threadPool.shutdown();
    try {
      threadPool.awaitTermination(1, TimeUnit.DAYS);
    } catch (InterruptedException ie) {
      ie.printStackTrace();
    }
  }

  public static void prepareTopologies() {
    for (int networkSize : defaultParameters.networkSizes) {
      for (int trial = 0; trial < defaultParameters.numTrials; ++trial) {
        topologyFiles.add(new TopologyFile(networkSize, String.valueOf(trial)));
      }
    }
    topologyFiles.add(new TopologyFile("GEANT"));
    topologyFiles.add(new TopologyFile("AS1755"));
    topologyFiles.add(new TopologyFile("AS4755"));
    topologyFiles.forEach(System.out::println);
  }

  /**
   * We compare the performance of the proposed online algorithms while using different cost functions, i.e., a linear cost function and an exponential cost
   * function
   */
  private static void CompareCostFnsWithoutDelays() {
    Parameters parametersWithExpCostFn = new Parameters.Builder().costFunc(new ExponentialCostFunction()).build();
    Parameters parametersWithLinearCostFn = new Parameters.Builder().costFunc(new LinCostFunction()).build();

    Result[][] expResults = new Result[defaultParameters.numTrials][defaultParameters.numRequest];
    Result[][] linearResults = new Result[defaultParameters.numTrials][defaultParameters.numRequest];

    for (int nSizeIndex = 0; nSizeIndex < defaultParameters.networkSizes.length; nSizeIndex++) {
      int networkSize = defaultParameters.networkSizes[nSizeIndex];

      for (int trial = 0; trial < defaultParameters.numTrials; ++trial) {
        int accepted = 0;

        Network network = generateAndInitializeNetwork(networkSize, trial, parametersWithExpCostFn);
        network.wipeLinks();
        ArrayList<Request> requests = generateRequests(parametersWithExpCostFn, network, parametersWithExpCostFn.numRequest);

        for (int i = 0; i < parametersWithExpCostFn.numRequest; ++i) {
          Algorithm alg = new Algorithm(network, requests.get(i), parametersWithExpCostFn);
          expResults[trial][i] = alg.maxThroughputWithoutDelay();
        }

        network.wipeLinks();
        for (int i = 0; i < parametersWithExpCostFn.numRequest; ++i) {
          Algorithm alg = new Algorithm(network, requests.get(i), parametersWithLinearCostFn);
          linearResults[trial][i] = alg.maxThroughputWithoutDelay();
        }
      }

      for (int i = 0; i < parametersWithExpCostFn.numRequest; ++i) {
        double expAdmittedCount = 0;
        double expPathCost = 0;
        double linearAdmittedCount = 0;
        double linearPathCost = 0;

        for (int trial = 0; trial < parametersWithExpCostFn.numTrials; ++trial) {
          if (expResults[trial][i].isAdmitted()) {
            ++expAdmittedCount;
            expPathCost += expResults[trial][i].getPathCost();
          }
          if (linearResults[trial][i].isAdmitted()) {
            ++linearAdmittedCount;
            linearPathCost += linearResults[trial][i].getPathCost();
          }
        }

        expAdmittedCount /= parametersWithExpCostFn.numTrials;
        expPathCost /= parametersWithExpCostFn.numTrials;
        linearAdmittedCount /= parametersWithExpCostFn.numTrials;
        linearPathCost /= parametersWithExpCostFn.numTrials;
        System.out.printf("%d %f %f\n", networkSize, expAdmittedCount, linearAdmittedCount);
        System.out.printf("%d %f %f\n", networkSize, expPathCost, linearPathCost);
      }
    }
  }

  private static void CompareCostFnsWithDelays() {
    Parameters parametersWithExpCostFn = new Parameters.Builder().costFunc(new ExponentialCostFunction()).build();
    Parameters parametersWithLinearCostFn = new Parameters.Builder().costFunc(new LinCostFunction()).build();

    Result[][] expResults = new Result[defaultParameters.numTrials][defaultParameters.numRequest];
    Result[][] linearResults = new Result[defaultParameters.numTrials][defaultParameters.numRequest];
    for (int networkSize : defaultParameters.networkSizes) {
      for (int trial = 0; trial < defaultParameters.numTrials; ++trial) {
        Network network = generateAndInitializeNetwork(networkSize, trial, parametersWithExpCostFn);
        ArrayList<Request> requests = generateRequests(parametersWithExpCostFn, network, parametersWithExpCostFn.numRequest);

        network.wipeLinks();
        for (int i = 0; i < parametersWithExpCostFn.numRequest; ++i) {
          Algorithm alg = new Algorithm(network, requests.get(i), parametersWithExpCostFn);
          expResults[trial][i] = alg.maxThroughputWithDelay();
        }

        network.wipeLinks();
        for (int i = 0; i < parametersWithExpCostFn.numRequest; ++i) {
          Algorithm alg = new Algorithm(network, requests.get(i), parametersWithLinearCostFn);
          linearResults[trial][i] = alg.maxThroughputWithDelay();
        }
      }

      for (int i = 0; i < parametersWithExpCostFn.numRequest; ++i) {
        double expAdmittedCount = 0;
        double expPathCost = 0;
        double linearAdmittedCount = 0;
        double linearPathCost = 0;

        for (int trial = 0; trial < parametersWithExpCostFn.numTrials; ++trial) {
          if (expResults[trial][i].isAdmitted()) {
            ++expAdmittedCount;
            expPathCost += expResults[trial][i].getPathCost();
          }
          if (linearResults[trial][i].isAdmitted()) {
            ++linearAdmittedCount;
            linearPathCost += linearResults[trial][i].getPathCost();
          }
        }

        expAdmittedCount /= parametersWithExpCostFn.numTrials;
        expPathCost /= parametersWithExpCostFn.numTrials;
        linearAdmittedCount /= parametersWithExpCostFn.numTrials;
        linearPathCost /= parametersWithExpCostFn.numTrials;
        System.out.printf("%d %f %f\n", networkSize, expAdmittedCount, linearAdmittedCount);
        System.out.printf("%d %f %f\n", networkSize, expPathCost, linearPathCost);
      }
    }
  }

  private static void betaImpactWithoutDelays() {
    int[] betas = new int[] {2, 4, 6};
    for (int networkSize : defaultParameters.networkSizes) {
      Result[][][] results = new Result[betas.length][defaultParameters.numTrials][defaultParameters.numRequest];

      for (int betaIdx = 0; betaIdx < betas.length; ++betaIdx) {
        int beta = betas[betaIdx];
        Parameters parameters = new Parameters.Builder().beta(beta).build();

        for (int trial = 0; trial < defaultParameters.numTrials; ++trial) {
          Network network = generateAndInitializeNetwork(networkSize, trial, parameters);
          ArrayList<Request> requests = generateRequests(parameters, network, parameters.numRequest);
          network.wipeLinks();

          for (int i = 0; i < parameters.numRequest; i++) {
            Algorithm alg = new Algorithm(network, requests.get(i), parameters);
            results[betaIdx][trial][i] = alg.maxThroughputWithoutDelay();
          }
        }
      }

      for (int betaIdx = 0; betaIdx < betas.length; ++betaIdx) {
        int beta = betas[betaIdx];
        StringBuilder count = new StringBuilder();
        StringBuilder cost = new StringBuilder();
        count.append(networkSize).append(" ");
        cost.append(networkSize).append(" ");
        for (int i = 0; i < defaultParameters.numRequest; ++i) {
          double expAdmittedCount = 0;
          double expPathCost = 0;

          for (int trial = 0; trial < defaultParameters.numTrials; ++trial) {
            if (results[betaIdx][trial][i].isAdmitted()) {
              ++expAdmittedCount;
              expPathCost += results[betaIdx][trial][i].getPathCost();
            }
          }

          expAdmittedCount /= defaultParameters.numTrials;
          count.append(expAdmittedCount).append(" ");
          expPathCost /= defaultParameters.numTrials;
          cost.append(expPathCost).append("");
        }
        System.out.println(count.toString());
        System.out.println(cost.toString());
      }
    }
  }

  private static void betaImpactWithDelays() {
    int[] betas = new int[] {2, 4, 6};
    for (int networkSize : defaultParameters.networkSizes) {
      Result[][][] results = new Result[betas.length][defaultParameters.numTrials][defaultParameters.numRequest];

      for (int betaIdx = 0; betaIdx < betas.length; ++betaIdx) {
        int beta = betas[betaIdx];
        Parameters parameters = new Parameters.Builder().beta(beta).build();

        for (int trial = 0; trial < defaultParameters.numTrials; ++trial) {
          Network network = generateAndInitializeNetwork(networkSize, trial, parameters);
          ArrayList<Request> requests = generateRequests(parameters, network, parameters.numRequest);
          network.wipeLinks();

          for (int i = 0; i < parameters.numRequest; i++) {
            Algorithm alg = new Algorithm(network, requests.get(i), parameters);
            results[betaIdx][trial][i] = alg.maxThroughputWithDelay();
          }
        }
      }

      for (int betaIdx = 0; betaIdx < betas.length; ++betaIdx) {
        int beta = betas[betaIdx];
        StringBuilder count = new StringBuilder();
        StringBuilder cost = new StringBuilder();
        count.append(networkSize).append(" ");
        cost.append(networkSize).append(" ");
        for (int i = 0; i < defaultParameters.numRequest; ++i) {
          double expAdmittedCount = 0;
          double expPathCost = 0;

          for (int trial = 0; trial < defaultParameters.numTrials; ++trial) {
            if (results[betaIdx][trial][i].isAdmitted()) {
              ++expAdmittedCount;
              expPathCost += results[betaIdx][trial][i].getPathCost();
            }
          }

          expAdmittedCount /= defaultParameters.numTrials;
          count.append(expAdmittedCount).append(" ");
          expPathCost /= defaultParameters.numTrials;
          cost.append(expPathCost).append("");
        }
        System.out.println(count.toString());
        System.out.println(cost.toString());
      }
    }
  }

  /**
   * We test the impact of threshold on the performance of the proposed online algorithms by running the algorithms with and without the threshold
   */
  private static void ThresholdEffectWithoutDelays() {
    Parameters parametersWithThreshold = new Parameters.Builder().build();
    Parameters parametersWithOutThreshold = new Parameters.Builder().threshold(Integer.MAX_VALUE)
                                                                    .build();

    Result[][] withThresholdResults = new Result[parametersWithThreshold.numTrials][parametersWithThreshold.numRequest];
    Result[][] withoutThresholdResults = new Result[parametersWithThreshold.numTrials][parametersWithThreshold.numRequest];

    for (int networkSize : defaultParameters.networkSizes) {
      for (int trial = 0; trial < defaultParameters.numTrials; ++trial) {
        Network network = generateAndInitializeNetwork(networkSize, trial, parametersWithThreshold);
        ArrayList<Request> requests = generateRequests(parametersWithThreshold, network, parametersWithThreshold.numRequest);

        network.wipeLinks();
        for (int i = 0; i < parametersWithThreshold.numRequest; ++i) {
          Algorithm alg = new Algorithm(network, requests.get(i), parametersWithThreshold);
          withThresholdResults[trial][i] = alg.maxThroughputWithoutDelay();
        }

        network.wipeLinks();
        for (int i = 0; i < parametersWithThreshold.numRequest; ++i) {
          Algorithm alg = new Algorithm(network, requests.get(i), parametersWithOutThreshold);
          withoutThresholdResults[trial][i] = alg.maxThroughputWithoutDelay();
        }
      }

      for (int i = 0; i < parametersWithThreshold.numRequest; ++i) {
        double expAdmittedCount = 0;
        double expPathCost = 0;
        double linearAdmittedCount = 0;
        double linearPathCost = 0;

        for (int trial = 0; trial < parametersWithThreshold.numTrials; ++trial) {
          if (withThresholdResults[trial][i].isAdmitted()) {
            ++expAdmittedCount;
            expPathCost += withThresholdResults[trial][i].getPathCost();
          }
          if (withoutThresholdResults[trial][i].isAdmitted()) {
            ++linearAdmittedCount;
            linearPathCost += withoutThresholdResults[trial][i].getPathCost();
          }
        }

        expAdmittedCount /= parametersWithThreshold.numTrials;
        expPathCost /= parametersWithThreshold.numTrials;
        linearAdmittedCount /= parametersWithOutThreshold.numTrials;
        linearPathCost /= parametersWithOutThreshold.numTrials;
        System.out.printf("%d %f %f\n", networkSize, expAdmittedCount, linearAdmittedCount);
        System.out.printf("%d %f %f\n", networkSize, expPathCost, linearPathCost);
      }
    }
  }

  private static void ThresholdEffectWithDelays() {
    Parameters parametersWithThreshold = new Parameters.Builder().build();
    Parameters parametersWithOutThreshold = new Parameters.Builder().threshold(Integer.MAX_VALUE)
                                                                    .build();

    Result[][] withThresholdResults = new Result[parametersWithThreshold.numTrials][parametersWithThreshold.numRequest];
    Result[][] withoutThresholdResults = new Result[parametersWithThreshold.numTrials][parametersWithThreshold.numRequest];

    for (int networkSize : defaultParameters.networkSizes) {
      for (int trial = 0; trial < defaultParameters.numTrials; ++trial) {
        Network network = generateAndInitializeNetwork(networkSize, trial, parametersWithThreshold);
        ArrayList<Request> requests = generateRequests(parametersWithThreshold, network, parametersWithThreshold.numRequest);

        network.wipeLinks();
        for (int i = 0; i < parametersWithThreshold.numRequest; ++i) {
          Algorithm alg = new Algorithm(network, requests.get(i), parametersWithThreshold);
          withThresholdResults[trial][i] = alg.maxThroughputWithDelay();
        }

        network.wipeLinks();
        for (int i = 0; i < parametersWithThreshold.numRequest; ++i) {
          Algorithm alg = new Algorithm(network, requests.get(i), parametersWithOutThreshold);
          withoutThresholdResults[trial][i] = alg.maxThroughputWithDelay();
        }
      }

      for (int i = 0; i < parametersWithThreshold.numRequest; ++i) {
        double expAdmittedCount = 0;
        double expPathCost = 0;
        double linearAdmittedCount = 0;
        double linearPathCost = 0;

        for (int trial = 0; trial < parametersWithThreshold.numTrials; ++trial) {
          if (withThresholdResults[trial][i].isAdmitted()) {
            ++expAdmittedCount;
            expPathCost += withThresholdResults[trial][i].getPathCost();
          }
          if (withoutThresholdResults[trial][i].isAdmitted()) {
            ++linearAdmittedCount;
            linearPathCost += withoutThresholdResults[trial][i].getPathCost();
          }
        }

        expAdmittedCount /= parametersWithThreshold.numTrials;
        expPathCost /= parametersWithThreshold.numTrials;
        linearAdmittedCount /= parametersWithOutThreshold.numTrials;
        linearPathCost /= parametersWithOutThreshold.numTrials;
        System.out.printf("%d %f %f\n", networkSize, expAdmittedCount, linearAdmittedCount);
        System.out.printf("%d %f %f\n", networkSize, expPathCost, linearPathCost);
      }
    }
  }

  private static void LEffectWithoutDelays() {
    for (int L = 2; L <= 6; L += 2) {

      System.out.println("L:" + L);

      Parameters parameters = new Parameters.Builder().L(L).build();

      int accepted = 0; //number of accepted requests
      int expSum = 0; //sum of all exponential cost accepted requests
      Result[] results = new Result[parameters.numRequest];

      //double[] averageCost = new double[defaultParameters.networkSizes.length];

      for (int networkSize : defaultParameters.networkSizes) {
        double averageCostNet = 0d;
        for (int trial = 0; trial < defaultParameters.numTrials; ++trial) {
          Network network = generateAndInitializeNetwork(networkSize, trial, parameters);
          ArrayList<Request> requests = generateRequests(parameters, network, parameters.numRequest);
          network.wipeLinks();

          double averageCostReq = 0d;
          for (int i = 0; i < parameters.numRequest; i++) {
            Algorithm alg = new Algorithm(network, requests.get(i), parameters);
            results[i] = alg.minOpCostWithoutDelay();
            if (results[i].isAdmitted()) {// not sure about here, this request should be always accepted according to assumptions.
              accepted++;
              averageCostReq += results[i].getPathCost();
            }
          }
          averageCostReq = averageCostReq / accepted;
          averageCostNet += (averageCostNet / defaultParameters.numTrials);
          expSum += accepted;
        }
        System.out.println(networkSize + " " + averageCostNet);
      }
    }
  }

  private static void LEffectWithDelays() {
    for (int L = 2; L <= 6; L += 2) {

      System.out.println("L:" + L);

      Parameters parameters = new Parameters.Builder().L(L).build();

      int accepted = 0; //number of accepted requests
      int expSum = 0; //sum of all exponential cost accepted requests
      Result[] results = new Result[parameters.numRequest];

      for (int networkSize : defaultParameters.networkSizes) {
        double averageCostNet = 0d;
        for (int trial = 0; trial < defaultParameters.numTrials; ++trial) {
          Network network = generateAndInitializeNetwork(networkSize, trial, parameters);
          Request[] requests = new Request[parameters.numRequest];
          for (int i = 0; i < parameters.numRequest; i++) {
            requests[i] =
                new Request(network.getRandomServer(), network.getRandomServer(), parameters);
          }
          network.wipeLinks();
          double averageCostReq = 0d;
          for (int i = 0; i < parameters.numRequest; i++) {
            Algorithm alg = new Algorithm(network, requests[i], parameters);
            results[i] = alg.minOpCostWithDelay();
            if (results[i].isAdmitted()) {
              accepted++;
              averageCostReq += results[i].getPathCost();
            }
          }
          averageCostReq = averageCostReq / accepted;
          averageCostNet += (averageCostNet / defaultParameters.numTrials);
          expSum += accepted;
        }
        System.out.println(networkSize + " " + averageCostNet);
      }
    }
  }

  private static ArrayList<Request> generateRequests(Parameters parameters, Network network, int numRequests) {
    ArrayList<Request> requests = new ArrayList<>();
    for (int i = 0; i < numRequests; ++i) {
      // source and destination should be different.
      Server source = network.getRandomServer();
      Server destination = network.getRandomServer();
      while (source.equals(destination)) {
        destination = network.getRandomServer();
      }
      requests.add(new Request(source, destination, parameters));
    }
    return requests;
  }

  private static void initializeNetwork(Network network, Parameters parameters) {
    NetworkValueSetter networkValueSetter = new NetworkValueSetter(network, parameters);
    networkValueSetter.setConstantServerCapacity(Integer.MAX_VALUE, parameters.serverRatio);
    networkValueSetter.setRandomLinkCapacity(parameters.linkBWCapMin, parameters.linkBWCapMax);
    networkValueSetter.placeNFVs(parameters.nfvProb);
  }

  private static Network generateAndInitializeNetwork(int networkSize, int trial, Parameters parameters) {
    Network network = new NetworkGenerator().generateRealNetworks(networkSize, String.valueOf(trial));
    initializeNetwork(network, parameters);
    return network;
  }

  private static Network generateAndInitializeNetwork(TopologyFile topologyFile, Parameters parameters) {
    Network network = new NetworkGenerator().generateRealNetworks(topologyFile.getPrefix(), topologyFile.getSuffix());
    initializeNetwork(network, parameters);
    return network;
  }

  private static class TopologyFile {
    private final int prefix;
    private final String suffix;

    TopologyFile(int prefix, String suffix) {
      this.prefix = prefix;
      this.suffix = suffix;
    }

    TopologyFile(String suffix) {
      this.prefix = -1;
      this.suffix = suffix;
    }

    int getPrefix() {
      return prefix;
    }

    String getSuffix() {
      return suffix;
    }

    @Override public String toString() {
      return "TopologyFile{" +
          "prefix=" + prefix +
          ", suffix='" + suffix + '\'' +
          '}';
    }
  }
}
