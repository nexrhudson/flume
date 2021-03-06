package com.nexr.rolling.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.util.ToolRunner;

import com.nexr.rolling.workflow.mapred.HourlyRollingMr;

public class DailyDedupJob {
	
	private static final Log log = LogFactory.getLog(DailyDedupJob.class);
	
	public static String SOURCE_PATH;
	public static String DAILY_MR_RESULT_PATH = "/daily-result";
	public static String DEDUP_HOURLY_PATH = "/dedup-daily";
	public static String DEDUP_MR_INPUT_PATH = "/dedupInput";
	public static String DEDUP_MR_OUTPUT_PATH = "/dedupOutput";

	final public static PathFilter SEQ_FILE_FILTER = new PathFilter() {
		public boolean accept(Path file) {
			return file.getName().startsWith("part");
		}
	};
	
	Configuration conf;
	FileSystem fs;
	
	public DailyDedupJob(String sourcePath) {
		SOURCE_PATH = sourcePath;
		conf = new Configuration();
		try {
			fs = FileSystem.get(conf);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public boolean initDedupMr() {
		boolean result = true;
		Path dedupInput = new Path(DEDUP_MR_INPUT_PATH);
		Path dedupOutput = new Path(DEDUP_MR_OUTPUT_PATH);
		try {
			if (!fs.exists(dedupInput)) {
				fs.mkdirs(dedupInput);
			}
			if (fs.exists(dedupOutput)) {
				fs.delete(dedupOutput);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			result = false;
			e.printStackTrace();
		}
		log.info("Success Initialization : Create " + DEDUP_MR_INPUT_PATH);
		return result;
	}

	
	public boolean moveDailyDedupMrData() {
		boolean result = true;
		int partCnt = 0;
		FileStatus[] parts;
		FileStatus[] datas;
		Path sourcePath = new Path(DAILY_MR_RESULT_PATH+SOURCE_PATH); // /daily-result/datatype/yyyyMMdd
		log.info(sourcePath.getParent() + File.separator +  sourcePath.getName());
		try {
			parts = fs.listStatus(sourcePath);
			for(FileStatus part : parts){
				result = fs.rename(new Path(part.getPath(), "data")
						, new Path(DEDUP_MR_INPUT_PATH + File.separator + "data_" + partCnt));
				
				
				log.info("Moving " + part.getPath()+"/data" + " to "
						+ DEDUP_MR_INPUT_PATH 
						+ File.separator 
						+ "data_" + partCnt 
						+ ", status is: "
						+ result);
				
				partCnt ++;
				fs.delete(part.getPath());
			}
			
			sourcePath = new Path(DEDUP_HOURLY_PATH+SOURCE_PATH); // /dedup-daily/datatype/yyyyMMdd
			parts = fs.listStatus(sourcePath);
			for(FileStatus part : parts){
				result = fs.rename(new Path(part.getPath(), "data"), 
						new Path(DEDUP_MR_INPUT_PATH + File.separator + "data_" + partCnt));
				fs.delete(new Path(part.getPath(), "index"));
				log.info("Moving " + part.getPath()+"/data" + " to "
						+ DEDUP_MR_INPUT_PATH 
						+ File.separator 
						+ "data_" + partCnt 
						+ ", status is: "
						+ result);
				
				partCnt ++;
				fs.delete(part.getPath());
			}
			
		}catch(IOException e){
			e.printStackTrace();
		}

		return result;
	}

	public boolean runDailyDedupMr() {
		List<String> params = new ArrayList<String>();
		params.add(DEDUP_MR_INPUT_PATH);
		params.add(DEDUP_MR_OUTPUT_PATH);

		try {
			String[] args = params.toArray(new String[params.size()]);
			return ToolRunner.run(conf, new DailyDedupMr(), args) == 0;
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean moveDailyMrResult() {
		boolean result = true;
		Path sourcePath = new Path(DEDUP_MR_OUTPUT_PATH);
		FileStatus[] dataType = null;
		FileStatus[] days = null;
		FileStatus[] files = null;
		try {
			if (!fs.exists(new Path(DAILY_MR_RESULT_PATH))) {
				fs.mkdirs(new Path(DAILY_MR_RESULT_PATH));
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		try {
			dataType = fs.listStatus(sourcePath);
			boolean rename;
			String dirName;
			for (FileStatus type : dataType) {
				days = fs.listStatus(new Path(sourcePath, type.getPath()
						.getName()));
				for (FileStatus day : days) {
					files = fs.listStatus(new Path(sourcePath, type.getPath()
							.getName()+File.separator+day.getPath()
						.getName()), SEQ_FILE_FILTER);
					for (FileStatus file : files) {
						log.info("Find File " + file.getPath());
						
						dirName = day.getPath().getName();
						
						Path dest = new Path(DAILY_MR_RESULT_PATH
								+ File.separator + type.getPath().getName()
								+ File.separator + day.getPath().getName());
								//+ File.separator + file.getPath().getName());
						log.info("Dest " + dest.toString());
						
						if (!fs.exists(dest)) {
							fs.mkdirs(dest);
							rename = fs.rename(file.getPath(), new Path(dest+File.separator+file.getPath().getName()));
						}else{
							//deduplication 실행
							rename = fs.rename(file.getPath(), new Path(dest+File.separator+file.getPath().getName()));
						}
					
						log.info("Moving " + file.getPath() + " to "
						+ new Path(dest+File.separator+file.getPath().getName()).toString() + " "
						+ rename);
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}

	public boolean endHourlyMr() {
		boolean result = true;
		try {
			fs = FileSystem.get(conf);
			fs.delete(new Path(DEDUP_MR_INPUT_PATH));
			fs.delete(new Path(DEDUP_MR_OUTPUT_PATH));
			fs.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			result = false;
			e.printStackTrace();
		}
		return result;
	}
	
	public static void main(String args[]){
		DailyDedupJob job = new DailyDedupJob("/tx/2011_05_16");
		job.initDedupMr();
		job.moveDailyDedupMrData();
		job.runDailyDedupMr();
		job.moveDailyMrResult();
		job.endHourlyMr();
		
	}
}
