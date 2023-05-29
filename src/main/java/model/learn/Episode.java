package model.learn;

import simulation.matching.RideMatchingStrategy;

public record Episode(RideMatchingStrategy rideMatchingStrategy, int episode, String format, int size, int size1,
                      int size2, double v, int totalTimeSteps, int currentTimeStep, int sum, double sum1, double sum2,
                      long l) {
}
