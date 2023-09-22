package azdo.junit;

import azdo.utils.Log;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

import static azdo.utils.Constants.*;

public class TimelineRecord {
    private static Log logger = Log.getLogger();
    public ArrayList<TimelineRecord> reorganizedTimelineRecords = new ArrayList<>();
    public String id;
    public String parentId;
    public String type;
    public String name;
    public String startTime;
    public String finishTime;
    long timeInSeconds;
    public String state;
    public String result;

    public void reorganize (ArrayList<TimelineRecord> timelineRecords) {
        logger.debug("==> Method: TimelineRecord.reorganize");

        int size = timelineRecords.size();
        TimelineRecord timelineRecord;
        for (int counter = 0; counter < size; counter++) {
            timelineRecord = timelineRecords.get(counter);
            logger.debug("Type (parent) is: {}", type);
            if (timelineRecord != null) {
                if (timelineRecord.parentId.equals(id)) {
                    // It is a child
                    logger.debug("Child found");
                    reorganizedTimelineRecords.add(timelineRecord);
                }
            }
        }

        // Calculate the execution time
        Instant start = Instant.parse(startTime);
        Instant finish = Instant.parse(finishTime);
        Duration res = Duration.between(start, finish);
        timeInSeconds = res.getSeconds();
    }

    public void dumpTimelineToLog () {
        logger.debug("==> Method: TimelineRecord.dumpTimelineToLog");

        // Only take a subset of types into account
        if ("Stage".equals(type) || "Phase".equals(type) || "Job".equals(type) || "Task".equals(type)) {

            // Skip the Phase, but it is needed to traverse through the tree
            if (!"Phase".equals(type)) {
                String color = LIGHT_GREEN;
                String tab = "";
                if (RunResult.Result.failed.toString().equals(result))
                    color = LIGHT_RED;
                if (RunResult.Result.skipped.toString().equals(result))
                    color = LIGHT_WHITE;
                if (RunResult.Result.canceled.toString().equals(result))
                    color = YELLOW;
                if (RunResult.Result.partiallySucceeded.toString().equals(result))
                    color = YELLOW;

                if ("Job".equals(type)) {
                    tab = "   ";
                }
                if ("Task".equals(type)) {
                    tab = "      ";
                }
                logger.infoColor(color, tab + "{}: \"{}\"       Execution time: {} seconds       Status: {}", type, name, timeInSeconds, result);
            }

            int size = reorganizedTimelineRecords.size();
            TimelineRecord timelineRecord;
            for (int counter = 0; counter < size; counter++) {
                timelineRecord = reorganizedTimelineRecords.get(counter);
                if (timelineRecord != null) {
                    timelineRecord.dumpTimelineToLog();
                }
            }
        }
    }
}
