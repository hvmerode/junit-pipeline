// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.junit;

public class RunResult {
    public Result result = Result.none;
    public Status status = Status.none;

    public static enum Result {
        canceled,
        failed,
        succeeded,
        partiallySucceeded,
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

    public RunResult ()
    {
    }

    public RunResult (String result, String status)
    {
        if (result != null)
            this.result = Result.valueOf(result);
        else
            this.result = Result.none;

        if (status != null)
            this.status = Status.valueOf(status);
        else
            this.status = Status.none;
    }
}
