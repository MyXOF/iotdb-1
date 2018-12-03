package cn.edu.tsinghua.iotdb.performance;

import cn.edu.tsinghua.iotdb.queryV2.factory.SeriesReaderFactory;
import cn.edu.tsinghua.tsfile.common.utils.ITsRandomAccessFileReader;
import cn.edu.tsinghua.tsfile.file.metadata.TimeSeriesMetadata;
import cn.edu.tsinghua.tsfile.file.metadata.TsDeltaObject;
import cn.edu.tsinghua.tsfile.file.metadata.TsFileMetaData;
import cn.edu.tsinghua.tsfile.timeseries.filter.TimeFilter;
import cn.edu.tsinghua.tsfile.timeseries.filter.basic.Filter;
import cn.edu.tsinghua.tsfile.timeseries.filter.expression.impl.SeriesFilter;
import cn.edu.tsinghua.tsfile.timeseries.filter.factory.FilterFactory;
import cn.edu.tsinghua.tsfile.timeseries.read.TsRandomAccessLocalFileReader;
import cn.edu.tsinghua.tsfile.timeseries.read.common.Path;
import cn.edu.tsinghua.tsfile.timeseries.readV2.datatype.TimeValuePair;
import cn.edu.tsinghua.tsfile.timeseries.readV2.reader.TimeValuePairReader;

import java.io.IOException;
import java.util.Map;

import static cn.edu.tsinghua.iotdb.performance.ReaderCreator.getTsFileMetadata;

public class TsFileReadPerformance {

    private static final String inputFilePath = "";

    public static void main(String[] args) throws IOException {
        long startTime = System.currentTimeMillis();

        ITsRandomAccessFileReader randomAccessFileReader = new TsRandomAccessLocalFileReader(inputFilePath);
        TsFileMetaData tsFileMetaData = getTsFileMetadata(randomAccessFileReader);

        long recordCount = 0;
        for (Map.Entry<String, TsDeltaObject> tsFileEntry : tsFileMetaData.getDeltaObjectMap().entrySet()) {
            String tsFileDeltaObjectId = tsFileEntry.getKey();
            long tsFileDeltaObjectStartTime = tsFileMetaData.getDeltaObject(tsFileDeltaObjectId).startTime;
            long tsFileDeltaObjectEndTime = tsFileMetaData.getDeltaObject(tsFileDeltaObjectId).endTime;
            for (TimeSeriesMetadata timeSeriesMetadata : tsFileMetaData.getTimeSeriesList()) {
                String measurementId = timeSeriesMetadata.getMeasurementUID();
                Filter<?> filter = FilterFactory.and(TimeFilter.gtEq(tsFileDeltaObjectStartTime), TimeFilter.ltEq(tsFileDeltaObjectEndTime));
                Path seriesPath = new Path(tsFileDeltaObjectId + "." + measurementId);
                SeriesFilter<?> seriesFilter = new SeriesFilter<>(seriesPath, filter);
                TimeValuePairReader tsFileReader = SeriesReaderFactory.getInstance().genTsFileSeriesReader(inputFilePath, seriesFilter);
                while (tsFileReader.hasNext()) {
                    TimeValuePair tp = tsFileReader.next();
                    recordCount++;
                }
                tsFileReader.close();
            }

        }
        long endTime = System.currentTimeMillis();
        System.out.println(String.format("read multi series time cost %dms, read record number is %d", endTime - startTime, recordCount));
    }
}