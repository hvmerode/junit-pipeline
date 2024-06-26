package azdo.junit;

import azdo.utils.Log;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

import static azdo.utils.Constants.*;

/******************************************************************************************
 A TimelineRecord contains information about a phase in the pipeline run. This phase can
 be of type "Stage", "Job", "Phase" (which is a Job), and "Task" (which represents
 all Steps).
 *******************************************************************************************/
public class TimelineRecord {
    private static final Log logger = Log.getLogger();
    public ArrayList<TimelineRecord> reorganizedTimelineRecords = new ArrayList<>();
    public String id;
    public String parentId;
    public String type;
    public String name;
    public String startTime;
    public String finishTime;
    long timeInSeconds = 0;
    String timeInSecondsAsString = "<1";
    public String state;
    public String result;

    /*
        Add a hierarchy to the TimelineRecords
     */
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
        if (!(startTime == null || startTime.isEmpty() || finishTime == null || finishTime.isEmpty())) {
            Instant start = Instant.parse(startTime);
            Instant finish = Instant.parse(finishTime);
            Duration res = Duration.between(start, finish);
            timeInSeconds = res.getSeconds();
            if (timeInSeconds > 0)
                timeInSecondsAsString = String.valueOf(timeInSeconds);
        }
        timeInSecondsAsString = timeInSecondsAsString + " sec.";
    }

    /*
        Write all TimelineRecords to the log in a formatted way.
     */
    public void dumpTimelineToLog () {
        logger.debug("==> Method: TimelineRecord.dumpTimelineToLog");

        // Only take a subset of types into account
        if ("Stage".equals(type) || "Phase".equals(type) || "Job".equals(type) || "Task".equals(type)) {

            String displayedType = type;
            boolean logDetails = true;

            if ("Phase".equals(type) && !RunResult.Result.skipped.toString().equals(result)) {
                // Use the Phase instead of the Job in case the job was skipped
                logDetails = false;
            }

            if (logDetails) {
                String color = LIGHT_GREEN;
                String arrow = ARROW_DOWN;
                if (RunResult.Result.failed.toString().equals(result))
                    color = LIGHT_RED;
                if (RunResult.Result.skipped.toString().equals(result)) {
                    color = LIGHT_WHITE;
                    arrow = " ";
                }
                if (RunResult.Result.canceled.toString().equals(result))
                    color = YELLOW;
                if (RunResult.Result.partiallySucceeded.toString().equals(result))
                    color = YELLOW;
                if (RunResult.Result.succeededWithIssues.toString().equals(result))
                    color = YELLOW;

                if ("Stage".equals(type)) {
                    displayedType = "Stage " + arrow + "      ";
                }
                if ("Job".equals(type)) {
                    displayedType = "Job " + arrow + "  ";
                }
                if ("Phase".equals(type)) {
                    displayedType = "Job " + arrow + "  "; // The type Phase is abstract and not for display purposes
                }
                if ("Task".equals(type)) {
                    displayedType = "Task";
                }
                String out = String.format("%14s %80s %16s %15s", displayedType, name, timeInSecondsAsString, result);
                logger.infoColor(color, out);
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
