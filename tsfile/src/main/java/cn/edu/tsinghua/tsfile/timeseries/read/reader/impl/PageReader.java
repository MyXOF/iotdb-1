package cn.edu.tsinghua.tsfile.timeseries.read.reader.impl;

import cn.edu.tsinghua.tsfile.common.exception.UnSupportedDataTypeException;
import cn.edu.tsinghua.tsfile.common.utils.Binary;
import cn.edu.tsinghua.tsfile.common.utils.ReadWriteForEncodingUtils;
import cn.edu.tsinghua.tsfile.encoding.decoder.Decoder;
import cn.edu.tsinghua.tsfile.file.metadata.enums.TSDataType;
import cn.edu.tsinghua.tsfile.timeseries.filter.basic.Filter;
import cn.edu.tsinghua.tsfile.timeseries.read.reader.BatchData;
import cn.edu.tsinghua.tsfile.timeseries.read.reader.Reader;

import java.io.IOException;
import java.nio.ByteBuffer;


public class PageReader implements Reader {

    private TSDataType dataType;

    // decoder for value column
    private Decoder valueDecoder;

    // decoder for time column
    private Decoder timeDecoder;

    // time column in memory
    private ByteBuffer timeBuffer;

    // value column in memory
    private ByteBuffer valueBuffer;

    private BatchData data = null;

    private Filter filter = null;

    public PageReader(ByteBuffer pageData, TSDataType dataType, Decoder valueDecoder, Decoder timeDecoder, Filter filter) {
        this(pageData, dataType, valueDecoder, timeDecoder);
        this.filter = filter;
    }


    public PageReader(ByteBuffer pageData, TSDataType dataType, Decoder valueDecoder, Decoder timeDecoder) {
        this.dataType = dataType;
        this.valueDecoder = valueDecoder;
        this.timeDecoder = timeDecoder;
        splitDataToTimeStampAndValue(pageData);
    }

    /**
     * split pageContent into two stream: time and value
     *
     * @param pageData uncompressed bytes size of time column, time column, value column
     * @throws IOException exception in reading data from pageContent
     */
    private void splitDataToTimeStampAndValue(ByteBuffer pageData) {
        int timeBufferLength = ReadWriteForEncodingUtils.readUnsignedVarInt(pageData);

        timeBuffer = pageData.slice();
        timeBuffer.limit(timeBufferLength);

        valueBuffer = pageData.slice();
        valueBuffer.position(timeBufferLength);
    }

    @Override
    public void close() {
        timeBuffer = null;
        valueBuffer = null;
    }


    @Override
    public boolean hasNextBatch() throws IOException {
        if (filter == null)
            data = getNextBatch();
        else
            data = getNextBatch(filter);
        return data.hasNext();
    }

    private BatchData getNextBatch() throws IOException {

        BatchData pageData = new BatchData(dataType, true);

        while (timeDecoder.hasNext(timeBuffer)) {
            long timestamp = timeDecoder.readLong(timeBuffer);

            pageData.putTime(timestamp);
            switch (dataType) {
                case BOOLEAN:
                    pageData.putBoolean(valueDecoder.readBoolean(valueBuffer));
                    break;
                case INT32:
                    pageData.putInt(valueDecoder.readInt(valueBuffer));
                    break;
                case INT64:
                    pageData.putLong(valueDecoder.readLong(valueBuffer));
                    break;
                case FLOAT:
                    pageData.putFloat(valueDecoder.readFloat(valueBuffer));
                    break;
                case DOUBLE:
                    pageData.putDouble(valueDecoder.readDouble(valueBuffer));
                    break;
                case TEXT:
                    pageData.putBinary(valueDecoder.readBinary(valueBuffer));
                    break;
                default:
                    throw new UnSupportedDataTypeException(String.valueOf(dataType));
            }
        }
        return pageData;
    }

    private BatchData getNextBatch(Filter filter) throws IOException {
        BatchData pageData = new BatchData(dataType, true);

        while (timeDecoder.hasNext(timeBuffer)) {
            long timestamp = timeDecoder.readLong(timeBuffer);

            switch (dataType) {
                case BOOLEAN:
                    boolean aBoolean = valueDecoder.readBoolean(valueBuffer);
                    if (filter.satisfy(timestamp, aBoolean)) {
                        pageData.putTime(timestamp);
                        pageData.putBoolean(aBoolean);
                    }
                    break;
                case INT32:
                    int anInt = valueDecoder.readInt(valueBuffer);
                    if (filter.satisfy(timestamp, anInt)) {
                        pageData.putTime(timestamp);
                        pageData.putInt(anInt);
                    }
                    break;
                case INT64:
                    long aLong = valueDecoder.readLong(valueBuffer);
                    if (filter.satisfy(timestamp, aLong)) {
                        pageData.putTime(timestamp);
                        pageData.putLong(aLong);
                    }
                    break;
                case FLOAT:
                    float aFloat = valueDecoder.readFloat(valueBuffer);
                    if (filter.satisfy(timestamp, aFloat)) {
                        pageData.putTime(timestamp);
                        pageData.putFloat(aFloat);
                    }
                    break;
                case DOUBLE:
                    double aDouble = valueDecoder.readDouble(valueBuffer);
                    if (filter.satisfy(timestamp, aDouble)) {
                        pageData.putTime(timestamp);
                        pageData.putDouble(aDouble);
                    }
                    break;
                case TEXT:
                    Binary aBinary = valueDecoder.readBinary(valueBuffer);
                    if (filter.satisfy(timestamp, aBinary)) {
                        pageData.putTime(timestamp);
                        pageData.putBinary(aBinary);
                    }
                    break;
                default:
                    throw new UnSupportedDataTypeException(String.valueOf(dataType));
            }
        }

        return pageData;
    }

    @Override
    public BatchData nextBatch() {
        return data;
    }

}