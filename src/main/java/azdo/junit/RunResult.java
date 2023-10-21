// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.junit;

import azdo.utils.Log;

import java.util.ArrayList;

import static azdo.utils.Constants.*;

/******************************************************************************************
 The result of the pipeline run and its details are stored in RunResult.
 *******************************************************************************************/
public class RunResult {
    private static final Log logger = Log.getLogger();
    public String buildId = null;

    public Result result = Result.none;
    public Status status = Status.none;
    private ArrayList<TimelineRecord> timelineRecords = new ArrayList<>();

    public static enum Result {
        canceled,
        failed,
        succeeded,
        partiallySucceeded,
        skipped,
        undetermined,
        none
    }

    public static enum Status {
        all,
        cancelling,
        completed,
        inProgress,
        notStarted,
        postponed,
        timeout,
        none
    }

    public RunResult() {
    }

    public RunResult(String result, String status, String buildId) {
        if (result != null)
            this.result = Result.valueOf(result);
        else
            this.result = Result.none;

        if (status != null)
            this.status = Status.valueOf(status);
        else
            this.status = Status.none;

        this.buildId = buildId;
    }

    public void addTimelineRecord(TimelineRecord timelineRecord) {
        timelineRecords.add(timelineRecord);
    }

    public ArrayList<TimelineRecord> getTimelineRecords() {
        return timelineRecords;
    }


/*
    The list of TimelineRecords contains information about each phase in the pipeline run. A phase can be of type
    "Stage", "Job", "Phase" (which is an abstract representation of a Job), and "Task" (which represents all Steps).
    After retrieval of the TimelineRecords, this list is unsorted. The reorganize() method takes care
    that the records are sorted and a hierarchy is introduced. This improves readability of the log.
 */
    public void reorganize() {
        logger.debug("==> Method: RunResult.reorganize");

        // Sort the list first; this properly displays the order of the phases in the pipeline (in most of the cases)
        sort(timelineRecords);

        int size = timelineRecords.size();
        TimelineRecord timelineRecord;
        for (int counter = 0; counter < size; counter++) {
            timelineRecord = timelineRecords.get(counter);
            if (timelineRecord != null) {
                timelineRecord.reorganize(timelineRecords);
            }
        }
    }

    /*
        Sort the TimelineRecord arraylist
     */
    private static void sort(ArrayList<TimelineRecord> list) {
        list.sort((o1, o2)
                -> o1.startTime.compareTo(
                        o2.startTime));
    }

    /*
        Write all TimelineRecords to the log
     */
    public void dumpTimelineToLog() {
        logger.debug("==> Method: RunResult.dumpTimelineToLog");

        logger.info("");
        logger.info(HEADER_FOOTER);
        String header = String.format("%14s %80s %16s %15s", "Type", "Name", "Execution time", "Result");
        logger.info(header);
        logger.info(HEADER_FOOTER);
        int size = timelineRecords.size();
        TimelineRecord timelineRecord;
        for (int counter = 0; counter < size; counter++) {
            timelineRecord = timelineRecords.get(counter);
            if (timelineRecord != null) {
                if ("Stage".equals(timelineRecord.type)) {
                    timelineRecord.dumpTimelineToLog();
                }
            }
        }
        logger.info(HEADER_FOOTER);
    }


    /*
        Return a TimelineRecord of a certain type and certain name (= displayValue)
     */
    public Result getSectionResultSearchByDisplayName(String sectionType,
                                                      String displayValue) {
        logger.debug("==> Method: RunResult.getSectionResultSearchByDisplayName");

        int size = timelineRecords.size();
        TimelineRecord timelineRecord;
        for (int counter = 0; counter < size; counter++) {
            timelineRecord = timelineRecords.get(counter);
            if (timelineRecord != null) {
                if (timelineRecord.type.equals(sectionType)) {
                    if (timelineRecord.name.equals(displayValue)) {
                        logger.debug("Found {}", displayValue);
                        return Result.valueOf(timelineRecord.result);
                    }
                }
            }
        }

        return Result.none;
    }

    public Result getStageResultSearchByDisplayName(String displayValue) {
        logger.debug("==> Method: RunResult.getStageResultSearchByDisplayName");
        return getSectionResultSearchByDisplayName("Stage", displayValue);
    }

    public Result getJobResultSearchByDisplayName(String displayValue) {
        logger.debug("==> Method: RunResult.getJobResultSearchByDisplayName");
        Result res = Result.none;
        res = getSectionResultSearchByDisplayName("Job", displayValue);
        if (res == Result.none) {
            // Not Job found (for example, if the Job was skipped); try the Phase instead
            res = getSectionResultSearchByDisplayName("Phase", displayValue);
        }

        return res;
    }

    public Result getStepResultSearchByDisplayName(String displayValue) {
        logger.debug("==> Method: RunResult.getStepResultSearchByDisplayName");
        return getSectionResultSearchByDisplayName("Task", displayValue);
    }
}