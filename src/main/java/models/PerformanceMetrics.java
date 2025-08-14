package models;
import controllers.DroneSubsystem;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PerformanceMetrics - Collects and logs performance metrics for the drone fire fighting system
 * Tracks response times, fire extinguish times, and other relevant metrics
 */
public class PerformanceMetrics {
    // Singleton instance
    private static PerformanceMetrics instance;

    // File for logging metrics
    private final String logFile;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    // Counters for various metrics
    private final AtomicInteger totalFireEvents = new AtomicInteger(0);
    private final AtomicInteger totalExtinguishedFires = new AtomicInteger(0);

    // Maps to track timing information
    private final Map<Integer, Long> fireStartTimes = new ConcurrentHashMap<>();
    private final Map<Integer, Long> fireResponseTimes = new ConcurrentHashMap<>();
    private final Map<Integer, Long> fireExtinguishTimes = new ConcurrentHashMap<>();

    // Maps to track drone assignments
    private final Map<Integer, Integer> dronesAssignedPerFire = new ConcurrentHashMap<>();

    // Performance statistics
    private long totalResponseTime = 0;
    private long totalExtinguishTime = 0;
    private int responseTimeCount = 0;
    private int extinguishTimeCount = 0;

    // Drone utilization tracking
    private final Map<String, DroneMetrics> droneMetrics = new ConcurrentHashMap<>();

    /**
     * Inner class to track metrics for individual drones
     */
    public static class DroneMetrics {
        private final String droneId;
        private int missionsCompleted = 0;
        private int agentDrops = 0;
        private long totalTravelTime = 0;
        private long totalOperationTime = 0;
        private long idleTime = 0;
        private long lastStateChangeTime;
        private String currentState;

        public DroneMetrics(String droneId) {
            this.droneId = droneId;
            this.lastStateChangeTime = System.currentTimeMillis();
            this.currentState = "Idle";
        }

        public void incrementMissions() {
            missionsCompleted++;
        }

        public void incrementDrops() {
            agentDrops++;
        }

        public void addTravelTime(long time) {
            totalTravelTime += time;
        }

        public void addOperationTime(long time) {
            totalOperationTime += time;
        }

        public void updateState(String newState) {
            long now = System.currentTimeMillis();
            long duration = now - lastStateChangeTime;

            // Update time spent in previous state
            if ("Idle".equals(currentState)) {
                idleTime += duration;
            } else if ("EnRoute".equals(currentState)) {
                totalTravelTime += duration;
            } else if ("DroppingAgent".equals(currentState)) {
                totalOperationTime += duration;
            }

            // Update state and timestamp
            currentState = newState;
            lastStateChangeTime = now;
        }

        public double getUtilizationRate() {
            long totalTime = totalTravelTime + totalOperationTime + idleTime;
            if (totalTime == 0) return 0;
            return (double)(totalTravelTime + totalOperationTime) / totalTime;
        }

        @Override
        public String toString() {
            return String.format(
                    "Drone %s: Missions=%d, Drops=%d, Travel=%.2fs, Operation=%.2fs, Idle=%.2fs, Utilization=%.2f%%",
                    droneId, missionsCompleted, agentDrops,
                    totalTravelTime/1000.0, totalOperationTime/1000.0, idleTime/1000.0,
                    getUtilizationRate() * 100
            );
        }
    }

    /**
     * Private constructor for singleton pattern
     */
    private PerformanceMetrics(String logFile) {
        this.logFile = logFile;
        initializeLogFile();
    }

    /**
     * Get the singleton instance
     */
    public static synchronized PerformanceMetrics getInstance() {
        if (instance == null) {
            instance = new PerformanceMetrics("performance_metrics.log");
        }
        return instance;
    }

    /**
     * Initialize the log file with headers
     */
    private void initializeLogFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile))) {
            writer.println("# Drone Fire Fighting System Performance Metrics");
            writer.println("# Created: " + dateFormat.format(new Date()));
            writer.println("# Format: timestamp,event_type,zone_id,value,details");
            writer.println("# -------------------------------------------------------");
        } catch (IOException e) {
            System.err.println("Error initializing metrics log file: " + e.getMessage());
        }
    }

    /**
     * Log a metric to the file
     */
    private synchronized void logMetric(String eventType, int zoneId, double value, String details) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.printf("%s,%s,%d,%.3f,%s%n",
                    dateFormat.format(new Date()), eventType, zoneId, value, details);
        } catch (IOException e) {
            System.err.println("Error logging metric: " + e.getMessage());
        }
    }

    /**
     * Record the start of a fire event
     */
    public void recordFireStart(int zoneId, String severity) {
        long timestamp = System.currentTimeMillis();
        fireStartTimes.put(zoneId, timestamp);
        totalFireEvents.incrementAndGet();
        dronesAssignedPerFire.put(zoneId, 0);
        logMetric("FIRE_START", zoneId, 0, "severity=" + severity);
    }

    /**
     * Record a drone being assigned to a fire
     */
    public void recordDroneAssignment(int zoneId, String droneId) {
        long timestamp = System.currentTimeMillis();
        Long startTime = fireStartTimes.get(zoneId);

        if (startTime != null) {
            long responseTime = timestamp - startTime;

            // Only record the first response time for this fire
            if (!fireResponseTimes.containsKey(zoneId)) {
                fireResponseTimes.put(zoneId, responseTime);
                totalResponseTime += responseTime;
                responseTimeCount++;
                logMetric("RESPONSE_TIME", zoneId, responseTime / 1000.0, "drone=" + droneId);
            }
        }

        // Increment drone count for this fire
        dronesAssignedPerFire.compute(zoneId, (id, count) -> count == null ? 1 : count + 1);

        // Ensure we have metrics for this drone
        getDroneMetrics(droneId).updateState("EnRoute");

        logMetric("DRONE_ASSIGNED", zoneId, dronesAssignedPerFire.getOrDefault(zoneId, 0),
                "drone=" + droneId);
    }

    /**
     * Record a drone arriving at a fire location
     */
    public void recordDroneArrival(int zoneId, String droneId) {
        getDroneMetrics(droneId).updateState("DroppingAgent");
        logMetric("DRONE_ARRIVAL", zoneId, 0, "drone=" + droneId);
    }

    /**
     * Record a drone dropping agent
     */
    public void recordAgentDrop(int zoneId, String droneId) {
        getDroneMetrics(droneId).incrementDrops();
        logMetric("AGENT_DROP", zoneId, 0, "drone=" + droneId);
    }

    /**
     * Record a drone returning to base
     */
    public void recordDroneReturn(String droneId) {
        getDroneMetrics(droneId).updateState("ArrivedToBase");
        logMetric("DRONE_RETURN", -1, 0, "drone=" + droneId);
    }

    /**
     * Record a drone completing a mission
     */
    public void recordMissionComplete(int zoneId, String droneId) {
        getDroneMetrics(droneId).incrementMissions();
        getDroneMetrics(droneId).updateState("Idle");
        logMetric("MISSION_COMPLETE", zoneId, 0, "drone=" + droneId);
    }

    /**
     * Record a fire being extinguished
     */
    public void recordFireExtinguished(int zoneId) {
        long timestamp = System.currentTimeMillis();
        Long startTime = fireStartTimes.get(zoneId);

        if (startTime != null) {
            long extinguishTime = timestamp - startTime;
            fireExtinguishTimes.put(zoneId, extinguishTime);
            totalExtinguishTime += extinguishTime;
            extinguishTimeCount++;
            totalExtinguishedFires.incrementAndGet();

            int dronesUsed = dronesAssignedPerFire.getOrDefault(zoneId, 0);
            logMetric("FIRE_EXTINGUISHED", zoneId, extinguishTime / 1000.0,
                    "drones_used=" + dronesUsed);
        }
    }

    /**
     * Record a drone state change
     */
    public void recordDroneStateChange(String droneId, String newState) {
        getDroneMetrics(droneId).updateState(newState);
        logMetric("STATE_CHANGE", -1, 0, "drone=" + droneId + ",state=" + newState);
    }

    /**
     * Get or create metrics for a specific drone
     */
    private DroneMetrics getDroneMetrics(String droneId) {
        return droneMetrics.computeIfAbsent(droneId, DroneMetrics::new);
    }

    /**
     * Get average response time in milliseconds
     */
    public double getAverageResponseTime() {
        return responseTimeCount > 0 ? (double) totalResponseTime / responseTimeCount : 0;
    }

    /**
     * Get average fire extinguish time in milliseconds
     */
    public double getAverageExtinguishTime() {
        return extinguishTimeCount > 0 ? (double) totalExtinguishTime / extinguishTimeCount : 0;
    }

    /**
     * Get fire extinguish success rate
     */
    public double getFireExtinguishSuccessRate() {
        int total = totalFireEvents.get();
        return total > 0 ? (double) totalExtinguishedFires.get() / total : 0;
    }

    /**
     * Get average drones per fire
     */
    public double getAverageDronesPerFire() {
        if (dronesAssignedPerFire.isEmpty()) return 0;

        int totalDrones = 0;
        for (Integer count : dronesAssignedPerFire.values()) {
            totalDrones += count;
        }

        return (double) totalDrones / dronesAssignedPerFire.size();
    }

    /**
     * Get overall fleet utilization rate
     */
    public double getFleetUtilizationRate() {
        if (droneMetrics.isEmpty()) return 0;

        double totalUtilization = 0;
        for (DroneMetrics metrics : droneMetrics.values()) {
            totalUtilization += metrics.getUtilizationRate();
        }

        return totalUtilization / droneMetrics.size();
    }

    /**
     * Print a summary of performance metrics
     */
    public void printSummary() {
        System.out.println("\n=== PERFORMANCE METRICS SUMMARY ===");
        System.out.println("Total Fire Events: " + totalFireEvents.get());
        System.out.println("Total Extinguished Fires: " + totalExtinguishedFires.get());
        System.out.printf("Fire Extinguish Success Rate: %.2f%%%n", getFireExtinguishSuccessRate() * 100);
        System.out.printf("Average Response Time: %.2f seconds%n", getAverageResponseTime() / 1000.0);
        System.out.printf("Average Extinguish Time: %.2f seconds%n", getAverageExtinguishTime() / 1000.0);
        System.out.printf("Average Drones Per Fire: %.2f%n", getAverageDronesPerFire());
        System.out.printf("Fleet Utilization Rate: %.2f%%%n", getFleetUtilizationRate() * 100);

        System.out.println("\n--- Individual Drone Metrics ---");
        for (DroneMetrics metrics : droneMetrics.values()) {
            System.out.println(metrics);
        }
        System.out.println("=================================\n");
    }

    /**
     * Static helper method to integrate with DroneSubsystem
     */
    public static void recordDroneEvent(DroneSubsystem drone, String eventType, int zoneId) {
        PerformanceMetrics metrics = getInstance();
        String droneId = drone.getDroneId();

        switch (eventType) {
            case "FIRE_START":
                metrics.recordFireStart(zoneId, "UNKNOWN");
                break;
            case "DRONE_ASSIGNED":
                metrics.recordDroneAssignment(zoneId, droneId);
                break;
            case "DRONE_ARRIVAL":
                metrics.recordDroneArrival(zoneId, droneId);
                break;
            case "AGENT_DROP":
                metrics.recordAgentDrop(zoneId, droneId);
                break;
            case "DRONE_RETURN":
                metrics.recordDroneReturn(droneId);
                break;
            case "MISSION_COMPLETE":
                metrics.recordMissionComplete(zoneId, droneId);
                break;
            case "FIRE_EXTINGUISHED":
                metrics.recordFireExtinguished(zoneId);
                break;
            case "STATE_CHANGE":
                metrics.recordDroneStateChange(droneId, drone.getCurrentStateName());
                break;
        }
    }
}
