package controllers;

import models.FireEvent;
import models.PerformanceMetrics;
import java.util.Timer;
import java.util.TimerTask;

/**
 * PerformanceMonitor - Integrates with the drone system to collect performance metrics
 * Hooks into key points in the system to record events and generate reports
 */
public class PerformanceMonitor {
    private final PerformanceMetrics metrics;
    private final Timer reportTimer;
    private static PerformanceMonitor instance;

    /**
     * Private constructor for singleton pattern
     */
    private PerformanceMonitor() {
        this.metrics = PerformanceMetrics.getInstance();
        this.reportTimer = new Timer("PerformanceReportTimer", true);

        // Schedule periodic reporting
        reportTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                generateReport();
            }
        }, 60000, 60000); // Generate report every minute
    }

    /**
     * Get the singleton instance
     */
    public static synchronized PerformanceMonitor getInstance() {
        if (instance == null) {
            instance = new PerformanceMonitor();
        }
        return instance;
    }

    /**
     * Generate a performance report
     */
    public void generateReport() {
        metrics.printSummary();
    }

    /**
     * Hook into DroneSubsystem to monitor state changes
     */
    public void monitorDroneState(DroneSubsystem drone, String oldState, String newState) {
        metrics.recordDroneStateChange(drone.getDroneId(), newState);
    }

    /**
     * Hook into fire event handling
     */
    public void monitorFireEvent(FireEvent event, DroneSubsystem drone) {
        int zoneId = event.getZoneID();

        // Record fire start if this is a new fire
        if (event.getEventType().equalsIgnoreCase("FIRE")) {
            metrics.recordFireStart(zoneId, event.getSeverity());
        }

        // Record drone assignment
        metrics.recordDroneAssignment(zoneId, drone.getDroneId());
    }

    /**
     * Hook into agent dropping
     */
    public void monitorAgentDrop(DroneSubsystem drone, FireEvent event) {
        if (event != null) {
            int zoneId = event.getZoneID();
            metrics.recordAgentDrop(zoneId, drone.getDroneId());
        }
    }

    /**
     * Hook into fire extinguished events
     */
    public void monitorFireExtinguished(int zoneId) {
        metrics.recordFireExtinguished(zoneId);
    }

    /**
     * Hook into mission completion
     */
    public void monitorMissionComplete(DroneSubsystem drone, FireEvent event) {
        if (event != null) {
            int zoneId = event.getZoneID();
            metrics.recordMissionComplete(zoneId, drone.getDroneId());
        }
    }

    /**
     * Shutdown the monitor
     */
    public void shutdown() {
        reportTimer.cancel();
        generateReport(); // Generate final report
    }
}