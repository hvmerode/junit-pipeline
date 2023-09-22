// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.junit;

import azdo.utils.Log;

import java.util.ArrayList;

import static azdo.utils.Constants.DEMARCATION;

/*
    The result of the pipeline run and its details are stored in RunResult
 */
public class RunResult {
    private static Log logger = Log.getLogger();
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


    public void reorganize() {
        logger.debug("==> Method: RunResult.reorganize");

        int size = timelineRecords.size();
        TimelineRecord timelineRecord;
        for (int counter = 0; counter < size; counter++) {
            timelineRecord = timelineRecords.get(counter);
            if (timelineRecord != null) {
                timelineRecord.reorganize(timelineRecords);
            }
        }
    }

    public void dumpTimelineToLog() {
        logger.debug("==> Method: RunResult.dumpTimelineToLog");

        logger.info(DEMARCATION);
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
        logger.info(DEMARCATION);
    }

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
        return getSectionResultSearchByDisplayName("Job", displayValue);
    }

    public Result getStepResultSearchByDisplayName(String displayValue) {
        logger.debug("==> Method: RunResult.getStepResultSearchByDisplayName");
        return getSectionResultSearchByDisplayName("Task", displayValue);
    }
}