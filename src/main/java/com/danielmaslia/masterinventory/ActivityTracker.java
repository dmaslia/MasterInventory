package com.danielmaslia.masterinventory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class ActivityTracker {
    private final LinkedList<Activity> activities = new LinkedList<>();
    private static final int MAX_ACTIVITIES = 100;
    private final File dataFolder;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    public record Activity(long timestamp, String playerName, String type, String details) {
        public String getFormattedTime() {
            return TIME_FORMAT.format(new Date(timestamp));
        }
    }

    public ActivityTracker(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public synchronized void logActivity(String playerName, String type, String details) {
        Activity activity = new Activity(System.currentTimeMillis(), playerName, type, details);
        activities.addFirst(activity);

        while (activities.size() > MAX_ACTIVITIES) {
            activities.removeLast();
        }
    }

    public synchronized List<Activity> getRecentActivities() {
        return new ArrayList<>(activities);
    }

    public synchronized void exportToCSV() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File file = new File(dataFolder, "activities.csv");

        try (PrintWriter pw = new PrintWriter(new FileWriter(file, false))) {
            pw.println("time,player,type,details");

            for (Activity activity : activities) {
                String escapedDetails = activity.details().replace("\"", "\"\"");
                if (escapedDetails.contains(",") || escapedDetails.contains("\"")) {
                    escapedDetails = "\"" + escapedDetails + "\"";
                }
                pw.println(activity.getFormattedTime() + "," +
                          activity.playerName() + "," +
                          activity.type() + "," +
                          escapedDetails);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized int getActivityCount() {
        return activities.size();
    }

    public synchronized void clear() {
        activities.clear();
    }
}
