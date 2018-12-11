//package cn.edu.tsinghua.iotdb.read.reader;
//
//import cn.edu.tsinghua.iotdb.engine.filenode.IntervalFileNode;
//import cn.edu.tsinghua.iotdb.engine.querycontext.GlobalSortedSeriesDataSource;
//import cn.edu.tsinghua.iotdb.engine.querycontext.UnsealedTsFile;
//import cn.edu.tsinghua.iotdb.queryV2.engine.control.OverflowFileStreamManager;
//import cn.edu.tsinghua.iotdb.queryV2.engine.reader.PriorityMergeReaderByTimestamp;
//import cn.edu.tsinghua.iotdb.queryV2.engine.reader.PrioritySeriesReader;
//import cn.edu.tsinghua.iotdb.queryV2.engine.reader.PrioritySeriesReaderByTimestamp;
//import cn.edu.tsinghua.iotdb.queryV2.engine.reader.SeriesReaderByTimeStamp;
//import cn.edu.tsinghua.iotdb.queryV2.engine.reader.series.RawSeriesChunkReaderByTimestamp;
//import cn.edu.tsinghua.iotdb.utils.TimeValuePair;
//import cn.edu.tsinghua.iotdb.utils.TsPrimitiveType;
//
//import java.io.IOException;
//import java.io.RandomAccessFile;
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * A reader for sequence insert data which can get the corresponding value of the specified time point.
// */
//public class SequenceInsertDataByTimeStampReader extends SequenceDataReader implements SeriesReaderByTimeStamp {
//
//  private long currentTimestamp;
//  private PriorityMergeReaderByTimestamp priorityMergeSortTimeValuePairReader;
//
//  public SequenceInsertDataByTimeStampReader(GlobalSortedSeriesDataSource sortedSeriesDataSource)
//          throws IOException {
//    super(sortedSeriesDataSource);
//
//    List<PrioritySeriesReaderByTimestamp> priorityTimeValuePairReaderByTimestamps = new ArrayList<>();
//    int priority = 1;
//
//    //data in sealedTsFiles and unSealedTsFile
//    if (sortedSeriesDataSource.getSealedTsFiles() != null) {
//      SealedTsFileWithTimeStampReader sealedTsFileWithTimeStampReader = new SealedTsFileWithTimeStampReader(sortedSeriesDataSource.getSealedTsFiles());
//      priorityTimeValuePairReaderByTimestamps.add(new PrioritySeriesReaderByTimestamp(sealedTsFileWithTimeStampReader, new PrioritySeriesReader.Priority(priority++)));
//    }
//    if (sortedSeriesDataSource.getUnsealedTsFile() != null) {
//      UnSealedTsFileWithTimeStampReader unSealedTsFileWithTimeStampReader = new UnSealedTsFileWithTimeStampReader(sortedSeriesDataSource.getUnsealedTsFile());
//      priorityTimeValuePairReaderByTimestamps.add(new PrioritySeriesReaderByTimestamp(unSealedTsFileWithTimeStampReader, new PrioritySeriesReader.Priority(priority++)));
//    }
//    //data in memTable
//    if (sortedSeriesDataSource.hasRawSeriesChunk()) {
//      RawSeriesChunkReaderByTimestamp rawSeriesChunkReaderByTimestamp = new RawSeriesChunkReaderByTimestamp(sortedSeriesDataSource.getRawSeriesChunk());
//      priorityTimeValuePairReaderByTimestamps.add(new PrioritySeriesReaderByTimestamp(rawSeriesChunkReaderByTimestamp, new PrioritySeriesReader.Priority(priority++)));
//    }
//
//    priorityMergeSortTimeValuePairReader = new PriorityMergeReaderByTimestamp(priorityTimeValuePairReaderByTimestamps);
//    currentTimestamp = Long.MIN_VALUE;
//  }
//
//  @Override
//  public boolean hasNext() throws IOException {
//    return priorityMergeSortTimeValuePairReader.hasNext();
//  }
//
//  @Override
//  public TimeValuePair next() throws IOException {
//    return priorityMergeSortTimeValuePairReader.next();
//  }
//
//  /**
//   * @param timestamp
//   * @return If there is no TimeValuePair whose timestamp equals to given timestamp, then return null.
//   * @throws IOException
//   */
//  @Override
//  public TsPrimitiveType getValueInTimestamp(long timestamp) throws IOException {
//    setCurrentTimestamp(timestamp);
//    return priorityMergeSortTimeValuePairReader.getValueInTimestamp(timestamp);
//  }
//
//
//  public void setCurrentTimestamp(long currentTimestamp) {
//    this.currentTimestamp = currentTimestamp;
//  }
//
//  private class SealedTsFileWithTimeStampReader extends SequenceDataReader.SealedTsFileReader implements SeriesReaderByTimeStamp {
//
//    private boolean hasCacheLastTimeValuePair;
//    private TimeValuePair cachedTimeValuePair;
//
//    private SealedTsFileWithTimeStampReader(List<IntervalFileNode> sealedTsFiles) {
//      super(sealedTsFiles);
//      hasCacheLastTimeValuePair = false;
//    }
//
//    @Override
//    public boolean hasNext() throws IOException {
//      //hasCached
//      if (hasCacheLastTimeValuePair && cachedTimeValuePair.getTimestamp() >= currentTimestamp) {
//        return true;
//      }
//      //singleTsFileReader has initialized
//      if (singleTsFileReaderInitialized) {
//        TsPrimitiveType value = ((SeriesReaderFromSingleFileByTimestampImpl) singleTsFileReader).getValueInTimestamp(currentTimestamp);
//        if (value != null) {
//          hasCacheLastTimeValuePair = true;
//          cachedTimeValuePair = new TimeValuePair(currentTimestamp, value);
//          return true;
//        } else {
//          if (singleTsFileReader.hasNext()) {
//            return true;
//          } else {
//            singleTsFileReaderInitialized = false;
//          }
//        }
//      }
//
//      while ((usedIntervalFileIndex + 1) < sealedTsFiles.size()) {
//        if (!singleTsFileReaderInitialized) {
//          IntervalFileNode fileNode = sealedTsFiles.get(++usedIntervalFileIndex);
//          //currentTimestamp<=maxTimestamp
//          if (singleTsFileSatisfied(fileNode)) {
//            initSingleTsFileReader(fileNode);
//            singleTsFileReaderInitialized = true;
//          } else {
//            continue;
//          }
//        }
//        //singleTsFileReader has already initialized
//        TsPrimitiveType value = ((SeriesReaderFromSingleFileByTimestampImpl) singleTsFileReader).getValueInTimestamp(currentTimestamp);
//        if (value != null) {
//          hasCacheLastTimeValuePair = true;
//          cachedTimeValuePair = new TimeValuePair(currentTimestamp, value);
//          return true;
//        } else {
//          if (singleTsFileReader.hasNext()) {
//            return true;
//          } else {
//            singleTsFileReaderInitialized = false;
//          }
//        }
//      }
//      return false;
//    }
//
//    @Override
//    public TimeValuePair next() throws IOException {
//      if (hasCacheLastTimeValuePair) {
//        hasCacheLastTimeValuePair = false;
//        return cachedTimeValuePair;
//      } else {
//        return singleTsFileReader.next();
//      }
//    }
//
//    protected boolean singleTsFileSatisfied(IntervalFileNode fileNode) {
//
//      if (fileNode.getStartTime(path.getDevice()) == -1) {
//        return false;
//      }
//      long maxTime = fileNode.getEndTime(path.getDevice());
//      return currentTimestamp <= maxTime;
//    }
//
//    protected void initSingleTsFileReader(IntervalFileNode fileNode) throws IOException {
//      RandomAccessFile raf = OverflowFileStreamManager.getInstance().get(jobId, fileNode.getFilePath());
//      ITsRandomAccessFileReader randomAccessFileReader = new TsRandomAccessLocalFileReader(raf);
//      singleTsFileReader = new SeriesReaderFromSingleFileByTimestampImpl(randomAccessFileReader, path);
//    }
//
//    @Override
//    public TsPrimitiveType getValueInTimestamp(long timestamp) throws IOException {
//      if (hasNext()) {
//        cachedTimeValuePair = next();
//        if (cachedTimeValuePair.getTimestamp() == timestamp) {
//          return cachedTimeValuePair.getValue();
//        } else {
//          hasCacheLastTimeValuePair = true;
//        }
//      }
//      return null;
//    }
//  }
//
//  protected class UnSealedTsFileWithTimeStampReader extends SequenceDataReader.UnSealedTsFileReader implements SeriesReaderByTimeStamp {
//
//    public UnSealedTsFileWithTimeStampReader(UnsealedTsFile unsealedTsFile) throws IOException {
//      super(unsealedTsFile);
//    }
//
//    @Override
//    public boolean hasNext() throws IOException {
//      return singleTsFileReader.hasNext();
//    }
//
//    @Override
//    public TimeValuePair next() throws IOException {
//      return singleTsFileReader.next();
//    }
//
//    protected void initSingleTsFileReader(ITsRandomAccessFileReader randomAccessFileReader,
//                                          SeriesChunkLoader seriesChunkLoader, List<EncodedSeriesChunkDescriptor> encodedSeriesChunkDescriptorList) {
//      singleTsFileReader = new SeriesReaderFromSingleFileByTimestampImpl(randomAccessFileReader, seriesChunkLoader, encodedSeriesChunkDescriptorList);
//    }
//
//    @Override
//    public TsPrimitiveType getValueInTimestamp(long timestamp) throws IOException {
//      return ((SeriesReaderByTimeStamp) singleTsFileReader).getValueInTimestamp(timestamp);
//    }
//  }
//
//}
