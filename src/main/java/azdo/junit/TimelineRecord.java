package azdo.junit;

import azdo.utils.Log;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

import static azdo.utils.Constants.*;

/*
    A TimelineRecord contains information about a phase in the pipeline run. This phase can be of type
    "Stage", "Job", "Phase" (which is a Job), and "Task" (which represents all Steps).
    After retrieval of the TimelineRecords, this list is unsorted. The reorganize() method takes care
    that the records are sorted and a hierarchy is introduced. This improves readability of the log.
 */
public class TimelineRecord {
    private static Log logger = Log.getLogger();
    public ArrayList<TimelineRecord> reorganizedTimelineRecords = new ArrayList<>();
    public String id;
    public String parentId;
    public String type;
    public String name;
    public String startTime;
    public String finishTime;
    long timeInSeconds = 0;
    public String state;
    public String result;

    /*
        Sort the TimelineRecords and add a hierarchy
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
        }
    }

    /*
        Write all TimelineRecords to the log
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
                if ("Phase".equals(type)) {
                    tab = "   ";
                    displayedType = "Job"; // The type Phase is abstract and not for display purposes
                }
                if ("Task".equals(type)) {
                    tab = "      ";
                }
                logger.infoColor(color, tab + "{}: \"{}\"       Execution time: {} seconds       Status: {}", displayedType, name, timeInSeconds, result);
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
