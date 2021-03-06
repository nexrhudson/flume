package com.nexr.rolling.workflow.job;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexr.framework.workflow.StepContext;
import com.nexr.rolling.workflow.RetryableDFSTaskletSupport;
import com.nexr.rolling.workflow.RollingConstants;

/**
 * 기초 데이터를 넣어주는 작업을 한다.
 * RESULT -> INPUT 으로 경로 변경
 * 
 * @author dani.kim@nexr.com
 */
public class PrepareTasklet extends RetryableDFSTaskletSupport {
	private Logger LOG = LoggerFactory.getLogger(getClass());

	final public static PathFilter DATA_FILTER = new PathFilter() {
		public boolean accept(Path file) {
			return file.getName().endsWith(".done");
		}
	};
	
	@Override
	public String doRun(StepContext context) {
		String jobType = context.getConfig().get(RollingConstants.JOB_TYPE, null);
		LOG.info("Prepare for M/R. jobType: {}, jobId: {}", new Object[] { jobType, context.getJobExecution().getKey() });
		Path sourcePath = new Path(context.get(RollingConstants.RAW_PATH, null));
		try {
			boolean isCollectorSource = context.getConfig().getBoolean(RollingConstants.IS_COLLECTOR_SOURCE, false);
			AtomicInteger fileCount = new AtomicInteger();
			int depth = renameTo(fs.listStatus(sourcePath), context.get(RollingConstants.INPUT_PATH, null), isCollectorSource, fileCount);
			if (fileCount.intValue() == 0) {
				LOG.info("Input Directory is empty.");
				return "cleanUp";
			}
			context.set(RollingConstants.INPUT_DEPTH, Integer.toString(depth));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return "run";
	}

	private int renameTo(FileStatus[] files, String destination, boolean isCollectorSource, AtomicInteger fileCount) throws IOException {
		int depth = 1;
		if (files != null) {
			for (FileStatus file : files) {
				if (file.isDir()) {
					depth += renameTo(fs.listStatus(file.getPath()), String.format("%s/%s", destination, file.getPath().getName()), isCollectorSource, fileCount);
				} else {
					Path destinationPath = new Path(destination);
					if (!fs.exists(destinationPath)) {
						fs.mkdirs(destinationPath);
					}
					fs.rename(file.getPath(), destinationPath);
					fileCount.incrementAndGet();
				}
			}
		}
		return depth;
	}
}
