package simulation.matching;

import com.google.common.base.Objects;
import config.Config;
import config.Qos;
import model.*;
import model.node.Node;

import java.util.*;
import java.util.stream.Collectors;

public class ResultAssignment {

    public Set<User> requestsServicedLevelAchieved;
    public Map<Vehicle, VisitObj> vehicleBestVisitMap;
    public Map<Qos, Integer> unmetServiceLevelClass;
    public Map<Qos, Integer> nOfRequestsClass;
    public Map<Qos, Integer> rejectedServiceLevelClass;
    public Set<User> requestsServicedLevelNotAchieved;
    public Map<Qos, Double> violationCountClassServiceLevel;
    // Set of users that were displaced from their current rides (is in unassigned)
    protected Set<User> requestsDisplaced;
    // Set of vehicles that interrupted routes and parked
    protected Set<Vehicle> vehiclesDisrupted;
    // Users who were picked up by hired vehicle
    protected List<User> roundPrivateRides;
    // Set of requests scheduled to vehicles
    protected Set<User> requestsOK;
    // Set of vehicles assigned to visits
    protected Set<Vehicle> vehiclesOK;
    // Set of visits chosen in round
    protected Set<VisitObj> visitsOK;
    // Unassigned requests
    private Set<User> requestsUnassigned;
    // New vehicle is added in list
    private Set<Vehicle> vehiclesHired;
    // Result refers to current simulation time
    private int currentTime;

    protected double objValTotalRejected;

    public void setObjValTotalRejected(double objValTotalRejected) {
        this.objValTotalRejected = objValTotalRejected;
    }

    public void setObjValTotalServiced(double objValTotalServiced) {
        this.objValTotalServiced = objValTotalServiced;
    }

    public void setObjValTotalWaiting(double objValTotalWaiting) {
        this.objValTotalWaiting = objValTotalWaiting;
    }

    public double getObjValRequestsPlusVFs() {
        return objValRequestsPlusVFs;
    }

    public void setObjValRequestsPlusVFs(double objValRequestsPlusVFs) {
        this.objValRequestsPlusVFs = objValRequestsPlusVFs;
    }


    protected double objValTotalServiced;
    protected double objValTotalWaiting;
    protected double objValRequestsPlusVFs;




    public double getTotalDelay() {
        return this.visitsOK.stream().reduce(0, (totalDelay, visit) -> totalDelay + visit.getDelay(), Integer::sum);
    }

    public double getTotalVFs() {
        return this.visitsOK.stream().reduce(0.0, (totalDelay, visit) -> totalDelay + visit.getVF(), Double::sum);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResultAssignment that = (ResultAssignment) o;
        return currentTime == that.currentTime
                //Objects.equal(unmetServiceLevelClass, that.unmetServiceLevelClass) &&
                //Objects.equal(nOfRequestsClass, that.nOfRequestsClass) &&
                //Objects.equal(rejectedServiceLevelClass, that.rejectedServiceLevelClass &&
                //&& violationCountClassServiceLevel.equals(that.violationCountClassServiceLevel) &&
                && requestsServicedLevelAchieved.size() == that.requestsServicedLevelAchieved.size()
                && requestsServicedLevelNotAchieved.size() == that.requestsServicedLevelNotAchieved.size()
                && requestsDisplaced.size() == that.requestsDisplaced.size()
                && getVehiclesDisrupted().size() == that.getVehiclesDisrupted().size()
                && roundPrivateRides.size() == that.roundPrivateRides.size()
                && getRequestsOK().size() == that.getRequestsOK().size()
                && getVehiclesOK().size() == that.getVehiclesOK().size()
                && getVisitsOK().size() == that.getVisitsOK().size()
                && getRequestsUnassigned().size() == that.getRequestsUnassigned().size()
                && getVehiclesHired().size() == that.getVehiclesHired().size()
                && objValTotalRejected == that.objValTotalRejected
                && objValTotalServiced == that.objValTotalServiced
                && objValTotalWaiting == that.objValTotalWaiting
                && objValRequestsPlusVFs == that.objValRequestsPlusVFs;

    }

    @Override
    public int hashCode() {
        return Objects.hashCode(requestsServicedLevelAchieved, unmetServiceLevelClass, nOfRequestsClass, rejectedServiceLevelClass, requestsServicedLevelNotAchieved, violationCountClassServiceLevel, requestsDisplaced, getVehiclesDisrupted(), roundPrivateRides, getRequestsOK(), getVehiclesOK(), getVisitsOK(), getRequestsUnassigned(), getVehiclesHired(), currentTime);
    }

    public ResultAssignment(int currentTime) {
        this.currentTime = currentTime;
        // Set of requests scheduled to vehicles
        requestsOK = new HashSet<>();
        requestsDisplaced = new HashSet<>();
        requestsUnassigned = new HashSet<>();
        requestsServicedLevelAchieved = new HashSet<>();
        requestsServicedLevelNotAchieved = new HashSet<>();

        // Class structures
        unmetServiceLevelClass = new HashMap<>();
        rejectedServiceLevelClass = new HashMap<>();
        nOfRequestsClass = new HashMap<>();
        violationCountClassServiceLevel = new HashMap<>();

        // Set of vehicles assigned to visits
        vehiclesOK = new HashSet<>();
        vehiclesHired = new HashSet<>();
        roundPrivateRides = new ArrayList<>();

        // Set of vehicles that interrupted routes and parked
        vehiclesDisrupted = new HashSet<>();

        // Set of visits chosen in round
        visitsOK = new HashSet<>();
        vehicleBestVisitMap = new HashMap<>();

        for (Qos qos : Config.getInstance().qosDic.values()) {
            unmetServiceLevelClass.put(qos, 0);
            nOfRequestsClass.put(qos, 0);
            rejectedServiceLevelClass.put(qos, 0);
            violationCountClassServiceLevel.put(qos, 0.0);
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // PRINTS //////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Map<Qos, List<User>> getClassUnassignedMap() {

        Map<Qos, List<User>> classUnassigned = new HashMap<>();

        for (Qos qos : Config.getInstance().qosDic.values()) {
            classUnassigned.put(qos, new ArrayList<>());
        }

        for (User request : this.requestsUnassigned) {
            classUnassigned.get(request.qos).add(request);
        }

        return classUnassigned;
    }

    public Map<Qos, List<User>> getClassSecondTierMap() {

        Map<Qos, List<User>> classSecondTier = new HashMap<>();

        for (Qos qos : Config.getInstance().qosDic.values()) {
            classSecondTier.put(qos, new ArrayList<>());
        }

        for (User request : this.requestsServicedLevelNotAchieved) {
            classSecondTier.get(request.qos).add(request);
        }

        return classSecondTier;
    }

    public void printRoundResult() {
        System.out.println("######## Round current time: " + this.currentTime);
        System.out.printf("\n\n# Assigned vehicles  (%d)  = %s%n", vehiclesOK.size(), vehiclesOK);
        System.out.printf("# Unassigned users   (%d)  = %s%n", requestsUnassigned.size(), requestsUnassigned);
        System.out.printf("# Unmet s. levels    (%d)  = %s%n", getUnmetServiceLevelRequests().size(), getUnmetServiceLevelRequests());
        System.out.printf("# Assigned users     (%d)  = %s%n", requestsOK.size(), requestsOK);
        System.out.printf("# Displaced users    (%d)  = %s%n", requestsDisplaced.size(), requestsDisplaced);
        System.out.printf("# Class service quality    = \n%s%n", overallServiceLevelDistribution());
        System.out.printf("# Vehicles disrupted (%d)  = %s%n", vehiclesDisrupted.size(), vehiclesDisrupted);
        System.out.printf("# Vehicles hired           = %s%n", getVehiclesHired().size());
        for (Vehicle vehicle : vehiclesDisrupted) {
            System.out.println("#### Disrupted = " + vehicle.getVisit());
        }
    }

    public void printRoundResultSummary() {

        System.out.println("######## Round current time: " + this.currentTime);
        System.out.printf("# Assigned vehicles = %d%n", vehiclesOK.size());
        System.out.printf("# Unassigned users  = %d%n", requestsUnassigned.size());
        System.out.printf("# Unmet s. levels   = %d%n", getUnmetServiceLevelRequests().size());
        System.out.printf("# Assigned users    = %d%n", requestsOK.size());
        System.out.printf("# Displaced users   = %d%n", requestsDisplaced.size());
        System.out.printf("# Class service quality: \n%s%n", overallServiceLevelDistribution());
        System.out.printf("# Disrupted         = %d%n", vehiclesDisrupted.size());
        System.out.printf("# Vehicles hired    = %s%n", getVehiclesHired().size());
        System.out.printf("# Obj. vfs          = %s%n", getTotalVFs());
        System.out.printf("# Obj. total delay  = %s%n", getTotalDelay());
        System.out.printf("# Obj. reqs. + vfs  = %s%n", objValRequestsPlusVFs);
        System.out.printf("# Obj. reqs         = %s%n", objValTotalServiced);


    }

    private String overallServiceLevelDistribution() {
        return nOfRequestsClass.keySet().stream().map(this::getConstraintCheck).collect(Collectors.joining("\n"));
    }

    private String getSummaryStats(Qos qos) {
        return String.format(
                "\n[%s] sl violation = %3d/ min=%3d (%.1f * %3d), met=%3d, unmet=%3d, unassigned=%3d",
                qos.id,
                unmetServiceLevelClass.get(qos) + rejectedServiceLevelClass.get(qos),
                (int) Math.ceil(qos.serviceRate * nOfRequestsClass.get(qos)),
                qos.serviceRate,
                nOfRequestsClass.get(qos),
                metServiceLevelClass(qos),
                unmetServiceLevelClass.get(qos),
                rejectedServiceLevelClass.get(qos));
    }

    private String getConstraintCheck(Qos qos) {
        return String.format(
                "[%s] = %3d (met) %+.0f (violation) >= %3d (%.1f * %3d) - unmet=%3d, rejected=%3d",
                qos.id,
                metServiceLevelClass(qos),
                violationCountClassServiceLevel.get(qos),
                (int) Math.ceil(qos.serviceRate * nOfRequestsClass.get(qos)),
                qos.serviceRate,
                nOfRequestsClass.get(qos),
                unmetServiceLevelClass.get(qos),
                rejectedServiceLevelClass.get(qos));
    }

    int metServiceLevelClass(Qos qos) {
        return nOfRequestsClass.get(qos) - unmetServiceLevelClass.get(qos) - rejectedServiceLevelClass.get(qos);
    }

    public void addHiredVehicle(Vehicle vehicle) {
        this.vehiclesHired.add(vehicle);
    }

    public void addVisit(VisitObj visit) {

        // Update requests, vehicles, and visitsOK solution
        this.requestsOK.addAll(visit.getRequests());
        this.vehiclesOK.add(visit.getVehicle());
        this.visitsOK.add(visit);
        this.vehicleBestVisitMap.put(visit.getVehicle(), visit);


        // Remove vehicle and users matched from graph
        for (User user : visit.getRequests()) {

            // If user changes visit
            if (user.getCurrentVisit() != null && visit != user.getCurrentVisit()) {

                // VisitObj of vehicle formerly carrying request will be changed
                this.vehiclesDisrupted.add(user.getCurrentVisit().getVehicle());
            }
        }
    }

    private void printCurrentStatus() {
        System.out.printf("# Requests (%d): %s%n", this.requestsOK.size(), this.requestsOK);
        System.out.printf("# Vehicles (%d): %s%n", this.vehiclesOK.size(), this.vehiclesOK);
        System.out.printf("# Disrupted (%d): %s%n", this.vehiclesDisrupted.size(), this.vehiclesDisrupted);
        System.out.printf("# Visits: %d%n", this.visitsOK.size());
        System.out.println("# QoS unmet");
    }

    private void unassignedUsersCannotBeServiced() {
        // All vehicles that lost requests rebalance to closest node (middle or next target)
        for (Vehicle vehicleDisrupted : this.vehiclesDisrupted) {

            assert vehicleDisrupted.getVisit().getPassengers().isEmpty() : "Interrupted vehicle had passenger!" + vehicleDisrupted.getVisit().getUserInfo() + " - VisitObj:" + vehicleDisrupted.getVisit();
            assert disruptedUsersAreServicedByDifferentVehicles(vehicleDisrupted) : "Disrupted users (unmatched) are not inserted in different vehicles.";
            /*for (User u : requestsFormerlyServicedByDisruptedVehicle) {
                assert u.getCurrentVisit() != null && u.getCurrentVisit() != vehicleDisrupted.getVisit() : String.format("User %s in vehicle %s is still associated with the vehicle", u, vehicleDisrupted);
            }*/

            /*for (User u : requestsFormerlyServicedByDisruptedVehicle) {
                assert u.getCurrentVisit() != null && u.getCurrentVisit() != vehicleDisrupted.getVisit() : String.format("User %s in vehicle %s is still associated with the vehicle", u, vehicleDisrupted);
            }*/
        }
    }

    public Set<User> getRequestsOK() {
        return requestsOK;
    }

    public List<User> getUnmetServiceLevelRequests() {
        return User.filterSecondTier(this.requestsOK);
    }

    public void setRequestsOK(Set<User> requestsOK) {
        this.requestsOK = requestsOK;
    }

    public Set<Vehicle> getVehiclesOK() {
        return vehiclesOK;
    }

    public void setVehiclesOK(Set<Vehicle> vehiclesOK) {
        this.vehiclesOK = vehiclesOK;
    }

    public Set<VisitObj> getVisitsOK() {
        return visitsOK;
    }

    public void setVisitsOK(Set<VisitObj> visitsOK) {
        this.visitsOK = visitsOK;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // ASSERTIONS //////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean disruptedUsersAreServicedByDifferentVehicles(Vehicle vehicleDisrupted) {

        Set<User> requestsFormerlyServicedByDisruptedVehicle = vehicleDisrupted.getVisit().getRequests();

        Set<User> requestsServicedByDifferentVehicles = requestsFormerlyServicedByDisruptedVehicle.stream()
                .filter(user -> user.getCurrentVisit().getVehicle() != vehicleDisrupted)
                .collect(Collectors.toSet());

        // Are all requests serviced in another visits?
        if (requestsServicedByDifferentVehicles.size() != requestsFormerlyServicedByDisruptedVehicle.size()) {
            requestsFormerlyServicedByDisruptedVehicle.removeAll(requestsServicedByDifferentVehicles);
            System.out.printf(" - Users %s from vehicle %s were left unmatched%n", requestsFormerlyServicedByDisruptedVehicle, vehicleDisrupted);
            return false;
        }

        return true;
    }


    public boolean assignedAndUnassignedAreDisjoint() {
        return Collections.disjoint(this.requestsOK, this.requestsUnassigned);

    }

    private List<User> getSortedListOfAssignedUsers() {
        List<User> listAssigned = new ArrayList<>(getRequestsOK());
        listAssigned.sort(Comparator.comparing(User::getPerformanceClass).thenComparing(User::getReqTime));
        return listAssigned;
    }

    protected void createFirstSecondTierListsFromServiced() {
        requestsServicedLevelAchieved.addAll(Visit.filterFirstTier(this.getVisitsOK()));
        requestsServicedLevelNotAchieved.addAll(Visit.filterSecondTier(this.getVisitsOK()));
    }

    public Set<User> getRequestsUnassigned() {
        return requestsUnassigned;
    }

    public void setRequestsUnassigned(Set<User> requestsUnassigned) {
        this.requestsUnassigned = requestsUnassigned;
    }

    public Set<Vehicle> getVehiclesHired() {
        return vehiclesHired;
    }

    public void setVehiclesHired(Set<Vehicle> vehiclesHired) {
        this.vehiclesHired = vehiclesHired;
    }

    public void accountRejected(User request) {
        requestsUnassigned.add(request);
        rejectedServiceLevelClass.computeIfPresent(request.qos, (qos, countRejected) -> countRejected + 1);

        // Rejected user was displaced from a routing plan
        if (request.getCurrentVisit() != null) {
//            request.setCurrentVisit(null);
            requestsDisplaced.add(request);
        }
    }

    public void accountMetServiceLevel(User request) {
        requestsServicedLevelAchieved.add(request);
    }

    public void accountUnmetServiceLevel(User request) {
        unmetServiceLevelClass.computeIfPresent(request.qos, (qos, countUnmet) -> countUnmet + 1);
        requestsServicedLevelNotAchieved.add(request);
    }

    public void accountNumberOfServiceLevelViolations(Qos qos, double nOfServiceLevelViolations) {
        violationCountClassServiceLevel.put(qos, nOfServiceLevelViolations);
    }

    public void accountNumberOfRequestsClass(Qos qos, int nOfRequestsPerClass) {
        nOfRequestsClass.put(qos, nOfRequestsPerClass);
    }

    /**
     * Show what each vehicle is doing:
     *  pos  (status) node-arrival(so far)
     *  4308 (S)4308-0/
     *  1776 (S)1776-0/
     *  3789 (S)3789-0/
     *  1229 (S)1229-0/
     *   554 (S)554-0/
     *  1686 (S)1686-0/
     *  1978 (S)1978-0/
     * @param listVehicles
     * @return
     */
    public String getSnapshot(Set<Vehicle> listVehicles) {
        StringBuilder builder = new StringBuilder();
        builder.append(this.currentTime);
        builder.append("*");
        for (Vehicle v : listVehicles) {
            builder.append(String.format("%5d ", v.getLastVisitedNode().getNetworkId()));
            if (v.getVisit() != null) {
                if (v.getVisit() instanceof VisitRelocation || v.getVisit() instanceof VisitDisplaceAndStop) {

                    builder.append("(R");
                    if (v.getVisit() instanceof VisitDisplaceAndStop){
                        builder.append("S");
                    }else{
                        builder.append(")");
                    }
                    builder.append(v.getTargetNode().getNetworkId());
                    builder.append("-");
                    builder.append(v.getTargetNode().getArrivalSoFar());
                } else {
                    builder.append("(V)");
                    for (Node n : v.getVisit().getSequenceVisits()) {
                        builder.append(n.getNetworkId());
                        builder.append("-");
                        builder.append(n.getArrivalSoFar());
                        builder.append(",");
                    }
                    builder.append("-d:");
                    builder.append(v.getVisit().getDelay());
                }
            } else {
                builder.append("(S)");
                builder.append(v.getLastVisitedNode().getNetworkId());
                builder.append("-");
                builder.append(v.getLastVisitedNode().getArrivalSoFar());
            }
            builder.append("/\n");
        }
        return builder.toString();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// SHOW ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void showSecondTierAssignedUsers() {
        List<User> listAssigned = getSortedListOfAssignedUsers();
        for (User user : listAssigned) {
            if (!user.isFirstTier())
                System.out.println(user.getCurrentAssigmentInfo());
        }
    }

    public void showFirstTierFromAssigned() {
        List<User> listAssigned = getSortedListOfAssignedUsers();
        for (User user : listAssigned) {
            if (user.isFirstTier())
                System.out.println(user.getCurrentAssigmentInfo());
        }
    }

    public Set<Vehicle> getVehiclesDisrupted() {
        return this.vehiclesDisrupted;
    }

    public VisitObj getChosenVisitForVehicle(Vehicle v) {
        return this.vehicleBestVisitMap.get(v);
    }
}
